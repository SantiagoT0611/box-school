package com.storres.box_school.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.UriComponentsBuilder;

import com.storres.box_school.config.AppProperties;
import com.storres.box_school.exception.InvalidPasswordChangeException;
import com.storres.box_school.exception.InvalidRegistrationException;
import com.storres.box_school.exception.UsernameAlreadyExistsException;
import com.storres.box_school.mapper.StudentMapper;
import com.storres.box_school.model.dto.AuthResponse;
import com.storres.box_school.model.dto.ChangePasswordRequest;
import com.storres.box_school.model.dto.ForgotPasswordRequest;
import com.storres.box_school.model.dto.LoginRequest;
import com.storres.box_school.model.dto.RegisterRequest;
import com.storres.box_school.model.dto.ResetPasswordRequest;
import com.storres.box_school.model.entity.Student;
import com.storres.box_school.model.entity.User;
import com.storres.box_school.model.shared.Roles;
import com.storres.box_school.model.shared.Status;
import com.storres.box_school.repository.StudentRepository;
import com.storres.box_school.repository.UserRepository;
import com.storres.box_school.security.JwtService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthServiceImpl.class);

    private final UserRepository userRepository;
    private final StudentRepository studentRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final CustomUserDetailsService userDetailsService;
    private final RegistrationCodeService registrationCodeService;
    private final PasswordResetService passwordResetService;
    private final MailService mailService;
    private final AppProperties props;

    @Override
    public AuthResponse login(LoginRequest request) {
        String username = normalizeUsername(request.getUsername());

        // Lanza BadCredentials/Disabled si falla (el handler global las traduce a 401 uniforme)
        var authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(username, request.getPassword()));

        log.info("Login correcto de '{}'", username);
        return buildResponse((UserDetails) authentication.getPrincipal());
    }

    /**
     * Activa la cuenta de un estudiante ya inscrito por el admin. Exige email + codigo de un solo uso:
     * conocer el email de alguien NO basta para apropiarse de su cuenta (ver D-004).
     * Todos los fallos de identidad devuelven el mismo error generico para no permitir enumeracion.
     */
    @Override
    @Transactional
    public AuthResponse register(RegisterRequest request) {
        // BCrypt (50-100 ms) ANTES de tomar el bloqueo de fila: no se retiene el candado del estudiante mientras
        // se calcula el hash. Se descarta si el registro termina rechazado.
        String encodedPassword = passwordEncoder.encode(request.getPassword());

        // Con bloqueo de fila: serializa con deactivate/pagos. Sin el, un estudiante desactivado entre la lectura y
        // el commit quedaba con la cuenta HABILITADA (re-auditoria final, NR-1).
        Student student = studentRepository.findByEmailForUpdate(StudentMapper.normalizeEmail(request.getEmail()))
                .orElseThrow(InvalidRegistrationException::new);

        if (student.getStatus() != Status.ACTIVE
                || userRepository.existsByStudentId(student.getId())
                || !registrationCodeService.matches(student, request.getRegistrationCode())) {
            log.warn("Registro rechazado para student id={}", student.getId());
            throw new InvalidRegistrationException();
        }

        String username = normalizeUsername(request.getUsername());
        if (userRepository.existsByUsername(username)) {
            throw new UsernameAlreadyExistsException("El nombre de usuario ya está en uso");
        }

        User user = User.builder()
                .username(username)
                .password(encodedPassword)
                .role(Roles.ROLE_USER)
                .enabled(true)
                .student(student)
                .build();
        userRepository.save(user);
        registrationCodeService.consume(student); // un solo uso

        log.info("Cuenta activada para student id={}", student.getId());
        return buildResponse(userDetailsService.loadUserByUsername(username));
    }

    /**
     * @Async para que quien pregunta por un email existente y quien pregunta por uno inexistente reciban la
     * respuesta en el mismo tiempo (la diferencia seria una escritura en la BD y el correo). Un fallo aqui solo se
     * registra: el cliente ya recibio el 202 generico.
     */
    @Override
    @Async
    public void forgotPassword(ForgotPasswordRequest request) {
        try {
            passwordResetService.issueFor(request.getEmail()).ifPresent(this::sendResetLink);
        } catch (RuntimeException e) {
            // Sin mensaje de la excepcion: podria incluir datos de la cuenta. Dos solicitudes simultaneas de la
            // misma cuenta chocan en la restriccion unica y una sola gana: es esperable, no un error.
            log.warn("No se pudo procesar una solicitud de recuperacion: {}", e.getClass().getSimpleName());
        }
    }

    @Override
    public void resetPassword(ResetPasswordRequest request) {
        var changed = passwordResetService.resetPassword(request.getToken(), request.getNewPassword());
        if (changed.email() != null) {
            mailService.sendQuietly(changed.email(), "Tu contraseña fue cambiada",
                    "Hola " + changed.firstName() + ",\n\n"
                            + "La contraseña de tu cuenta en la escuela de boxeo se restableció. Ya puedes iniciar "
                            + "sesión con la nueva.\n"
                            + "Si no fuiste tú, comunícate con la escuela de inmediato.");
        }
    }

    @Override
    @Transactional
    public AuthResponse changePassword(String username, ChangePasswordRequest request) {
        User user = userRepository.findWithStudentByUsername(normalizeUsername(username))
                .orElseThrow(() -> new BadCredentialsException("Usuario no encontrado"));

        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPassword())) {
            log.warn("Cambio de clave rechazado (clave actual incorrecta): user id={}", user.getId());
            throw new InvalidPasswordChangeException("La clave actual es incorrecta");
        }
        if (request.getCurrentPassword().equals(request.getNewPassword())) {
            throw new InvalidPasswordChangeException("La clave nueva debe ser distinta de la actual");
        }

        Student student = user.getStudent();
        // Condicional (enabled = true): una cuenta deshabilitada entre medio no cambia nada
        if (userRepository.changePassword(user.getId(), passwordEncoder.encode(request.getNewPassword())) != 1) {
            throw new DisabledException("Cuenta deshabilitada");
        }
        log.info("Clave cambiada por el usuario: user id={}", user.getId());

        if (student != null) {
            mailService.sendQuietly(student.getEmail(), "Tu contraseña fue cambiada",
                    "Hola " + student.getFirstName() + ",\n\n"
                            + "La contraseña de tu cuenta en la escuela de boxeo se cambió desde tu sesión.\n"
                            + "Si no fuiste tú, comunícate con la escuela de inmediato.");
        }
        // Sesion nueva con la version actual: el token con el que llego esta peticion ya no vale
        return buildResponse(userDetailsService.loadUserByUsername(user.getUsername()));
    }

    private void sendResetLink(PasswordResetService.IssuedToken issued) {
        // La base del enlace sale SIEMPRE de la configuracion, nunca de cabeceras del request (Host injection)
        String link = UriComponentsBuilder.fromUriString(props.passwordReset().url())
                .queryParam("token", issued.token()).build().toUriString();
        mailService.sendQuietly(issued.email(), "Recupera tu contraseña",
                "Hola " + issued.firstName() + ",\n\n"
                        + "Recibimos una solicitud para restablecer la contraseña de tu cuenta en la escuela de "
                        + "boxeo. Usa este enlace (de un solo uso, vence en "
                        + props.passwordReset().tokenValidityMinutes() + " minutos):\n\n"
                        + link + "\n\n"
                        + "Si no fuiste tú, ignora este mensaje: tu contraseña no cambia hasta que uses el enlace.");
    }

    private AuthResponse buildResponse(UserDetails userDetails) {
        return AuthResponse.builder()
                .token(jwtService.generateToken(userDetails))
                .username(userDetails.getUsername())
                .roles(userDetails.getAuthorities().stream().map(GrantedAuthority::getAuthority).toList())
                .build();
    }

    private static String normalizeUsername(String username) {
        return username.trim().toLowerCase(java.util.Locale.ROOT);
    }
}

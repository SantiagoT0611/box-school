package com.storres.box_school.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.storres.box_school.exception.InvalidRegistrationException;
import com.storres.box_school.exception.UsernameAlreadyExistsException;
import com.storres.box_school.mapper.StudentMapper;
import com.storres.box_school.model.dto.AuthResponse;
import com.storres.box_school.model.dto.LoginRequest;
import com.storres.box_school.model.dto.RegisterRequest;
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
        Student student = studentRepository.findByEmail(StudentMapper.normalizeEmail(request.getEmail()))
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
                .password(passwordEncoder.encode(request.getPassword()))
                .role(Roles.ROLE_USER)
                .enabled(true)
                .student(student)
                .build();
        userRepository.save(user);
        registrationCodeService.consume(student); // un solo uso

        log.info("Cuenta activada para student id={}", student.getId());
        return buildResponse(userDetailsService.loadUserByUsername(username));
    }

    private AuthResponse buildResponse(UserDetails userDetails) {
        return AuthResponse.builder()
                .token(jwtService.generateToken(userDetails))
                .username(userDetails.getUsername())
                .roles(userDetails.getAuthorities().stream().map(GrantedAuthority::getAuthority).toList())
                .build();
    }

    private static String normalizeUsername(String username) {
        return username.trim().toLowerCase();
    }
}

package com.storres.box_school.service;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.storres.box_school.config.AppProperties;
import com.storres.box_school.exception.InvalidPasswordResetException;
import com.storres.box_school.mapper.StudentMapper;
import com.storres.box_school.model.entity.PasswordResetToken;
import com.storres.box_school.model.entity.Student;
import com.storres.box_school.model.entity.User;
import com.storres.box_school.model.shared.Status;
import com.storres.box_school.repository.PasswordResetTokenRepository;
import com.storres.box_school.repository.UserRepository;

import lombok.RequiredArgsConstructor;

/**
 * Tokens de recuperacion de contrasena (D-024). Es la unica clase que conoce su ciclo de vida:
 * <ul>
 *   <li>256 bits de {@link SecureRandom}, Base64 URL-safe; en la BD solo el SHA-256 (con esa entropia no hace
 *       falta pimienta ni costo: no se puede probar por fuerza bruta aunque se filtre la tabla).</li>
 *   <li>Vive {@code app.password-reset.token-validity-minutes}, un solo uso, y hay a lo sumo UNO por usuario:
 *       pedir otro invalida el anterior.</li>
 *   <li>Solo existen para cuentas de estudiante habilitadas: los administradores no tienen email.</li>
 * </ul>
 * El token en claro solo existe en {@link IssuedToken}, de camino al correo; nunca se loguea.
 */
@Service
@RequiredArgsConstructor
public class PasswordResetService {

    private static final Logger log = LoggerFactory.getLogger(PasswordResetService.class);
    private static final int TOKEN_BYTES = 32;

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository tokens;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;
    private final AppProperties props;

    private final SecureRandom random = new SecureRandom();

    /** Lo necesario para el correo. {@code toString} no incluye el token ni el email para que no llegue a un log. */
    public record IssuedToken(String token, String email, String firstName, LocalDateTime expiresAt) {
        @Override
        public String toString() {
            return "IssuedToken[redacted]";
        }
    }

    /** A quien avisar de que la clave cambio. {@code email} puede ser null (cuentas sin estudiante). */
    public record PasswordChanged(String email, String firstName) {
    }

    /**
     * Emite un token si el email corresponde a una cuenta de estudiante habilitada y no se envio otro hace menos
     * del enfriamiento. Devuelve vacio en cualquier otro caso SIN distinguir el motivo (el llamador responde igual).
     */
    @Transactional
    public Optional<IssuedToken> issueFor(String rawEmail) {
        Optional<User> found = userRepository.findWithStudentByStudentEmail(StudentMapper.normalizeEmail(rawEmail));
        if (found.isEmpty()) {
            return Optional.empty();
        }
        User user = found.get();
        Student student = user.getStudent();
        if (!Boolean.TRUE.equals(user.getEnabled()) || student.getStatus() != Status.ACTIVE) {
            return Optional.empty();
        }

        LocalDateTime now = LocalDateTime.now(clock);
        LocalDateTime notBefore = now.minusSeconds(props.passwordReset().resendCooldownSeconds());
        boolean tooSoon = tokens.findByUserId(user.getId())
                .map(previous -> previous.getCreatedAt().isAfter(notBefore))
                .orElse(false);
        if (tooSoon) {
            log.info("Recuperacion omitida por enfriamiento: user id={}", user.getId());
            return Optional.empty();
        }

        String token = newToken();
        LocalDateTime expiresAt = now.plusMinutes(props.passwordReset().tokenValidityMinutes());

        tokens.deleteByUserId(user.getId()); // el token anterior deja de servir
        PasswordResetToken entity = new PasswordResetToken();
        entity.setUser(user);
        entity.setTokenHash(hash(token));
        entity.setCreatedAt(now);
        entity.setExpiresAt(expiresAt);
        tokens.saveAndFlush(entity);

        log.info("Token de recuperacion emitido: user id={}", user.getId());
        return Optional.of(new IssuedToken(token, student.getEmail(), student.getFirstName(), expiresAt));
    }

    /**
     * Consume el token y cambia la clave en una sola transaccion. Cualquier fallo (token desconocido, expirado,
     * usado, o cuenta deshabilitada) lanza la MISMA excepcion y deja todo como estaba.
     */
    @Transactional
    public PasswordChanged resetPassword(String token, String newPassword) {
        String hash = hash(token);

        // Una sola sentencia: de dos solicitudes simultaneas con el mismo token, solo una devuelve 1.
        // Antes de calcular BCrypt: un token invalido no cuesta CPU.
        if (tokens.consume(hash, LocalDateTime.now(clock)) != 1) {
            log.warn("Restablecimiento rechazado: token no valido");
            throw new InvalidPasswordResetException();
        }
        PasswordResetToken entity = tokens.findWithUserByTokenHash(hash).orElseThrow(InvalidPasswordResetException::new);
        User user = entity.getUser();
        Student student = user.getStudent();
        PasswordChanged recipient = student == null ? new PasswordChanged(null, null)
                : new PasswordChanged(student.getEmail(), student.getFirstName());

        // Condicional (enabled = true): si deshabilitaron la cuenta entre medio, no se cambia nada y el rollback
        // devuelve el token a su estado. Nunca reactiva ni pisa otras columnas.
        if (userRepository.changePassword(user.getId(), passwordEncoder.encode(newPassword)) != 1) {
            log.warn("Restablecimiento rechazado: cuenta deshabilitada, user id={}", user.getId());
            throw new InvalidPasswordResetException();
        }
        log.info("Clave restablecida: user id={}", user.getId());
        return recipient;
    }

    private String newToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String hash(String token) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("SHA-256 no disponible", e);
        }
    }
}

package com.storres.box_school.service;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Locale;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.storres.box_school.config.AppProperties;
import com.storres.box_school.model.dto.RegistrationCodeResponse;
import com.storres.box_school.model.entity.Student;

/**
 * Codigos de registro de un solo uso. Se generan con SecureRandom, se entregan una unica vez y en la BD
 * solo se guarda su HMAC-SHA256 con una "pimienta" derivada del secreto de la aplicacion (jwt.secret):
 * un volcado de la base de datos por si solo NO permite probar candidatos offline (con ~49 bits de entropia
 * un SHA-256 sin clave se agotaba en horas con GPU; auditoria Fase 0, B-14). Ademas hay rate limit,
 * expiracion y un solo uso.
 *
 * Nota operativa: rotar jwt.secret invalida los codigos pendientes (basta regenerarlos).
 */
@Service
public class RegistrationCodeService {

    // Sin caracteres ambiguos (0/O, 1/I/L)
    private static final String ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";
    private static final int CODE_LENGTH = 10;
    private static final String HMAC = "HmacSHA256";

    private final SecureRandom random = new SecureRandom();
    private final Clock clock;
    private final AppProperties props;
    private final byte[] pepper;

    public RegistrationCodeService(Clock clock, AppProperties props, @Value("${jwt.secret}") String secret) {
        this.clock = clock;
        this.props = props;
        // Separacion de dominio: la clave del HMAC no es el secreto del JWT en bruto
        this.pepper = sha256(("registration-code|" + secret).getBytes(StandardCharsets.UTF_8));
    }

    /** Genera un codigo nuevo para el estudiante (invalida el anterior). El llamador debe persistir la entidad. */
    public RegistrationCodeResponse issueFor(Student student) {
        StringBuilder code = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < CODE_LENGTH; i++) {
            code.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        LocalDateTime expiresAt = LocalDateTime.now(clock).plusHours(props.registration().codeValidityHours());

        student.setRegistrationCodeHash(hash(code.toString()));
        student.setRegistrationCodeExpiresAt(expiresAt);
        return new RegistrationCodeResponse(code.toString(), expiresAt);
    }

    public boolean matches(Student student, String candidate) {
        if (student.getRegistrationCodeHash() == null || student.getRegistrationCodeExpiresAt() == null
                || candidate == null) {
            return false;
        }
        if (student.getRegistrationCodeExpiresAt().isBefore(LocalDateTime.now(clock))) {
            return false;
        }
        byte[] expected = student.getRegistrationCodeHash().getBytes(StandardCharsets.UTF_8);
        byte[] actual = hash(candidate.trim().toUpperCase(Locale.ROOT)).getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(expected, actual); // comparacion en tiempo constante
    }

    public void consume(Student student) {
        student.setRegistrationCodeHash(null);
        student.setRegistrationCodeExpiresAt(null);
    }

    private String hash(String code) {
        try {
            Mac mac = Mac.getInstance(HMAC);
            mac.init(new SecretKeySpec(pepper, HMAC));
            return HexFormat.of().formatHex(mac.doFinal(code.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HMAC-SHA256 no disponible", e);
        }
    }

    private static byte[] sha256(byte[] input) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(input);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("SHA-256 no disponible", e);
        }
    }
}

package com.storres.box_school.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.HexFormat;

import org.springframework.stereotype.Service;

import com.storres.box_school.config.AppProperties;
import com.storres.box_school.model.dto.RegistrationCodeResponse;
import com.storres.box_school.model.entity.Student;

import lombok.RequiredArgsConstructor;

/**
 * Codigos de registro de un solo uso. Se generan con SecureRandom, se entregan una unica vez
 * y en la BD solo se guarda su hash SHA-256 (un volcado de la BD no permite activar cuentas).
 * Con ~50 bits de entropia + rate limit + expiracion, SHA-256 sin sal es suficiente.
 */
@Service
@RequiredArgsConstructor
public class RegistrationCodeService {

    // Sin caracteres ambiguos (0/O, 1/I/L)
    private static final String ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";
    private static final int CODE_LENGTH = 10;

    private final SecureRandom random = new SecureRandom();
    private final Clock clock;
    private final AppProperties props;

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
        byte[] actual = hash(candidate.trim().toUpperCase()).getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(expected, actual); // comparacion en tiempo constante
    }

    public void consume(Student student) {
        student.setRegistrationCodeHash(null);
        student.setRegistrationCodeExpiresAt(null);
    }

    private static String hash(String code) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(code.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 no disponible", e);
        }
    }
}

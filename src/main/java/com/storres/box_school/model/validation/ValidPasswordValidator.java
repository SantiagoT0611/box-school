package com.storres.box_school.model.validation;

import java.nio.charset.StandardCharsets;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class ValidPasswordValidator implements ConstraintValidator<ValidPassword, String> {

    static final int MIN_LENGTH = 8;
    static final int MAX_BYTES = 72;

    @Override
    public boolean isValid(String password, ConstraintValidatorContext context) {
        if (password == null) {
            return true; // la obligatoriedad la decide @NotBlank
        }
        if (password.length() < MIN_LENGTH) {
            return reject(context, "La clave debe tener al menos " + MIN_LENGTH + " caracteres");
        }
        // BCrypt rechaza claves de mas de 72 BYTES; con tildes/emoji 72 caracteres pueden superarlos (daba 500).
        // 72 bytes implica como mucho 72 caracteres, asi que este unico tope cubre ambos.
        if (password.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) {
            return reject(context, "La clave no puede superar " + MAX_BYTES
                    + " bytes (72 caracteres; menos si usas tildes o simbolos)");
        }
        return true;
    }

    private static boolean reject(ConstraintValidatorContext context, String message) {
        context.disableDefaultConstraintViolation();
        context.buildConstraintViolationWithTemplate(message).addConstraintViolation();
        return false;
    }
}

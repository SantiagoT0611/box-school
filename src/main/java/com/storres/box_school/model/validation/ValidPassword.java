package com.storres.box_school.model.validation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

/**
 * Regla unica de contrasena para registro, restablecimiento y cambio: entre 8 y 72 caracteres y como maximo
 * 72 BYTES en UTF-8 (BCrypt ignora/rechaza lo que pase de 72 bytes). No reemplaza a @NotBlank.
 */
@Documented
@Constraint(validatedBy = ValidPasswordValidator.class)
@Target({ ElementType.FIELD, ElementType.PARAMETER })
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidPassword {

    String message() default "La clave debe tener entre 8 y 72 caracteres";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}

package com.storres.box_school.exception;

/**
 * El token de recuperacion no sirve. Un unico mensaje para todos los motivos (inexistente, expirado, ya usado,
 * cuenta deshabilitada): quien lo prueba no debe aprender nada sobre cuentas ni tokens.
 */
public class InvalidPasswordResetException extends RuntimeException {
    public InvalidPasswordResetException() {
        super("El enlace de recuperacion no es valido o ya expiro. Solicita uno nuevo");
    }
}

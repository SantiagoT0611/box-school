package com.storres.box_school.exception;

/**
 * Cambio de clave rechazado (clave actual incorrecta o nueva igual a la actual). Es un 400 y no un 401 a
 * proposito: el cliente no debe interpretarlo como "sesion vencida" y cerrar la sesion.
 */
public class InvalidPasswordChangeException extends RuntimeException {
    public InvalidPasswordChangeException(String message) {
        super(message);
    }
}

package com.storres.box_school.exception;

/**
 * Registro rechazado. El mensaje es deliberadamente generico: no debe revelar si el
 * email existe, si ya tiene usuario o si el codigo es incorrecto (evita enumeracion).
 */
public class InvalidRegistrationException extends RuntimeException {
    public InvalidRegistrationException() {
        super("Los datos de registro no son validos o el codigo expiro");
    }
}

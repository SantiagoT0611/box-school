package com.storres.box_school.exception;

public class StudentNotActiveException extends RuntimeException {

    public StudentNotActiveException(String message) {
        super(message);
    }
// TODO: manejar la excepcion en el globalexception
}

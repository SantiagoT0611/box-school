package com.storres.box_school.exception;

public class StudentNotFoundException extends RuntimeException{

    public StudentNotFoundException(){
        super("El estudiante con el id ingresado no ha sido encontrado");
    }

}

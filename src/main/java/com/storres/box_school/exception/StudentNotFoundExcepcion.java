package com.storres.box_school.exception;

public class StudentNotFoundExcepcion extends RuntimeException{

    public StudentNotFoundExcepcion(){
        super("El estudiante con el id ingresado no ha sido encontrado");
    }

}

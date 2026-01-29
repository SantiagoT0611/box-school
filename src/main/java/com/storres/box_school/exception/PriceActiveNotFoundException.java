package com.storres.box_school.exception;

public class PriceActiveNotFoundException extends RuntimeException {

    public PriceActiveNotFoundException(){
        super("No existe actualmente un precio activo");
    }
    // TODO: manejar la excepcion en el globalexception

}

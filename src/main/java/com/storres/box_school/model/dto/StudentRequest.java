package com.storres.box_school.model.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class StudentRequest {

    @NotBlank(message = "El nombre no puede estar vacio")
    private String firstName;
    @NotBlank(message = "El segundo nombre no puede ser un espacio en blanco")
    private String lastName;
    @Email(message = "El email no tiene un formato valido")
    @NotBlank(message = "El email no puede estar vacio ")
    private String email;
    @NotBlank(message = "El telefono no puede ser un campo vacio")
    private String phone;
    
    
}

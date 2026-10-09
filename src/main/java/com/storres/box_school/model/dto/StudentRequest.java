package com.storres.box_school.model.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class StudentRequest {

    @NotBlank(message = "El nombre no puede estar vacio")
    @Size(max = 100, message = "El nombre no puede superar 100 caracteres")
    private String firstName;

    @NotBlank(message = "El apellido no puede estar vacio")
    @Size(max = 100, message = "El apellido no puede superar 100 caracteres")
    private String lastName;

    @NotBlank(message = "El email no puede estar vacio")
    @Email(message = "El email no tiene un formato valido")
    @Size(max = 150, message = "El email no puede superar 150 caracteres")
    private String email;

    @NotBlank(message = "El telefono no puede estar vacio")
    @Pattern(regexp = "^[+0-9()\\s-]{7,20}$", message = "El telefono no tiene un formato valido")
    private String phone;
}

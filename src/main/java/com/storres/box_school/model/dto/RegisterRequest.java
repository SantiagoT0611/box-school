package com.storres.box_school.model.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * El estudiante activa su cuenta con el email con el que lo registro el administrador
 * y el codigo de un solo uso que recibio (ver docs/DECISIONS.md D-004).
 */
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class RegisterRequest {

    @NotBlank(message = "El nombre de usuario no puede estar vacio")
    @Size(min = 3, max = 50, message = "El usuario debe tener entre 3 y 50 caracteres")
    @Pattern(regexp = "^[A-Za-z0-9._-]+$", message = "El usuario solo puede contener letras, numeros, punto, guion y guion bajo")
    private String username;

    // BCrypt solo usa los primeros 72 bytes: se limita para que la clave completa cuente.
    @NotBlank(message = "La clave no puede estar vacia")
    @Size(min = 8, max = 72, message = "La clave debe tener entre 8 y 72 caracteres")
    private String password;

    @NotBlank(message = "El email no puede estar vacio")
    @Email(message = "El email no tiene un formato valido")
    private String email;

    @NotBlank(message = "El codigo de registro es obligatorio")
    @Size(max = 32)
    private String registrationCode;
}

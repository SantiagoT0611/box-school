package com.storres.box_school.model.dto;

import com.storres.box_school.model.validation.ValidPassword;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ResetPasswordRequest {

    /** El que llego por correo (Base64 URL-safe, 43 caracteres). Cualquier otra forma ni se consulta en la BD. */
    @NotBlank(message = "El token es obligatorio")
    @Size(min = 20, max = 128, message = "El token no es valido")
    @Pattern(regexp = "^[A-Za-z0-9_-]+$", message = "El token no es valido")
    private String token;

    @NotBlank(message = "La clave no puede estar vacia")
    @ValidPassword
    private String newPassword;
}

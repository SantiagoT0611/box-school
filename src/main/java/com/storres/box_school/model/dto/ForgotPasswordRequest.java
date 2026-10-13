package com.storres.box_school.model.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
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
public class ForgotPasswordRequest {

    @NotBlank(message = "El email no puede estar vacio")
    @Email(message = "El email no tiene un formato valido")
    @Size(max = 255)
    private String email;
}

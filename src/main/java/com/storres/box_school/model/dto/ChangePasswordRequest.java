package com.storres.box_school.model.dto;

import com.storres.box_school.model.validation.ValidPassword;

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
public class ChangePasswordRequest {

    @NotBlank(message = "La clave actual no puede estar vacia")
    @Size(max = 128)
    private String currentPassword;

    @NotBlank(message = "La clave nueva no puede estar vacia")
    @ValidPassword
    private String newPassword;
}

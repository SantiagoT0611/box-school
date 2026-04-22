package com.storres.box_school.model.dto;

import com.storres.box_school.model.shared.Roles;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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
public class RegisterRequest {

    @NotBlank(message = "El nombre de usuario no puede estar vacio")
    private String username;
    @NotBlank(message = "La contrase;a no puede ser vacia")
    private String password;
    @NotNull
    private String email;
    // @NotNull
    // private Roles role;
    // @NotBlank
    // private String firstName;
    // @NotBlank
    // private String lastName;
    // @Email
    // private String email;
    // @NotBlank
    // private String phone;
    

}

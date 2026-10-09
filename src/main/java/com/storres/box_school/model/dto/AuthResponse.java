package com.storres.box_school.model.dto;

import java.util.List;

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
public class AuthResponse {
    private String token;
    private String username;
    /** Informativo para que el frontend decida que menu mostrar; la autorizacion real la hace el backend. */
    private List<String> roles;
}

package com.storres.box_school.service;

import com.storres.box_school.model.dto.AuthResponse;
import com.storres.box_school.model.dto.ChangePasswordRequest;
import com.storres.box_school.model.dto.ForgotPasswordRequest;
import com.storres.box_school.model.dto.LoginRequest;
import com.storres.box_school.model.dto.RegisterRequest;
import com.storres.box_school.model.dto.ResetPasswordRequest;

public interface AuthService {

    public AuthResponse login(LoginRequest request);

    public AuthResponse register(RegisterRequest request);

    /**
     * Pide el enlace de recuperacion. Se procesa en segundo plano y NUNCA informa si el email existe: el tiempo de
     * respuesta es el mismo exista o no (D-024).
     */
    public void forgotPassword(ForgotPasswordRequest request);

    /** Cambia la clave con el token recibido por correo. No inicia sesion: el usuario entra con la clave nueva. */
    public void resetPassword(ResetPasswordRequest request);

    /** El usuario autenticado cambia su clave. Devuelve una sesion nueva; las anteriores dejan de valer. */
    public AuthResponse changePassword(String username, ChangePasswordRequest request);

}

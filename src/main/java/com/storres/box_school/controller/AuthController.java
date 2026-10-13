package com.storres.box_school.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.storres.box_school.model.dto.AuthResponse;
import com.storres.box_school.model.dto.ChangePasswordRequest;
import com.storres.box_school.model.dto.ForgotPasswordRequest;
import com.storres.box_school.model.dto.LoginRequest;
import com.storres.box_school.model.dto.MessageResponse;
import com.storres.box_school.model.dto.RegisterRequest;
import com.storres.box_school.model.dto.ResetPasswordRequest;
import com.storres.box_school.service.AuthService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    /** Activa la cuenta de un estudiante inscrito por el admin (requiere email + codigo de registro). */
    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(request));
    }

    /** Pide el enlace de recuperacion. Siempre 202 con el mismo cuerpo: no revela si el email tiene cuenta. */
    @PostMapping("/forgot-password")
    public ResponseEntity<MessageResponse> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        authService.forgotPassword(request);
        return ResponseEntity.accepted().body(new MessageResponse(
                "Si el email corresponde a una cuenta activa, te enviaremos un enlace para recuperar tu contraseña"));
    }

    /** Cambia la clave con el token recibido por correo (un solo uso). */
    @PostMapping("/reset-password")
    public ResponseEntity<MessageResponse> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        authService.resetPassword(request);
        return ResponseEntity.ok(new MessageResponse("Tu contraseña fue actualizada. Ya puedes iniciar sesión"));
    }

    /** Cambio de clave del usuario autenticado (pide la actual). Devuelve una sesion nueva. */
    @PreAuthorize("isAuthenticated()")
    @PostMapping("/change-password")
    public ResponseEntity<AuthResponse> changePassword(Authentication authentication,
            @Valid @RequestBody ChangePasswordRequest request) {
        return ResponseEntity.ok(authService.changePassword(authentication.getName(), request));
    }
}

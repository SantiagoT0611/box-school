package com.storres.box_school.model.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Token de recuperacion de contrasena. Nunca se guarda en claro: solo su SHA-256 (el token tiene 256 bits de
 * entropia, no se puede probar por fuerza bruta). A lo sumo hay uno por usuario (restriccion unica en user_id).
 */
@Getter
@Setter
@Entity
@NoArgsConstructor
@Table(name = "password_reset_tokens")
public class PasswordResetToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    /** Se rellena al usarlo: un token usado no vuelve a servir aunque no haya expirado. */
    @Column(name = "used_at")
    private LocalDateTime usedAt;
}

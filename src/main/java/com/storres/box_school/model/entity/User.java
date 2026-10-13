package com.storres.box_school.model.entity;

import com.storres.box_school.model.shared.Roles;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.FetchType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * @DynamicUpdate: solo se escriben las columnas modificadas. Sin esto, deshabilitar la cuenta (que lee el usuario y
 * confirma despues) reescribia password y token_version con los valores leidos al inicio y deshacia un cambio de
 * contrasena confirmado entre medio (Fase 1; mismo motivo que en Student, N-2).
 */
@org.hibernate.annotations.DynamicUpdate
@Getter
@Setter
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "users")
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @NotNull
    @Column(nullable = false, unique = true)
    private String username;
    @NotNull
    @Column(nullable = false)
    private String password;
    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Roles role;
    @Builder.Default
    @Column(nullable = false)
    private Boolean enabled = true;

    /** Sube con cada cambio de clave; el JWT lleva la version con la que se emitio (ver JwtService). */
    @Builder.Default
    @Column(name = "token_version", nullable = false)
    private Integer tokenVersion = 0;

    @OneToOne(fetch = FetchType.LAZY)
    // Nullable: las cuentas ADMIN no corresponden a un estudiante
    @JoinColumn(name = "student_id", unique = true)
    private Student student;

}

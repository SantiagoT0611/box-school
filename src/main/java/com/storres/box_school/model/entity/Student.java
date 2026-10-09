package com.storres.box_school.model.entity;

import java.time.LocalDate;
import java.time.LocalDateTime;

import com.storres.box_school.model.shared.Status;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Entity
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "students")
public class Student {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @NotNull
    @Column(name = "first_name")
    private String firstName;
    @NotNull
    @Column(name = "last_name")
    private String lastName;
    @NotNull
    @Column(unique = true)
    private String email;
    @NotNull
    private String phone;
    @NotNull
    @Column(name = "registration_date")
    private LocalDate registrationDate;
    @NotNull
    @Column(name = "expiration_date")
    private LocalDate expirationDate;
    @NotNull
    @Enumerated(EnumType.STRING)
    private Status status;

    /** SHA-256 (hex) del codigo de registro de un solo uso entregado al estudiante. Nunca se guarda en claro. */
    @Column(name = "registration_code_hash", length = 64)
    private String registrationCodeHash;

    @Column(name = "registration_code_expires_at")
    private LocalDateTime registrationCodeExpiresAt;

}

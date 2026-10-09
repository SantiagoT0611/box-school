package com.storres.box_school.model.dto;

import java.time.LocalDate;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.storres.box_school.model.shared.Status;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class StudentResponse {

    private Long id;
    private String firstName;
    private String lastName;
    private String email;
    private String phone;
    private LocalDate registrationDate;
    private LocalDate expirationDate;
    private Status status;

    /** Dias que faltan para el vencimiento; negativo si la membresia ya vencio. */
    private Long daysUntilExpiration;

    /** true si la membresia ya vencio (expirationDate anterior a hoy). */
    private boolean membershipExpired;

    /** Solo se informa al crear al estudiante; despues solo se puede regenerar, nunca consultar. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String registrationCode;
}

package com.storres.box_school.model.entity;

import java.time.LocalDate;
import java.util.List;

import com.storres.box_school.model.shared.Status;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "Students")
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

    @OneToMany(mappedBy = "student")
    private List<Payment> payments;

    @OneToMany(mappedBy = "student")
    private List<Invoice> invoices;

    @OneToOne(mappedBy = "student")
    private User user;
}

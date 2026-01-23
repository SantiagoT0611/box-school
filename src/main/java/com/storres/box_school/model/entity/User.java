package com.storres.box_school.model.entity;

import com.storres.box_school.model.shared.Roles;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
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
    @Column(nullable = false)
    private Boolean enabled = true;

    @OneToOne
    @JoinColumn(name = "student_id", nullable = false)
    private Student student;

}

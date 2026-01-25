package com.storres.box_school.model.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.storres.box_school.model.shared.PaymentType;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "prices")
@Getter
@Setter
public class Price {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @Enumerated(EnumType.STRING)
    private PaymentType type;

    @NotNull
    @Positive
    @Column(precision = 19, scale = 2)
    private BigDecimal amount;

    @NotNull
    @Positive
    @Column(name = "duration_days")
    private Integer durationDays;

    @NotNull
    private Boolean active;

    @NotNull
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}

package com.storres.box_school.model.entity;

import java.math.BigDecimal;
import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;
@Getter
@Setter
@Entity
@Table(name = "payments")
public class Payment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @NotNull
    @Column(name = "payment_date")
    private LocalDate paymentDate;
    @NotNull
    @Column(name = "period_start")
    private LocalDate periodStart;
    @NotNull
    @Column(name = "period_End")
    private LocalDate periodEnd;
    @NotNull
    @Column(precision = 19, scale = 2)
    @Positive
    private BigDecimal amount;
@ManyToOne(fetch = FetchType.LAZY)
@JoinColumn(name = "student_id", nullable = false)
private Student student;
}

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
@Table(name = "invoices")
public class Invoice {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @NotNull
    @Column(name = "invoice_number", nullable = false, unique = true)
    private Integer invoiceNumber;
    @NotNull
    @Column(name = "issue_date")
    private LocalDate issueDate;
    @NotNull
    @Column(name = "period_start")
    private LocalDate periodStart;
    @NotNull
    @Column(name = "period_end")
    private LocalDate periodEnd;
    @NotNull
    @Positive
    @Column(precision = 19, scale = 2)
    private BigDecimal total;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id", nullable = false)
    private Student student;
}

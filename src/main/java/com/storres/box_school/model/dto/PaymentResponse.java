package com.storres.box_school.model.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.storres.box_school.model.shared.PaymentType;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PaymentResponse {
    private Long id;
    private LocalDate paymentDate;
    private LocalDate periodStart;
    private LocalDate periodEnd;
    private BigDecimal amountPaid;
    private Long studentId;
    private PaymentType type;
}

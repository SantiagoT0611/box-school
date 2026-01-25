package com.storres.box_school.model.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.storres.box_school.model.shared.PaymentType;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PriceRequest {
    
    @NotNull
    private PaymentType type;
    @NotNull
    @Positive
    private BigDecimal amount;
    @NotNull
    @Positive
    private Integer durationDays;
}

package com.storres.box_school.model.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.storres.box_school.model.shared.PaymentType;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PriceResponse {
    private Long id;
    private PaymentType type;
    private BigDecimal amount;
    private Integer durationDays;
    private Boolean active;
    private LocalDateTime createdAt;
}

package com.storres.box_school.model.dto;

import com.storres.box_school.model.shared.PaymentType;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PaymentRequest {
    @NotNull
    private Long studentId;
    @NotNull
    private PaymentType type;
}

package com.storres.box_school.model.dto;

import com.storres.box_school.model.shared.PaymentType;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/** El estudiante al que se le registra el pago va en la URL, no en el cuerpo. */
@Getter
@Setter
public class PaymentRequest {
    @NotNull
    private PaymentType type;
}

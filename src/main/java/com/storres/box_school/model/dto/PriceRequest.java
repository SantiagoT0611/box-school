package com.storres.box_school.model.dto;

import java.math.BigDecimal;

import com.storres.box_school.model.shared.PaymentType;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
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
    @DecimalMax(value = "99999999.99", message = "El monto es demasiado alto")
    @Digits(integer = 8, fraction = 2, message = "El monto admite hasta 8 enteros y 2 decimales")
    private BigDecimal amount;

    // Una membresia dura como maximo un año: un error de tecleo no puede dejar al estudiante pago por décadas
    @NotNull
    @Positive
    @Max(value = 366, message = "La duracion maxima es 366 dias")
    private Integer durationDays;
}

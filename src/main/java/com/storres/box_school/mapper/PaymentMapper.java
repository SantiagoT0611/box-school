package com.storres.box_school.mapper;

import org.springframework.stereotype.Component;

import com.storres.box_school.model.dto.PaymentResponse;
import com.storres.box_school.model.entity.Payment;

@Component
public class PaymentMapper {

    public PaymentResponse toDto(Payment payment) {
        PaymentResponse dto = new PaymentResponse();
        dto.setId(payment.getId());
        dto.setPaymentDate(payment.getPaymentDate());
        dto.setPeriodStart(payment.getPeriodStart());
        dto.setPeriodEnd(payment.getPeriodEnd());
        dto.setAmountPaid(payment.getAmountPaid());
        dto.setStudentId(payment.getStudent().getId());
        dto.setType(payment.getPrice().getType());

        return dto;

    }

}

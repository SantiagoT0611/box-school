package com.storres.box_school.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.storres.box_school.model.dto.PaymentRequest;
import com.storres.box_school.model.dto.PaymentResponse;

public interface PaymentService {

    /** Registra el pago de una membresia y extiende el vencimiento del estudiante. */
    PaymentResponse payMembership(PaymentRequest request, Long studentId);

    Page<PaymentResponse> studentPayments(Long studentId, Pageable pageable);

    /** Historial de pagos del estudiante dueno de la cuenta autenticada. */
    Page<PaymentResponse> myPayments(String username, Pageable pageable);

    Page<PaymentResponse> findAll(Pageable pageable);
}

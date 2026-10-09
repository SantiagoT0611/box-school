package com.storres.box_school.controller;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.storres.box_school.model.dto.PaymentRequest;
import com.storres.box_school.model.dto.PaymentResponse;
import com.storres.box_school.service.PaymentService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/payments")
public class PaymentController {

    private final PaymentService paymentService;

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping
    public ResponseEntity<Page<PaymentResponse>> getAllPayments(Pageable pageable) {
        return ResponseEntity.ok(paymentService.findAll(pageable));
    }

    /** Historial de pagos del estudiante autenticado. */
    @GetMapping("/me")
    public ResponseEntity<Page<PaymentResponse>> myPayments(Authentication authentication, Pageable pageable) {
        return ResponseEntity.ok(paymentService.myPayments(authentication.getName(), pageable));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/students/{studentId}")
    public ResponseEntity<Page<PaymentResponse>> getPaymentsByStudentId(@PathVariable Long studentId,
            Pageable pageable) {
        return ResponseEntity.ok(paymentService.studentPayments(studentId, pageable));
    }

    /** Registra el pago de una membresia (efectivo/transferencia). El monto lo fija el precio vigente. */
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/students/{studentId}")
    public ResponseEntity<PaymentResponse> createNewPayment(@PathVariable Long studentId,
            @Valid @RequestBody PaymentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(paymentService.payMembership(request, studentId));
    }
}

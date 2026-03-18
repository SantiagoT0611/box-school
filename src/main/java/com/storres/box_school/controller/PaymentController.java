package com.storres.box_school.controller;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
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
    @ResponseStatus(HttpStatus.OK)
    @GetMapping
    public ResponseEntity<Page<PaymentResponse>> studentPaymentsAll(Pageable pageable) {
        return ResponseEntity.ok(paymentService.findAll(pageable));
    }

    // ADMIN AND STUDENT
    @ResponseStatus(HttpStatus.OK)
    @GetMapping("/{studentId}")
    public ResponseEntity<Page<PaymentResponse>> studentPaymentsById(@PathVariable Long studentId, Pageable pageable) {
        return ResponseEntity.ok(paymentService.studentPayments(studentId, pageable));

    }

    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping("/{studentId}")
    public ResponseEntity<PaymentResponse> createNewPayment(@Valid @RequestBody PaymentRequest request,
            @PathVariable Long studentId) {
        PaymentResponse info = paymentService.payMembership(request, studentId);

        return ResponseEntity.status(HttpStatus.CREATED).body(info);
    }

}

package com.storres.box_school.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
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

    //ADMIN  ESTO ES PARA SPRING SECURITY
    @ResponseStatus(HttpStatus.OK)
    @GetMapping
    public List<PaymentResponse> studentPaymentsAll() {
        return paymentService.findAll();
    }
    //ADMIN AND STUDENT
    @ResponseStatus(HttpStatus.OK)
    @GetMapping("/{studentId}")
    public List<PaymentResponse> studentPaymentsById(@PathVariable Long studentId) {
        return paymentService.studentPayments(studentId);

    }
    //ADMIN
    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping("/{studentId}")
    public PaymentResponse createNewPayment(@Valid @RequestBody PaymentRequest request, @PathVariable Long studentId) {
        return paymentService.payMembership(request, studentId);
    }

}

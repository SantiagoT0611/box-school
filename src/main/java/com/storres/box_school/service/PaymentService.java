package com.storres.box_school.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.storres.box_school.model.dto.PaymentRequest;
import com.storres.box_school.model.dto.PaymentResponse;

public interface PaymentService {
 
 PaymentResponse payMembership(PaymentRequest request, Long id);

 Page<PaymentResponse> studentPayments(Long studentId, Pageable pageable);

 Page<PaymentResponse> findAll(Pageable pageable);


}

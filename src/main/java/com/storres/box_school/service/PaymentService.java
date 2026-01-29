package com.storres.box_school.service;

import java.util.List;

import com.storres.box_school.model.dto.PaymentRequest;
import com.storres.box_school.model.dto.PaymentResponse;

public interface PaymentService {
 
 PaymentResponse payMembership(PaymentRequest request, Long id);

 List<PaymentResponse> studentPayments(Long studentId);

 List<PaymentResponse> findAll();


}

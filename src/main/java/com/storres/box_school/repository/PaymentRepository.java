package com.storres.box_school.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.storres.box_school.model.entity.Payment;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

   List<Payment> findByStudenId(Long studentId);

}

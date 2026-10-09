package com.storres.box_school.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import com.storres.box_school.model.entity.Payment;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    // El mapper necesita price.type: se trae en la misma consulta para evitar N+1
    @EntityGraph(attributePaths = "price")
    Page<Payment> findByStudentId(Long studentId, Pageable pageable);

    @Override
    @EntityGraph(attributePaths = "price")
    Page<Payment> findAll(Pageable pageable);
}

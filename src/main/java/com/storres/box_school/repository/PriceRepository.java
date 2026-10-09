package com.storres.box_school.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.storres.box_school.model.entity.Price;
import com.storres.box_school.model.shared.PaymentType;

import jakarta.persistence.LockModeType;

public interface PriceRepository extends JpaRepository<Price, Long> {

    Optional<Price> findByTypeAndActiveTrue(PaymentType type);

    List<Price> findByActiveTrueOrderByType();

    /** Bloquea el precio activo mientras se reemplaza, para que dos admins no dejen dos precios activos. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Price p where p.type = :type and p.active = true")
    Optional<Price> findActiveForUpdate(@Param("type") PaymentType type);
}

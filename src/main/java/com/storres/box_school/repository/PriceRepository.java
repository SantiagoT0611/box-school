package com.storres.box_school.repository;


import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.storres.box_school.model.entity.Price;
import com.storres.box_school.model.shared.PaymentType;

public interface PriceRepository extends JpaRepository<Price,Long>{

    Optional<Price> findByTypeAndActiveTrue(PaymentType type);
    
  


}

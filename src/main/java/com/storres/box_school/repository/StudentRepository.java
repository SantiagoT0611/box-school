package com.storres.box_school.repository;

import java.time.LocalDate;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.storres.box_school.model.entity.Student;
import com.storres.box_school.model.shared.Status;

public interface StudentRepository extends JpaRepository<Student, Long>{

    Page<Student> findByExpirationDateBefore(LocalDate date, Pageable pageable);

    boolean existsByEmail(String email);
    
    Optional<Student> findByEmail(String email);

    Page<Student> findByStatus(Status status, Pageable pageable);

   

    



}

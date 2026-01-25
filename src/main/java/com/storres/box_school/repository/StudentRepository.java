package com.storres.box_school.repository;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.storres.box_school.model.dto.StudentResponse;
import com.storres.box_school.model.entity.Student;

public interface StudentRepository extends JpaRepository<Student, Long>{

    List<Student> findByExpirationDateBefore(LocalDate date);

    boolean existsByEmail(String email);

    StudentResponse desactive(Long id);

    StudentResponse active(Long id);



}

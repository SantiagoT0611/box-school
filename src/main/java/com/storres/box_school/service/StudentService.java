package com.storres.box_school.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.storres.box_school.model.dto.StudentRequest;
import com.storres.box_school.model.dto.StudentResponse;

public interface StudentService {
    
     StudentResponse create(StudentRequest student);

     Page<StudentResponse> findAll(Pageable pageable);

     void delete (Long id);

     StudentResponse getById(Long id);

     Page<StudentResponse> findStudentsWithExpireMembership(Pageable pageable);

     StudentResponse updateStudent (Long id , StudentRequest info);

     StudentResponse desactiveStudent(Long id);

     StudentResponse activeStudent(Long id);
     
     Page<StudentResponse> findActiveStudents(Pageable pageable);


}

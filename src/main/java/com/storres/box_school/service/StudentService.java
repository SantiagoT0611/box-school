package com.storres.box_school.service;

import java.util.List;

import com.storres.box_school.model.dto.StudentRequest;
import com.storres.box_school.model.dto.StudentResponse;

public interface StudentService {
    
    StudentResponse create(StudentRequest student);

    List<StudentResponse> findAll();

     void delete (Long id);

     StudentResponse getById(Long id);

     List<StudentResponse> findStudentsWithExpireMembership();

     StudentResponse updateStudent (Long id , StudentRequest info);

     StudentResponse desactiveStudent(Long id);

     StudentResponse activeStudent(Long id);
     


}

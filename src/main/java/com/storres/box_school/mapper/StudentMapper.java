package com.storres.box_school.mapper;

import org.springframework.stereotype.Component;

import com.storres.box_school.model.dto.StudentRequest;
import com.storres.box_school.model.dto.StudentResponse;
import com.storres.box_school.model.entity.Student;

@Component
public class StudentMapper {

    private StudentMapper() {

    }

    public Student toEntity(StudentRequest info) {
        var response = new Student();
        response.setFirstName(info.getFirstName());
        response.setLastName(info.getLastName());
        response.setEmail(info.getEmail());
        response.setPhone(info.getPhone());

        return response;

    }

    public StudentResponse toDto(Student student) {
        var dto = new StudentResponse();
        dto.setFirstName(student.getFirstName());
        dto.setLastName(student.getLastName());
        dto.setEmail(student.getEmail());
        dto.setPhone(student.getPhone());
        dto.setRegistrationDate(student.getRegistrationDate());
        dto.setExpirationDate(student.getExpirationDate());
        dto.setStatus(student.getStatus());
        return dto;
    }

}

package com.storres.box_school.mapper;

import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

import org.springframework.stereotype.Component;

import com.storres.box_school.model.dto.StudentRequest;
import com.storres.box_school.model.dto.StudentResponse;
import com.storres.box_school.model.entity.Student;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class StudentMapper {

    private final Clock clock;

    public Student toEntity(StudentRequest info) {
        var student = new Student();
        applyTo(student, info);
        return student;
    }

    /** Copia los datos editables del request sobre la entidad (email normalizado a minusculas). */
    public void applyTo(Student student, StudentRequest info) {
        student.setFirstName(info.getFirstName().trim());
        student.setLastName(info.getLastName().trim());
        student.setEmail(normalizeEmail(info.getEmail()));
        student.setPhone(info.getPhone().trim());
    }

    public static String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase();
    }

    public StudentResponse toDto(Student student) {
        var dto = new StudentResponse();
        dto.setId(student.getId());
        dto.setFirstName(student.getFirstName());
        dto.setLastName(student.getLastName());
        dto.setEmail(student.getEmail());
        dto.setPhone(student.getPhone());
        dto.setRegistrationDate(student.getRegistrationDate());
        dto.setExpirationDate(student.getExpirationDate());
        dto.setStatus(student.getStatus());

        LocalDate today = LocalDate.now(clock);
        dto.setDaysUntilExpiration(ChronoUnit.DAYS.between(today, student.getExpirationDate()));
        dto.setMembershipExpired(student.getExpirationDate().isBefore(today));
        return dto;
    }
}

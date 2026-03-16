package com.storres.box_school.controller;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.storres.box_school.model.dto.StudentRequest;
import com.storres.box_school.model.dto.StudentResponse;
import com.storres.box_school.service.StudentService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@EnableMethodSecurity
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/students")
public class StudentController {

    private final StudentService studentService;

    // TODO: realizar los ressponseStatus

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping
    public StudentResponse create(@Valid @RequestBody StudentRequest studentRequest) {
        return studentService.create(studentRequest);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping
    public List<StudentResponse> getAllStudents() {
        return studentService.findAll();
    }

    @GetMapping("/{id}")
    public StudentResponse getById(@PathVariable Long id) {
        return studentService.getById(id);

    }

    @PutMapping("/{id}")
    public StudentResponse updateStudents(@PathVariable Long id, @Valid @RequestBody StudentRequest info) {
        return studentService.updateStudent(id, info);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("{id}")
    public void delete(@PathVariable Long id) {
        studentService.delete(id);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/expired")
    public List<StudentResponse> findStudentsWithExpireMembership() {
        return studentService.findStudentsWithExpireMembership();
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PatchMapping("/{id}/desactive")
    public StudentResponse desativeStudents(@PathVariable Long id) {
        return studentService.desactiveStudent(id);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PatchMapping("/{id}/active")
    public StudentResponse activeStudents(@PathVariable Long id) {
        return studentService.activeStudent(id);
    }
}

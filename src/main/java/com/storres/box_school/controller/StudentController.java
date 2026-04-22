package com.storres.box_school.controller;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
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

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/students")
public class StudentController {

    private final StudentService studentService;

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping
    public ResponseEntity<StudentResponse> create(@Valid @RequestBody StudentRequest studentRequest) {
        StudentResponse student = studentService.create(studentRequest);

        return ResponseEntity.status(HttpStatus.CREATED).body(student);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping
    public ResponseEntity<Page<StudentResponse>> getAllStudents(Pageable pageable) {
        Page<StudentResponse> students = studentService.findAll(pageable);

        return ResponseEntity.status(HttpStatus.OK).body(students);
    }

    @GetMapping("/{id}")
    public ResponseEntity<StudentResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(studentService.getById(id));

    }

    @PutMapping("/{id}")
    public ResponseEntity<StudentResponse> updateStudents(@PathVariable Long id,
            @Valid @RequestBody StudentRequest info) {
        StudentResponse updateInfo = studentService.updateStudent(id, info);
        return ResponseEntity.ok(updateInfo);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        studentService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/expired")
    public ResponseEntity<Page<StudentResponse>> findStudentsWithExpireMembership(Pageable pageable) {
        return ResponseEntity.ok(studentService.findStudentsWithExpireMembership(pageable)) ;
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PatchMapping("/{id}/desactive")
    public ResponseEntity<StudentResponse> desactiveStudents(@PathVariable Long id) {
        StudentResponse info = studentService.desactiveStudent(id);
        return ResponseEntity.ok(info);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PatchMapping("/{id}/active")
    public ResponseEntity<StudentResponse> activeStudents(@PathVariable Long id) {
        StudentResponse info = studentService.activeStudent(id);

        return ResponseEntity.ok(info);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/active")
    public ResponseEntity<Page<StudentResponse>> getActiveStudents(Pageable pageable) {
        return ResponseEntity.ok(studentService.findActiveStudents(pageable));
    }
}

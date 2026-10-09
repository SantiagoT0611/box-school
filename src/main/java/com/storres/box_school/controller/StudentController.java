package com.storres.box_school.controller;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.storres.box_school.model.dto.RegistrationCodeResponse;
import com.storres.box_school.model.dto.StudentRequest;
import com.storres.box_school.model.dto.StudentResponse;
import com.storres.box_school.model.shared.Status;
import com.storres.box_school.service.StudentService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/students")
public class StudentController {

    private final StudentService studentService;

    /** Alta de estudiante (solo admin). La respuesta incluye una unica vez el codigo para que active su cuenta. */
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping
    public ResponseEntity<StudentResponse> create(@Valid @RequestBody StudentRequest studentRequest) {
        return ResponseEntity.status(HttpStatus.CREATED).body(studentService.create(studentRequest));
    }

    /** Listado paginado. Filtros opcionales: ?q=texto (nombre, apellido o email) y ?status=ACTIVE|INACTIVE. */
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping
    public ResponseEntity<Page<StudentResponse>> getAllStudents(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Status status,
            Pageable pageable) {
        return ResponseEntity.ok(studentService.search(q, status, pageable));
    }

    /** Perfil del estudiante autenticado (incluye dias para el vencimiento). */
    @GetMapping("/me")
    public ResponseEntity<StudentResponse> me(Authentication authentication) {
        return ResponseEntity.ok(studentService.getByUsername(authentication.getName()));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/{id}")
    public ResponseEntity<StudentResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(studentService.getById(id));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/{id}")
    public ResponseEntity<StudentResponse> updateStudent(@PathVariable Long id,
            @Valid @RequestBody StudentRequest info) {
        return ResponseEntity.ok(studentService.updateStudent(id, info));
    }

    /** Membresias ya vencidas de estudiantes activos. */
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/expired")
    public ResponseEntity<Page<StudentResponse>> findExpired(Pageable pageable) {
        return ResponseEntity.ok(studentService.findExpired(pageable));
    }

    /** Membresias que vencen en los proximos {days} dias (por defecto 2, igual que el aviso por correo). */
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/expiring")
    public ResponseEntity<Page<StudentResponse>> findExpiring(
            @RequestParam(defaultValue = "2") @Min(0) @Max(60) int days,
            Pageable pageable) {
        return ResponseEntity.ok(studentService.findExpiring(days, pageable));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PatchMapping("/{id}/deactivate")
    public ResponseEntity<StudentResponse> deactivate(@PathVariable Long id) {
        return ResponseEntity.ok(studentService.deactivateStudent(id));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PatchMapping("/{id}/activate")
    public ResponseEntity<StudentResponse> activate(@PathVariable Long id) {
        return ResponseEntity.ok(studentService.activateStudent(id));
    }

    /** Genera un codigo de registro nuevo (por ejemplo si el estudiante perdio el anterior o expiro). */
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/{id}/registration-code")
    public ResponseEntity<RegistrationCodeResponse> regenerateRegistrationCode(@PathVariable Long id) {
        return ResponseEntity.ok(studentService.regenerateRegistrationCode(id));
    }
}

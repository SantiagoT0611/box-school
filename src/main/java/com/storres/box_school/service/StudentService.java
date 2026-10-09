package com.storres.box_school.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.storres.box_school.model.dto.RegistrationCodeResponse;
import com.storres.box_school.model.dto.StudentRequest;
import com.storres.box_school.model.dto.StudentResponse;
import com.storres.box_school.model.shared.Status;

public interface StudentService {

    /** Crea el estudiante y devuelve, por unica vez, su codigo de registro. */
    StudentResponse create(StudentRequest student);

    /** Listado con filtros opcionales (texto en nombre/apellido/email y estado). */
    Page<StudentResponse> search(String text, Status status, Pageable pageable);

    StudentResponse getById(Long id);

    /** Perfil del estudiante dueno de la cuenta autenticada. */
    StudentResponse getByUsername(String username);

    StudentResponse updateStudent(Long id, StudentRequest info);

    /** Deshabilita al estudiante y bloquea su acceso a la plataforma. */
    StudentResponse deactivateStudent(Long id);

    StudentResponse activateStudent(Long id);

    /** Membresias ya vencidas (expirationDate anterior a hoy) de estudiantes activos. */
    Page<StudentResponse> findExpired(Pageable pageable);

    /** Membresias que vencen entre hoy y hoy + days (inclusive) de estudiantes activos. */
    Page<StudentResponse> findExpiring(int days, Pageable pageable);

    /** Genera un nuevo codigo de registro (invalida el anterior) y lo envia por correo. */
    RegistrationCodeResponse regenerateRegistrationCode(Long id);
}

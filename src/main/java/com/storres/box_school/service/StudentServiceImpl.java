package com.storres.box_school.service;

import java.time.LocalDate;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import com.storres.box_school.exception.EmailAlreadyExistsException;
import com.storres.box_school.exception.StudentNotFoundExcepcion;
import com.storres.box_school.mapper.StudentMapper;
import com.storres.box_school.model.dto.StudentRequest;
import com.storres.box_school.model.dto.StudentResponse;
import com.storres.box_school.model.entity.Student;
import com.storres.box_school.model.shared.Status;
import com.storres.box_school.repository.StudentRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class StudentServiceImpl implements StudentService {

    private final StudentRepository studentRepository;
    private final StudentMapper studentMapper;
    private static final Logger log = LoggerFactory.getLogger(StudentServiceImpl.class);

    @Override
    public StudentResponse create(StudentRequest student) {
        log.info("Creando estudiante con email: {}", student.getEmail());
        // convertimos Request DTO a entity
        if (studentRepository.existsByEmail(student.getEmail())) {
            log.warn("Intento de crear estudiante con email duplicado: {} ", student.getEmail());
            throw new EmailAlreadyExistsException("El email ya está registrado");
        }
        Student entity = studentMapper.toEntity(student);
        // campos que decide el sistema
        entity.setRegistrationDate(LocalDate.now());
        entity.setStatus(Status.ACTIVE);
        entity.setExpirationDate(LocalDate.now().plusMonths(1));

        // guardo en la db
        Student saved = studentRepository.save(entity);
        log.info("Estudiante creado correctamente con ID: {}", saved.getId());

        return studentMapper.toDto(saved);

    }

    @Override
    public Page<StudentResponse> findAll(Pageable pageable) {
        log.info("Consultado todos los estudiantes registrados");
        return studentRepository.findAll(pageable)
                .map(studentMapper::toDto);

    }

    @Override
    public void delete(Long id) {
        log.warn("Eliminando estudiante con id {}", id);
        Student student = studentRepository.findById(id)
                .orElseThrow(() -> new StudentNotFoundExcepcion());

        log.info("validando que el id sea valido");
        studentRepository.delete(student);
        log.info("Estudiante eliminado");
    }

    @Override
    public StudentResponse getById(Long id) {
        validateId(id);
        log.info("validando que el id sea valido");
        return studentRepository.findById(id)
                .map(studentMapper::toDto)
                .orElseThrow(() -> new StudentNotFoundExcepcion());

    }

    @Override
    public StudentResponse updateStudent(Long id, StudentRequest info) {
        log.info("Iniciando actualizacion del estudiante con id {}", id);

        log.info("validando que el id sea valido");
        validateId(id);

        Student student = studentRepository.findById(id)
                .orElseThrow(() -> new StudentNotFoundExcepcion());

        if (info.getEmail() != null) {
            String newEmail = info.getEmail();
            String actualEmail = student.getEmail();

            if (!newEmail.equals(actualEmail)) {
                log.info("Validando nuevo email: {}", newEmail);

                if (studentRepository.existsByEmail(newEmail)) {
                    throw new EmailAlreadyExistsException("El email ya está registrado");
                }

                student.setEmail(newEmail);
            }
        }

        if (info.getFirstName() != null) {
            student.setFirstName(info.getFirstName());
        }
        if (info.getLastName() != null) {
            student.setLastName(info.getLastName());
        }
        if (info.getPhone() != null) {
            student.setPhone(info.getPhone());
        }

        log.info("Estudiante con el id {} ha sido actualizado correctamente...", id);
        Student update = studentRepository.save(student);

        return studentMapper.toDto(update);

    }

    @Override
    public Page<StudentResponse> findStudentsWithExpireMembership(Pageable pageable) {
        log.info("Consultando estudiantes con membresia por expirar");
        return studentRepository.findByExpirationDateBefore(LocalDate.now().plusDays(3), pageable)
                .map(studentMapper::toDto);
    }

    @Override
    public StudentResponse desactiveStudent(Long id) {
        log.info("Desactivando estudiante con el id {}", id);

        validateId(id);
        Student student = studentRepository.findById(id)
                .orElseThrow(() -> new StudentNotFoundExcepcion());

        if (student.getStatus() == Status.INACTIVE) {
            log.warn("El estudiante con el id {} ya esta inactivo", id);
            return studentMapper.toDto(student);
        }
        student.setStatus(Status.INACTIVE);

        Student update = studentRepository.save(student);
        log.info("Estudiante con id {} desactivado correctamente", id);

        return studentMapper.toDto(update);

    }

    @Override
    public StudentResponse activeStudent(Long id) {
        log.info("Activando estudiante con el id {}", id);

        validateId(id);
        Student student = studentRepository.findById(id)
                .orElseThrow(() -> new StudentNotFoundExcepcion());

        if (student.getStatus() == Status.ACTIVE) {
            log.warn("El estudiante con el id {} ya se encuentra activo", id);
            return studentMapper.toDto(student);
        }
        student.setStatus(Status.ACTIVE);

        Student update = studentRepository.save(student);

        return studentMapper.toDto(update);
    }

    public Page<StudentResponse> findActiveStudents(Pageable pageable){
        log.info("Consultando estudiantes activos");

        return studentRepository.findByStatus(Status.ACTIVE, pageable)
            .map(studentMapper::toDto);

    };

    private void validateId(Long id) {

        if (id == null || id < 0) {
            throw new StudentNotFoundExcepcion();

        }
    }

}

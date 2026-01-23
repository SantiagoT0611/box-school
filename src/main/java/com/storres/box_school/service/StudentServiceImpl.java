package com.storres.box_school.service;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

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

    @Override
    public StudentResponse create(StudentRequest student) {
        // convertimos Request DTO a entity
        Student entity = studentMapper.toEntity(student);
        // campos que decide el sistema
        entity.setRegistrationDate(LocalDate.now());
        entity.setStatus(Status.ACTIVE);

        if (studentRepository.existsByEmail(entity.getEmail())) {
            throw new EmailAlreadyExistsException("El email ya está registrado");
        }
        // guardo en la db
        Student saved = studentRepository.save(entity);

        return studentMapper.toDto(saved);

    }

    @Override
    public List<StudentResponse> findAll() {
        return studentRepository.findAll().stream()
                .map(studentMapper::toDto)
                .collect(Collectors.toList());
    }

    @Override
    public void delete(Long id) {
        validateId(id);
        studentRepository.deleteById(id);
    }

    @Override
    public StudentResponse getById(Long id) {
        validateId(id);
        return studentRepository.findById(id)
                .map(studentMapper::toDto)
                .orElseThrow(() -> new StudentNotFoundExcepcion());

    }

    @Override
    public List<StudentResponse> findStudentsWithExpireMembership() {
        return studentRepository.findByExpirationDateBefore(LocalDate.now()).stream()
                .map(studentMapper::toDto)
                .collect(Collectors.toList());
    }

    private void validateId(Long id) {
        if (id == null || id < 0) {
            throw new StudentNotFoundExcepcion();

        }
    }

}

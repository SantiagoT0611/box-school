package com.storres.box_school.service;

import java.time.Clock;
import java.time.LocalDate;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.storres.box_school.exception.EmailAlreadyExistsException;
import com.storres.box_school.exception.StudentNotFoundException;
import com.storres.box_school.exception.UsernameAlreadyExistsException;
import com.storres.box_school.mapper.StudentMapper;
import com.storres.box_school.model.dto.RegistrationCodeResponse;
import com.storres.box_school.model.dto.StudentRequest;
import com.storres.box_school.model.dto.StudentResponse;
import com.storres.box_school.model.entity.Student;
import com.storres.box_school.model.shared.Status;
import com.storres.box_school.repository.StudentRepository;
import com.storres.box_school.repository.StudentSpecifications;
import com.storres.box_school.repository.UserRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StudentServiceImpl implements StudentService {

    private static final Logger log = LoggerFactory.getLogger(StudentServiceImpl.class);

    private final StudentRepository studentRepository;
    private final UserRepository userRepository;
    private final StudentMapper studentMapper;
    private final RegistrationCodeService registrationCodeService;
    private final MailService mailService;
    private final Clock clock;

    @Override
    @Transactional
    public StudentResponse create(StudentRequest request) {
        String email = StudentMapper.normalizeEmail(request.getEmail());
        if (studentRepository.existsByEmail(email)) {
            throw new EmailAlreadyExistsException("El email ya está registrado");
        }

        Student student = studentMapper.toEntity(request);
        LocalDate today = LocalDate.now(clock);
        student.setRegistrationDate(today);
        student.setStatus(Status.ACTIVE);
        // Recien inscrito: se le da un mes de membresia (ver docs/DECISIONS.md D-006)
        student.setExpirationDate(today.plusMonths(1));
        RegistrationCodeResponse code = registrationCodeService.issueFor(student);

        Student saved = studentRepository.save(student);
        log.info("Estudiante creado id={}", saved.getId());

        sendRegistrationCode(saved, code);
        StudentResponse response = studentMapper.toDto(saved);
        response.setRegistrationCode(code.registrationCode());
        return response;
    }

    @Override
    public Page<StudentResponse> search(String text, Status status, Pageable pageable) {
        Specification<Student> spec = Specification
                .where(StudentSpecifications.hasStatus(status))
                .and(StudentSpecifications.matchesText(text));
        return studentRepository.findAll(spec, pageable).map(studentMapper::toDto);
    }

    @Override
    public StudentResponse getById(Long id) {
        return studentMapper.toDto(findOrThrow(id));
    }

    @Override
    public StudentResponse getByUsername(String username) {
        var user = userRepository.findWithStudentByUsername(username)
                .orElseThrow(StudentNotFoundException::new);
        if (user.getStudent() == null) {
            throw new StudentNotFoundException(); // cuentas admin no tienen perfil de estudiante
        }
        return studentMapper.toDto(user.getStudent());
    }

    @Override
    @Transactional
    public StudentResponse updateStudent(Long id, StudentRequest request) {
        Student student = findOrThrow(id);

        String newEmail = StudentMapper.normalizeEmail(request.getEmail());
        if (!newEmail.equals(student.getEmail()) && studentRepository.existsByEmail(newEmail)) {
            throw new EmailAlreadyExistsException("El email ya está registrado");
        }
        studentMapper.applyTo(student, request);

        log.info("Estudiante actualizado id={}", id);
        return studentMapper.toDto(student); // entidad gestionada: se persiste al cerrar la transaccion
    }

    @Override
    @Transactional
    public StudentResponse deactivateStudent(Long id) {
        return changeStatus(id, Status.INACTIVE);
    }

    @Override
    @Transactional
    public StudentResponse activateStudent(Long id) {
        return changeStatus(id, Status.ACTIVE);
    }

    @Override
    public Page<StudentResponse> findExpired(Pageable pageable) {
        return studentRepository
                .findByStatusAndExpirationDateBefore(Status.ACTIVE, LocalDate.now(clock), pageable)
                .map(studentMapper::toDto);
    }

    @Override
    public Page<StudentResponse> findExpiring(int days, Pageable pageable) {
        LocalDate today = LocalDate.now(clock);
        return studentRepository
                .findByStatusAndExpirationDateBetween(Status.ACTIVE, today, today.plusDays(days), pageable)
                .map(studentMapper::toDto);
    }

    @Override
    @Transactional
    public RegistrationCodeResponse regenerateRegistrationCode(Long id) {
        Student student = findOrThrow(id);
        if (userRepository.existsByStudentId(id)) {
            throw new UsernameAlreadyExistsException("Este estudiante ya tiene una cuenta activada");
        }
        RegistrationCodeResponse code = registrationCodeService.issueFor(student);
        sendRegistrationCode(student, code);
        return code;
    }

    /**
     * Cambiar el estado del estudiante tambien habilita/deshabilita su cuenta de acceso:
     * "deshabilitar" un estudiante debe cortarle el acceso de verdad (ver D-007).
     */
    private StudentResponse changeStatus(Long id, Status status) {
        Student student = findOrThrow(id);
        if (student.getStatus() != status) {
            student.setStatus(status);
            userRepository.findByStudentId(id).ifPresent(user -> user.setEnabled(status == Status.ACTIVE));
            log.info("Estudiante id={} ahora {}", id, status);
        }
        return studentMapper.toDto(student);
    }

    private Student findOrThrow(Long id) {
        return studentRepository.findById(id).orElseThrow(StudentNotFoundException::new);
    }

    private void sendRegistrationCode(Student student, RegistrationCodeResponse code) {
        mailService.sendQuietly(student.getEmail(), "Activa tu cuenta en la escuela de boxeo",
                "Hola " + student.getFirstName() + ",\n\n"
                        + "Ya estas inscrito en la escuela de boxeo. Para crear tu usuario usa este codigo "
                        + "junto con tu email: " + code.registrationCode() + "\n"
                        + "El codigo es de un solo uso y vence el " + code.expiresAt().toLocalDate() + ".");
    }
}

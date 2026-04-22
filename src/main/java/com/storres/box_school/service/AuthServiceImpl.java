package com.storres.box_school.service;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.storres.box_school.exception.StudentNotFoundExcepcion;
import com.storres.box_school.exception.UsernameAlreadyExistsException;
import com.storres.box_school.model.dto.AuthResponse;
import com.storres.box_school.model.dto.LoginRequest;
import com.storres.box_school.model.dto.RegisterRequest;
import com.storres.box_school.model.entity.Student;
import com.storres.box_school.model.entity.User;
import com.storres.box_school.model.shared.Roles;
import com.storres.box_school.repository.StudentRepository;
import com.storres.box_school.repository.UserRepository;
import com.storres.box_school.security.JwtService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor

public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final CustomUserDetailsService userDetailsService;
    private final StudentRepository studentRepository;

    @Override
    public AuthResponse login(LoginRequest request) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.getUsername(),
                        request.getPassword())

        );

        UserDetails userDetails = userDetailsService.loadUserByUsername(request.getUsername());

        String token = jwtService.generateToken(userDetails);

        // retorno del JWT
        return AuthResponse.builder()
                .token(token)
                .build();
    }

    @Override
    public AuthResponse register(RegisterRequest request) {

        // Student student = new Student();
        // student.setFirstName(request.getFirstName());
        // student.setLastName(request.getLastName());
        // student.setEmail(request.getEmail());
        // student.setPhone(request.getPhone());

        // student.setRegistrationDate(LocalDate.now());
        // student.setExpirationDate(LocalDate.now().plusMonths(1));
        // student.setStatus(Status.ACTIVE);

        // Student savedStudent = studentRepository.save(student);
        Student student = studentRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new StudentNotFoundExcepcion());

        if (student.getUser() != null) {
            throw new UsernameAlreadyExistsException("Este estudiante ya tiene usuario");

        }

        // request.getRole() != null ? request.getRole() :
        User user = User.builder()
                .username(request.getUsername())
                .password(passwordEncoder.encode(request.getPassword()))
                .role(Roles.ROLE_USER)
                .enabled(true)
                .student(student)
                .build();

        userRepository.save(user);

        UserDetails userDetails = userDetailsService.loadUserByUsername(user.getUsername());

        String token = jwtService.generateToken(userDetails);

        return AuthResponse.builder()
                .token(token)
                .build();
    }

}

package com.storres.box_school.service;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.storres.box_school.model.dto.AuthResponse;
import com.storres.box_school.model.dto.LoginRequest;
import com.storres.box_school.model.dto.RegisterRequest;
import com.storres.box_school.model.entity.User;
import com.storres.box_school.model.shared.Roles;
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

    @Override
    public AuthResponse login(LoginRequest request) {
       authenticationManager.authenticate(
        new UsernamePasswordAuthenticationToken(
            request.getUsername(),
             request.getPassword()) 

            );

            UserDetails userDetails = userDetailsService.loadUserByUsername(request.getUsername());

            String token = jwtService.generateToken(userDetails);

            //retorno del JWT
            return AuthResponse.builder()
            .token(token)
            .build();
    }

    @Override
    public AuthResponse register(RegisterRequest request) {

        // if (userRepository.existsByUsername(request.getUsername())) {
        //     throw new UsernameAlreadyExistsException("El username ya esta registrado");
            
        // }

        User user = User.builder()
                .username(request.getUsername())
                .password(passwordEncoder.encode(request.getPassword()))
                .role(request.getRole() != null ? request.getRole() : Roles.ROLE_USER)
                .enabled(true)
                .build();

                userRepository.save(user);

                UserDetails userDetails = userDetailsService.loadUserByUsername(user.getUsername());

                String token = jwtService.generateToken(userDetails);

                return AuthResponse.builder()
                .token(token)
                .build();
    }


}

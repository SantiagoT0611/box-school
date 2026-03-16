package com.storres.box_school.service;

import java.util.List;

import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import com.storres.box_school.exception.StudentNotFoundExcepcion;
import com.storres.box_school.repository.UserRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService{

    private final UserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
      var user = userRepository.findByUsername(username)
       .orElseThrow(() -> new StudentNotFoundExcepcion());

       var authorities = List.of(
        new SimpleGrantedAuthority(user.getRole().name()));

       return User.builder()
       .username(user.getUsername())
       .password(user.getPassword())
       .authorities(authorities)
       .disabled(!user.getEnabled())
       .build();
    }

}

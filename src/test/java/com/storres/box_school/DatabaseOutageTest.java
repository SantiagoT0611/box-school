package com.storres.box_school;

import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import com.storres.box_school.model.entity.User;
import com.storres.box_school.model.shared.Roles;
import com.storres.box_school.repository.UserRepository;
import com.storres.box_school.service.CustomUserDetailsService;

/**
 * Regresion de M-7 (auditoria Fase 0): una caida de la base de datos al validar el token NO es un token
 * invalido. Antes devolvia 401 y el frontend cerraba la sesion de todos; ahora 503.
 */
@HermeticTest
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class DatabaseOutageTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired UserRepository users;
    @Autowired PasswordEncoder encoder;
    @MockitoSpyBean CustomUserDetailsService userDetailsService;

    @Test
    void databaseFailureWhileValidatingTheTokenIs503NotAnAuthenticationFailure() throws Exception {
        users.save(User.builder().username("admin").password(encoder.encode("AdminPass12345"))
                .role(Roles.ROLE_ADMIN).enabled(true).build());
        String body = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("username", "admin", "password", "AdminPass12345"))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String token = JsonPath.read(body, "$.token");

        // Con la BD sana el token sirve...
        mvc.perform(get("/api/students").header("Authorization", "Bearer " + token)).andExpect(status().isOk());

        // ...y si la BD cae justo al validarlo, es un 503, no un 401
        doThrow(new DataAccessResourceFailureException("conexion perdida"))
                .when(userDetailsService).loadUserByUsername("admin");

        mvc.perform(get("/api/students").header("Authorization", "Bearer " + token))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value(503));
    }
}

package com.storres.box_school.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import com.storres.box_school.model.entity.User;
import com.storres.box_school.model.shared.Roles;
import com.storres.box_school.repository.UserRepository;

import lombok.RequiredArgsConstructor;

/**
 * Crea el primer administrador al arrancar, SOLO si:
 *  - se definieron BOOTSTRAP_ADMIN_USERNAME y BOOTSTRAP_ADMIN_PASSWORD (app.bootstrap-admin.*), y
 *  - todavia no existe ningun usuario ADMIN.
 * No hay credenciales por defecto en el codigo. Despues del primer arranque conviene quitar esas variables.
 */
@Component
@RequiredArgsConstructor
public class AdminBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);
    private static final int MIN_PASSWORD_LENGTH = 12;

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AppProperties props;

    @Override
    public void run(ApplicationArguments args) {
        var cfg = props.bootstrapAdmin();
        if (cfg.username() == null || cfg.username().isBlank() || cfg.password() == null || cfg.password().isBlank()) {
            if (!userRepository.existsByRole(Roles.ROLE_ADMIN)) {
                log.warn("No existe ningun administrador. Define BOOTSTRAP_ADMIN_USERNAME y BOOTSTRAP_ADMIN_PASSWORD para crear el primero.");
            }
            return;
        }
        if (userRepository.existsByRole(Roles.ROLE_ADMIN)) {
            return;
        }
        if (cfg.password().length() < MIN_PASSWORD_LENGTH) {
            throw new IllegalStateException(
                    "BOOTSTRAP_ADMIN_PASSWORD debe tener al menos " + MIN_PASSWORD_LENGTH + " caracteres");
        }

        userRepository.save(User.builder()
                .username(cfg.username().trim().toLowerCase())
                .password(passwordEncoder.encode(cfg.password()))
                .role(Roles.ROLE_ADMIN)
                .enabled(true)
                .build());
        log.info("Administrador inicial '{}' creado", cfg.username());
    }
}

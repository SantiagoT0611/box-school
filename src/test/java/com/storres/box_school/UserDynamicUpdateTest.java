package com.storres.box_school;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import com.storres.box_school.model.entity.User;
import com.storres.box_school.model.shared.Roles;
import com.storres.box_school.repository.UserRepository;

/**
 * Regresion de la Fase 1: deshabilitar una cuenta lee el usuario y confirma despues. Sin @DynamicUpdate Hibernate
 * reescribia password y token_version con los valores leidos al inicio y DESHACIA un cambio de contrasena confirmado
 * entre medio (la clave vieja volvia a valer y las sesiones cerradas se reabrian). Mismo patron que
 * {@link StudentDynamicUpdateTest}. Sin @Transactional a proposito: hacen falta dos transacciones reales.
 */
@HermeticTest
@SpringBootTest
class UserDynamicUpdateTest {

    @Autowired UserRepository users;
    @Autowired PlatformTransactionManager txManager;
    @Autowired JdbcTemplate jdbc;

    private Long userId;

    @AfterEach
    void cleanUp() {
        if (userId != null) {
            jdbc.update("delete from users where id = ?", userId);
        }
    }

    @Test
    void disablingTheAccountDoesNotUndoAConcurrentPasswordChange() {
        User user = User.builder().username("dinamico").password("hash-viejo").role(Roles.ROLE_USER).enabled(true).build();
        userId = users.saveAndFlush(user).getId();

        TransactionTemplate outer = new TransactionTemplate(txManager);
        TransactionTemplate inner = new TransactionTemplate(txManager);
        inner.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        outer.executeWithoutResult(status -> {
            User loaded = users.findById(userId).orElseThrow(); // lee password = hash-viejo, token_version = 0

            // "otro hilo": se restablece la clave y se confirma mientras la transaccion externa sigue abierta
            inner.executeWithoutResult(s -> assertThat(users.changePassword(userId, "hash-nuevo")).isEqualTo(1));

            // la transaccion externa solo deshabilita la cuenta y confirma
            loaded.setEnabled(false);
        });

        var row = jdbc.queryForMap("select password, token_version, enabled from users where id = ?", userId);
        assertThat(row.get("password")).as("el cambio de clave concurrente no debe perderse").isEqualTo("hash-nuevo");
        assertThat(((Number) row.get("token_version")).intValue()).isEqualTo(1);
        assertThat(row.get("enabled")).isEqualTo(false);
    }

    @Test
    void passwordChangeNeverReEnablesADisabledAccount() {
        User user = User.builder().username("deshabilitado").password("hash-viejo").role(Roles.ROLE_USER)
                .enabled(false).build();
        userId = users.saveAndFlush(user).getId();

        Integer applied = new TransactionTemplate(txManager).execute(s -> users.changePassword(userId, "hash-nuevo"));
        assertThat(applied).as("cuenta deshabilitada: no se aplica").isZero();

        var row = jdbc.queryForMap("select password, token_version, enabled from users where id = ?", userId);
        assertThat(row.get("password")).isEqualTo("hash-viejo");
        assertThat(((Number) row.get("token_version")).intValue()).isZero();
        assertThat(row.get("enabled")).isEqualTo(false);
    }
}

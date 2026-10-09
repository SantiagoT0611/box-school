package com.storres.box_school;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;

/**
 * Reproduce en un PostgreSQL REAL (Docker) la ruta exacta de actualizacion de la base de desarrollo:
 * esquema creado por Hibernate ddl-auto=update (legacy-schema.sql) -> Flyway con baseline en V1 ->
 * V2..V5 -> Hibernate validate. Despues comprueba lo que H2 no puede: restricciones reales,
 * login de usuarios heredados y bloqueo de fila con pagos simultaneos.
 * Se omite automaticamente si no hay Docker.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
@AutoConfigureMockMvc
class PostgresLegacyUpgradeTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withInitScript("legacy-schema.sql");

    private static final String PASSWORD = "ClaveVieja123";

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder encoder;

    @Test
    void legacySchemaIsRepairedByTheMigrations() {
        List<String> history = jdbc.queryForList(
                "select version from flyway_schema_history where success order by installed_rank", String.class);
        assertThat(history).containsExactly("1", "2", "3", "4", "5");

        List<String> paymentFks = jdbc.queryForList("""
                select confrelid::regclass::text from pg_constraint
                where contype = 'f' and conrelid = 'payments'::regclass""", String.class);
        assertThat(paymentFks).as("la FK vieja a 'price' debe haberse eliminado")
                .containsExactlyInAnyOrder("prices", "students");

        Integer uniqueEmail = jdbc.queryForObject("""
                select count(*) from pg_constraint
                where contype = 'u' and conrelid = 'students'::regclass
                  and pg_get_constraintdef(oid) = 'UNIQUE (email)'""", Integer.class);
        assertThat(uniqueEmail).as("students.email debe ser UNIQUE").isEqualTo(1);

        Integer legacyAmountColumn = jdbc.queryForObject("""
                select count(*) from information_schema.columns
                where table_name = 'payments' and column_name = 'amount'""", Integer.class);
        assertThat(legacyAmountColumn).as("la columna heredada payments.amount debe haberse eliminado").isZero();

        List<String> usernames = jdbc.queryForList("select username from users order by id", String.class);
        assertThat(usernames).containsExactly("salito", "sofi");
    }

    @Test
    void legacyUserWithUppercaseCanStillLogIn() throws Exception {
        jdbc.update("update users set password = ? where username = 'salito'", encoder.encode(PASSWORD));

        // se escribe como en la base antigua ("Salito"): el login normaliza y encuentra al usuario migrado
        String token = login("Salito", PASSWORD);
        assertThat(token).isNotBlank();
        postJson("/api/auth/login", null, Map.of("username", "Salito", "password", "otra"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void concurrentPaymentsOnTheSameStudentAreSerializedByTheRowLock() throws Exception {
        jdbc.update("update users set password = ? where username = 'salito'", encoder.encode(PASSWORD));
        String admin = login("salito", PASSWORD);

        postJson("/api/prices", admin, Map.of("type", "MONTHLY", "amount", 80000, "durationDays", 30))
                .andExpect(status().isCreated());
        String created = postJson("/api/students", admin, Map.of("firstName", "Carrera", "lastName", "Pagos",
                "email", "carrera@legacy.test", "phone", "3001234567"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long studentId = ((Number) JsonPath.read(created, "$.id")).longValue();
        LocalDate before = LocalDate.parse(JsonPath.read(created, "$.expirationDate"));

        int parallel = 8;
        List<Integer> statuses = Collections.synchronizedList(new ArrayList<>());
        List<Thread> threads = new ArrayList<>();
        for (int i = 0; i < parallel; i++) {
            Thread t = new Thread(() -> {
                try {
                    statuses.add(postJson("/api/payments/students/" + studentId, admin, Map.of("type", "MONTHLY"))
                            .andReturn().getResponse().getStatus());
                } catch (Exception e) {
                    statuses.add(-1);
                }
            });
            threads.add(t);
            t.start();
        }
        for (Thread t : threads) {
            t.join();
        }

        assertThat(statuses).as("todos los pagos simultaneos deben registrarse").containsOnly(201);
        LocalDate after = jdbc.queryForObject("select expiration_date from students where id = ?", LocalDate.class, studentId);
        assertThat(after).as("cada pago suma 30 dias sin pisar a los demas").isEqualTo(before.plusDays(30L * parallel));

        // los periodos forman una cadena contigua: ninguno se solapa ni deja huecos
        List<Map<String, Object>> periods = jdbc.queryForList(
                "select period_start, period_end from payments where student_id = ? order by period_start", studentId);
        for (int i = 0; i < periods.size() - 1; i++) {
            assertThat(periods.get(i).get("period_end")).isEqualTo(periods.get(i + 1).get("period_start"));
        }
    }

    // ------------------------------------------------------------------ helpers

    private String login(String username, String password) throws Exception {
        String body = postJson("/api/auth/login", null, Map.of("username", username, "password", password))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.token");
    }

    private ResultActions postJson(String url, String token, Object body) throws Exception {
        var request = post(url).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
        if (token != null) {
            request.header("Authorization", "Bearer " + token);
        }
        return mvc.perform(request);
    }
}

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
@HermeticTest
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
    @org.springframework.test.context.bean.override.mockito.MockitoSpyBean
    com.storres.box_school.service.RegistrationCodeService registrationCodes;

    @Test
    void legacySchemaIsRepairedByTheMigrations() {
        List<String> history = jdbc.queryForList(
                "select version from flyway_schema_history where success order by installed_rank", String.class);
        assertThat(history).containsExactly("1", "2", "3", "4", "5", "6");

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

    @Test
    void doubleSubmitOfCreatePriceNeverLeavesTwoActivePricesOfTheSameType() throws Exception {
        // A-2 (auditoria Fase 0): antes quedaban 2 activos y TODOS los pagos de ese tipo daban 500
        jdbc.update("update users set password = ? where username = 'salito'", encoder.encode(PASSWORD));
        String admin = login("salito", PASSWORD);

        for (int round = 0; round < 3; round++) { // varias rondas: la carrera no es determinista
            int parallel = 6;
            List<Integer> statuses = Collections.synchronizedList(new ArrayList<>());
            List<Thread> threads = new ArrayList<>();
            final int amount = 10_000 + round;
            for (int i = 0; i < parallel; i++) {
                Thread t = new Thread(() -> {
                    try {
                        statuses.add(postJson("/api/prices", admin,
                                Map.of("type", "BIWEEKLY", "amount", amount, "durationDays", 15))
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

            assertThat(statuses).as("ronda %d: solo 201 (creado) o 409 (perdio la carrera), nunca 500", round)
                    .allMatch(code -> code == 201 || code == 409).contains(201);
            Integer active = jdbc.queryForObject(
                    "select count(*) from prices where type = 'BIWEEKLY' and active = true", Integer.class);
            assertThat(active).as("ronda %d: exactamente un precio activo por tipo", round).isEqualTo(1);
            // y la API sigue sana: el precio vigente se puede consultar (antes: 500)
            mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                            .get("/api/prices/active/BIWEEKLY").header("Authorization", "Bearer " + admin))
                    .andExpect(status().isOk());
        }
    }

    @Test
    void profileEditsAndPaymentsRunningTogetherNeverLoseAPaymentExtension() throws Exception {
        // M-1 (auditoria Fase 0): editar el perfil leia sin bloqueo y reescribia expiration_date con el valor viejo
        jdbc.update("update users set password = ? where username = 'salito'", encoder.encode(PASSWORD));
        String admin = login("salito", PASSWORD);
        postJson("/api/prices", admin, Map.of("type", "MONTHLY", "amount", 80000, "durationDays", 30));
        String created = postJson("/api/students", admin, Map.of("firstName", "Edita", "lastName", "Mientras",
                "email", "edita@legacy.test", "phone", "3001234567"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long studentId = ((Number) JsonPath.read(created, "$.id")).longValue();
        LocalDate before = LocalDate.parse(JsonPath.read(created, "$.expirationDate"));

        int payments = 6;
        List<Integer> payStatuses = Collections.synchronizedList(new ArrayList<>());
        List<Integer> editStatuses = Collections.synchronizedList(new ArrayList<>());
        List<Thread> threads = new ArrayList<>();
        for (int i = 0; i < payments; i++) {
            final int n = i;
            threads.add(new Thread(() -> {
                try {
                    payStatuses.add(postJson("/api/payments/students/" + studentId, admin, Map.of("type", "MONTHLY"))
                            .andReturn().getResponse().getStatus());
                } catch (Exception e) {
                    payStatuses.add(-1);
                }
            }));
            threads.add(new Thread(() -> {
                try {
                    editStatuses.add(mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                                    .put("/api/students/" + studentId).contentType(MediaType.APPLICATION_JSON)
                                    .header("Authorization", "Bearer " + admin)
                                    .content(json.writeValueAsString(Map.of("firstName", "Edita" + n, "lastName", "Mientras",
                                            "email", "edita@legacy.test", "phone", "300123456" + n))))
                            .andReturn().getResponse().getStatus());
                } catch (Exception e) {
                    editStatuses.add(-1);
                }
            }));
        }
        threads.forEach(Thread::start);
        for (Thread t : threads) {
            t.join();
        }

        assertThat(payStatuses).containsOnly(201);
        assertThat(editStatuses).containsOnly(200);
        LocalDate after = jdbc.queryForObject("select expiration_date from students where id = ?", LocalDate.class, studentId);
        assertThat(after).as("ninguna edicion de perfil debe revertir la extension de un pago")
                .isEqualTo(before.plusDays(30L * payments));
    }

    @Test
    void registerRacingWithDeactivateNeverLeavesAnEnabledAccountForAnInactiveStudent() throws Exception {
        // NR-1 (verificacion final), entrelazado DETERMINISTA: register ya leyo al estudiante (ACTIVE) y se detiene
        // justo antes de crear el usuario; en ese instante se desactiva al estudiante. Sin el bloqueo de fila,
        // deactivate confirma de inmediato, no encuentra usuario que deshabilitar y register crea despues una
        // cuenta HABILITADA para un estudiante INACTIVO. Con el bloqueo, deactivate espera a que register termine
        // y entonces deshabilita la cuenta recien creada.
        jdbc.update("update users set password = ? where username = 'salito'", encoder.encode(PASSWORD));
        String admin = login("salito", PASSWORD);

        String email = "nr1@legacy.test";
        String created = postJson("/api/students", admin, Map.of("firstName", "Reg", "lastName", "Desact",
                "email", email, "phone", "3001234567"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long studentId = ((Number) JsonPath.read(created, "$.id")).longValue();
        String code = JsonPath.read(created, "$.registrationCode");

        java.util.concurrent.CountDownLatch registerHasReadTheStudent = new java.util.concurrent.CountDownLatch(1);
        org.mockito.Mockito.doAnswer(invocation -> {
            registerHasReadTheStudent.countDown();
            Thread.sleep(1500); // ventana en la que se desactiva al estudiante
            return invocation.callRealMethod();
        }).when(registrationCodes).matches(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq(code));

        Thread register = new Thread(() -> {
            try {
                postJson("/api/auth/register", null, Map.of("username", "nr1user", "password", "ClaveSegura123",
                        "email", email, "registrationCode", code));
            } catch (Exception ignored) {
                // el resultado se verifica por el estado final de la base
            }
        });
        register.start();
        assertThat(registerHasReadTheStudent.await(10, java.util.concurrent.TimeUnit.SECONDS)).isTrue();

        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .patch("/api/students/" + studentId + "/deactivate")
                .header("Authorization", "Bearer " + admin)).andExpect(status().isOk());
        register.join();

        String statusNow = jdbc.queryForObject("select status from students where id = ?", String.class, studentId);
        Integer enabledAccounts = jdbc.queryForObject(
                "select count(*) from users where student_id = ? and enabled = true", Integer.class, studentId);
        assertThat(statusNow).isEqualTo("INACTIVE");
        assertThat(enabledAccounts).as("un estudiante INACTIVO no puede tener la cuenta habilitada").isZero();
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

package com.storres.box_school;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import com.storres.box_school.model.entity.User;
import com.storres.box_school.model.shared.Roles;
import com.storres.box_school.repository.StudentRepository;
import com.storres.box_school.repository.UserRepository;
import com.storres.box_school.service.MailService;

/**
 * Recorre la API real (seguridad, validacion, negocio, BD con las migraciones Flyway) sobre H2.
 * Cada test corre en una transaccion que se revierte al terminar.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ApiFlowIntegrationTest {

    private static final String ADMIN_PASSWORD = "AdminPass12345";

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired UserRepository users;
    @Autowired StudentRepository students;
    @Autowired PasswordEncoder encoder;
    @Autowired Clock clock;
    @MockitoBean MailService mailService; // no se envian correos reales

    private String adminToken;

    @BeforeEach
    void setUp() throws Exception {
        users.save(User.builder().username("admin").password(encoder.encode(ADMIN_PASSWORD))
                .role(Roles.ROLE_ADMIN).enabled(true).build());
        adminToken = login("admin", ADMIN_PASSWORD);
    }

    // ---------------------------------------------------------------- seguridad

    @Test
    void requestsWithoutTokenGet401() throws Exception {
        mvc.perform(get("/api/students/me")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/students")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/students").header("Authorization", "Bearer token.invalido.xyz"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void wrongPasswordAndUnknownUserGetTheSame401() throws Exception {
        String wrongPassword = postJson("/api/auth/login", null, Map.of("username", "admin", "password", "mala"))
                .andExpect(status().isUnauthorized()).andReturn().getResponse().getContentAsString();
        String unknownUser = postJson("/api/auth/login", null, Map.of("username", "nadie", "password", "mala"))
                .andExpect(status().isUnauthorized()).andReturn().getResponse().getContentAsString();

        // no debe poder distinguirse "usuario inexistente" de "clave incorrecta"
        assertThat(JsonPath.<String>read(wrongPassword, "$.message")).isEqualTo(JsonPath.<String>read(unknownUser, "$.message"));
    }

    @Test
    void studentCannotUseAdminEndpointsNorSeeOtherStudents() throws Exception {
        Created ana = createStudent("ana@test.com");
        Created luis = createStudent("luis@test.com");
        String anaToken = register(ana, "ana");

        mvc.perform(get("/api/students/me").header("Authorization", bearer(anaToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("ana@test.com"));

        // IDOR: ana intenta leer a luis y su historial de pagos, y operar como admin
        mvc.perform(get("/api/students/" + luis.id).header("Authorization", bearer(anaToken)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/payments/students/" + luis.id).header("Authorization", bearer(anaToken)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/students").header("Authorization", bearer(anaToken))).andExpect(status().isForbidden());
        postJson("/api/students", anaToken, studentBody("intruso@test.com")).andExpect(status().isForbidden());
        postJson("/api/prices", anaToken, priceBody("MONTHLY", "50.00", 30)).andExpect(status().isForbidden());
        postJson("/api/payments/students/" + ana.id, anaToken, Map.of("type", "MONTHLY"))
                .andExpect(status().isForbidden());
    }

    // ---------------------------------------------------------------- registro

    @Test
    void registrationRequiresTheOneTimeCode() throws Exception {
        Created ana = createStudent("ana@test.com");

        // conocer solo el email no alcanza
        postJson("/api/auth/register", null, registerBody("hacker", "ana@test.com", "CODIGOFALSO")).andExpect(status().isBadRequest());
        // email inexistente: mismo error generico
        postJson("/api/auth/register", null, registerBody("hacker", "noexiste@test.com", ana.code)).andExpect(status().isBadRequest());

        postJson("/api/auth/register", null, registerBody("ana", "ana@test.com", ana.code))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.roles[0]").value("ROLE_USER"));

        // el codigo es de un solo uso
        postJson("/api/auth/register", null, registerBody("ana2", "ana@test.com", ana.code)).andExpect(status().isBadRequest());
    }

    @Test
    void registrationValidatesInput() throws Exception {
        postJson("/api/auth/register", null, registerBody("a", "no-es-email", "X")
                ).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.username").exists())
                .andExpect(jsonPath("$.errors.email").exists());
    }

    // ---------------------------------------------------------------- estudiantes

    @Test
    void duplicateEmailIsRejectedCaseInsensitively() throws Exception {
        createStudent("ana@test.com");
        postJson("/api/students", adminToken, studentBody("ANA@Test.com")).andExpect(status().isConflict());
    }

    @Test
    void invalidStudentGets400WithFieldErrors() throws Exception {
        postJson("/api/students", adminToken, Map.of("firstName", "", "lastName", "X", "email", "mal", "phone", "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.firstName").exists())
                .andExpect(jsonPath("$.errors.email").exists())
                .andExpect(jsonPath("$.errors.phone").exists());
    }

    @Test
    void deactivatingAStudentCutsAccessEvenWithAnUnexpiredToken() throws Exception {
        Created ana = createStudent("ana@test.com");
        String anaToken = register(ana, "ana");
        mvc.perform(get("/api/students/me").header("Authorization", bearer(anaToken))).andExpect(status().isOk());

        mvc.perform(patch("/api/students/" + ana.id + "/deactivate").header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("INACTIVE"));

        mvc.perform(get("/api/students/me").header("Authorization", bearer(anaToken))).andExpect(status().isUnauthorized());
        postJson("/api/auth/login", null, Map.of("username", "ana", "password", "ClaveSegura123"))
                .andExpect(status().isUnauthorized());

        mvc.perform(patch("/api/students/" + ana.id + "/activate").header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk());
        login("ana", "ClaveSegura123"); // vuelve a poder entrar
    }

    @Test
    void searchFiltersByTextAndStatus() throws Exception {
        createStudent("ana@test.com");
        Created luis = createStudent("luis@test.com");
        mvc.perform(patch("/api/students/" + luis.id + "/deactivate").header("Authorization", bearer(adminToken)));

        mvc.perform(get("/api/students").param("q", "ANA").header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].email").value("ana@test.com"));
        mvc.perform(get("/api/students").param("status", "INACTIVE").header("Authorization", bearer(adminToken)))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].email").value("luis@test.com"));
        // un % del usuario se busca literalmente, no como comodin
        mvc.perform(get("/api/students").param("q", "%").header("Authorization", bearer(adminToken)))
                .andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(get("/api/students").param("sort", "campoInexistente").header("Authorization", bearer(adminToken)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void expiredAndExpiringListsUseTheirOwnWindows() throws Exception {
        LocalDate today = LocalDate.now(clock);
        Created vencida = createStudent("vencida@test.com");
        Created porVencer = createStudent("porvencer@test.com");
        Created lejana = createStudent("lejana@test.com");
        setExpiration(vencida.id, today.minusDays(3));
        setExpiration(porVencer.id, today.plusDays(2));
        setExpiration(lejana.id, today.plusDays(20));

        mvc.perform(get("/api/students/expired").header("Authorization", bearer(adminToken)))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].email").value("vencida@test.com"))
                .andExpect(jsonPath("$.content[0].membershipExpired").value(true))
                .andExpect(jsonPath("$.content[0].daysUntilExpiration").value(-3));
        mvc.perform(get("/api/students/expiring").header("Authorization", bearer(adminToken)))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].email").value("porvencer@test.com"));
    }

    // ---------------------------------------------------------------- precios y pagos

    @Test
    void paymentWithoutActivePriceIs400() throws Exception {
        Created ana = createStudent("ana@test.com");
        postJson("/api/payments/students/" + ana.id, adminToken, Map.of("type", "MONTHLY"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void paymentsExtendMembershipWithoutLosingPaidDays() throws Exception {
        postJson("/api/prices", adminToken, priceBody("MONTHLY", "50.00", 30)).andExpect(status().isCreated());
        Created ana = createStudent("ana@test.com");
        String anaToken = register(ana, "ana");
        LocalDate firstExpiration = students.findById(ana.id).orElseThrow().getExpirationDate();

        postJson("/api/payments/students/" + ana.id, adminToken, Map.of("type", "MONTHLY"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.amountPaid").value(50.00))
                .andExpect(jsonPath("$.periodStart").value(firstExpiration.toString()))
                .andExpect(jsonPath("$.periodEnd").value(firstExpiration.plusDays(30).toString()));
        postJson("/api/payments/students/" + ana.id, adminToken, Map.of("type", "MONTHLY"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.periodEnd").value(firstExpiration.plusDays(60).toString()));

        assertThat(students.findById(ana.id).orElseThrow().getExpirationDate()).isEqualTo(firstExpiration.plusDays(60));
        mvc.perform(get("/api/payments/me").header("Authorization", bearer(anaToken)))
                .andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    void expiredMembershipRestartsFromToday() throws Exception {
        postJson("/api/prices", adminToken, priceBody("MONTHLY", "50.00", 30));
        Created ana = createStudent("ana@test.com");
        LocalDate today = LocalDate.now(clock);
        setExpiration(ana.id, today.minusDays(10));

        postJson("/api/payments/students/" + ana.id, adminToken, Map.of("type", "MONTHLY"))
                .andExpect(jsonPath("$.periodStart").value(today.toString()))
                .andExpect(jsonPath("$.periodEnd").value(today.plusDays(30).toString()));
    }

    @Test
    void creatingAPriceDeactivatesThePreviousOneOfTheSameType() throws Exception {
        postJson("/api/prices", adminToken, priceBody("MONTHLY", "50.00", 30));
        postJson("/api/prices", adminToken, priceBody("MONTHLY", "60.00", 30));

        mvc.perform(get("/api/prices/active/MONTHLY").header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.amount").value(60.00));
        mvc.perform(get("/api/prices/active").header("Authorization", bearer(adminToken)))
                .andExpect(jsonPath("$.length()").value(1));
        postJson("/api/prices", adminToken, priceBody("MONTHLY", "-5", 30)).andExpect(status().isBadRequest());
    }

    // ---------------------------------------------------------------- helpers

    private record Created(Long id, String email, String code) {
    }

    private Created createStudent(String email) throws Exception {
        String body = postJson("/api/students", adminToken, studentBody(email))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.registrationCode").isNotEmpty())
                .andReturn().getResponse().getContentAsString();
        return new Created(((Number) JsonPath.read(body, "$.id")).longValue(),
                email.toLowerCase(), JsonPath.read(body, "$.registrationCode"));
    }

    private String register(Created student, String username) throws Exception {
        String body = postJson("/api/auth/register", null, registerBody(username, student.email, student.code))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.token");
    }

    private String login(String username, String password) throws Exception {
        String body = postJson("/api/auth/login", null, Map.of("username", username, "password", password))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.token");
    }

    private void setExpiration(Long studentId, LocalDate date) {
        var student = students.findById(studentId).orElseThrow();
        student.setExpirationDate(date);
        students.saveAndFlush(student);
    }

    private ResultActions postJson(String url, String token, Object body) throws Exception {
        var request = post(url).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
        if (token != null) {
            request.header("Authorization", bearer(token));
        }
        return mvc.perform(request);
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }

    private static Map<String, Object> studentBody(String email) {
        return Map.of("firstName", "Nombre", "lastName", "Apellido", "email", email, "phone", "3001234567");
    }

    private static Map<String, Object> priceBody(String type, String amount, int days) {
        return Map.of("type", type, "amount", new java.math.BigDecimal(amount), "durationDays", days);
    }

    private static Map<String, Object> registerBody(String username, String email, String code) {
        return Map.of("username", username, "password", "ClaveSegura123", "email", email, "registrationCode", code);
    }
}

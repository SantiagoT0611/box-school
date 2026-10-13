package com.storres.box_school;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import com.storres.box_school.model.entity.User;
import com.storres.box_school.model.shared.Roles;
import com.storres.box_school.repository.UserRepository;
import com.storres.box_school.service.MailService;
import com.storres.box_school.service.PasswordResetService;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

/**
 * Fase 1: recuperar y cambiar la contrasena. Sin @Transactional a proposito: la solicitud del enlace corre en
 * segundo plano (otro hilo) y debe ver los datos ya confirmados. Cada test limpia las tablas que toca.
 *
 * Es la bateria de "intentos de romperlo" de la fase: token adivinado, reutilizado, expirado, reemplazado,
 * cuenta deshabilitada, enumeracion de cuentas, bombardeo de correo, sesiones anteriores y fuga en los logs.
 */
@HermeticTest
@SpringBootTest
@AutoConfigureMockMvc
class PasswordRecoveryIntegrationTest {

    private static final String ADMIN_PASSWORD = "AdminPass12345";
    private static final String OLD_PASSWORD = "ClaveSegura123";
    private static final String NEW_PASSWORD = "NuevaClave4567";
    private static final String EMAIL = "ana@test.com";
    private static final Pattern LINK_TOKEN = Pattern.compile("token=([A-Za-z0-9_-]{43})");
    private static final String GENERIC_FORGOT = "Si el email corresponde a una cuenta activa, te enviaremos un enlace para recuperar tu contraseña";

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired UserRepository users;
    @Autowired PasswordEncoder encoder;
    @Autowired JdbcTemplate jdbc;
    @Autowired Clock clock;
    @MockitoSpyBean PasswordResetService resets;
    @MockitoBean MailService mailService;

    private String adminToken;
    private long studentId;
    private String oldSessionToken; // sesion de ana abierta ANTES de cualquier cambio de clave

    @BeforeEach
    void setUp() throws Exception {
        cleanUp();
        users.save(User.builder().username("admin").password(encoder.encode(ADMIN_PASSWORD))
                .role(Roles.ROLE_ADMIN).enabled(true).build());
        adminToken = login("admin", ADMIN_PASSWORD);

        String created = postJson("/api/students", adminToken,
                Map.of("firstName", "Ana", "lastName", "Perez", "email", EMAIL, "phone", "3001234567"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        studentId = ((Number) JsonPath.read(created, "$.id")).longValue();
        String registered = postJson("/api/auth/register", null, Map.of("username", "ana", "password", OLD_PASSWORD,
                "email", EMAIL, "registrationCode", JsonPath.<String>read(created, "$.registrationCode")))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        oldSessionToken = JsonPath.read(registered, "$.token");
    }

    @AfterEach
    void cleanUp() {
        jdbc.update("delete from password_reset_tokens");
        jdbc.update("delete from users");
        jdbc.update("delete from students");
    }

    // ------------------------------------------------------------ solicitud del enlace

    @Test
    void forgotPasswordAnswersExactlyTheSameWhetherTheEmailExistsOrNot() throws Exception {
        String known = postJson("/api/auth/forgot-password", null, Map.of("email", EMAIL))
                .andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString();
        String unknown = postJson("/api/auth/forgot-password", null, Map.of("email", "nadie@test.com"))
                .andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString();
        String upperCase = postJson("/api/auth/forgot-password", null, Map.of("email", "ANA@Test.com"))
                .andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString();

        assertThat(known).isEqualTo(unknown).isEqualTo(upperCase);
        assertThat(JsonPath.<String>read(known, "$.message")).isEqualTo(GENERIC_FORGOT);
    }

    @Test
    void theRequestReturnsBeforeTheWorkIsDoneSoTimingDoesNotRevealTheAccount() throws Exception {
        // Si el trabajo fuera sincrono, una cuenta existente tardaria mas que una inexistente (lectura + escritura
        // + token). Se simula un trabajo lento: la respuesta HTTP no debe esperarlo.
        doAnswer(invocation -> {
            Thread.sleep(1500);
            return invocation.callRealMethod();
        }).when(resets).issueFor(anyString());

        long started = System.nanoTime();
        postJson("/api/auth/forgot-password", null, Map.of("email", EMAIL)).andExpect(status().isAccepted());
        long millis = (System.nanoTime() - started) / 1_000_000;

        assertThat(millis).as("la respuesta no espera al trabajo en segundo plano").isLessThan(1000);
        verify(mailService, timeout(8000)).sendQuietly(eq(EMAIL), anyString(), anyString()); // y el trabajo si termina
    }

    @Test
    void theLinkReachesTheStudentByEmailAndWorksEndToEnd() throws Exception {
        postJson("/api/auth/forgot-password", null, Map.of("email", EMAIL)).andExpect(status().isAccepted());

        String body = capturedResetMailBody();
        assertThat(body).contains("http://localhost:4200/reset-password?token=").contains("30 minutos");
        String token = tokenFrom(body);

        reset(token, NEW_PASSWORD).andExpect(status().isOk());
        login("ana", NEW_PASSWORD);
    }

    @Test
    void noEmailIsSentForUnknownDeactivatedOrNeverRegisteredAccounts() throws Exception {
        // estudiante inscrito pero que nunca creo su cuenta
        postJson("/api/students", adminToken,
                Map.of("firstName", "Luis", "lastName", "Gomez", "email", "luis@test.com", "phone", "3001234567"))
                .andExpect(status().isCreated());
        // estudiante deshabilitado
        mvc.perform(patch("/api/students/" + studentId + "/deactivate").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());

        assertThat(resets.issueFor("nadie@test.com")).isEmpty();
        assertThat(resets.issueFor("luis@test.com")).as("sin cuenta de acceso no hay nada que recuperar").isEmpty();
        assertThat(resets.issueFor(EMAIL)).as("una cuenta deshabilitada no puede recuperarse").isEmpty();

        postJson("/api/auth/forgot-password", null, Map.of("email", EMAIL)).andExpect(status().isAccepted());
        verify(mailService, after(500).never()).sendQuietly(anyString(), eq("Recupera tu contraseña"), anyString());
        assertThat(jdbc.queryForObject("select count(*) from password_reset_tokens", Integer.class)).isZero();
    }

    @Test
    void resendCooldownStopsMailBombingAndOnlyTheNewestLinkWorks() throws Exception {
        String first = resets.issueFor(EMAIL).orElseThrow().token();
        assertThat(resets.issueFor(EMAIL)).as("segundo intento dentro del enfriamiento: no se emite ni se envia").isEmpty();

        // pasado el enfriamiento se emite otro y el anterior deja de servir
        jdbc.update("update password_reset_tokens set created_at = ?", LocalDateTime.now(clock).minusMinutes(5));
        String second = resets.issueFor(EMAIL).orElseThrow().token();

        assertThat(second).isNotEqualTo(first);
        reset(first, NEW_PASSWORD).andExpect(status().isBadRequest());
        reset(second, NEW_PASSWORD).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("select count(*) from password_reset_tokens", Integer.class))
                .as("a lo sumo un token por usuario").isEqualTo(1);
    }

    // ------------------------------------------------------------ restablecer

    @Test
    void resettingChangesThePasswordAndClosesEveryOpenSession() throws Exception {
        mvc.perform(get("/api/students/me").header("Authorization", "Bearer " + oldSessionToken))
                .andExpect(status().isOk());
        String token = resets.issueFor(EMAIL).orElseThrow().token();

        reset(token, NEW_PASSWORD).andExpect(status().isOk());

        postJson("/api/auth/login", null, Map.of("username", "ana", "password", OLD_PASSWORD))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/students/me").header("Authorization", "Bearer " + oldSessionToken))
                .andExpect(status().isUnauthorized());
        String fresh = login("ana", NEW_PASSWORD);
        mvc.perform(get("/api/students/me").header("Authorization", "Bearer " + fresh)).andExpect(status().isOk());
    }

    @Test
    void aResetTokenWorksOnlyOnce() throws Exception {
        String token = resets.issueFor(EMAIL).orElseThrow().token();
        reset(token, NEW_PASSWORD).andExpect(status().isOk());

        String again = reset(token, "OtraClaveDistinta789").andExpect(status().isBadRequest())
                .andReturn().getResponse().getContentAsString();

        assertThat(JsonPath.<String>read(again, "$.message")).contains("no es valido");
        login("ana", NEW_PASSWORD); // la segunda clave NO se aplico
    }

    @Test
    void expiredGuessedAndMalformedTokensAreRejectedWithTheSameMessage() throws Exception {
        String token = resets.issueFor(EMAIL).orElseThrow().token();
        jdbc.update("update password_reset_tokens set expires_at = ?", LocalDateTime.now(clock).minusMinutes(1));

        String expired = reset(token, NEW_PASSWORD).andExpect(status().isBadRequest())
                .andReturn().getResponse().getContentAsString();
        String guessed = reset("A".repeat(43), NEW_PASSWORD).andExpect(status().isBadRequest())
                .andReturn().getResponse().getContentAsString();

        assertThat(JsonPath.<String>read(expired, "$.message")).isEqualTo(JsonPath.<String>read(guessed, "$.message"));
        // formato imposible: ni siquiera se consulta la BD (400 de validacion)
        reset("corto", NEW_PASSWORD).andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.token").exists());
        reset("../../etc/passwd" + "x".repeat(10), NEW_PASSWORD).andExpect(status().isBadRequest());
        reset("' or '1'='1" + "x".repeat(20), NEW_PASSWORD).andExpect(status().isBadRequest());
        login("ana", OLD_PASSWORD); // nada cambio
    }

    @Test
    void theTokenIsNeverStoredInPlainText() throws Exception {
        var issued = resets.issueFor(EMAIL).orElseThrow();

        String stored = jdbc.queryForObject("select token_hash from password_reset_tokens", String.class);
        String expected = HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(issued.token().getBytes(StandardCharsets.UTF_8)));

        assertThat(issued.token()).hasSize(43); // 256 bits en Base64 URL-safe
        assertThat(stored).isEqualTo(expected).isNotEqualTo(issued.token());
        assertThat(issued.toString()).doesNotContain(issued.token()).doesNotContain(EMAIL);
    }

    @Test
    void aDeactivatedAccountCannotUseATokenIssuedBeforeItWasDisabled() throws Exception {
        String token = resets.issueFor(EMAIL).orElseThrow().token();
        mvc.perform(patch("/api/students/" + studentId + "/deactivate").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());

        reset(token, NEW_PASSWORD).andExpect(status().isBadRequest());

        Map<String, Object> row = jdbc.queryForMap("select enabled, token_version, password from users where username = 'ana'");
        assertThat(row.get("enabled")).isEqualTo(false);          // no se reactiva
        assertThat(((Number) row.get("token_version")).intValue()).isZero(); // nada cambio
        assertThat(encoder.matches(OLD_PASSWORD, (String) row.get("password"))).isTrue();
    }

    @Test
    void resetValidatesTheNewPassword() throws Exception {
        String token = resets.issueFor(EMAIL).orElseThrow().token();

        reset(token, "corta").andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.newPassword").exists());
        reset(token, "ñ".repeat(60)).andExpect(status().isBadRequest()) // 120 bytes
                .andExpect(jsonPath("$.errors.newPassword").exists());
        reset(token, "   ").andExpect(status().isBadRequest());

        // el token no se gasto con los intentos invalidos
        reset(token, NEW_PASSWORD).andExpect(status().isOk());
    }

    // ------------------------------------------------------------ cambiar (autenticado)

    @Test
    void changePasswordNeedsAnActiveSession() throws Exception {
        postJson("/api/auth/change-password", null, Map.of("currentPassword", OLD_PASSWORD, "newPassword", NEW_PASSWORD))
                .andExpect(status().isUnauthorized());
        postJson("/api/auth/change-password", "token.invalido.xyz",
                Map.of("currentPassword", OLD_PASSWORD, "newPassword", NEW_PASSWORD))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void changePasswordRequiresTheCurrentOneAndClosesTheOtherSessions() throws Exception {
        // con la clave actual equivocada: 400 (no 401: el cliente no debe cerrar la sesion) y nada cambia
        postJson("/api/auth/change-password", oldSessionToken,
                Map.of("currentPassword", "ClaveEquivocada1", "newPassword", NEW_PASSWORD))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("La clave actual es incorrecta"));
        // clave nueva igual a la actual
        postJson("/api/auth/change-password", oldSessionToken,
                Map.of("currentPassword", OLD_PASSWORD, "newPassword", OLD_PASSWORD))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/students/me").header("Authorization", "Bearer " + oldSessionToken))
                .andExpect(status().isOk());

        String body = postJson("/api/auth/change-password", oldSessionToken,
                Map.of("currentPassword", OLD_PASSWORD, "newPassword", NEW_PASSWORD))
                .andExpect(status().isOk()).andExpect(jsonPath("$.token").isNotEmpty())
                .andReturn().getResponse().getContentAsString();
        String newSession = JsonPath.read(body, "$.token");

        mvc.perform(get("/api/students/me").header("Authorization", "Bearer " + oldSessionToken))
                .andExpect(status().isUnauthorized()); // la sesion con la que se pidio el cambio ya no vale
        mvc.perform(get("/api/students/me").header("Authorization", "Bearer " + newSession))
                .andExpect(status().isOk());
        postJson("/api/auth/login", null, Map.of("username", "ana", "password", OLD_PASSWORD))
                .andExpect(status().isUnauthorized());
        login("ana", NEW_PASSWORD);
    }

    @Test
    void anAdministratorCanChangeThePasswordToo() throws Exception {
        postJson("/api/auth/change-password", adminToken,
                Map.of("currentPassword", ADMIN_PASSWORD, "newPassword", "OtraAdminPass999"))
                .andExpect(status().isOk());

        mvc.perform(get("/api/students").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isUnauthorized());
        login("admin", "OtraAdminPass999");
    }

    // ------------------------------------------------------------ higiene

    @Test
    void noLogLineEverContainsTheTokenNorThePasswords() throws Exception {
        var root = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
        var appender = new ListAppender<ILoggingEvent>();
        appender.start();
        Level previous = root.getLevel();
        root.setLevel(Level.DEBUG);
        root.addAppender(appender);
        String token;
        try {
            postJson("/api/auth/forgot-password", null, Map.of("email", EMAIL)).andExpect(status().isAccepted());
            token = tokenFrom(capturedResetMailBody());
            reset(token, NEW_PASSWORD).andExpect(status().isOk());
            reset(token, NEW_PASSWORD).andExpect(status().isBadRequest());               // reutilizado
            reset("B".repeat(43), "OtraClaveDistinta789").andExpect(status().isBadRequest()); // adivinado
            postJson("/api/auth/change-password", login("ana", NEW_PASSWORD),
                    Map.of("currentPassword", "ClaveEquivocada1", "newPassword", "UltimaClave98765"))
                    .andExpect(status().isBadRequest());
        } finally {
            root.detachAppender(appender);
            root.setLevel(previous);
        }

        List<String> lines = appender.list.stream().map(event -> {
            String thrown = event.getThrowableProxy() == null ? ""
                    : ch.qos.logback.classic.spi.ThrowableProxyUtil.asString(event.getThrowableProxy());
            return event.getFormattedMessage() + " " + thrown;
        }).toList();
        assertThat(lines).isNotEmpty();
        assertThat(lines).noneMatch(line -> line.contains(token));
        assertThat(lines).noneMatch(line -> line.contains(NEW_PASSWORD) || line.contains(OLD_PASSWORD)
                || line.contains("ClaveEquivocada1") || line.contains("OtraClaveDistinta789")
                || line.contains("UltimaClave98765"));
        assertThat(lines).as("el email tampoco se registra en las trazas de la recuperacion")
                .noneMatch(line -> line.contains("forgot") && line.contains(EMAIL));
    }

    // ------------------------------------------------------------ helpers

    private String capturedResetMailBody() {
        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        verify(mailService, timeout(8000)).sendQuietly(eq(EMAIL), eq("Recupera tu contraseña"), body.capture());
        return body.getValue();
    }

    private static String tokenFrom(String mailBody) {
        Matcher matcher = LINK_TOKEN.matcher(mailBody);
        assertThat(matcher.find()).as("el correo debe traer el enlace con el token").isTrue();
        return matcher.group(1);
    }

    private ResultActions reset(String token, String newPassword) throws Exception {
        return postJson("/api/auth/reset-password", null, Map.of("token", token, "newPassword", newPassword));
    }

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

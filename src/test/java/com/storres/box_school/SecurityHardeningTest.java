package com.storres.box_school;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.storres.box_school.model.dto.LoginRequest;

/**
 * Regresiones de la auditoria de la Fase 0 sobre Tomcat real y la configuracion REAL (application.yml):
 * A-3 (salud publica), M-8 (tamano de cuerpo) y la exposicion de Swagger/actuator.
 */
@HermeticTest
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SecurityHardeningTest {

    @LocalServerPort
    int port;

    @Autowired
    ObjectMapper mapper;

    private final HttpClient client = HttpClient.newHttpClient();

    private HttpResponse<String> send(HttpRequest.Builder builder) throws Exception {
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpRequest.Builder request(String path) {
        return HttpRequest.newBuilder(URI.create("http://localhost:" + port + path));
    }

    // ---- A-3: solo las sondas de vida/disponibilidad son publicas

    @Test
    void onlyLivenessAndReadinessProbesArePublic() throws Exception {
        assertThat(send(request("/actuator/health/liveness")).statusCode()).isEqualTo(200);
        assertThat(send(request("/actuator/health/readiness")).statusCode()).isEqualTo(200);

        // El agregado (que consultaba SMTP y base de datos) exige autenticacion
        assertThat(send(request("/actuator/health")).statusCode()).isEqualTo(401);
        assertThat(send(request("/actuator/env")).statusCode()).isEqualTo(401);
        assertThat(send(request("/actuator")).statusCode()).isEqualTo(401);
    }

    @Test
    void swaggerIsOffByDefaultAndErrorsDoNotLeakDetails() throws Exception {
        // Config real: springdoc apagado salvo SWAGGER_ENABLED / perfil dev
        assertThat(send(request("/v3/api-docs")).statusCode()).isEqualTo(404);
        assertThat(send(request("/swagger-ui/index.html")).statusCode()).isEqualTo(404);

        HttpResponse<String> notFound = send(request("/api/ruta-que-no-existe")); // sin token
        assertThat(notFound.statusCode()).isEqualTo(401);
        assertThat(notFound.body()).doesNotContain("Exception", "at com.", "org.springframework", "Tomcat");
    }

    // ---- M-8: tamano de cuerpo en endpoints publicos

    @Test
    void oversizedBodyIsRejectedBeforeBeingParsed() throws Exception {
        String hugeUsername = "a".repeat(200_000); // ~200 KB: 3 veces el limite
        String body = "{\"username\":\"" + hugeUsername + "\",\"password\":\"x\"}";

        HttpResponse<String> response = send(request("/api/auth/login")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body)));

        assertThat(response.statusCode()).isEqualTo(413);
        assertThat(response.body()).contains("demasiado grande");
    }

    @Test
    void chunkedBodyWithoutContentLengthIsAlsoCutOff() throws Exception {
        byte[] body = ("{\"username\":\"" + "a".repeat(200_000) + "\",\"password\":\"x\"}").getBytes();

        HttpResponse<String> response = send(request("/api/auth/login")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofInputStream(() -> new ByteArrayInputStream(body))));

        assertThat(response.statusCode()).as("sin Content-Length se corta la lectura: nunca 200 ni 401")
                .isIn(400, 413);
    }

    @Test
    void normalSizedLoginStillReachesTheController() throws Exception {
        HttpResponse<String> response = send(request("/api/auth/login")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"username\":\"nadie\",\"password\":\"clave-incorrecta\"}")));

        assertThat(response.statusCode()).isEqualTo(401); // credenciales invalidas, no 413
    }

    @Test
    void jacksonRefusesAbsurdlyLongStrings() {
        String json = "{\"username\":\"" + "a".repeat(150_000) + "\",\"password\":\"x\"}";

        assertThatThrownBy(() -> mapper.readValue(json, LoginRequest.class))
                .hasMessageContaining("String value length");
    }
}

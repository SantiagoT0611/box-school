package com.storres.box_school;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

/**
 * Regresion de A-1 (auditoria Fase 0): el limite de intentos de /api/auth/** no se podia evadir
 * codificando una letra de la ruta. Corre sobre Tomcat real porque el hallazgo depende de como el
 * contenedor entrega getRequestURI() / getServletPath().
 *
 * Clase aparte con limite bajo: el contador es por IP y compartido por toda la clase.
 */
@HermeticTest
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "app.rate-limit.auth-requests-per-minute=4")
class RateLimitIntegrationTest {

    @LocalServerPort
    int port;

    private final HttpClient client = HttpClient.newHttpClient();

    @Test
    void encodedPathSegmentsCannotBypassTheLoginRateLimit() throws Exception {
        // Se agota el cupo por la ruta normal
        int lastNormal = 0;
        for (int i = 0; i < 6; i++) {
            lastNormal = login("/api/auth/login");
        }
        assertThat(lastNormal).as("la ruta normal debe estar limitada").isEqualTo(429);

        // La misma ruta con una letra codificada llega al mismo controlador: debe seguir limitada
        for (String variant : new String[] { "/api/%61uth/login", "/%61pi/auth/login", "/api/auth/%6cogin",
                "/api/auth/login/", "/api/auth//login" }) {
            assertThat(login(variant)).as("variante '%s' debe contarse en el mismo limite", variant)
                    .isIn(429, 400); // 429 si llega al filtro; 400 si el firewall la rechaza antes
            assertThat(login(variant)).as("variante '%s' nunca debe llegar a autenticar", variant).isNotEqualTo(200);
        }

        // La clave correcta tampoco debe pasar por una variante mientras la IP este bloqueada
        assertThat(login("/api/%61uth/login")).isNotEqualTo(200).isNotEqualTo(401);
    }

    private int login(String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"username\":\"nadie\",\"password\":\"clave-incorrecta\"}"))
                .build();
        return client.send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
    }
}

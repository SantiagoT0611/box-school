package com.storres.box_school;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;

/**
 * Hace que un test de Spring sea HERMETICO (auditoria Fase 0, M-9 y N-3): da igual que la maquina tenga
 * definidas JWT_SECRET, SPRING_MAIL_*, SPRING_DATASOURCE_*, APP_TIMEZONE... Las propiedades de
 * @TestPropertySource tienen MAS prioridad que las variables de entorno, asi que los tests nunca usan el
 * secreto real, ni envian correos reales, ni se conectan a una base que no sea la de prueba.
 *
 * El perfil "test" (application-test.yml) se superpone al application.yml REAL, de modo que tambien se
 * ejercita la configuracion de produccion (Swagger apagado, errores sin mensaje, sondas, limites...).
 *
 * Ademas {@link Guard} aborta el contexto si, pese a todo, la URL del datasource no es de pruebas.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@ActiveProfiles("test")
@ContextConfiguration(initializers = HermeticTest.Guard.class)
@TestPropertySource(properties = {
        "jwt.secret=dGVzdC1vbmx5LXNlY3JldC1rZXktdGVzdC1vbmx5LXNlY3JldC1rZXktMDEyMzQ1Njc4OQ==",
        "app.timezone=UTC",
        // Base de datos: siempre la H2 de pruebas (o la que levante Testcontainers via @ServiceConnection)
        "spring.datasource.url=jdbc:h2:mem:boxschool;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        // Sin JavaMailSender: ningun test puede abrir una conexion SMTP, aunque exista SPRING_MAIL_HOST
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.mail.MailSenderAutoConfiguration",
        // Variables que cambiarian el comportamiento real si estuvieran definidas en la maquina
        "springdoc.api-docs.enabled=false",
        "springdoc.swagger-ui.enabled=false",
        "server.forward-headers-strategy=none",
        "app.cors.allowed-origins=http://localhost:4200",
        "app.registration.code-validity-hours=72"
})
public @interface HermeticTest {

    /** Cinturon y tirantes: falla el contexto si el datasource efectivo no es de pruebas. */
    class Guard implements ApplicationContextInitializer<ConfigurableApplicationContext> {
        @Override
        public void initialize(ConfigurableApplicationContext context) {
            ConfigurableEnvironment env = context.getEnvironment();
            // Flyway puede traer su propia conexion (SPRING_FLYWAY_URL) distinta del datasource: tambien se vigila
            for (String key : new String[] { "spring.datasource.url", "spring.flyway.url" }) {
                String url = env.getProperty(key);
                boolean safe = url == null || url.isBlank()
                        || url.startsWith("jdbc:h2:mem:")
                        || url.startsWith("jdbc:postgresql://localhost")
                        || url.startsWith("jdbc:postgresql://127.0.0.1");
                if (!safe) {
                    throw new IllegalStateException("Test no hermetico: '" + key + "' apunta fuera de la maquina de "
                            + "pruebas. Revisa las variables de entorno SPRING_DATASOURCE_* y SPRING_FLYWAY_*.");
                }
            }
        }
    }
}

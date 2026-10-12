package com.storres.box_school;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

/**
 * Hace que un test de Spring sea HERMETICO (auditoria Fase 0, M-9): da igual que la maquina tenga definidas
 * JWT_SECRET, SPRING_MAIL_*, APP_TIMEZONE... Las propiedades de @TestPropertySource tienen MAS prioridad que
 * las variables de entorno, asi que los tests nunca usan el secreto real ni envian correos reales.
 *
 * El perfil "test" (application-test.yml) se superpone al application.yml REAL, de modo que tambien se
 * ejercita la configuracion de produccion (Swagger apagado, errores sin mensaje, sondas, limites...).
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "jwt.secret=dGVzdC1vbmx5LXNlY3JldC1rZXktdGVzdC1vbmx5LXNlY3JldC1rZXktMDEyMzQ1Njc4OQ==",
        "app.timezone=UTC",
        // Sin JavaMailSender: ningun test puede abrir una conexion SMTP, aunque exista SPRING_MAIL_HOST
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.mail.MailSenderAutoConfiguration"
})
public @interface HermeticTest {
}

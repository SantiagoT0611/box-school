package com.storres.box_school.config;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Unico punto de lectura de la configuracion propia del negocio (prefijo "app").
 * Todo lo que cambia entre entornos (CORS, cron, timezone...) se lee desde aqui.
 */
@ConfigurationProperties(prefix = "app")
public record AppProperties(
        /** Zona horaria del negocio (ej. America/Bogota). Vacio = la del servidor. */
        @DefaultValue("") String timezone,
        @DefaultValue Cors cors,
        @DefaultValue Mail mail,
        @DefaultValue MembershipReminder membershipReminder,
        @DefaultValue Registration registration,
        @DefaultValue RateLimit rateLimit,
        @DefaultValue BootstrapAdmin bootstrapAdmin) {

    public record Cors(@DefaultValue("http://localhost:4200") List<String> allowedOrigins) {
    }

    /** Remitente de los correos. Si esta vacio se usa spring.mail.username. */
    public record Mail(@DefaultValue("") String from) {
    }

    public record MembershipReminder(
            @DefaultValue("true") boolean enabled,
            @DefaultValue("2") int daysBeforeExpiry,
            @DefaultValue("0 0 8 * * *") String cron) {
    }

    public record Registration(@DefaultValue("72") int codeValidityHours) {
    }

    public record RateLimit(@DefaultValue("10") int authRequestsPerMinute) {
    }

    /** Si username y password estan definidos y no existe ningun admin, se crea uno al arrancar. */
    public record BootstrapAdmin(String username, String password) {
    }
}

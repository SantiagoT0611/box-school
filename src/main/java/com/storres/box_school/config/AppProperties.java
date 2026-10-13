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
        @DefaultValue BootstrapAdmin bootstrapAdmin,
        @DefaultValue PasswordReset passwordReset) {

    public record Cors(@DefaultValue("http://localhost:4200") List<String> allowedOrigins) {
    }

    /** Remitente de los correos. Si esta vacio se usa spring.mail.username. */
    public record Mail(@DefaultValue("") String from) {
    }

    public record MembershipReminder(
            @DefaultValue("true") boolean enabled,
            @DefaultValue("2") int daysBeforeExpiry,
            @DefaultValue("0 0 8 * * *") String cron,
            /** Pausa entre correos de un mismo ciclo: los proveedores SMTP limitan los envios por segundo. */
            @DefaultValue("1000") long delayBetweenEmailsMs) {
    }

    public record Registration(@DefaultValue("72") int codeValidityHours) {
    }

    public record RateLimit(@DefaultValue("10") int authRequestsPerMinute) {
    }

    /** Si username y password estan definidos y no existe ningun admin, se crea uno al arrancar. */
    public record BootstrapAdmin(String username, String password) {
    }

    /** Recuperacion de contrasena (D-024). Los limites se validan al arrancar: un valor absurdo no debe pasar. */
    public record PasswordReset(
            /** Vida del token. Corta a proposito: es una credencial de un solo uso. */
            @DefaultValue("30") int tokenValidityMinutes,
            /** Minimo entre dos correos de recuperacion para la misma cuenta (frena el bombardeo de correo). */
            @DefaultValue("60") int resendCooldownSeconds,
            /** Pagina del frontend a la que lleva el enlace del correo; se le agrega ?token=. Nunca sale del request. */
            @DefaultValue("http://localhost:4200/reset-password") String url) {

        public PasswordReset {
            if (tokenValidityMinutes < 1 || tokenValidityMinutes > 1440) {
                throw new IllegalArgumentException("app.password-reset.token-validity-minutes debe estar entre 1 y 1440");
            }
            if (resendCooldownSeconds < 0 || resendCooldownSeconds > 3600) {
                throw new IllegalArgumentException("app.password-reset.resend-cooldown-seconds debe estar entre 0 y 3600");
            }
            String scheme = url == null ? null : java.net.URI.create(url).getScheme();
            if (!"http".equals(scheme) && !"https".equals(scheme)) {
                throw new IllegalArgumentException("app.password-reset.url debe ser una URL http(s) absoluta");
            }
        }
    }
}

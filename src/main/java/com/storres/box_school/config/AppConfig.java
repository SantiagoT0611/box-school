package com.storres.box_school.config;

import java.time.Clock;
import java.time.ZoneId;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableAsync
@EnableScheduling
@EnableConfigurationProperties(AppProperties.class)
public class AppConfig {

    /**
     * Reloj unico de la aplicacion. Toda la logica de fechas (vencimientos, pagos,
     * recordatorios) lo usa en vez de LocalDate.now(), para que sea testeable y
     * consistente con la zona horaria del negocio.
     */
    @Bean
    public Clock clock(AppProperties props) {
        String tz = props.timezone();
        return tz == null || tz.isBlank() ? Clock.systemDefaultZone() : Clock.system(ZoneId.of(tz));
    }
}

package com.storres.box_school.config;

import java.time.Clock;
import java.time.ZoneId;

import com.fasterxml.jackson.core.StreamReadConstraints;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
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
    /**
     * Tope a los strings del JSON de entrada (por defecto Jackson admite 20 millones de caracteres):
     * defensa en profundidad junto al limite de tamano de cuerpo (auditoria Fase 0, M-8).
     */
    @Bean
    public Jackson2ObjectMapperBuilderCustomizer jacksonReadLimits() {
        return builder -> builder.postConfigurer(mapper -> mapper.getFactory().setStreamReadConstraints(
                StreamReadConstraints.builder().maxStringLength(100_000).build()));
    }

    @Bean
    public Clock clock(AppProperties props) {
        String tz = props.timezone();
        return tz == null || tz.isBlank() ? Clock.systemDefaultZone() : Clock.system(ZoneId.of(tz));
    }
}

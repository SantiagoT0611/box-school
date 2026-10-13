package com.storres.box_school;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.core.env.MapPropertySource;

/**
 * El Guard de HermeticTest debe abortar de verdad ante una URL fuera de la maquina (no ser decorativo).
 * Usa una fuente de propiedades PROPIA del contexto: nunca las propiedades del sistema de la JVM, que
 * contaminarian a los demas tests.
 */
class HermeticGuardTest {

    private static GenericApplicationContext contextWith(String... pairs) {
        Map<String, Object> values = new HashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            values.put(pairs[i], pairs[i + 1]);
        }
        GenericApplicationContext ctx = new GenericApplicationContext();
        ctx.getEnvironment().getPropertySources().addFirst(new MapPropertySource("guard-test", values));
        return ctx;
    }

    @Test
    void safeUrlsPass() {
        assertThatCode(() -> new HermeticTest.Guard().initialize(contextWith(
                "spring.datasource.url", "jdbc:h2:mem:boxschool",
                "spring.flyway.url", "jdbc:postgresql://localhost:55432/test"))).doesNotThrowAnyException();
        assertThatCode(() -> new HermeticTest.Guard().initialize(contextWith())).doesNotThrowAnyException();
    }

    @Test
    void remoteDatasourceAborts() {
        assertThatThrownBy(() -> new HermeticTest.Guard().initialize(contextWith(
                "spring.datasource.url", "jdbc:postgresql://203.0.113.5:5432/produccion")))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("spring.datasource.url");
    }

    @Test
    void remoteFlywayAborts() {
        assertThatThrownBy(() -> new HermeticTest.Guard().initialize(contextWith(
                "spring.flyway.url", "jdbc:postgresql://db.ejemplo.com/produccion")))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("spring.flyway.url");
    }
}

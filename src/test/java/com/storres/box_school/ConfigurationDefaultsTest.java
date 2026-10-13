package com.storres.box_school;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.yaml.snakeyaml.Yaml;

import com.storres.box_school.config.AppConfig;
import com.storres.box_school.config.AppProperties;
import com.storres.box_school.mapper.StudentMapper;
import com.storres.box_school.model.entity.User;

/**
 * Invariantes de configuracion y de diseno que el perfil "test" ocultaria (re-auditoria Fase 0, N-4):
 * leen el application.yml REAL de produccion SIN superponerle nada y comprueban los valores por defecto
 * peligrosos. Si alguien los cambia, el test se pone en rojo.
 */
class ConfigurationDefaultsTest {

    @SuppressWarnings("unchecked")
    private static Map<String, String> productionConfig() throws Exception {
        try (InputStream in = new ClassPathResource("application.yml").getInputStream()) {
            Map<String, Object> root = new Yaml().load(in);
            Map<String, String> flat = new LinkedHashMap<>();
            flatten("", root, flat);
            return flat;
        }
    }

    @SuppressWarnings("unchecked")
    private static void flatten(String prefix, Map<String, Object> node, Map<String, String> out) {
        node.forEach((key, value) -> {
            String path = prefix.isEmpty() ? key : prefix + "." + key;
            if (value instanceof Map<?, ?> child) {
                flatten(path, (Map<String, Object>) child, out);
            } else {
                out.put(path, String.valueOf(value));
            }
        });
    }

    @Test
    void productionDefaultsAreTheSafeOnes() throws Exception {
        Map<String, String> c = productionConfig();

        // B-9: baseline apagado salvo que el operador lo pida (primera adopcion de una base heredada)
        assertThat(c.get("spring.flyway.baseline-on-migrate")).isEqualTo("${FLYWAY_BASELINE_ON_MIGRATE:false}");
        // El esquema lo gobierna Flyway; Hibernate solo valida
        assertThat(c.get("spring.jpa.hibernate.ddl-auto")).isEqualTo("validate");
        assertThat(c.get("spring.jpa.open-in-view")).isEqualTo("false");
        // Swagger apagado por defecto
        assertThat(c.get("springdoc.api-docs.enabled")).isEqualTo("${SWAGGER_ENABLED:false}");
        assertThat(c.get("springdoc.swagger-ui.enabled")).isEqualTo("${SWAGGER_ENABLED:false}");
        // M-11: la zona del negocio no depende de la del servidor
        assertThat(c.get("app.timezone")).isEqualTo("${APP_TIMEZONE:America/Bogota}");
        // A-3: el indicador de correo abriria una conexion SMTP por consulta de salud
        assertThat(c.get("management.health.mail.enabled")).isEqualTo("false");
        assertThat(c.get("management.endpoints.web.exposure.include")).isEqualTo("health");
        // N-1: la API es solo JSON; el filtro de formularios leia cuerpos enteros en memoria
        assertThat(c.get("spring.mvc.formcontent.filter.enabled")).isEqualTo("false");
        // Errores sin detalle interno
        assertThat(c.get("server.error.include-message")).isEqualTo("never");
        assertThat(c.get("server.error.include-stacktrace")).isEqualTo("never");
        // Paginacion acotada
        assertThat(c.get("spring.data.web.pageable.max-page-size")).isEqualTo("100");
        // Sin secretos reales: todo valor sensible llega por variable de entorno
        assertThat(c.get("spring.datasource.password")).isEqualTo("${DB_PASSWORD}");
        assertThat(c.get("jwt.secret")).isEqualTo("${JWT_SECRET}");
    }

    @Test
    void blankTimezoneMeansBusinessTimezoneNotTheServerOne() {
        var props = new AppProperties("", new AppProperties.Cors(java.util.List.of()), new AppProperties.Mail(""),
                new AppProperties.MembershipReminder(false, 2, "0 0 8 * * *", 0),
                new AppProperties.Registration(72), new AppProperties.RateLimit(10),
                new AppProperties.BootstrapAdmin(null, null));

        assertThat(new AppConfig().clock(props).getZone().getId()).isEqualTo("America/Bogota");
    }

    @Test
    void userStudentRelationIsLazySoAuthenticatingDoesNotLoadTheStudent() throws Exception {
        var oneToOne = User.class.getDeclaredField("student").getAnnotation(jakarta.persistence.OneToOne.class);

        assertThat(oneToOne.fetch()).isEqualTo(jakarta.persistence.FetchType.LAZY); // B-11
    }

    @Test
    void emailNormalizationDoesNotDependOnTheServerLocale() {
        Locale previous = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR")); // en turco "I".toLowerCase() == "ı"
            assertThat(StudentMapper.normalizeEmail("  ISABEL@MAIL.COM ")).isEqualTo("isabel@mail.com"); // B-12
        } finally {
            Locale.setDefault(previous);
        }
    }
}

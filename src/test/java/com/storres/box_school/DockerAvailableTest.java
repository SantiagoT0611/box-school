package com.storres.box_school;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.testcontainers.DockerClientFactory;

/**
 * Los tests de PostgreSQL real (Testcontainers) se OMITEN en silencio cuando no hay Docker y la suite
 * parece verde (re-auditoria Fase 0, R-9). En CI o antes de cerrar una fase, ejecutar con
 * REQUIRE_DOCKER=true: este test falla si Docker no esta disponible.
 */
class DockerAvailableTest {

    @Test
    void dockerMustBeAvailableWhenRequired() {
        if ("true".equalsIgnoreCase(System.getenv("REQUIRE_DOCKER"))) {
            assertThat(DockerClientFactory.instance().isDockerAvailable())
                    .as("REQUIRE_DOCKER=true pero Docker no responde: los tests de PostgreSQL real no correrian")
                    .isTrue();
        }
    }
}

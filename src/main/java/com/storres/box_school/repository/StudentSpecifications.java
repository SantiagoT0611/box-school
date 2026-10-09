package com.storres.box_school.repository;

import org.springframework.data.jpa.domain.Specification;

import com.storres.box_school.model.entity.Student;
import com.storres.box_school.model.shared.Status;

/** Filtros opcionales y componibles para el listado de estudiantes del admin. */
public final class StudentSpecifications {

    private static final char ESCAPE = '\\';

    private StudentSpecifications() {
    }

    public static Specification<Student> hasStatus(Status status) {
        return (root, query, cb) -> status == null ? null : cb.equal(root.get("status"), status);
    }

    /** Busca por nombre, apellido o email (sin distinguir mayusculas). */
    public static Specification<Student> matchesText(String text) {
        return (root, query, cb) -> {
            if (text == null || text.isBlank()) {
                return null;
            }
            String pattern = "%" + escapeLike(text.trim().toLowerCase()) + "%";
            return cb.or(
                    cb.like(cb.lower(root.get("firstName")), pattern, ESCAPE),
                    cb.like(cb.lower(root.get("lastName")), pattern, ESCAPE),
                    cb.like(cb.lower(root.get("email")), pattern, ESCAPE));
        };
    }

    /** Escapa los comodines de LIKE para que el texto del usuario se busque literalmente. */
    private static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}

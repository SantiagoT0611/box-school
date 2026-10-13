package com.storres.box_school.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.storres.box_school.model.entity.User;
import com.storres.box_school.model.shared.Roles;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByUsername(String username);

    boolean existsByUsername(String username);

    boolean existsByRole(Roles role);

    Optional<User> findByStudentId(Long studentId);

    boolean existsByStudentId(Long studentId);

    /** Usuario junto con su estudiante (para los endpoints "me"). */
    @EntityGraph(attributePaths = "student")
    Optional<User> findWithStudentByUsername(String username);

    /** Cuenta de un estudiante por su email (ya normalizado). Las cuentas ADMIN no tienen estudiante: no aparecen. */
    @Query("select u from User u join fetch u.student s where s.email = :email")
    Optional<User> findWithStudentByStudentEmail(@Param("email") String email);

    /**
     * Cambia la clave y sube la version de sesion en UNA sentencia, y solo si la cuenta sigue habilitada: no pisa
     * columnas que otra transaccion haya cambiado (p. ej. deshabilitar la cuenta) ni reactiva a nadie.
     * Devuelve 1 si se aplico, 0 si la cuenta esta deshabilitada o no existe.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update User u set u.password = :password, u.tokenVersion = u.tokenVersion + 1
            where u.id = :id and u.enabled = true
            """)
    int changePassword(@Param("id") Long id, @Param("password") String encodedPassword);
}

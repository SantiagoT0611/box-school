package com.storres.box_school.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.storres.box_school.model.entity.Student;
import com.storres.box_school.model.shared.NotificationType;
import com.storres.box_school.model.shared.Status;

import jakarta.persistence.LockModeType;

public interface StudentRepository extends JpaRepository<Student, Long>, JpaSpecificationExecutor<Student> {

    boolean existsByEmail(String email);

    Optional<Student> findByEmail(String email);

    /** Membresias vencidas (expirationDate anterior a la fecha dada) de estudiantes con el estado indicado. */
    Page<Student> findByStatusAndExpirationDateBefore(Status status, LocalDate date, Pageable pageable);

    /** Membresias que vencen dentro del rango [from, to] (ambos inclusive). */
    Page<Student> findByStatusAndExpirationDateBetween(Status status, LocalDate from, LocalDate to, Pageable pageable);

    /** Bloqueo de fila: serializa pagos concurrentes del mismo estudiante. Usar dentro de una transaccion. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Student s where s.id = :id")
    Optional<Student> findByIdForUpdate(@Param("id") Long id);

    /**
     * Estudiantes activos que vencen en [from, to] y a los que aun no se les envio este aviso.
     * Paginacion por clave (id > afterId): el resultado cambia mientras se procesa, asi que
     * no se puede usar OFFSET.
     */
    @Query("""
            select s from Student s
            where s.status = :status
              and s.expirationDate between :from and :to
              and s.id > :afterId
              and not exists (
                    select 1 from NotificationLog n
                    where n.student = s and n.type = :type and n.referenceDate = s.expirationDate)
            order by s.id
            """)
    List<Student> findPendingReminders(@Param("status") Status status, @Param("from") LocalDate from,
            @Param("to") LocalDate to, @Param("type") NotificationType type, @Param("afterId") Long afterId,
            Pageable pageable);
}

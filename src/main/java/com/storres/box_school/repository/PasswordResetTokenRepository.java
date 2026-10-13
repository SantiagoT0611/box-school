package com.storres.box_school.repository;

import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.storres.box_school.model.entity.PasswordResetToken;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {

    Optional<PasswordResetToken> findByUserId(Long userId);

    /** Borra el token del usuario (a lo sumo hay uno). Se ejecuta de inmediato, antes de insertar el nuevo. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from PasswordResetToken t where t.user.id = :userId")
    int deleteByUserId(@Param("userId") Long userId);

    /**
     * Consume el token de forma ATOMICA (una sola sentencia): si llegan dos solicitudes con el mismo token, la base
     * de datos deja pasar a una sola. Devuelve 1 si el token existia, no habia sido usado y no habia expirado.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update PasswordResetToken t set t.usedAt = :now
            where t.tokenHash = :hash and t.usedAt is null and t.expiresAt > :now
            """)
    int consume(@Param("hash") String hash, @Param("now") LocalDateTime now);

    /** Token con su usuario y el estudiante del usuario (para avisar por correo del cambio). */
    @Query("""
            select t from PasswordResetToken t
            join fetch t.user u
            left join fetch u.student
            where t.tokenHash = :hash
            """)
    Optional<PasswordResetToken> findWithUserByTokenHash(@Param("hash") String hash);
}

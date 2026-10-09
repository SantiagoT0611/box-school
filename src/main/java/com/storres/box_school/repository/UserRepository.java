package com.storres.box_school.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

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
}

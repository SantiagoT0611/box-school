package com.storres.box_school.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.storres.box_school.model.entity.User;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByUsername(String Username);

    boolean existsByUsername(String Username);
}

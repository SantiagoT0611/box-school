package com.storres.box_school.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.storres.box_school.model.entity.NotificationLog;

public interface NotificationLogRepository extends JpaRepository<NotificationLog, Long> {
}

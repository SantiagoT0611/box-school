package com.storres.box_school.controller;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.storres.box_school.service.MembershipReminderService;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final MembershipReminderService reminderService;

    /** Ejecuta el envio de recordatorios ahora mismo (el job programado hace lo mismo cada dia). Idempotente. */
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/membership-reminders/run")
    public ResponseEntity<Map<String, Integer>> runMembershipReminders() {
        return ResponseEntity.ok(Map.of("sent", reminderService.sendDueReminders()));
    }
}

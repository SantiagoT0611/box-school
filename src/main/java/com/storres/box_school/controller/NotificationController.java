package com.storres.box_school.controller;

import java.util.Map;

import org.springframework.http.HttpStatus;
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

    /**
     * Dispara el envio de recordatorios ahora mismo (el job programado hace lo mismo cada dia).
     * Responde de inmediato: el lote corre en segundo plano porque, con la pausa entre correos, puede
     * tardar minutos (un proxy cortaria la peticion). Idempotente: no repite avisos ya enviados.
     */
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/membership-reminders/run")
    public ResponseEntity<Map<String, String>> runMembershipReminders() {
        return switch (reminderService.triggerAsync()) {
            case STARTED -> ResponseEntity.status(HttpStatus.ACCEPTED).body(Map.of("status", "started"));
            case ALREADY_RUNNING -> ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("status", "already-running"));
            case MAIL_NOT_CONFIGURED ->
                ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("status", "mail-not-configured"));
        };
    }
}

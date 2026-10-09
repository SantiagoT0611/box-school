package com.storres.box_school.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

/** Dispara el envio de recordatorios segun app.membership-reminder.cron (por defecto, todos los dias a las 8:00). */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.membership-reminder", name = "enabled", havingValue = "true", matchIfMissing = true)
public class MembershipReminderScheduler {

    private final MembershipReminderService reminderService;

    @Scheduled(cron = "${app.membership-reminder.cron:0 0 8 * * *}", zone = "${app.timezone:}")
    public void run() {
        reminderService.sendDueReminders();
    }
}

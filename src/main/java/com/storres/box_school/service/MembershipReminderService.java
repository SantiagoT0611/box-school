package com.storres.box_school.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import com.storres.box_school.config.AppProperties;
import com.storres.box_school.model.entity.NotificationLog;
import com.storres.box_school.model.entity.Student;
import com.storres.box_school.model.shared.NotificationType;
import com.storres.box_school.model.shared.Status;
import com.storres.box_school.repository.NotificationLogRepository;
import com.storres.box_school.repository.StudentRepository;

import lombok.RequiredArgsConstructor;

/**
 * Avisa por correo a los estudiantes activos cuya membresia vence en los proximos N dias
 * (app.membership-reminder.days-before-expiry, por defecto 2).
 *
 * Idempotente: cada aviso se "reclama" insertando un NotificationLog con restriccion unica
 * (estudiante, tipo, fecha de vencimiento) ANTES de enviar. Asi, si el job corre dos veces
 * o en dos instancias a la vez, solo una envia. Si el envio falla se libera el registro y
 * el siguiente ciclo lo reintenta. Si el servidor estuvo caido, el aviso se envia igualmente
 * en cuanto vuelve (la consulta cubre todo el rango [hoy, hoy + N]).
 *
 * Este metodo NO es transaccional a proposito: cada guardado es su propia transaccion corta y
 * no se mantiene una conexion abierta mientras se habla con el servidor SMTP.
 */
@Service
@RequiredArgsConstructor
public class MembershipReminderService {

    private static final Logger log = LoggerFactory.getLogger(MembershipReminderService.class);
    private static final int BATCH_SIZE = 100;
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final StudentRepository studentRepository;
    private final NotificationLogRepository notificationLogRepository;
    private final MailService mailService;
    private final AppProperties props;
    private final Clock clock;

    /** @return cantidad de avisos enviados en esta ejecucion */
    public int sendDueReminders() {
        if (!mailService.isConfigured()) {
            log.warn("Recordatorios omitidos: el correo no esta configurado (spring.mail.host)");
            return 0;
        }

        LocalDate today = LocalDate.now(clock);
        LocalDate until = today.plusDays(props.membershipReminder().daysBeforeExpiry());
        int sent = 0;
        int failed = 0;
        long afterId = 0L;

        while (true) {
            List<Student> batch = studentRepository.findPendingReminders(
                    Status.ACTIVE, today, until, NotificationType.MEMBERSHIP_EXPIRING_SOON, afterId,
                    PageRequest.of(0, BATCH_SIZE));
            if (batch.isEmpty()) {
                break;
            }
            for (Student student : batch) {
                afterId = student.getId();
                if (remind(student, today)) {
                    sent++;
                } else {
                    failed++;
                }
            }
        }

        log.info("Recordatorios de vencimiento: {} enviados, {} fallidos/omitidos", sent, failed);
        return sent;
    }

    private boolean remind(Student student, LocalDate today) {
        NotificationLog claim = new NotificationLog();
        claim.setStudent(student);
        claim.setType(NotificationType.MEMBERSHIP_EXPIRING_SOON);
        claim.setReferenceDate(student.getExpirationDate());
        claim.setSentAt(LocalDateTime.now(clock));

        try {
            notificationLogRepository.saveAndFlush(claim);
        } catch (DataIntegrityViolationException e) {
            return false; // otra instancia/ejecucion ya lo reclamo
        }

        try {
            long days = ChronoUnit.DAYS.between(today, student.getExpirationDate());
            mailService.send(student.getEmail(), "Tu mensualidad esta por vencer", buildBody(student, days));
            return true;
        } catch (Exception e) {
            log.error("No se pudo enviar el recordatorio al estudiante id={}", student.getId(), e);
            notificationLogRepository.delete(claim); // se reintenta en el proximo ciclo
            return false;
        }
    }

    private String buildBody(Student student, long daysLeft) {
        String when = daysLeft <= 0 ? "vence hoy"
                : daysLeft == 1 ? "vence manana" : "vence en " + daysLeft + " dias";
        return "Hola " + student.getFirstName() + ",\n\n"
                + "Tu mensualidad en la escuela de boxeo " + when
                + " (" + student.getExpirationDate().format(DATE_FORMAT) + ").\n"
                + "Realiza tu pago a tiempo para mantener tu acceso.\n\n"
                + "Si ya pagaste, ignora este mensaje.";
    }
}

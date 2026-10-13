package com.storres.box_school.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
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

/**
 * Avisa por correo a los estudiantes activos cuya membresia vence en los proximos N dias
 * (app.membership-reminder.days-before-expiry, por defecto 2).
 *
 * Idempotente: cada aviso se "reclama" insertando un NotificationLog con restriccion unica
 * (estudiante, tipo, fecha de vencimiento) ANTES de enviar. Asi, si el job corre dos veces
 * o en dos instancias a la vez, solo una envia. Si el envio falla (por cualquier causa, incluso un Error)
 * se libera el registro y el siguiente ciclo lo reintenta. Si el servidor estuvo caido, el aviso se envia
 * igualmente en cuanto vuelve (la consulta cubre todo el rango [hoy, hoy + N]).
 *
 * Dentro de un mismo proceso un candado impide ejecuciones solapadas (job + disparo manual).
 *
 * Este metodo NO es transaccional a proposito: cada guardado es su propia transaccion corta y
 * no se mantiene una conexion abierta mientras se habla con el servidor SMTP.
 */
@Service
public class MembershipReminderService {

    private static final Logger log = LoggerFactory.getLogger(MembershipReminderService.class);
    private static final int BATCH_SIZE = 100;
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final StudentRepository studentRepository;
    private final NotificationLogRepository notificationLogRepository;
    private final MailService mailService;
    private final AppProperties props;
    private final Clock clock;
    private final TaskExecutor taskExecutor;

    private final AtomicBoolean running = new AtomicBoolean(false);

    public MembershipReminderService(StudentRepository studentRepository,
            NotificationLogRepository notificationLogRepository, MailService mailService, AppProperties props,
            Clock clock, @Qualifier("applicationTaskExecutor") TaskExecutor taskExecutor) {
        this.studentRepository = studentRepository;
        this.notificationLogRepository = notificationLogRepository;
        this.mailService = mailService;
        this.props = props;
        this.clock = clock;
        this.taskExecutor = taskExecutor;
    }

    public enum Trigger { STARTED, ALREADY_RUNNING, MAIL_NOT_CONFIGURED }

    /**
     * Dispara el envio en segundo plano y vuelve de inmediato (el lote puede tardar minutos por la pausa
     * entre correos). El resultado es explicito para no informar "started" cuando no arranco nada (R-4).
     */
    public Trigger triggerAsync() {
        if (!mailService.isConfigured()) {
            return Trigger.MAIL_NOT_CONFIGURED;
        }
        if (running.get()) {
            return Trigger.ALREADY_RUNNING;
        }
        taskExecutor.execute(this::sendDueReminders);
        return Trigger.STARTED;
    }

    /** @return cantidad de avisos enviados en esta ejecucion (0 si ya hay otra en curso) */
    public int sendDueReminders() {
        if (!mailService.isConfigured()) {
            log.warn("Recordatorios omitidos: el correo no esta configurado (spring.mail.host)");
            return 0;
        }
        if (!running.compareAndSet(false, true)) {
            log.warn("Recordatorios: ya hay una ejecucion en curso; se omite esta");
            return 0;
        }
        try {
            return process();
        } finally {
            running.set(false);
        }
    }

    private int process() {
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
                if (Thread.currentThread().isInterrupted()) {
                    // apagado en curso: no enviar el resto en rafaga (la pausa tambien se interrumpe); R-3
                    log.warn("Recordatorios interrumpidos: {} enviados, quedan pendientes para el proximo ciclo", sent);
                    return sent;
                }
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

        boolean sent = false;
        try {
            long days = ChronoUnit.DAYS.between(today, student.getExpirationDate());
            mailService.send(student.getEmail(), "Tu mensualidad está por vencer", buildBody(student, days));
            sent = true;
        } catch (Exception e) {
            log.error("No se pudo enviar el recordatorio al estudiante id={}", student.getId(), e);
        } finally {
            if (!sent) {
                // Se libera el reclamo ante CUALQUIER fallo (tambien un Error): se reintenta en el proximo ciclo.
                // Solo un corte duro del proceso (kill -9) puede dejarlo huerfano.
                releaseClaim(claim);
            }
        }
        // Pausa tambien tras un fallo: si el proveedor limita rafagas, insistir de inmediato solo empeora
        pauseBetweenEmails();
        return sent;
    }

    private void releaseClaim(NotificationLog claim) {
        try {
            notificationLogRepository.delete(claim);
        } catch (RuntimeException e) {
            log.error("No se pudo liberar el reclamo del aviso id={}", claim.getId(), e);
        }
    }

    /** Evita superar el limite de envios por segundo del proveedor SMTP (el job corre en segundo plano). */
    private void pauseBetweenEmails() {
        long delay = props.membershipReminder().delayBetweenEmailsMs();
        if (delay > 0) {
            sleep(delay);
        }
    }

    /** Separado para poder sustituirlo en pruebas sin esperas reales. */
    void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private String buildBody(Student student, long daysLeft) {
        String when = daysLeft <= 0 ? "vence hoy"
                : daysLeft == 1 ? "vence mañana" : "vence en " + daysLeft + " días";
        return "Hola " + student.getFirstName() + ",\n\n"
                + "Tu mensualidad en la escuela de boxeo " + when
                + " (" + student.getExpirationDate().format(DATE_FORMAT) + ").\n"
                + "Realiza tu pago a tiempo para mantener tu acceso.\n\n"
                + "Si ya pagaste, ignora este mensaje.";
    }
}

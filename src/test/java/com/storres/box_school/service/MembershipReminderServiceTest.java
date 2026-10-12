package com.storres.box_school.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.storres.box_school.config.AppProperties;
import com.storres.box_school.model.entity.NotificationLog;
import com.storres.box_school.model.entity.Student;
import com.storres.box_school.repository.NotificationLogRepository;
import com.storres.box_school.repository.StudentRepository;

/**
 * Pruebas unitarias de MembershipReminderService sin base de datos ni esperas reales. Cubre lo que la
 * auditoria de la Fase 0 encontro sin proteccion: la pausa entre correos (M-10), la liberacion del reclamo
 * ante un Error (B-3), la pausa tras un fallo (B-4) y el candado contra ejecuciones solapadas (M-4).
 */
class MembershipReminderServiceTest {

    private static final long DELAY = 700;
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-08T13:00:00Z"), ZoneOffset.UTC);

    private final StudentRepository students = mock(StudentRepository.class);
    private final NotificationLogRepository logs = mock(NotificationLogRepository.class);
    private final MailService mail = mock(MailService.class);
    private final List<Long> sleeps = new ArrayList<>();
    private MembershipReminderService service;

    @BeforeEach
    void setUp() {
        when(mail.isConfigured()).thenReturn(true);
        service = newService(DELAY);
    }

    private MembershipReminderService newService(long delayMs) {
        var props = new AppProperties("", new AppProperties.Cors(List.of()), new AppProperties.Mail(""),
                new AppProperties.MembershipReminder(true, 2, "0 0 8 * * *", delayMs),
                new AppProperties.Registration(72), new AppProperties.RateLimit(10),
                new AppProperties.BootstrapAdmin(null, null));
        return new MembershipReminderService(students, logs, mail, props, CLOCK, Runnable::run) {
            @Override
            void sleep(long millis) {
                sleeps.add(millis); // sin esperar de verdad
            }
        };
    }

    private static Student student(long id) {
        Student s = new Student();
        s.setId(id);
        s.setFirstName("Ana" + id);
        s.setEmail("ana" + id + "@test.com");
        s.setExpirationDate(LocalDate.of(2026, 10, 9));
        return s;
    }

    private void pending(Student... batch) {
        when(students.findPendingReminders(any(), any(), any(), any(), anyLong(), any()))
                .thenReturn(List.of(batch), List.of());
    }

    @Test
    void pausesBetweenEveryEmailSoTheProviderRateLimitIsNotExceeded() {
        pending(student(1), student(2), student(3));

        assertThat(service.sendDueReminders()).isEqualTo(3);

        assertThat(sleeps).as("una pausa configurada tras cada correo").containsExactly(DELAY, DELAY, DELAY);
    }

    @Test
    void pausesAlsoAfterAFailureAndReleasesTheClaimSoItIsRetried() {
        Student ok = student(1);
        Student failing = student(2);
        pending(ok, failing);
        doThrow(new IllegalStateException("550 Too many emails per second"))
                .when(mail).send(eq(failing.getEmail()), anyString(), anyString());

        assertThat(service.sendDueReminders()).isEqualTo(1);

        assertThat(sleeps).as("tambien tras el fallo: insistir de inmediato empeora el limite").hasSize(2);
        verify(logs).delete(any(NotificationLog.class)); // solo el reclamo del que fallo
    }

    @Test
    void aHardErrorStillReleasesTheClaim() {
        pending(student(1));
        doThrow(new StackOverflowError("simulado")).when(mail).send(anyString(), anyString(), anyString());

        assertThatThrownBy(() -> service.sendDueReminders()).isInstanceOf(StackOverflowError.class);

        verify(logs).delete(any(NotificationLog.class)); // sin el finally quedaba huerfano para siempre (B-3)
    }

    @Test
    void noPauseWhenDelayIsZero() {
        MembershipReminderService noDelay = newService(0);
        pending(student(1), student(2));

        assertThat(noDelay.sendDueReminders()).isEqualTo(2);

        assertThat(sleeps).isEmpty();
    }

    @Test
    void anotherRunWhileOneIsInProgressIsSkippedAndTheTriggerReportsIt() {
        AtomicReference<Integer> nestedRun = new AtomicReference<>();
        AtomicReference<Boolean> nestedTrigger = new AtomicReference<>();
        pending(student(1));
        doAnswer(invocation -> {
            // estamos "dentro" de la ejecucion: un segundo intento no debe solaparse
            nestedRun.set(service.sendDueReminders());
            nestedTrigger.set(service.triggerAsync());
            return null;
        }).when(mail).send(anyString(), anyString(), anyString());

        assertThat(service.sendDueReminders()).isEqualTo(1);

        assertThat(nestedRun.get()).isZero();
        assertThat(nestedTrigger.get()).isFalse();
        // y al terminar el candado queda libre
        pending(student(2));
        assertThat(service.triggerAsync()).isTrue(); // ejecuta inline (Runnable::run) y devuelve true
    }

    @Test
    void doesNothingWithoutMailConfiguration() {
        when(mail.isConfigured()).thenReturn(false);

        assertThat(service.sendDueReminders()).isZero();

        verify(students, never()).findPendingReminders(any(), any(), any(), any(), anyLong(), any());
    }
}

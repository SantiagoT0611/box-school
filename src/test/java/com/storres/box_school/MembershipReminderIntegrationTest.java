package com.storres.box_school;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.LocalDate;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.storres.box_school.model.entity.Student;
import com.storres.box_school.model.shared.Status;
import com.storres.box_school.repository.NotificationLogRepository;
import com.storres.box_school.repository.StudentRepository;
import com.storres.box_school.service.MailService;
import com.storres.box_school.service.MembershipReminderService;

/** Sin @Transactional a proposito: el servicio hace commits cortos por estudiante, igual que en produccion. */
@SpringBootTest
class MembershipReminderIntegrationTest {

    @Autowired MembershipReminderService reminderService;
    @Autowired StudentRepository students;
    @Autowired NotificationLogRepository logs;
    @Autowired Clock clock;
    @MockitoBean MailService mailService;

    private LocalDate today;

    @BeforeEach
    void setUp() {
        cleanUp();
        today = LocalDate.now(clock);
        when(mailService.isConfigured()).thenReturn(true);
    }

    @AfterEach
    void cleanUp() {
        logs.deleteAll();
        students.deleteAll();
    }

    @Test
    void remindsOnlyActiveStudentsExpiringWithinTwoDaysAndOnlyOnce() {
        student("dos-dias@test.com", Status.ACTIVE, today.plusDays(2));   // avisa
        student("hoy@test.com", Status.ACTIVE, today);                    // avisa
        student("lejos@test.com", Status.ACTIVE, today.plusDays(5));      // no
        student("vencida@test.com", Status.ACTIVE, today.minusDays(1));   // no (ya vencio)
        student("inactivo@test.com", Status.INACTIVE, today.plusDays(1)); // no (deshabilitado)

        assertThat(reminderService.sendDueReminders()).isEqualTo(2);
        verify(mailService).send(eq("dos-dias@test.com"), anyString(), anyString());
        verify(mailService).send(eq("hoy@test.com"), anyString(), anyString());
        verify(mailService, times(2)).send(anyString(), anyString(), anyString());

        // segunda ejecucion el mismo dia: no repite ningun aviso
        assertThat(reminderService.sendDueReminders()).isZero();
        verify(mailService, times(2)).send(anyString(), anyString(), anyString());
    }

    @Test
    void failedSendIsRetriedOnNextRun() {
        student("ana@test.com", Status.ACTIVE, today.plusDays(1));
        doThrow(new IllegalStateException("SMTP caido")).when(mailService).send(anyString(), anyString(), anyString());

        assertThat(reminderService.sendDueReminders()).isZero();
        assertThat(logs.count()).isZero(); // el registro se libero para poder reintentar

        doNothing().when(mailService).send(anyString(), anyString(), anyString());
        assertThat(reminderService.sendDueReminders()).isEqualTo(1);
        assertThat(logs.count()).isEqualTo(1);
    }

    @Test
    void renewedMembershipGetsANewReminderForTheNewExpiration() {
        Student ana = student("ana@test.com", Status.ACTIVE, today.plusDays(1));
        assertThat(reminderService.sendDueReminders()).isEqualTo(1);

        // paga, vuelve a vencer pronto (otra fecha de referencia) -> nuevo aviso valido
        ana.setExpirationDate(today.plusDays(2));
        students.save(ana);
        assertThat(reminderService.sendDueReminders()).isEqualTo(1);
    }

    @Test
    void doesNothingWhenMailIsNotConfigured() {
        student("ana@test.com", Status.ACTIVE, today.plusDays(1));
        when(mailService.isConfigured()).thenReturn(false);

        assertThat(reminderService.sendDueReminders()).isZero();
        assertThat(logs.count()).isZero();
    }

    private Student student(String email, Status status, LocalDate expiration) {
        Student s = new Student();
        s.setFirstName("Nombre");
        s.setLastName("Apellido");
        s.setEmail(email);
        s.setPhone("3001234567");
        s.setRegistrationDate(today);
        s.setExpirationDate(expiration);
        s.setStatus(status);
        return students.save(s);
    }
}

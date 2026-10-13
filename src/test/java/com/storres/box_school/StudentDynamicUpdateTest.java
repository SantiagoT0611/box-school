package com.storres.box_school;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import com.storres.box_school.model.entity.Student;
import com.storres.box_school.model.shared.Status;
import com.storres.box_school.repository.StudentRepository;

/**
 * Regresion de N-2 (re-auditoria Fase 0): activar la cuenta o regenerar el codigo leian al estudiante,
 * y al confirmar Hibernate reescribia TODAS las columnas, incluida expiration_date con el valor viejo,
 * pisando un pago confirmado entre medio. Con @DynamicUpdate solo se escriben las columnas que cambian.
 *
 * Sin @Transactional a proposito: hacen falta dos transacciones reales entrelazadas.
 */
@HermeticTest
@SpringBootTest
class StudentDynamicUpdateTest {

    @Autowired StudentRepository students;
    @Autowired PlatformTransactionManager txManager;
    @Autowired JdbcTemplate jdbc;

    private Long studentId;

    @AfterEach
    void cleanUp() {
        if (studentId != null) {
            jdbc.update("delete from students where id = ?", studentId);
        }
    }

    @Test
    void aTransactionThatOnlyTouchesTheCodeDoesNotOverwriteAConcurrentExpirationUpdate() {
        LocalDate original = LocalDate.of(2026, 11, 1);
        LocalDate afterPayment = original.plusDays(30);

        Student s = new Student();
        s.setFirstName("Din");
        s.setLastName("Amico");
        s.setEmail("dinamico@test.com");
        s.setPhone("3001234567");
        s.setRegistrationDate(LocalDate.of(2026, 10, 1));
        s.setExpirationDate(original);
        s.setStatus(Status.ACTIVE);
        studentId = students.saveAndFlush(s).getId();

        TransactionTemplate outer = new TransactionTemplate(txManager);
        TransactionTemplate inner = new TransactionTemplate(txManager);
        inner.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        outer.executeWithoutResult(status -> {
            Student loaded = students.findById(studentId).orElseThrow(); // lee expiration_date = original

            // "otro hilo": un pago confirma y extiende el vencimiento mientras la transaccion sigue abierta
            inner.executeWithoutResult(s2 -> jdbc.update(
                    "update students set expiration_date = ? where id = ?", afterPayment, studentId));

            // la transaccion externa solo cambia el codigo de registro y confirma
            loaded.setRegistrationCodeHash("hash-de-prueba");
        });

        LocalDate stored = jdbc.queryForObject(
                "select expiration_date from students where id = ?", LocalDate.class, studentId);
        assertThat(stored).as("el pago concurrente no debe perderse").isEqualTo(afterPayment);
        assertThat(jdbc.queryForObject("select registration_code_hash from students where id = ?",
                String.class, studentId)).isEqualTo("hash-de-prueba");
    }
}

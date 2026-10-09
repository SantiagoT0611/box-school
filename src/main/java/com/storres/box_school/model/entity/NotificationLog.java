package com.storres.box_school.model.entity;

import java.time.LocalDate;
import java.time.LocalDateTime;

import com.storres.box_school.model.shared.NotificationType;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Registro de notificaciones enviadas. La restriccion unica (estudiante, tipo, fecha de vencimiento)
 * garantiza que el mismo aviso no se envie dos veces aunque el job corra en paralelo o se reinicie.
 */
@Getter
@Setter
@Entity
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "notification_logs", uniqueConstraints = @UniqueConstraint(
        name = "uk_notification_student_type_ref", columnNames = { "student_id", "type", "reference_date" }))
public class NotificationLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false)
    private Student student;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private NotificationType type;

    /** Fecha de vencimiento de la membresia sobre la que se avisa. */
    @Column(name = "reference_date", nullable = false)
    private LocalDate referenceDate;

    @Column(name = "sent_at", nullable = false)
    private LocalDateTime sentAt;
}

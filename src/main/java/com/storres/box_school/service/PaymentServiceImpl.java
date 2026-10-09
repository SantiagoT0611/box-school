package com.storres.box_school.service;

import java.time.Clock;
import java.time.LocalDate;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.storres.box_school.exception.PriceActiveNotFoundException;
import com.storres.box_school.exception.StudentNotActiveException;
import com.storres.box_school.exception.StudentNotFoundException;
import com.storres.box_school.mapper.PaymentMapper;
import com.storres.box_school.model.dto.PaymentRequest;
import com.storres.box_school.model.dto.PaymentResponse;
import com.storres.box_school.model.entity.Payment;
import com.storres.box_school.model.entity.Price;
import com.storres.box_school.model.entity.Student;
import com.storres.box_school.model.shared.Status;
import com.storres.box_school.repository.PaymentRepository;
import com.storres.box_school.repository.PriceRepository;
import com.storres.box_school.repository.StudentRepository;
import com.storres.box_school.repository.UserRepository;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Service
@Transactional(readOnly = true)
public class PaymentServiceImpl implements PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentServiceImpl.class);

    private final StudentRepository studentRepository;
    private final UserRepository userRepository;
    private final PriceRepository priceRepository;
    private final PaymentRepository paymentRepository;
    private final PaymentMapper paymentMapper;
    private final Clock clock;

    /**
     * Regla de negocio: si la membresia sigue vigente, el nuevo periodo empieza donde termina el actual
     * (no se pierden dias pagados); si ya vencio, empieza hoy.
     * El estudiante se lee con bloqueo de fila para que dos pagos simultaneos no se pisen el vencimiento.
     */
    @Override
    @Transactional
    public PaymentResponse payMembership(PaymentRequest request, Long studentId) {
        Student student = studentRepository.findByIdForUpdate(studentId)
                .orElseThrow(StudentNotFoundException::new);

        if (student.getStatus() != Status.ACTIVE) {
            throw new StudentNotActiveException("El estudiante no se encuentra activo");
        }
        Price price = priceRepository.findByTypeAndActiveTrue(request.getType())
                .orElseThrow(PriceActiveNotFoundException::new);

        LocalDate today = LocalDate.now(clock);
        LocalDate periodStart = student.getExpirationDate().isAfter(today) ? student.getExpirationDate() : today;
        LocalDate periodEnd = periodStart.plusDays(price.getDurationDays());

        Payment payment = new Payment();
        payment.setPaymentDate(today);
        payment.setPeriodStart(periodStart);
        payment.setPeriodEnd(periodEnd);
        payment.setAmountPaid(price.getAmount()); // el monto sale del precio vigente, nunca del cliente
        payment.setStudent(student);
        payment.setPrice(price);
        paymentRepository.save(payment);

        student.setExpirationDate(periodEnd);

        log.info("Pago registrado id={} studentId={} periodo {} -> {}", payment.getId(), studentId,
                periodStart, periodEnd);
        return paymentMapper.toDto(payment);
    }

    @Override
    public Page<PaymentResponse> studentPayments(Long studentId, Pageable pageable) {
        if (!studentRepository.existsById(studentId)) {
            throw new StudentNotFoundException();
        }
        return paymentRepository.findByStudentId(studentId, pageable).map(paymentMapper::toDto);
    }

    @Override
    public Page<PaymentResponse> myPayments(String username, Pageable pageable) {
        var user = userRepository.findWithStudentByUsername(username)
                .orElseThrow(StudentNotFoundException::new);
        if (user.getStudent() == null) {
            throw new StudentNotFoundException();
        }
        return paymentRepository.findByStudentId(user.getStudent().getId(), pageable)
                .map(paymentMapper::toDto);
    }

    @Override
    public Page<PaymentResponse> findAll(Pageable pageable) {
        return paymentRepository.findAll(pageable).map(paymentMapper::toDto);
    }
}

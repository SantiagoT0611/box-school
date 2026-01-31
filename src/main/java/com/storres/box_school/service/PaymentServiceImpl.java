package com.storres.box_school.service;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.storres.box_school.exception.PriceActiveNotFoundException;
import com.storres.box_school.exception.StudentNotActiveException;
import com.storres.box_school.exception.StudentNotFoundExcepcion;
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

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Service
public class PaymentServiceImpl implements PaymentService {

    private final StudentRepository studentRepository;
    private final PaymentMapper paymentMapper;
    private final PriceRepository priceRepository;
    private final PaymentRepository paymentRepository;

    private static final Logger log = LoggerFactory.getLogger(PaymentServiceImpl.class);

    @Override
    @Transactional
    public PaymentResponse payMembership(PaymentRequest request, Long id) {
        log.info("Busqueda de estudiante existente por id {}", id);
        Student existing = studentRepository.findById(id)
                .orElseThrow(() -> new StudentNotFoundExcepcion());
        log.warn("El estudiante buscando con el id {} no existe", id);

        if (existing.getStatus() != Status.ACTIVE) {
            throw new StudentNotActiveException("El estudiante no se encuentra activo");
        }
        Price activePrice = priceRepository.findByTypeAndActiveTrue(request.getType())
                .orElseThrow(() -> new PriceActiveNotFoundException());

        LocalDate periodStart;
        log.info("verificacion tiempo de membresia activo o inactivo");

        if (existing.getExpirationDate().isAfter(LocalDate.now())) {
            periodStart = existing.getExpirationDate();

        } else {
            periodStart = LocalDate.now();
        }

        LocalDate periodEnd = periodStart.plusDays(activePrice.getDurationDays());

        log.info("creando nuevo payment");
        Payment payment = new Payment();
        payment.setPaymentDate(LocalDate.now());
        payment.setPeriodStart(periodStart);
        payment.setPeriodEnd(periodEnd);
        payment.setAmountPaid(activePrice.getAmount());
        payment.setStudent(existing);
        payment.setPrice(activePrice);

        paymentRepository.save(payment);

        existing.setExpirationDate(periodEnd);
        studentRepository.save(existing);

        return paymentMapper.toDto(payment);

    }

    @Override
    public List<PaymentResponse> studentPayments(Long studentId) {
        log.info("Obteniendo el listado de pagos para el estudiante con id: {}",studentId);

        return paymentRepository.findByStudentId(studentId)
        .stream().map(paymentMapper::toDto)
        .collect(Collectors.toList());
    }

    @Override
    public List<PaymentResponse> findAll() {
        log.info("Obteniendo el listado de pagos");

        return paymentRepository.findAll().stream()
        .map(paymentMapper::toDto)
        .collect(Collectors.toList());
    }

}

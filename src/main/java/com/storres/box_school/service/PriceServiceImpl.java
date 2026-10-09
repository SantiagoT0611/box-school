package com.storres.box_school.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.storres.box_school.exception.PriceActiveNotFoundException;
import com.storres.box_school.mapper.PriceMapper;
import com.storres.box_school.model.dto.PriceRequest;
import com.storres.box_school.model.dto.PriceResponse;
import com.storres.box_school.model.entity.Price;
import com.storres.box_school.model.shared.PaymentType;
import com.storres.box_school.repository.PriceRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PriceServiceImpl implements PriceService {

    private static final Logger log = LoggerFactory.getLogger(PriceServiceImpl.class);

    private final PriceRepository priceRepository;
    private final PriceMapper priceMapper;
    private final Clock clock;

    @Override
    @Transactional
    public PriceResponse createPrice(PriceRequest request) {
        // Bloqueo de fila: dos admins creando a la vez no pueden dejar dos precios activos del mismo tipo
        priceRepository.findActiveForUpdate(request.getType()).ifPresent(previous -> previous.setActive(false));

        Price newPrice = priceMapper.toEntity(request);
        newPrice.setActive(true);
        newPrice.setCreatedAt(LocalDateTime.now(clock));
        Price saved = priceRepository.save(newPrice);

        log.info("Nuevo precio activo id={} tipo={} monto={}", saved.getId(), saved.getType(), saved.getAmount());
        return priceMapper.toDto(saved);
    }

    @Override
    public PriceResponse getActivePriceByType(PaymentType type) {
        return priceRepository.findByTypeAndActiveTrue(type)
                .map(priceMapper::toDto)
                .orElseThrow(PriceActiveNotFoundException::new);
    }

    @Override
    public List<PriceResponse> getActivePrices() {
        return priceRepository.findByActiveTrueOrderByType().stream().map(priceMapper::toDto).toList();
    }
}

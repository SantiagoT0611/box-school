package com.storres.box_school.service;

import java.time.LocalDateTime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.storres.box_school.mapper.PriceMapper;
import com.storres.box_school.model.dto.PriceRequest;
import com.storres.box_school.model.dto.PriceResponse;
import com.storres.box_school.model.entity.Price;
import com.storres.box_school.model.shared.PaymentType;
import com.storres.box_school.repository.PriceRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PriceServiceImpl implements PriceService {

    private final PriceRepository priceRepository;
    private final PriceMapper priceMapper;
    private static final Logger log = LoggerFactory.getLogger(PriceServiceImpl.class);

    @Transactional
    @Override
    public PriceResponse createPrice(PriceRequest request) {
        log.info("Buscando precio activo actualmente para el tipo solicitado");
        priceRepository.findByTypeAndActiveTrue(request.getType())
                .ifPresent(existingPrice -> {
                    existingPrice.setActive(false);
                    priceRepository.save(existingPrice);
                    log.info("Se desactivo correctamente el precio y se guardo en la base de datos");
                });
        Price newPrice = priceMapper.toEntity(request);
        newPrice.setActive(true);
        newPrice.setCreatedAt(LocalDateTime.now());
        log.info("se creo el nuevo precio con un estado activo");

        Price savedPrice = priceRepository.save(newPrice);
        log.info("Se guardo de manera exitosa el nuevo precio en la base de datos");

        return priceMapper.toDto(savedPrice);

    }

    @Override
    public PriceResponse getActivePriceByType(PaymentType type) {
        log.info("Buscando precio activo para el tipo de pago {}", type);

        Price price = priceRepository.findByTypeAndActiveTrue(type)
        .orElseThrow(() -> {
            log.error("No existe un precio activo para el tipo de pago {}", type);
            return new IllegalArgumentException("No existe un precio activo configurado para el tipo de pago: {}" + type);
        });

        log.info("Precio activo encontrado para {} con el monto {}", type, price.getAmount());

        return priceMapper.toDto(price);
    }


}

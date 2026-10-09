package com.storres.box_school.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.storres.box_school.model.dto.PriceRequest;
import com.storres.box_school.model.dto.PriceResponse;
import com.storres.box_school.model.shared.PaymentType;
import com.storres.box_school.service.PriceService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/prices")
public class PriceController {

    private final PriceService priceService;

    /** Define un precio nuevo; el anterior del mismo tipo queda inactivo (historial conservado). */
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping
    public ResponseEntity<PriceResponse> create(@Valid @RequestBody PriceRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(priceService.createPrice(request));
    }

    /** Precios vigentes (cualquier usuario autenticado, para mostrar cuanto debe pagar). */
    @GetMapping("/active")
    public ResponseEntity<List<PriceResponse>> activePrices() {
        return ResponseEntity.ok(priceService.getActivePrices());
    }

    @GetMapping("/active/{type}")
    public ResponseEntity<PriceResponse> activePrice(@PathVariable PaymentType type) {
        return ResponseEntity.ok(priceService.getActivePriceByType(type));
    }
}

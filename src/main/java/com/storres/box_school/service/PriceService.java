package com.storres.box_school.service;

import java.util.List;

import com.storres.box_school.model.dto.PriceRequest;
import com.storres.box_school.model.dto.PriceResponse;
import com.storres.box_school.model.shared.PaymentType;

public interface PriceService {

    /** Crea un precio nuevo y desactiva el anterior del mismo tipo (el historial se conserva). */
    PriceResponse createPrice(PriceRequest request);

    PriceResponse getActivePriceByType(PaymentType type);

    List<PriceResponse> getActivePrices();
}

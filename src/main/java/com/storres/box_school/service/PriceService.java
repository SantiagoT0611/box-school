package com.storres.box_school.service;

import com.storres.box_school.model.dto.PriceRequest;
import com.storres.box_school.model.dto.PriceResponse;
import com.storres.box_school.model.shared.PaymentType;

public interface PriceService {

    PriceResponse createPrice(PriceRequest Request);

    PriceResponse getActivePriceByType(PaymentType type);

}

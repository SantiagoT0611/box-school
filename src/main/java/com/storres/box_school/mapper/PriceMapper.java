package com.storres.box_school.mapper;

import org.springframework.stereotype.Component;

import com.storres.box_school.model.dto.PriceRequest;
import com.storres.box_school.model.dto.PriceResponse;
import com.storres.box_school.model.entity.Price;

@Component
public class PriceMapper {

    public Price toEntity(PriceRequest info) {
        var price = new Price();
        price.setType(info.getType());
        price.setAmount(info.getAmount());
        price.setDurationDays(info.getDurationDays());
        return price;
    }

    public PriceResponse toDto(Price price) {
        var dto = new PriceResponse();
        dto.setId(price.getId());
        dto.setType(price.getType());
        dto.setAmount(price.getAmount());
        dto.setDurationDays(price.getDurationDays());
        dto.setActive(price.getActive());
        dto.setCreatedAt(price.getCreatedAt());
        return dto;
    }
}

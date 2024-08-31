package com.service.order.model.dto.response;

import java.math.BigDecimal;

public record ProductDetailsDto(
        String id,
        String name,
        String code,
        String description,
        BigDecimal price
) {
}

package com.service.order.model.dto.response;

public record ProductAvailability(
        Boolean isAvailable,
        Long quantity
) {
}

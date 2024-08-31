package com.service.order.model.dto.response;

import org.springframework.http.HttpStatus;

public record ProductResponseDto(
        ProductDetailsDto payload,
        String message,
        HttpStatus httpStatus
) {
}

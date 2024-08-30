package com.service.order.model.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record OrderItemDto(
        @NotBlank(message = "Product ID is mandatory")
        String productId,

        @NotNull(message = "Quantity is mandatory")
        @Min(value = 1, message = "Quantity must be at least 1")
        Long quantity
) {
}

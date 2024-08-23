package com.service.order.model.dto.request;

import com.service.order.model.enums.PaymentMethod;
import com.service.order.model.enums.OrderStatus;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;

public record OrderRequestDto(
        @NotBlank(message = "Order name cannot be blank")
        @Size(max = 255, message = "Order name cannot exceed 255 characters")
        String orderName,

        @NotBlank(message = "Order date cannot be blank")
        @Pattern(regexp = "\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}", message = "Order date must be in the format YYYY-MM-DDTHH:MM:SS")
        String orderDate,

        @NotNull(message = "Status cannot be null")
        OrderStatus status,

        @NotNull(message = "Total amount cannot be null")
        @DecimalMin(value = "0.0", inclusive = true, message = "Total amount must be greater than or equal to 0")
        BigDecimal totalAmount,

        @NotBlank(message = "Shipping address cannot be blank")
        String shippingAddress,

        @NotNull(message = "Payment method cannot be null")
        PaymentMethod paymentMethod,

        @Size(max = 500, message = "Notes cannot exceed 500 characters")
        String notes
) {
}

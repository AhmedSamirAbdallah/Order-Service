package com.service.order.model.dto.request;

import com.service.order.model.dto.OrderItemDto;
import com.service.order.model.entity.OrderItem;
import com.service.order.model.enums.OrderSource;
import com.service.order.model.enums.OrderStatus;
import com.service.order.model.enums.PaymentMethod;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record OrderRequestDto(
        String orderNumber,

        @NotNull(message = "Customer ID is mandatory")
        Long customerId,

        String orderDate,

        OrderStatus status,

        @DecimalMin(value = "0.00", message = "Total amount must be positive")
        BigDecimal totalAmount,

        @NotBlank(message = "Shipping address is mandatory")
        String shippingAddress,

        @NotNull(message = "Payment method is mandatory")
        PaymentMethod paymentMethod,

        @NotEmpty(message = "Order items cannot be empty")
        List<@Valid OrderItemDto> orderItems,

        @DecimalMin(value = "0.00", message = "Shipping cost must be positive")
        BigDecimal shippingCost,

        @DecimalMin(value = "0.00", message = "Discount must be positive")
        BigDecimal discount,

        @DecimalMin(value = "0.00", message = "Tax amount must be positive")
        BigDecimal taxAmount,

        OrderSource orderSource,

        @Size(max = 1000, message = "Notes can be up to 1000 characters")
        String notes
) {
}

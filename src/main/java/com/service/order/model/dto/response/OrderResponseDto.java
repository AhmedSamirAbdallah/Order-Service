package com.service.order.model.dto.response;

import com.service.order.model.enums.OrderStatus;
import com.service.order.model.enums.PaymentMethod;

import java.math.BigDecimal;

public record OrderResponseDto(
        Long id,
        String orderName,
        String orderDate,
        OrderStatus OrderStatus,
        BigDecimal totalAmount,
        String shippingAddress,
        PaymentMethod paymentMethod,
        String notes
) {
}

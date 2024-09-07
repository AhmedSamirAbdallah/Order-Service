package com.service.order.model.dto.response;

import com.service.order.model.dto.OrderItemDto;
import com.service.order.model.enums.OrderSource;
import com.service.order.model.enums.OrderStatus;
import com.service.order.model.enums.PaymentMethod;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record OrderResponseDto(
        Long id,
        String orderNumber,
        Long customerId,
        String orderDate,
        OrderStatus status,
        BigDecimal totalAmount,
        String shippingAddress,
        PaymentMethod paymentMethod,
        List<OrderItemDto> orderItems,
        BigDecimal shippingCost,
        BigDecimal discount,
        BigDecimal taxAmount,
        OrderSource orderSource,
        String notes
) {
}

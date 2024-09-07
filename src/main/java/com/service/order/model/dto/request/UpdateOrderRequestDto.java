package com.service.order.model.dto.request;

import com.service.order.model.entity.OrderItem;
import com.service.order.model.enums.OrderStatus;
import jakarta.persistence.Column;

import java.util.List;

public record UpdateOrderRequestDto(
        String shippingAddress,
        List<OrderItem> orderItems,
        OrderStatus status,
        String notes

) {
}

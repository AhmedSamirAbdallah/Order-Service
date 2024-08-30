package com.service.order.mapper;

import com.service.order.model.dto.OrderItemDto;
import com.service.order.model.dto.request.OrderRequestDto;
import com.service.order.model.dto.response.OrderResponseDto;
import com.service.order.model.entity.OrderItem;
import com.service.order.model.entity.Orders;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface MapStructMapper {
    OrderResponseDto toOrderResponseDto(Orders order);

    OrderRequestDto toOrderRequestDto(Orders order);

    OrderItemDto toOrderItemDto(OrderItem orderItem);


}

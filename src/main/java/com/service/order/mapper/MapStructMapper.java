package com.service.order.mapper;

import com.service.order.model.dto.response.OrderResponseDto;
import com.service.order.model.entity.Orders;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface MapStructMapper {
    OrderResponseDto toOrderResponseDto(Orders order);
}

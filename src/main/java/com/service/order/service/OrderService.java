package com.service.order.service;

import com.service.order.exception.BusinessException;
import com.service.order.mapper.MapStructMapper;
import com.service.order.model.dto.request.OrderRequestDto;
import com.service.order.model.dto.response.OrderResponseDto;
import com.service.order.model.entity.Orders;
import com.service.order.model.enums.OrderStatus;
import com.service.order.repository.OrderRepository;
import org.hibernate.query.Order;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

@Service
public class OrderService {

    private final OrderRepository orderRepository;
    private final MapStructMapper mapStructMapper;

    @Autowired
    OrderService(OrderRepository orderRepository, MapStructMapper mapStructMapper) {
        this.orderRepository = orderRepository;
        this.mapStructMapper = mapStructMapper;
    }


    public OrderResponseDto createOrder(OrderRequestDto requestDto) {
        List<OrderStatus> vals = Arrays.stream(OrderStatus.values()).toList();
        if (!vals.contains(requestDto.status())) {
            throw new BusinessException("enter valid order status", HttpStatus.BAD_REQUEST);
        }

        Orders savedOrder = Orders.builder()
                .orderName(requestDto.orderName())
                .orderDate(LocalDateTime.parse(requestDto.orderDate()))
                .status(requestDto.status())
                .totalAmount(requestDto.totalAmount())
                .shippingAddress(requestDto.shippingAddress())
                .paymentMethod(requestDto.paymentMethod())
                .notes(requestDto.notes())
                .build();
        savedOrder = orderRepository.save(savedOrder);
        return mapStructMapper.toOrderResponseDto(savedOrder);
    }
}

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

import javax.swing.*;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

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

    public List<OrderResponseDto> getOrders() {

        List<OrderResponseDto> ordersList = orderRepository.findAll()
                .stream()
                .map(mapStructMapper::toOrderResponseDto)
                .toList();

        return ordersList;
    }

    public OrderResponseDto getOrderById(Long id) {

        Optional<OrderResponseDto> orderResponseDtoOptional = orderRepository.findById(id).map(mapStructMapper::toOrderResponseDto);

        if (!orderResponseDtoOptional.isPresent()) {
            throw new BusinessException("order not found", HttpStatus.NOT_FOUND);
        }

        return orderResponseDtoOptional.get();
    }

    public String editOrder(Long id, OrderRequestDto requestDto) {
        Optional<Orders> optionalOrder = orderRepository.findById(id);

        if (!optionalOrder.isPresent()) {
            throw new BusinessException("order not found", HttpStatus.NOT_FOUND);
        }

        Orders order = optionalOrder.get();

        if (order.getOrderName().equals(requestDto.orderName())) {
            order.setOrderName(requestDto.orderName());
        }
        if (!Objects.equals(order.getOrderDate(), requestDto.orderDate())) {//hhhhhhhhhh
            order.setOrderDate(LocalDateTime.parse(requestDto.orderDate()));
        }
        if (order.getStatus().equals(requestDto.status())) {
            order.setStatus(requestDto.status());
        }
        if (!Objects.equals(order.getTotalAmount(), requestDto.totalAmount())) {
            order.setOrderName(requestDto.orderName());
        }
        if (!order.getShippingAddress().equals(requestDto.shippingAddress())) {
            order.setShippingAddress(requestDto.shippingAddress());
        }
        if (!order.getPaymentMethod().equals(requestDto.paymentMethod())) {
            order.setOrderName(requestDto.orderName());
        }
        if (!order.getNotes().equals(requestDto.notes())) {
            order.setNotes(requestDto.notes());
        }
        orderRepository.save(order);
        return "order updated successfully!";
    }

    public String deleteOrder(Long id) {
        Optional<Orders> optionalOrder = orderRepository.findById(id);

        if (!optionalOrder.isPresent()) {
            throw new BusinessException("order not found", HttpStatus.NOT_FOUND);
        }
        orderRepository.deleteById(id);
        return "Order deleted successfully!";
    }
}

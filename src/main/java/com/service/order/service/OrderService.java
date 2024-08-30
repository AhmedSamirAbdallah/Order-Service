package com.service.order.service;

import com.service.order.exception.BusinessException;
import com.service.order.mapper.MapStructMapper;
import com.service.order.model.dto.OrderItemDto;
import com.service.order.model.dto.request.OrderRequestDto;
import com.service.order.model.dto.response.OrderResponseDto;
import com.service.order.model.entity.OrderItem;
import com.service.order.model.entity.Orders;
import com.service.order.model.enums.OrderStatus;
import com.service.order.repository.OrderRepository;
import com.service.order.util.Constants;
import jakarta.transaction.Transactional;
import org.hibernate.query.Order;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import javax.swing.*;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class OrderService {

    private final OrderRepository orderRepository;
    private final MapStructMapper mapStructMapper;

    @Autowired
    OrderService(OrderRepository orderRepository, MapStructMapper mapStructMapper) {
        this.orderRepository = orderRepository;
        this.mapStructMapper = mapStructMapper;
    }


    @Transactional
    public OrderResponseDto createOrder(OrderRequestDto requestDto) {

        Orders order = Orders.builder()
                .orderNumber(requestDto.orderNumber())
                .customerId(requestDto.customerId())
                .orderDate(requestDto.orderDate())
                .status(OrderStatus.PENDING)
                .totalAmount(requestDto.totalAmount())
                .shippingAddress(requestDto.shippingAddress())
                .paymentMethod(requestDto.paymentMethod())
                .shippingCost(requestDto.shippingCost())
                .discount(requestDto.discount())
                .taxAmount(requestDto.taxAmount())
                .orderSource(requestDto.orderSource())
                .notes(requestDto.notes())
                .build();


        List<OrderItem> orderItems = new ArrayList<>();

        //TODO checks the availability of the selected products (by consulting the Inventory Service).
        for (OrderItemDto item : requestDto.orderItems()) {
            orderItems.add(OrderItem.builder()
                    .order(order)
                    .productId(item.productId())
                    .quantity(item.quantity())
                    .build());
        }
        //TODO  calculates the total cost, including any taxes, discounts, and shipping fees
        order.setOrderItems(orderItems);
        order = orderRepository.save(order);

        return mapStructMapper.toOrderResponseDto(order);
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
            throw new BusinessException(Constants.ORDER_NOT_FOUND, HttpStatus.NOT_FOUND);
        }

        return orderResponseDtoOptional.get();
    }

    //    public OrderResponseDto updateOrder(Long id, OrderRequestDto requestDto) {
//        Optional<Orders> optionalOrder = orderRepository.findById(id);
//
//        if (!optionalOrder.isPresent()) {
//            throw new BusinessException(Constants.ORDER_NOT_FOUND, HttpStatus.NOT_FOUND);
//        }
//
//        Orders order = optionalOrder.get();
//
//        if (requestDto.) {
//            order.setOrderName(requestDto.orderName());
//        }
//        if (!Objects.equals(order.getOrderDate(), requestDto.orderDate())) {//hhhhhhhhhh
//            order.setOrderDate(LocalDateTime.parse(requestDto.orderDate()));
//        }
//        if (order.getStatus().equals(requestDto.status())) {
//            order.setStatus(requestDto.status());
//        }
//        if (!Objects.equals(order.getTotalAmount(), requestDto.totalAmount())) {
//            order.setOrderName(requestDto.orderName());
//        }
//        if (!order.getShippingAddress().equals(requestDto.shippingAddress())) {
//            order.setShippingAddress(requestDto.shippingAddress());
//        }
//        if (!order.getPaymentMethod().equals(requestDto.paymentMethod())) {
//            order.setOrderName(requestDto.orderName());
//        }
//        if (!order.getNotes().equals(requestDto.notes())) {
//            order.setNotes(requestDto.notes());
//        }
//        orderRepository.save(order);
//        return "order updated successfully!";
//    }
//
    public OrderResponseDto deleteOrder(Long id) {
        Optional<Orders> optionalOrder = orderRepository.findById(id);

        if (!optionalOrder.isPresent()) {
            throw new BusinessException(Constants.ORDER_NOT_FOUND, HttpStatus.NOT_FOUND);
        }
        orderRepository.deleteById(id);
        return mapStructMapper.toOrderResponseDto(optionalOrder.get());
    }
}

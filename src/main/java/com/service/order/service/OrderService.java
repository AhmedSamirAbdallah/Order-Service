package com.service.order.service;

import com.service.order.client.ProductClient;
import com.service.order.config.OrderServiceConfig;
import com.service.order.exception.BusinessException;
import com.service.order.mapper.MapStructMapper;
import com.service.order.model.dto.OrderItemDto;
import com.service.order.model.dto.request.OrderRequestDto;
import com.service.order.model.dto.response.OrderResponseDto;
import com.service.order.model.dto.response.ProductResponseDto;
import com.service.order.model.entity.OrderItem;
import com.service.order.model.entity.Orders;
import com.service.order.model.enums.OrderStatus;
import com.service.order.repository.OrderRepository;
import com.service.order.util.Constants;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.query.Order;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import javax.swing.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

@Service
@Slf4j
public class OrderService {

    private final OrderRepository orderRepository;
    private final MapStructMapper mapStructMapper;
    private final ProductClient productClient;
    private final OrderServiceConfig orderServiceConfig;
    private static final Logger logger = LoggerFactory.getLogger(OrderService.class);

    @Autowired
    OrderService(OrderRepository orderRepository, MapStructMapper mapStructMapper, ProductClient productClient, OrderServiceConfig orderServiceConfig) {
        this.orderRepository = orderRepository;
        this.mapStructMapper = mapStructMapper;
        this.productClient = productClient;
        this.orderServiceConfig = orderServiceConfig;
    }

    private BigDecimal calculateTotalAmount(BigDecimal totalAmount) {
        //calc discount
        BigDecimal discountAmount = totalAmount.multiply(orderServiceConfig.getDiscount());
        totalAmount = totalAmount.subtract(discountAmount);

        //add tax amount
        BigDecimal taxAmount = totalAmount.multiply(orderServiceConfig.getTax());
        totalAmount = totalAmount.add(taxAmount);

        //add shipping cost
        totalAmount = totalAmount.add(orderServiceConfig.getShippingCost());

        return totalAmount;
    }

    public ProductResponseDto getProduct(String id) {
        return productClient.getProductById(id);
    }

//    private ProductResponseDto fallbackForProductService(String id, Throwable th) {
//        logger.error("Product service is unavailable: {}", th.getMessage());
//        return new ProductResponseDto(null, "PRODUCT_SERVICE_UNAVAILABLE", HttpStatus.SERVICE_UNAVAILABLE);
//    }

    @Transactional
    public OrderResponseDto createOrder(OrderRequestDto requestDto) {

        Orders order = Orders.builder()
                .orderNumber(requestDto.orderNumber())
                .customerId(requestDto.customerId())
                .orderDate(requestDto.orderDate())
                .status(OrderStatus.PENDING)
                .shippingAddress(requestDto.shippingAddress())
                .paymentMethod(requestDto.paymentMethod())
                .shippingCost(orderServiceConfig.getShippingCost())
                .discount(orderServiceConfig.getDiscount())
                .taxAmount(orderServiceConfig.getTax())
                .orderSource(requestDto.orderSource())
                .notes(requestDto.notes())
                .build();


        List<OrderItem> orderItems = new ArrayList<>();

        //TODO checks the availability of the selected products (by consulting the Inventory Service).
        BigDecimal totalAmount = BigDecimal.ZERO;

        for (OrderItemDto item : requestDto.orderItems()) {

            ProductResponseDto responseDto = getProduct(item.productId());
            if(responseDto==null) continue;
            BigDecimal productPrice = responseDto.payload().price();

            productPrice = productPrice.multiply(BigDecimal.valueOf(item.quantity()));
            totalAmount = totalAmount.add(productPrice);


            orderItems.add(OrderItem.builder()
                    .order(order)
                    .productId(item.productId())
                    .quantity(item.quantity())
                    .build());

        }

        order.setOrderItems(orderItems);
        order.setTotalAmount(calculateTotalAmount(totalAmount));
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

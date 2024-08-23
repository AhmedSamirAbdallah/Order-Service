package com.service.order.controller;

import com.service.order.common.ApiResponse;
import com.service.order.model.dto.request.OrderRequestDto;
import com.service.order.service.OrderService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(path = "/orders")
public class OrderController {
    private final OrderService orderService;

    @Autowired
    OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    ApiResponse createOrder(@RequestBody @Valid OrderRequestDto orderRequestDto) {
        return ApiResponse.ok(orderService.createOrder(orderRequestDto), "order created successfully!", HttpStatus.CREATED.value());
    }

}

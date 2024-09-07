package com.service.order.controller;

import com.service.order.common.ApiResponse;
import com.service.order.model.dto.request.OrderRequestDto;
import com.service.order.model.dto.request.UpdateOrderRequestDto;
import com.service.order.service.OrderService;
import com.service.order.util.Constants;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(path = "/api/orders")
public class OrderController {
    private final OrderService orderService;

    @Autowired
    OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    ApiResponse createOrder(@RequestBody @Valid OrderRequestDto orderRequestDto) {
        return ApiResponse.success(orderService.createOrder(orderRequestDto), Constants.ORDER_CREATED, HttpStatus.CREATED);
    }

    @GetMapping
    ApiResponse getOrder() {
        return ApiResponse.success(orderService.getOrders(), Constants.ORDER_RETRIEVED, HttpStatus.OK);
    }

    @GetMapping(path = "/{id}")
    ApiResponse getOrders(@PathVariable Long id) {
        return ApiResponse.success(orderService.getOrderById(id), Constants.ORDER_RETRIEVED, HttpStatus.OK);
    }

    @PutMapping(path = "/{id}")
    ApiResponse editOrder(@PathVariable Long id, @RequestBody @Valid UpdateOrderRequestDto requestDto) {
        return ApiResponse.success(orderService.updateOrder(id, requestDto), Constants.ORDER_UPDATED, HttpStatus.OK);
    }

    @DeleteMapping(path = "/{id}")
    ApiResponse deleteOrder(@PathVariable Long id) {
        return ApiResponse.success(orderService.deleteOrder(id), Constants.ORDER_DELETED, HttpStatus.OK);
    }

    @GetMapping(path = "/products/{id}")
    ApiResponse getProductDetails(@PathVariable String id) {
        return ApiResponse.success(orderService.getProduct(id), "", HttpStatus.OK);
    }

}

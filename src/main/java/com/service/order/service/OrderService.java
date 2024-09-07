package com.service.order.service;

import com.service.order.client.ProductClient;
import com.service.order.config.OrderServiceConfig;
import com.service.order.exception.BusinessException;
import com.service.order.exception.ProductServiceUnAvailableException;
import com.service.order.mapper.MapStructMapper;
import com.service.order.model.dto.OrderItemDto;
import com.service.order.model.dto.request.OrderRequestDto;
import com.service.order.model.dto.request.UpdateOrderRequestDto;
import com.service.order.model.dto.response.OrderResponseDto;
import com.service.order.model.dto.response.ProductResponseDto;
import com.service.order.model.entity.OrderItem;
import com.service.order.model.entity.Orders;
import com.service.order.model.enums.OrderSource;
import com.service.order.model.enums.OrderStatus;
import com.service.order.model.enums.PaymentMethod;
import com.service.order.repository.OrderItemRepository;
import com.service.order.repository.OrderRepository;
import com.service.order.util.Constants;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.query.Order;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collector;
import java.util.stream.Collectors;

@Service
@Slf4j
public class OrderService {

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final MapStructMapper mapStructMapper;
    private final ProductClient productClient;
    private final OrderServiceConfig orderServiceConfig;
    private static final Logger logger = LoggerFactory.getLogger(OrderService.class);
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final RedisTemplate<String, Object> redisTemplate;

    @Value(value = "${redis.cache.ttl}")
    private Long timeToLive;

    @Autowired
    OrderService(OrderRepository orderRepository, MapStructMapper mapStructMapper, ProductClient productClient, OrderServiceConfig orderServiceConfig, KafkaTemplate<String, Object> kafkaTemplate, RedisTemplate<String, Object> redisTemplate, OrderItemRepository orderItemRepository) {
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.mapStructMapper = mapStructMapper;
        this.productClient = productClient;
        this.orderServiceConfig = orderServiceConfig;
        this.kafkaTemplate = kafkaTemplate;
        this.redisTemplate = redisTemplate;
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

    @Transactional
    public OrderResponseDto createOrder(OrderRequestDto requestDto) {

        Orders order = Orders.builder()
                .orderNumber("ORD" + orderRepository.findNextValOfSequence())
                .customerId(requestDto.customerId())
                .orderDate(LocalDateTime.now())
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

            try {
                ProductResponseDto responseDto = productClient.getProductById(item.productId());
                if (responseDto.httpStatus().equals(HttpStatus.OK)) {

                    BigDecimal productPrice = responseDto.payload().price();

                    productPrice = productPrice.multiply(BigDecimal.valueOf(item.quantity()));
                    totalAmount = totalAmount.add(productPrice);


                    orderItems.add(OrderItem.builder()
                            .order(order)
                            .productId(item.productId())
                            .quantity(item.quantity())
                            .build());
                } else {
                    throw new BusinessException(responseDto.message(), responseDto.httpStatus());
                }
            } catch (Exception e) {
                logger.error(e.getMessage());
                throw new ProductServiceUnAvailableException(Constants.PRODUCT_SERVICE_NOT_AVAILABLE, HttpStatus.SERVICE_UNAVAILABLE);
            }
        }
        order.setOrderItems(orderItems);
        order.setTotalAmount(calculateTotalAmount(totalAmount));
        order = orderRepository.save(order);

//        redisTemplate.opsForValue().set(Constants.ORDER_CACHE_KEY_PREFIX + order.getId(), order, timeToLive, TimeUnit.MINUTES);

        OrderResponseDto responseDto = mapStructMapper.toOrderResponseDto(order);
        kafkaTemplate.send(Constants.ORDER_CREATED_EVENT, responseDto);

        //TODO Update Inventory STOCK

        return responseDto;
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

    private Orders getOrder(Long id) {
        return orderRepository.findById(id).orElseThrow(() -> new BusinessException(Constants.ORDER_NOT_FOUND, HttpStatus.NOT_FOUND));
    }

    private void validateUpdateOrder(UpdateOrderRequestDto requestDto, Orders order) {
//        if (order.getStatus().equals(OrderStatus.SHIPPED)) {
//            throw new BusinessException(Constants.ORDER_STATUS_SHIPPED_UPDATE_ERROR, HttpStatus.FORBIDDEN);
//        }


        Set<OrderStatus> validOrderStatus = Set.of(
                OrderStatus.PENDING,
                OrderStatus.CONFIRMED,
                OrderStatus.SHIPPED,
                OrderStatus.DELIVERED,
                OrderStatus.CANCELED

        );

        if (!validOrderStatus.contains(requestDto.status())) {
            throw new BusinessException(Constants.INVALID_ORDER_STATUS + requestDto.status(), HttpStatus.BAD_REQUEST);
        }


        if (requestDto.orderItems().isEmpty()) {
            throw new BusinessException(Constants.EMPTY_ORDER_LIST + requestDto.status(), HttpStatus.BAD_REQUEST);
        }

        //TODO Ensure that each productId is valid and exists in the inventory.
        //Check that the quantity is positive and within allowable limits.
        //Update the order items accordingly.

    }

    @Transactional
    public OrderResponseDto updateOrder(Long id, UpdateOrderRequestDto requestDto) {

        Orders order = getOrder(id);

        validateUpdateOrder(requestDto, order);

        //TODO Stock Availability

        if (requestDto.status() != null) {
            order.setStatus(requestDto.status());
        }

        if (requestDto.shippingAddress() != null) {
            order.setShippingAddress(requestDto.shippingAddress());
        }

        if (!requestDto.orderItems().isEmpty()) {

            Map<String, OrderItem> existingItems = order.getOrderItems().stream().collect(Collectors.toMap(OrderItem::getProductId, Function.identity()));

            BigDecimal totalAmount = BigDecimal.ZERO;

            List<OrderItem> updatedItems = new ArrayList<>();

            for (OrderItem item : requestDto.orderItems()) {

                ProductResponseDto productResponseDto = productClient.getProductById(item.getProductId());

                if (productResponseDto == null || productResponseDto.payload() == null) {
                    throw new BusinessException(productResponseDto.message(), productResponseDto.httpStatus());
                }


                BigDecimal total = productResponseDto.payload().price().multiply(BigDecimal.valueOf(item.getQuantity()));
                totalAmount = totalAmount.add(total);


                if (existingItems.containsKey(item.getProductId())) {

                    OrderItem orderItem = existingItems.get(item.getProductId());
                    orderItem.setQuantity(item.getQuantity());
                    updatedItems.add(orderItem);

                } else {

                    OrderItem newItem = OrderItem.builder()
                            .productId(item.getProductId())
                            .quantity(item.getQuantity())
                            .order(order)
                            .build();

                    updatedItems.add(newItem);


                }

            }

            List<OrderItem> toBeRemoved = order.getOrderItems()
                    .stream()
                    .filter(existingItem -> !updatedItems
                            .stream()
                            .anyMatch(item -> item.getProductId()
                                    .equals(existingItem.getProductId())))
                    .toList();

            totalAmount = calculateTotalAmount(totalAmount);

            order.setOrderItems(updatedItems);
            order.setTotalAmount(totalAmount);

            orderItemRepository.saveAll(updatedItems);
            orderItemRepository.deleteAll(toBeRemoved);
        }

        if (requestDto.notes() != null) {
            order.setNotes(requestDto.notes());
        }

        order = orderRepository.save(order);

        return mapStructMapper.toOrderResponseDto(order);
    }

    public OrderResponseDto deleteOrder(Long id) {

        Orders order = getOrder(id);

        orderRepository.deleteById(id);

        return mapStructMapper.toOrderResponseDto(order);
    }
}

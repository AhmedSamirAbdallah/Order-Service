package com.service.order.service;

import aj.org.objectweb.asm.TypeReference;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.service.order.client.InventoryClient;
import com.service.order.client.ProductClient;
import com.service.order.common.ApiResponse;
import com.service.order.config.OrderServiceConfig;
import com.service.order.exception.BusinessException;
import com.service.order.mapper.MapStructMapper;
import com.service.order.model.dto.OrderItemDto;
import com.service.order.model.dto.request.OrderRequestDto;
import com.service.order.model.dto.request.UpdateOrderRequestDto;
import com.service.order.model.dto.response.OrderResponseDto;
import com.service.order.model.dto.response.ProductAvailability;
import com.service.order.model.dto.response.ProductResponseDto;
import com.service.order.model.entity.OrderItem;
import com.service.order.model.entity.Orders;
import com.service.order.model.enums.OrderStatus;
import com.service.order.repository.OrderItemRepository;
import com.service.order.repository.OrderRepository;
import com.service.order.util.Constants;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
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
    private final InventoryClient inventoryClient;
    private final ObjectMapper objectMapper;

    @Value(value = "${redis.cache.ttl}")
    private Long timeToLive;

    @Autowired
    OrderService(OrderRepository orderRepository, MapStructMapper mapStructMapper, ProductClient productClient, OrderServiceConfig orderServiceConfig, KafkaTemplate<String, Object> kafkaTemplate, RedisTemplate<String, Object> redisTemplate, OrderItemRepository orderItemRepository, InventoryClient inventoryClient, ObjectMapper objectMapper) {
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.mapStructMapper = mapStructMapper;
        this.productClient = productClient;
        this.orderServiceConfig = orderServiceConfig;
        this.kafkaTemplate = kafkaTemplate;
        this.redisTemplate = redisTemplate;
        this.inventoryClient = inventoryClient;
        this.objectMapper = objectMapper;
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
                .orderDate((LocalDateTime.now()).toString())
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

        BigDecimal totalAmount = BigDecimal.ZERO;

        for (OrderItemDto item : requestDto.orderItems()) {

            ProductResponseDto responseDto = productClient.getProductById(item.productId());

            if (responseDto == null || responseDto.payload() == null) {
                throw new BusinessException(responseDto.message(), responseDto.httpStatus());
            }

            if (!checkAvailability(item.productId(), item.quantity())) {
                throw new BusinessException(Constants.PRODUCT_NOT_AVAILABLE, HttpStatus.BAD_REQUEST);
            }

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

        OrderResponseDto responseDto = mapStructMapper.toOrderResponseDto(order);

        redisTemplate.opsForValue().set(Constants.ORDER_CACHE_KEY_PREFIX + order.getId(), responseDto, timeToLive, TimeUnit.MINUTES);
        kafkaTemplate.send(Constants.ORDER_CREATED_EVENT, responseDto);

        return responseDto;
    }

    public Page<OrderResponseDto> getOrders(int page, int size) {

        Pageable pageable = PageRequest.of(page, size);

        Page<Orders> ordersPage = orderRepository.findAll(pageable);

        return ordersPage.map(order -> mapStructMapper.toOrderResponseDto(order));
    }

    public OrderResponseDto getOrderById(Long id) {

        Orders order = getOrder(id);

        return mapStructMapper.toOrderResponseDto(order);
    }

    private Orders getOrder(Long id) {

        String cacheKey = Constants.ORDER_CACHE_KEY_PREFIX + id;
        OrderResponseDto orderResponseDto = (OrderResponseDto) redisTemplate.opsForValue().get(cacheKey);

        if (orderResponseDto != null) {
            return mapStructMapper.toOrder(orderResponseDto);
        }

        Orders order = orderRepository.findById(id).orElseThrow(() -> new BusinessException(Constants.ORDER_NOT_FOUND, HttpStatus.NOT_FOUND));

        redisTemplate.opsForValue().set(cacheKey, mapStructMapper.toOrderRequestDto(order), timeToLive, TimeUnit.MINUTES);

        return order;
    }

    private void validateUpdateOrder(UpdateOrderRequestDto requestDto, Orders order) {
        if (order.getStatus().equals(OrderStatus.SHIPPED)) {
            throw new BusinessException(Constants.ORDER_STATUS_SHIPPED_UPDATE_ERROR, HttpStatus.FORBIDDEN);
        }


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
    }

    @Transactional
    public OrderResponseDto updateOrder(Long id, UpdateOrderRequestDto requestDto) {

        Orders order = getOrder(id);

        validateUpdateOrder(requestDto, order);

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

                if (!checkAvailability(item.getProductId(), item.getQuantity())) {
                    throw new BusinessException(Constants.PRODUCT_NOT_AVAILABLE, HttpStatus.BAD_REQUEST);
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

        OrderResponseDto responseDto = mapStructMapper.toOrderResponseDto(order);

        if (order.getStatus().equals(OrderStatus.CANCELED)) {
            kafkaTemplate.send(Constants.ORDER_CANCELED_EVENT, responseDto);
        } else {
            kafkaTemplate.send(Constants.ORDER_UPDATED, responseDto);
        }

        redisTemplate.delete(Constants.ORDER_CACHE_KEY_PREFIX + id);

        return responseDto;
    }

    public OrderResponseDto deleteOrder(Long id) {

        Orders order = getOrder(id);

        orderRepository.deleteById(id);

        OrderResponseDto responseDto = mapStructMapper.toOrderResponseDto(order);

        kafkaTemplate.send(Constants.ORDER_DELETED, responseDto);
        redisTemplate.delete(Constants.ORDER_CACHE_KEY_PREFIX + id);

        return responseDto;
    }

    private Boolean checkAvailability(String productId, Long quantity) {
        try {

            ApiResponse response = inventoryClient.checkProductAvailability(productId, quantity);

            if (response == null || response.getPayload() == null) {
                logger.error(String.valueOf(response));
                throw new BusinessException(response.getMessage(), response.getHttpStatus());
            }

            String json = objectMapper.writeValueAsString(response.getPayload());
            ProductAvailability productAvailability = objectMapper.readValue(json, ProductAvailability.class);

            return productAvailability.isAvailable();

        } catch (JsonProcessingException ex) {
            logger.error("Error processing JSON response for productId: {} with quantity: {}. Exception: {}", productId, quantity, ex.getMessage(), ex);
            return false;
        }
    }
}

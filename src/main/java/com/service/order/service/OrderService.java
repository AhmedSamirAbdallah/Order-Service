package com.service.order.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.service.order.client.InventoryClient;
import com.service.order.client.ProductClient;
import com.service.order.common.ApiResponse;
import com.service.order.config.OrderServiceConfig;
import com.service.order.exception.BusinessException;
import com.service.order.mapper.MapStructMapper;
import com.service.order.model.dto.OrderItemDto;
import com.service.order.model.dto.PaginatedResponse;
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
import jakarta.servlet.http.HttpServletResponse;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDFormContentStream;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDTrueTypeFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.io.IOException;
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

    public PaginatedResponse<OrderResponseDto> getOrders(int page, int size, String[] sortBy, String[] sortOrder) {
        Sort sort = Sort.by(sortOrder[0].equals("desc") ? Sort.Direction.DESC : Sort.Direction.ASC, sortBy[0]);

        for (int i = 1; i < sortBy.length; i++) {

            sort.and(Sort.by(sortOrder[i].equals("desc") ? Sort.Direction.DESC : Sort.Direction.ASC, sortBy[i]));
        }

        Pageable pageable = PageRequest.of(page, size, sort);

        Page<Orders> ordersPage = orderRepository.findAll(pageable);

        List<OrderResponseDto> orderResponseDtoList = ordersPage
                .stream()
                .map(order -> mapStructMapper.toOrderResponseDto(order))
                .toList();

        return new PaginatedResponse<>(orderResponseDtoList,
                ordersPage.getNumber(),
                ordersPage.getSize(),
                ordersPage.getTotalElements(),
                ordersPage.getTotalPages());

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

    public void exportOrdersReportInExcel(HttpServletResponse response) throws IOException {
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader("Content-Disposition", "attachment; filename=orders.xlsx");

        Workbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet("ordersSheet");

        sheet.setColumnWidth(0, 20 * 256);
        sheet.setColumnWidth(1, 20 * 256);
        sheet.setColumnWidth(2, 20 * 256);
        sheet.setColumnWidth(3, 30 * 256);
        sheet.setColumnWidth(4, 20 * 256);
        sheet.setColumnWidth(5, 20 * 256);

        List<Orders> ordersList = orderRepository.findAll();

        Row header = sheet.createRow(0);
        header.createCell(0).setCellValue("order id");
        header.createCell(1).setCellValue("order number");
        header.createCell(2).setCellValue("customer id");
        header.createCell(3).setCellValue("order date");
        header.createCell(4).setCellValue("order status");
        header.createCell(5).setCellValue("total amount");

        Integer rowNumber = 1;

        for (Orders orders : ordersList) {
            Row row = sheet.createRow(rowNumber++);
            row.createCell(0).setCellValue(orders.getId());
            row.createCell(1).setCellValue(orders.getOrderNumber());
            row.createCell(2).setCellValue(orders.getCustomerId());
            row.createCell(3).setCellValue(orders.getOrderDate());
            row.createCell(4).setCellValue(orders.getStatus().toString());
            row.createCell(5).setCellValue(Constants.parseToString(orders.getTotalAmount()));
        }
        workbook.write(response.getOutputStream());
    }

    public void exportOrdersReportInPdf(HttpServletResponse response) throws IOException {
        response.setContentType("application/pdf");
        response.setHeader("Content-Disposition", "attachment; filename=orders.pdf");

        PDDocument document = new PDDocument();
        PDPage page = new PDPage();
        document.addPage(page);
        PDPageContentStream contentStream = new PDPageContentStream(document, page);

        // Title
        contentStream.setFont(PDType1Font.HELVETICA_BOLD, 16);
        contentStream.beginText();
        contentStream.newLineAtOffset(200, 750); // Centered title
        contentStream.showText("Orders Report");
        contentStream.endText();

        // Header
        contentStream.setFont(PDType1Font.HELVETICA_BOLD, 12);
        contentStream.beginText();
        contentStream.newLineAtOffset(100, 720); // Adjust Y position for header
        contentStream.showText("Order ID");
        contentStream.newLineAtOffset(120, 0);
        contentStream.showText("Order Number");
        contentStream.endText();

        // Underline header
        contentStream.setLineWidth(1f);
        contentStream.moveTo(100, 715); // Adjust Y position for line
        contentStream.lineTo(400, 715); // Adjust X position for line
        contentStream.stroke();

        // Data
        contentStream.setFont(PDType1Font.TIMES_ROMAN, 12);
        List<Orders> ordersList = orderRepository.findAll();
        Integer positionY = 700;

        for (Orders orders : ordersList) {
            contentStream.beginText();
            contentStream.newLineAtOffset(100, positionY);
            contentStream.showText(String.valueOf(orders.getId()));
            contentStream.newLineAtOffset(120, 0);
            contentStream.showText(orders.getOrderNumber());
            contentStream.endText();
            positionY -= 15; // Move down for the next line
        }

        contentStream.close();
        document.save(response.getOutputStream());
        document.close();
    }


}

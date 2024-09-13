package com.service.order.util;

import java.math.BigDecimal;

public class Constants {

    // Success messages
    public static final String ORDER_CREATED = "Order created successfully.";
    public static final String ORDER_UPDATED = "Order updated successfully.";
    public static final String ORDER_DELETED = "Order deleted successfully.";
    public static final String ORDER_RETRIEVED = "Order retrieved successfully.";
    public static final String ORDER_CACHE_KEY_PREFIX = "order:";


    // Error messages

    public static final String PRODUCT_SERVICE_NOT_AVAILABLE = "Product Service Not available.";
    public static final String PRODUCT_NOT_AVAILABLE = "Product not available";
    public static final String ORDER_NOT_FOUND = "Order not found.";
    public static final String INVALID_INPUT = "Invalid input. Please check your request.";
    public static final String INVALID_ORDER_ID = "Invalid order ID provided.";
    public static final String ORDER_CREATION_FAILED = "Failed to create order.";
    public static final String ORDER_STATUS_SHIPPED_UPDATE_ERROR = "Cannot update order with status 'SHIPPED'";
    public static final String INVALID_PAYMENT_METHOD = "Invalid payment method: ";
    public static final String INVALID_ORDER_STATUS = "Invalid order status: ";
    public static final String INVALID_ORDER_SOURCE = "Invalid order source: ";
    public static final String EMPTY_ORDER_LIST = "Order list can't be empty.";


    // Kafka Topics
    public static final String ORDER_CREATED_EVENT = "order-created-event";
    public static final String ORDER_UPDATED_EVENT = "order-updated-event";
    public static final String ORDER_DELETED_EVENT = "order-deleted-event";
    public static final String ORDER_CANCELED_EVENT = "order-canceled-event";

}

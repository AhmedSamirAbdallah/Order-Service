package com.service.order.util;

public class Constants {

    public static final int MAX_RETRIES = 3;
    public static final double TAX_RATE = 0.07;


    // Success messages
    public static final String ORDER_CREATED = "Order created successfully.";
    public static final String ORDER_UPDATED = "Order updated successfully.";
    public static final String ORDER_DELETED = "Order deleted successfully.";
    public static final String ORDER_RETRIEVED = "Order retrieved successfully.";

    // Error messages
    public static final String ORDER_NOT_FOUND = "Order not found.";
    public static final String INVALID_INPUT = "Invalid input. Please check your request.";
    public static final String INVALID_ORDER_ID = "Invalid order ID provided.";
    public static final String ORDER_CREATION_FAILED = "Failed to create order.";
}

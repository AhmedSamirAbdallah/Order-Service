package com.service.order.model.enums;

public enum OrderStatus {

    PENDING("Pending"),
    CONFIRMED("Confirmed"),
    SHIPPED("Shipped"),
    DELIVERED("Delivered"),
    CANCELED("Canceled");

    private String description;

    OrderStatus(String description) {
        this.description = description;
    }
}

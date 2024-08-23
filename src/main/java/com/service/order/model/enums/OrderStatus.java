package com.service.order.model.enums;

public enum OrderStatus {

    PENDING("pending"),
    SHIPPED("shipped"),
    DELIVERED("delivered"),
    CANCELED("canceled");

    private String description;

    OrderStatus(String description) {
        this.description = description;
    }
}

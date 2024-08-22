package com.service.order.model.enums;

public enum Status {

    PENDING("pending"),
    SHIPPED("shipped"),
    DELIVERED("delivered"),
    CANCELED("canceled");

    private String description;

    Status(String description) {
        this.description = description;
    }
}

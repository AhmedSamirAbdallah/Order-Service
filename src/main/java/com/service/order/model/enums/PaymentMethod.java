package com.service.order.model.enums;

public enum PaymentMethod {

    CREDIT_CARD("credit card"),
    PAYPAL("paypal"),
    CASH("cash");

    private String description;

    PaymentMethod(String description) {
        this.description = description;
    }
}

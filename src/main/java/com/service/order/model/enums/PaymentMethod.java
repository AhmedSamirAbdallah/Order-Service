package com.service.order.model.enums;

public enum PaymentMethod {

    CREDIT_CARD("Credit Card"),
    PAYPAL("Paypal"),
    CASH("Cash"),
    BANK_TRANSFER("Bank Transfer");

    private String description;

    PaymentMethod(String description) {
        this.description = description;
    }
}

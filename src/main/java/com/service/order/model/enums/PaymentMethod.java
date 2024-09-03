package com.service.order.model.enums;

public enum PaymentMethod {

    CREDIT_CARD("Credit Card"),
    PAYPAL("Paypal"),
    CASH("Cash"),
    DEBIT_CARD("debit card"),
    BANK_TRANSFER("Bank Transfer");

    private String description;

    PaymentMethod(String description) {
        this.description = description;
    }
}

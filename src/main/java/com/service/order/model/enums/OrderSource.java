package com.service.order.model.enums;

public enum OrderSource {

    WEBSITE("website"),
    IN_STORE("in store"),
    MOBILE_APP("mobile app");

    private String description;

    OrderSource(String description) {
        this.description = description;
    }
}

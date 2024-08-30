package com.service.order.model.enums;

public enum OrderSource {

    WEBSITE("website"),
    MOBILE_APP("mobile app");

    private String description;

    OrderSource(String description) {
        this.description = description;
    }
}

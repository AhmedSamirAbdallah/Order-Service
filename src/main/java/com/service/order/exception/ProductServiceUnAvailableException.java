package com.service.order.exception;

import org.springframework.http.HttpStatus;

public class ProductServiceUnAvailableException extends RuntimeException {
    private final HttpStatus httpStatus;

    public ProductServiceUnAvailableException(String message, HttpStatus httpStatus) {
        super(message);
        this.httpStatus = httpStatus;
    }
}


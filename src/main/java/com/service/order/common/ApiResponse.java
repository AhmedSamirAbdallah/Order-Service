package com.service.order.common;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Builder
@NoArgsConstructor
@AllArgsConstructor
@Data
public class ApiResponse<T> {
    private String message;
    private Integer code;
    private T payload;

    public static <T> ApiResponse ok(T payload, String message, Integer code) {
        return ApiResponse
                .builder()
                .payload(payload)
                .message(message)
                .code(code)
                .build();
    }

    public static <T> ApiResponse failed(String message, Integer code) {
        return ApiResponse
                .builder()
                .payload(null)
                .message(message)
                .code(code)
                .build();
    }


}

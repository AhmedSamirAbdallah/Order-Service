package com.service.order.exception;

import com.service.order.common.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ApiResponse<List<String>> handleValidationException(MethodArgumentNotValidException ex) {
        List<String> errors = ex.getBindingResult().getAllErrors().stream()
                .map(error -> error.getDefaultMessage())
                .collect(Collectors.toList());

        return ApiResponse.ok(errors, "Validation failed", HttpStatus.BAD_REQUEST.value());
    }

    @ExceptionHandler(BusinessException.class)
    public ApiResponse handelBusinessException(BusinessException ex) {
        return ApiResponse.failed(ex.getMessage(), HttpStatus.BAD_REQUEST.value());
    }


    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ApiResponse handelHttpMessageNotReadableException(HttpMessageNotReadableException ex) {
        return ApiResponse.failed("Invalid input. Please check your request.", HttpStatus.BAD_REQUEST.value());
    }

//    @ExceptionHandler(Exception.class)
//    public ResponseEntity<ApiResponse<String>> handleGeneralException(Exception ex) {
//        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
//                .body(ApiResponse.failed("An unexpected error occurred", HttpStatus.INTERNAL_SERVER_ERROR.value()));
//    }

}

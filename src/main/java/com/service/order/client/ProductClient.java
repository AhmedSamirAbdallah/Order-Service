package com.service.order.client;

import com.service.order.model.dto.response.ProductResponseDto;
import com.service.order.util.Constants;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "product-service", url = "${inventory.service.url}")
public interface ProductClient {

    @GetMapping(path = "/api/products/{id}")
    @CircuitBreaker(name = "productServiceCircuitBreaker", fallbackMethod = "fallbackForProductService")
    ProductResponseDto getProductById(@PathVariable String id);

    default ProductResponseDto fallbackForProductService(String id, Throwable th) {
        return new ProductResponseDto(null, Constants.PRODUCT_SERVICE_NOT_AVAILABLE, HttpStatus.SERVICE_UNAVAILABLE);
    }
}

package com.service.order.client;

import com.service.order.common.ApiResponse;
import com.service.order.util.Constants;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "inventory-service", url = "${inventory.service.url}")
public interface InventoryClient {

    @GetMapping(path = "/api/inventory/check")
    @CircuitBreaker(name = "inventoryServiceCircuitBreaker", fallbackMethod = "fallbackForInventoryService")
    ApiResponse checkProductAvailability(@RequestParam("productId") String productId, @RequestParam("quantity") Long quantity);

    default ApiResponse fallbackForInventoryService(String productId, Long quantity, Throwable th) {
        return ApiResponse.error(Constants.INVENTORY_SERVICE_NOT_AVAILABLE, HttpStatus.SERVICE_UNAVAILABLE);
    }
}

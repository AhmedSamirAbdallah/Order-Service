package com.service.order.client;

import com.service.order.common.ApiResponse;
import jakarta.ws.rs.QueryParam;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "inventory-service", url = "${inventory.service.url}")
public interface InventoryClient {

    @GetMapping(path = "/api/inventory/check")
    ApiResponse checkProductAvailability(@RequestParam("productId") String productId, @RequestParam("quantity") Long quantity);

}

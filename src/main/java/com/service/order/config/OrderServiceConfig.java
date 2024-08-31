package com.service.order.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
@ConfigurationProperties(prefix = "order")
@Data
public class OrderServiceConfig {
    private BigDecimal discount;
    private BigDecimal tax;
    private BigDecimal shippingCost;
}

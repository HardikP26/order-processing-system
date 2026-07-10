package com.hardik.orderprocessing.dto;

import com.hardik.orderprocessing.model.OrderItem;

import java.math.BigDecimal;

public record OrderItemResponse(
        Long id,
        String productName,
        Integer quantity,
        BigDecimal price
) {

    public static OrderItemResponse from(OrderItem item) {
        return new OrderItemResponse(item.getId(), item.getProductName(), item.getQuantity(), item.getPrice());
    }
}

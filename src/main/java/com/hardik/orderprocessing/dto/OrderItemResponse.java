package com.hardik.orderprocessing.dto;

import com.hardik.orderprocessing.model.OrderItem;

import java.math.BigDecimal;

public class OrderItemResponse {

    private Long id;
    private String productName;
    private Integer quantity;
    private BigDecimal price;

    public OrderItemResponse() {
    }

    public static OrderItemResponse from(OrderItem item) {
        OrderItemResponse response = new OrderItemResponse();
        response.id = item.getId();
        response.productName = item.getProductName();
        response.quantity = item.getQuantity();
        response.price = item.getPrice();
        return response;
    }

    public Long getId() {
        return id;
    }

    public String getProductName() {
        return productName;
    }

    public Integer getQuantity() {
        return quantity;
    }

    public BigDecimal getPrice() {
        return price;
    }
}

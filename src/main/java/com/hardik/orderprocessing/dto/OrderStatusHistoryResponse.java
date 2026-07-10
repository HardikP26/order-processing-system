package com.hardik.orderprocessing.dto;

import com.hardik.orderprocessing.model.OrderStatus;
import com.hardik.orderprocessing.model.OrderStatusHistory;

import java.time.LocalDateTime;

public class OrderStatusHistoryResponse {

    private OrderStatus fromStatus;
    private OrderStatus toStatus;
    private LocalDateTime changedAt;
    private OrderStatusHistory.Source source;

    public OrderStatusHistoryResponse() {
    }

    public static OrderStatusHistoryResponse from(OrderStatusHistory history) {
        OrderStatusHistoryResponse response = new OrderStatusHistoryResponse();
        response.fromStatus = history.getFromStatus();
        response.toStatus = history.getToStatus();
        response.changedAt = history.getChangedAt();
        response.source = history.getSource();
        return response;
    }

    public OrderStatus getFromStatus() {
        return fromStatus;
    }

    public OrderStatus getToStatus() {
        return toStatus;
    }

    public LocalDateTime getChangedAt() {
        return changedAt;
    }

    public OrderStatusHistory.Source getSource() {
        return source;
    }
}

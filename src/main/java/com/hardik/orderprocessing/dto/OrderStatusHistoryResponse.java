package com.hardik.orderprocessing.dto;

import com.hardik.orderprocessing.model.OrderStatus;
import com.hardik.orderprocessing.model.OrderStatusHistory;

import java.time.LocalDateTime;

public record OrderStatusHistoryResponse(
        OrderStatus fromStatus,
        OrderStatus toStatus,
        LocalDateTime changedAt,
        OrderStatusHistory.Source source
) {

    public static OrderStatusHistoryResponse from(OrderStatusHistory history) {
        return new OrderStatusHistoryResponse(
                history.getFromStatus(),
                history.getToStatus(),
                history.getChangedAt(),
                history.getSource()
        );
    }
}

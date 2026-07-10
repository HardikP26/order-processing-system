package com.hardik.orderprocessing.model.state;

import com.hardik.orderprocessing.model.OrderStatus;

import java.util.Set;

public final class PendingState implements OrderState {

    @Override
    public OrderStatus status() {
        return OrderStatus.PENDING;
    }

    @Override
    public Set<OrderStatus> allowedNextStatuses() {
        return Set.of(OrderStatus.PROCESSING, OrderStatus.CANCELLED);
    }
}

package com.hardik.orderprocessing.model.state;

import com.hardik.orderprocessing.model.OrderStatus;

import java.util.Set;

public final class CancelledState implements OrderState {

    @Override
    public OrderStatus status() {
        return OrderStatus.CANCELLED;
    }

    @Override
    public Set<OrderStatus> allowedNextStatuses() {
        return Set.of();
    }
}

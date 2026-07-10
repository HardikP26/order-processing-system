package com.hardik.orderprocessing.model.state;

import com.hardik.orderprocessing.model.OrderStatus;

import java.util.Set;

public final class DeliveredState implements OrderState {

    @Override
    public OrderStatus status() {
        return OrderStatus.DELIVERED;
    }

    @Override
    public Set<OrderStatus> allowedNextStatuses() {
        return Set.of();
    }
}

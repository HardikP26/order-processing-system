package com.hardik.orderprocessing.model.state;

import com.hardik.orderprocessing.model.OrderStatus;

import java.util.Set;

public final class ShippedState implements OrderState {

    @Override
    public OrderStatus status() {
        return OrderStatus.SHIPPED;
    }

    @Override
    public Set<OrderStatus> allowedNextStatuses() {
        return Set.of(OrderStatus.DELIVERED);
    }
}

package com.hardik.orderprocessing.model.state;

import com.hardik.orderprocessing.model.OrderStatus;

import java.util.Set;

/**
 * One implementation per {@link OrderStatus}, each owning the set of statuses it may
 * legally move to. {@link com.hardik.orderprocessing.model.Order#transitionTo} delegates
 * to the state matching its current status rather than consulting a shared table, so
 * adding a new status/rule means adding (or editing) one class, not a central map.
 */
public sealed interface OrderState
        permits PendingState, ProcessingState, ShippedState, DeliveredState, CancelledState {

    OrderStatus status();

    Set<OrderStatus> allowedNextStatuses();

    default boolean canTransitionTo(OrderStatus target) {
        return allowedNextStatuses().contains(target);
    }
}

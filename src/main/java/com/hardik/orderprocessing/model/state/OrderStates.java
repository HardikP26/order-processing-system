package com.hardik.orderprocessing.model.state;

import com.hardik.orderprocessing.model.OrderStatus;

import java.util.EnumMap;
import java.util.Map;

/**
 * Maps each {@link OrderStatus} to its singleton {@link OrderState} instance. The enum
 * remains the persisted representation (a plain @Enumerated(STRING) column); this is
 * purely the lookup from "persisted status" to "behavior for that status".
 */
public final class OrderStates {

    private static final Map<OrderStatus, OrderState> STATES = new EnumMap<>(OrderStatus.class);

    static {
        register(new PendingState());
        register(new ProcessingState());
        register(new ShippedState());
        register(new DeliveredState());
        register(new CancelledState());
    }

    private OrderStates() {
    }

    private static void register(OrderState state) {
        STATES.put(state.status(), state);
    }

    public static OrderState forStatus(OrderStatus status) {
        OrderState state = STATES.get(status);
        if (state == null) {
            throw new IllegalStateException("No OrderState registered for status " + status);
        }
        return state;
    }
}

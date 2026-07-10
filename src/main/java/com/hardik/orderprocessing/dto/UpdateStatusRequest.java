package com.hardik.orderprocessing.dto;

import com.hardik.orderprocessing.model.OrderStatus;
import jakarta.validation.constraints.NotNull;

public class UpdateStatusRequest {

    @NotNull(message = "status is required")
    private OrderStatus status;

    public UpdateStatusRequest() {
    }

    public OrderStatus getStatus() {
        return status;
    }

    public void setStatus(OrderStatus status) {
        this.status = status;
    }
}

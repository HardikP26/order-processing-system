package com.hardik.orderprocessing.dto;

import com.hardik.orderprocessing.model.OrderStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateStatusRequest(
        @NotNull(message = "status is required")
        OrderStatus status
) {
}

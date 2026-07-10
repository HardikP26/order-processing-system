package com.hardik.orderprocessing.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public record CreateOrderRequest(
        @NotBlank(message = "customerName is required")
        @Size(max = 255, message = "customerName must be at most 255 characters")
        String customerName,

        @NotEmpty(message = "order must contain at least one item")
        @Valid
        List<OrderItemRequest> items
) {
}

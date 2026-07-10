package com.hardik.orderprocessing.controller;

import com.hardik.orderprocessing.dto.CreateOrderRequest;
import com.hardik.orderprocessing.dto.OrderResponse;
import com.hardik.orderprocessing.dto.OrderStatusHistoryResponse;
import com.hardik.orderprocessing.dto.UpdateStatusRequest;
import com.hardik.orderprocessing.model.OrderStatus;
import com.hardik.orderprocessing.service.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/orders")
@Tag(name = "Orders", description = "Order creation, retrieval, status transitions, and cancellation")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @Operation(summary = "Create an order", description = "Places an order with one or more items. Always starts in PENDING status.")
    @PostMapping
    public ResponseEntity<OrderResponse> createOrder(@Valid @RequestBody CreateOrderRequest request) {
        OrderResponse created = orderService.createOrder(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @Operation(summary = "Get an order by id")
    @GetMapping("/{id}")
    public ResponseEntity<OrderResponse> getOrder(@PathVariable Long id) {
        return ResponseEntity.ok(orderService.getOrder(id));
    }

    @Operation(summary = "List orders", description = "Lists all orders, optionally filtered by status.")
    @GetMapping
    public ResponseEntity<List<OrderResponse>> listOrders(@RequestParam(required = false) OrderStatus status) {
        return ResponseEntity.ok(orderService.listOrders(Optional.ofNullable(status)));
    }

    @Operation(summary = "Get an order's status transition history")
    @GetMapping("/{id}/history")
    public ResponseEntity<List<OrderStatusHistoryResponse>> getOrderHistory(@PathVariable Long id) {
        return ResponseEntity.ok(orderService.getOrderHistory(id));
    }

    @Operation(summary = "Cancel an order", description = "Only allowed while the order is still PENDING.")
    @PutMapping("/{id}/cancel")
    public ResponseEntity<OrderResponse> cancelOrder(@PathVariable Long id) {
        return ResponseEntity.ok(orderService.cancelOrder(id));
    }

    @Operation(summary = "Manually advance an order's status", description = "Enforces the same legal-transition rules as the scheduled job.")
    @PutMapping("/{id}/status")
    public ResponseEntity<OrderResponse> updateStatus(@PathVariable Long id, @Valid @RequestBody UpdateStatusRequest request) {
        return ResponseEntity.ok(orderService.updateStatus(id, request.getStatus()));
    }
}

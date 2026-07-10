package com.hardik.orderprocessing.model;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * Audit trail of every status transition an order goes through. Deliberately has no
 * "actor" field — this system has no authentication/identity concept, so a real actor
 * can't be sourced. "source" instead records which code path drove the change.
 */
@Entity
@Table(name = "order_status_history", indexes = @Index(name = "idx_history_order_id", columnList = "order_id"))
public class OrderStatusHistory {

    public enum Source {
        API,
        SCHEDULER
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = true)
    private OrderStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderStatus toStatus;

    @Column(nullable = false)
    private LocalDateTime changedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Source source;

    public OrderStatusHistory() {
    }

    public OrderStatusHistory(Long orderId, OrderStatus fromStatus, OrderStatus toStatus, Source source) {
        this.orderId = orderId;
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
        this.source = source;
        this.changedAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public Long getOrderId() {
        return orderId;
    }

    public OrderStatus getFromStatus() {
        return fromStatus;
    }

    public OrderStatus getToStatus() {
        return toStatus;
    }

    public LocalDateTime getChangedAt() {
        return changedAt;
    }

    public Source getSource() {
        return source;
    }
}

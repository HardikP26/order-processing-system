package com.hardik.orderprocessing.model;

import com.hardik.orderprocessing.exception.InvalidOrderStateException;
import com.hardik.orderprocessing.model.state.OrderState;
import com.hardik.orderprocessing.model.state.OrderStates;
import jakarta.persistence.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Entity
@EntityListeners(AuditingEntityListener.class)
@Table(name = "orders", indexes = @Index(name = "idx_order_status", columnList = "status"))
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String customerName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderStatus status;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<OrderItem> items = new ArrayList<>();

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @Version
    private Long version;

    public Order() {
    }

    @PrePersist
    protected void onCreate() {
        if (this.status == null) {
            this.status = OrderStatus.PENDING;
        }
    }

    public BigDecimal getTotalAmount() {
        return items.stream()
                .map(item -> item.getPrice().multiply(BigDecimal.valueOf(item.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public void addItem(OrderItem item) {
        items.add(item);
        item.setOrder(this);
    }

    /**
     * Applies a status transition if (and only if) it's legal from the current status,
     * throwing InvalidOrderStateException otherwise. The single gate every transition
     * — manual endpoint or scheduled job — has to pass through. Delegates to the
     * State pattern (com.hardik.orderprocessing.model.state) for the actual rule.
     */
    public void transitionTo(OrderStatus newStatus) {
        OrderState currentState = OrderStates.forStatus(status);
        if (!currentState.canTransitionTo(newStatus)) {
            throw new InvalidOrderStateException(
                    "Cannot transition order %d from %s to %s.".formatted(id, status, newStatus));
        }
        this.status = newStatus;
    }

    public boolean isPending() {
        return status == OrderStatus.PENDING;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getCustomerName() {
        return customerName;
    }

    public void setCustomerName(String customerName) {
        this.customerName = customerName;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public void setStatus(OrderStatus status) {
        this.status = status;
    }

    public List<OrderItem> getItems() {
        return Collections.unmodifiableList(items);
    }

    public void setItems(List<OrderItem> items) {
        this.items = items;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public Long getVersion() {
        return version;
    }
}

package com.hardik.orderprocessing.service;

import com.hardik.orderprocessing.dto.CreateOrderRequest;
import com.hardik.orderprocessing.dto.OrderItemRequest;
import com.hardik.orderprocessing.dto.OrderResponse;
import com.hardik.orderprocessing.exception.InvalidOrderStateException;
import com.hardik.orderprocessing.exception.OrderNotFoundException;
import com.hardik.orderprocessing.model.Order;
import com.hardik.orderprocessing.model.OrderItem;
import com.hardik.orderprocessing.model.OrderStatus;
import com.hardik.orderprocessing.repository.OrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@Transactional
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    /**
     * Legal forward transitions. Anything not listed here is rejected,
     * which is what keeps an order from jumping e.g. PENDING -> DELIVERED directly.
     */
    private static final Map<OrderStatus, EnumSet<OrderStatus>> ALLOWED_TRANSITIONS = new EnumMap<>(OrderStatus.class);

    static {
        ALLOWED_TRANSITIONS.put(OrderStatus.PENDING, EnumSet.of(OrderStatus.PROCESSING, OrderStatus.CANCELLED));
        ALLOWED_TRANSITIONS.put(OrderStatus.PROCESSING, EnumSet.of(OrderStatus.SHIPPED));
        ALLOWED_TRANSITIONS.put(OrderStatus.SHIPPED, EnumSet.of(OrderStatus.DELIVERED));
        ALLOWED_TRANSITIONS.put(OrderStatus.DELIVERED, EnumSet.noneOf(OrderStatus.class));
        ALLOWED_TRANSITIONS.put(OrderStatus.CANCELLED, EnumSet.noneOf(OrderStatus.class));
    }

    private final OrderRepository orderRepository;

    public OrderService(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    public OrderResponse createOrder(CreateOrderRequest request) {
        Order order = new Order();
        order.setCustomerName(request.getCustomerName());
        for (OrderItemRequest itemRequest : request.getItems()) {
            order.addItem(new OrderItem(itemRequest.getProductName(), itemRequest.getQuantity(), itemRequest.getPrice()));
        }
        Order saved = orderRepository.save(order);
        log.info("Created order {} for customer '{}' with {} item(s)", saved.getId(), saved.getCustomerName(), saved.getItems().size());
        return OrderResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public OrderResponse getOrder(Long id) {
        return OrderResponse.from(findOrderOrThrow(id));
    }

    @Transactional(readOnly = true)
    public List<OrderResponse> listOrders(Optional<OrderStatus> statusFilter) {
        List<Order> orders = statusFilter.map(orderRepository::findByStatus)
                .orElseGet(orderRepository::findAll);
        return orders.stream().map(OrderResponse::from).toList();
    }

    public OrderResponse cancelOrder(Long id) {
        Order order = findOrderOrThrow(id);
        if (order.getStatus() != OrderStatus.PENDING) {
            throw new InvalidOrderStateException(
                    "Order %d cannot be cancelled because it is already %s. Only PENDING orders can be cancelled."
                            .formatted(id, order.getStatus()));
        }
        order.setStatus(OrderStatus.CANCELLED);
        log.info("Cancelled order {}", id);
        return OrderResponse.from(order);
    }

    public OrderResponse updateStatus(Long id, OrderStatus newStatus) {
        Order order = findOrderOrThrow(id);
        OrderStatus current = order.getStatus();
        if (!ALLOWED_TRANSITIONS.getOrDefault(current, EnumSet.noneOf(OrderStatus.class)).contains(newStatus)) {
            throw new InvalidOrderStateException(
                    "Cannot transition order %d from %s to %s.".formatted(id, current, newStatus));
        }
        order.setStatus(newStatus);
        log.info("Order {} transitioned from {} to {}", id, current, newStatus);
        return OrderResponse.from(order);
    }

    /**
     * Used by the scheduled job: promotes every PENDING order to PROCESSING.
     * Returns the number of orders updated, purely so the scheduler can log something useful.
     */
    public int promoteAllPendingToProcessing() {
        List<Order> pending = orderRepository.findByStatus(OrderStatus.PENDING);
        for (Order order : pending) {
            order.setStatus(OrderStatus.PROCESSING);
        }
        return pending.size();
    }

    private Order findOrderOrThrow(Long id) {
        return orderRepository.findById(id).orElseThrow(() -> new OrderNotFoundException(id));
    }
}

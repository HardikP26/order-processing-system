package com.hardik.orderprocessing.service;

import com.hardik.orderprocessing.dto.CreateOrderRequest;
import com.hardik.orderprocessing.dto.OrderItemRequest;
import com.hardik.orderprocessing.dto.OrderResponse;
import com.hardik.orderprocessing.dto.OrderStatusHistoryResponse;
import com.hardik.orderprocessing.exception.OrderNotFoundException;
import com.hardik.orderprocessing.model.Order;
import com.hardik.orderprocessing.model.OrderItem;
import com.hardik.orderprocessing.model.OrderStatus;
import com.hardik.orderprocessing.model.OrderStatusHistory;
import com.hardik.orderprocessing.repository.OrderRepository;
import com.hardik.orderprocessing.repository.OrderStatusHistoryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@Transactional
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    private final OrderRepository orderRepository;
    private final OrderStatusHistoryRepository orderStatusHistoryRepository;

    public OrderService(OrderRepository orderRepository, OrderStatusHistoryRepository orderStatusHistoryRepository) {
        this.orderRepository = orderRepository;
        this.orderStatusHistoryRepository = orderStatusHistoryRepository;
    }

    public OrderResponse createOrder(CreateOrderRequest request) {
        Order order = new Order();
        order.setCustomerName(request.getCustomerName());
        for (OrderItemRequest itemRequest : request.getItems()) {
            order.addItem(new OrderItem(itemRequest.getProductName(), itemRequest.getQuantity(), itemRequest.getPrice()));
        }
        Order saved = orderRepository.save(order);
        recordHistory(saved, null, OrderStatusHistory.Source.API);
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

    @Transactional(readOnly = true)
    public List<OrderStatusHistoryResponse> getOrderHistory(Long id) {
        findOrderOrThrow(id);
        return orderStatusHistoryRepository.findByOrderIdOrderByChangedAtAsc(id).stream()
                .map(OrderStatusHistoryResponse::from)
                .toList();
    }

    public OrderResponse cancelOrder(Long id) {
        Order order = findOrderOrThrow(id);
        OrderStatus previous = order.getStatus();
        order.transitionTo(OrderStatus.CANCELLED);
        recordHistory(order, previous, OrderStatusHistory.Source.API);
        log.info("Cancelled order {}", id);
        return OrderResponse.from(order);
    }

    public OrderResponse updateStatus(Long id, OrderStatus newStatus) {
        Order order = findOrderOrThrow(id);
        OrderStatus previous = order.getStatus();
        order.transitionTo(newStatus);
        recordHistory(order, previous, OrderStatusHistory.Source.API);
        log.info("Order {} transitioned from {} to {}", id, previous, newStatus);
        return OrderResponse.from(order);
    }

    /**
     * Used by the scheduled job: promotes every PENDING order to PROCESSING.
     * Returns the number of orders updated, purely so the scheduler can log/measure something useful.
     */
    public int promoteAllPendingToProcessing() {
        List<Order> pending = orderRepository.findByStatus(OrderStatus.PENDING);
        for (Order order : pending) {
            order.transitionTo(OrderStatus.PROCESSING);
            recordHistory(order, OrderStatus.PENDING, OrderStatusHistory.Source.SCHEDULER);
        }
        return pending.size();
    }

    private void recordHistory(Order order, OrderStatus fromStatus, OrderStatusHistory.Source source) {
        orderStatusHistoryRepository.save(new OrderStatusHistory(order.getId(), fromStatus, order.getStatus(), source));
    }

    private Order findOrderOrThrow(Long id) {
        return orderRepository.findById(id).orElseThrow(() -> new OrderNotFoundException(id));
    }
}

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
import com.hardik.orderprocessing.repository.OrderStatusHistoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderStatusHistoryRepository orderStatusHistoryRepository;

    private OrderService orderService;

    @BeforeEach
    void setUp() {
        orderService = new OrderService(orderRepository, orderStatusHistoryRepository);
    }

    private Order pendingOrderWithId(Long id) {
        Order order = new Order();
        order.setId(id);
        order.setCustomerName("Hardik Parmar");
        order.setStatus(OrderStatus.PENDING);
        order.addItem(new OrderItem("Keyboard", 2, new BigDecimal("50.00")));
        return order;
    }

    @Test
    void createOrder_savesOrderWithItemsAndReturnsResponse() {
        CreateOrderRequest request = new CreateOrderRequest();
        request.setCustomerName("Hardik Parmar");
        OrderItemRequest itemRequest = new OrderItemRequest();
        itemRequest.setProductName("Mechanical Keyboard");
        itemRequest.setQuantity(2);
        itemRequest.setPrice(new BigDecimal("75.50"));
        request.setItems(List.of(itemRequest));

        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> {
            Order toSave = invocation.getArgument(0);
            toSave.setId(1L);
            return toSave;
        });

        OrderResponse response = orderService.createOrder(request);

        ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).save(captor.capture());
        Order savedOrder = captor.getValue();

        assertThat(savedOrder.getCustomerName()).isEqualTo("Hardik Parmar");
        assertThat(savedOrder.getItems()).hasSize(1);
        assertThat(response.getId()).isEqualTo(1L);
        assertThat(response.getTotalAmount()).isEqualByComparingTo("151.00");
    }

    @Test
    void getOrder_whenOrderMissing_throwsOrderNotFoundException() {
        when(orderRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.getOrder(99L))
                .isInstanceOf(OrderNotFoundException.class)
                .hasMessageContaining("99");
    }

    @Test
    void cancelOrder_whenPending_transitionsToCancelled() {
        Order order = pendingOrderWithId(5L);
        when(orderRepository.findById(5L)).thenReturn(Optional.of(order));

        OrderResponse response = orderService.cancelOrder(5L);

        assertThat(response.getStatus()).isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    void cancelOrder_whenNotPending_throwsInvalidOrderStateException() {
        Order order = pendingOrderWithId(6L);
        order.setStatus(OrderStatus.SHIPPED);
        when(orderRepository.findById(6L)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.cancelOrder(6L))
                .isInstanceOf(InvalidOrderStateException.class)
                .hasMessageContaining("SHIPPED");
    }

    @Test
    void updateStatus_withLegalTransition_succeeds() {
        Order order = pendingOrderWithId(7L);
        order.setStatus(OrderStatus.PROCESSING);
        when(orderRepository.findById(7L)).thenReturn(Optional.of(order));

        OrderResponse response = orderService.updateStatus(7L, OrderStatus.SHIPPED);

        assertThat(response.getStatus()).isEqualTo(OrderStatus.SHIPPED);
    }

    @Test
    void updateStatus_withIllegalSkipTransition_throwsInvalidOrderStateException() {
        Order order = pendingOrderWithId(8L);
        order.setStatus(OrderStatus.PENDING);
        when(orderRepository.findById(8L)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.updateStatus(8L, OrderStatus.DELIVERED))
                .isInstanceOf(InvalidOrderStateException.class)
                .hasMessageContaining("PENDING")
                .hasMessageContaining("DELIVERED");
    }

    @Test
    void promoteAllPendingToProcessing_movesAllPendingOrders() {
        Order first = pendingOrderWithId(1L);
        Order second = pendingOrderWithId(2L);
        when(orderRepository.findByStatus(OrderStatus.PENDING)).thenReturn(List.of(first, second));

        int updatedCount = orderService.promoteAllPendingToProcessing();

        assertThat(updatedCount).isEqualTo(2);
        assertThat(first.getStatus()).isEqualTo(OrderStatus.PROCESSING);
        assertThat(second.getStatus()).isEqualTo(OrderStatus.PROCESSING);
    }

    @Test
    void listOrders_withStatusFilter_delegatesToFindByStatus() {
        when(orderRepository.findByStatus(OrderStatus.PENDING)).thenReturn(List.of(pendingOrderWithId(3L)));

        List<OrderResponse> responses = orderService.listOrders(Optional.of(OrderStatus.PENDING));

        assertThat(responses).hasSize(1);
        verify(orderRepository, never()).findAll();
    }
}

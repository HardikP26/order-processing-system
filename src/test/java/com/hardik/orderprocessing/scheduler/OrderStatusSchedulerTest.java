package com.hardik.orderprocessing.scheduler;

import com.hardik.orderprocessing.model.Order;
import com.hardik.orderprocessing.model.OrderStatus;
import com.hardik.orderprocessing.repository.OrderRepository;
import com.hardik.orderprocessing.service.OrderService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderStatusSchedulerTest {

    @Mock
    private OrderRepository orderRepository;

    @Test
    void promotePendingOrders_promotesEveryPendingOrderToProcessing() {
        Order pending = new Order();
        pending.setStatus(OrderStatus.PENDING);
        when(orderRepository.findByStatus(OrderStatus.PENDING)).thenReturn(List.of(pending));

        new OrderStatusScheduler(new OrderService(orderRepository)).promotePendingOrders();

        assertThat(pending.getStatus()).isEqualTo(OrderStatus.PROCESSING);
    }

    @Test
    void promotePendingOrders_withNothingPending_stillQueriesWithoutError() {
        when(orderRepository.findByStatus(OrderStatus.PENDING)).thenReturn(List.of());

        new OrderStatusScheduler(new OrderService(orderRepository)).promotePendingOrders();

        verify(orderRepository).findByStatus(OrderStatus.PENDING);
    }
}

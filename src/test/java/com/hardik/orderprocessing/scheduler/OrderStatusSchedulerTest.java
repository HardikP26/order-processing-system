package com.hardik.orderprocessing.scheduler;

import com.hardik.orderprocessing.model.Order;
import com.hardik.orderprocessing.model.OrderStatus;
import com.hardik.orderprocessing.repository.OrderRepository;
import com.hardik.orderprocessing.repository.OrderStatusHistoryRepository;
import com.hardik.orderprocessing.service.OrderService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
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

    @Mock
    private OrderStatusHistoryRepository orderStatusHistoryRepository;

    private OrderStatusScheduler newScheduler() {
        OrderService orderService = new OrderService(orderRepository, orderStatusHistoryRepository);
        return new OrderStatusScheduler(orderService, new SimpleMeterRegistry());
    }

    @Test
    void promotePendingOrders_promotesEveryPendingOrderToProcessing() {
        Order pending = new Order();
        pending.setId(1L);
        pending.setStatus(OrderStatus.PENDING);
        when(orderRepository.findByStatus(OrderStatus.PENDING)).thenReturn(List.of(pending));

        newScheduler().promotePendingOrders();

        assertThat(pending.getStatus()).isEqualTo(OrderStatus.PROCESSING);
    }

    @Test
    void promotePendingOrders_withNothingPending_stillQueriesWithoutError() {
        when(orderRepository.findByStatus(OrderStatus.PENDING)).thenReturn(List.of());

        newScheduler().promotePendingOrders();

        verify(orderRepository).findByStatus(OrderStatus.PENDING);
    }
}

package com.hardik.orderprocessing.service;

import com.hardik.orderprocessing.model.Order;
import com.hardik.orderprocessing.model.OrderItem;
import com.hardik.orderprocessing.model.OrderStatus;
import com.hardik.orderprocessing.repository.OrderRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.OptimisticLockingFailureException;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifies the @Version field on Order actually catches the race it's meant to catch:
 * a scheduler run and a customer's cancel request both reading the same order, then both
 * trying to write back based on stale data.
 */
@SpringBootTest
class OrderConcurrencyTest {

    @Autowired
    private OrderRepository orderRepository;

    @Test
    void concurrentUpdatesToSameOrder_secondWriteFailsWithOptimisticLockException() {
        Order order = new Order();
        order.setCustomerName("Race Condition Test");
        order.setStatus(OrderStatus.PENDING);
        order.addItem(new OrderItem("Widget", 1, new BigDecimal("9.99")));
        Long id = orderRepository.saveAndFlush(order).getId();

        // Simulate two concurrent readers (e.g. the scheduler and a cancel request)
        // each loading their own copy of the same row.
        Order copyA = orderRepository.findById(id).orElseThrow();
        Order copyB = orderRepository.findById(id).orElseThrow();

        copyA.transitionTo(OrderStatus.PROCESSING);
        orderRepository.saveAndFlush(copyA);

        copyB.transitionTo(OrderStatus.CANCELLED);
        assertThatThrownBy(() -> orderRepository.saveAndFlush(copyB))
                .isInstanceOf(OptimisticLockingFailureException.class);
    }
}

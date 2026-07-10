package com.hardik.orderprocessing.repository;

import com.hardik.orderprocessing.model.Order;
import com.hardik.orderprocessing.model.OrderItem;
import com.hardik.orderprocessing.model.OrderStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class OrderRepositoryTest {

    @Autowired
    private OrderRepository orderRepository;

    private Order newOrder(String customerName, OrderStatus status) {
        Order order = new Order();
        order.setCustomerName(customerName);
        order.setStatus(status);
        order.addItem(new OrderItem("Widget", 1, new BigDecimal("9.99")));
        return order;
    }

    @Test
    void findByStatus_returnsOnlyMatchingOrders() {
        orderRepository.save(newOrder("Alice", OrderStatus.PENDING));
        orderRepository.save(newOrder("Bob", OrderStatus.SHIPPED));
        orderRepository.save(newOrder("Carol", OrderStatus.PENDING));

        List<Order> pending = orderRepository.findByStatus(OrderStatus.PENDING);

        assertThat(pending).hasSize(2)
                .extracting(Order::getCustomerName)
                .containsExactlyInAnyOrder("Alice", "Carol");
    }

    @Test
    void save_persistsItemsAndComputesTotalAmountOnReload() {
        Order saved = orderRepository.saveAndFlush(newOrder("Dave", OrderStatus.PENDING));

        Order reloaded = orderRepository.findById(saved.getId()).orElseThrow();

        assertThat(reloaded.getItems()).hasSize(1);
        assertThat(reloaded.getTotalAmount()).isEqualByComparingTo("9.99");
        assertThat(reloaded.getCreatedAt()).isNotNull();
        assertThat(reloaded.getUpdatedAt()).isNotNull();
    }
}

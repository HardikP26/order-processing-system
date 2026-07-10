package com.hardik.orderprocessing.repository;

import com.hardik.orderprocessing.model.Order;
import com.hardik.orderprocessing.model.OrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OrderRepository extends JpaRepository<Order, Long> {

    List<Order> findByStatus(OrderStatus status);
}

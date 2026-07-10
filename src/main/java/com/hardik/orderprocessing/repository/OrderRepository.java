package com.hardik.orderprocessing.repository;

import com.hardik.orderprocessing.model.Order;
import com.hardik.orderprocessing.model.OrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long> {

    @Override
    @EntityGraph(attributePaths = "items")
    Optional<Order> findById(Long id);

    @Override
    @EntityGraph(attributePaths = "items")
    List<Order> findAll();

    @Override
    @EntityGraph(attributePaths = "items")
    Page<Order> findAll(Pageable pageable);

    @EntityGraph(attributePaths = "items")
    List<Order> findByStatus(OrderStatus status);

    @EntityGraph(attributePaths = "items")
    Page<Order> findByStatus(OrderStatus status, Pageable pageable);
}

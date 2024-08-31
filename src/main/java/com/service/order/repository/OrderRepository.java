package com.service.order.repository;

import com.service.order.model.entity.Orders;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface OrderRepository extends JpaRepository<Orders, Long> {
    Boolean existsByOrderNumber(String orderNumber);

    @Query(value = "select nextval('order_number_seq')", nativeQuery = true)
    Long findNextValOfSequence();
}

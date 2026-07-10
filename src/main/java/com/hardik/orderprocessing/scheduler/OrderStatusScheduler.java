package com.hardik.orderprocessing.scheduler;

import com.hardik.orderprocessing.service.OrderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class OrderStatusScheduler {

    private static final Logger log = LoggerFactory.getLogger(OrderStatusScheduler.class);

    private final OrderService orderService;

    public OrderStatusScheduler(OrderService orderService) {
        this.orderService = orderService;
    }

    /**
     * Every 5 minutes, promote every PENDING order to PROCESSING.
     * fixedRate (not fixedDelay) so the cadence stays every 5 minutes
     * regardless of how long a given run takes.
     */
    @Scheduled(fixedRateString = "${order.pending-promotion.interval-ms:300000}")
    public void promotePendingOrders() {
        int updated = orderService.promoteAllPendingToProcessing();
        if (updated > 0) {
            log.info("Scheduled job: promoted {} order(s) from PENDING to PROCESSING", updated);
        } else {
            log.debug("Scheduled job: no PENDING orders to promote");
        }
    }
}

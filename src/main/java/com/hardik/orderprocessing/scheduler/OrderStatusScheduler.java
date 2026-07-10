package com.hardik.orderprocessing.scheduler;

import com.hardik.orderprocessing.service.OrderService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class OrderStatusScheduler {

    private static final Logger log = LoggerFactory.getLogger(OrderStatusScheduler.class);

    private final OrderService orderService;
    private final Counter promotedOrdersCounter;
    private final Counter failedRunsCounter;
    private final Timer runTimer;

    public OrderStatusScheduler(OrderService orderService, MeterRegistry meterRegistry) {
        this.orderService = orderService;
        this.promotedOrdersCounter = Counter.builder("orders.scheduler.promoted")
                .description("Number of orders promoted from PENDING to PROCESSING by the scheduler")
                .register(meterRegistry);
        this.failedRunsCounter = Counter.builder("orders.scheduler.failures")
                .description("Number of scheduler runs that failed")
                .register(meterRegistry);
        this.runTimer = Timer.builder("orders.scheduler.duration")
                .description("Execution time of the PENDING->PROCESSING promotion job")
                .register(meterRegistry);
    }

    /**
     * Every 5 minutes, promote every PENDING order to PROCESSING.
     * fixedRate (not fixedDelay) so the cadence stays every 5 minutes
     * regardless of how long a given run takes.
     */
    @Scheduled(fixedRateString = "${order.pending-promotion.interval-ms:300000}")
    public void promotePendingOrders() {
        try {
            int updated = runTimer.record(orderService::promoteAllPendingToProcessing);
            promotedOrdersCounter.increment(updated);
            if (updated > 0) {
                log.info("Scheduled job: promoted {} order(s) from PENDING to PROCESSING", updated);
            } else {
                log.debug("Scheduled job: no PENDING orders to promote");
            }
        } catch (RuntimeException ex) {
            failedRunsCounter.increment();
            log.error("Scheduled job: failed to promote PENDING orders", ex);
            throw ex;
        }
    }
}

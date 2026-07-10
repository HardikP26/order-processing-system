package com.hardik.orderprocessing.web;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.local.LocalBucket;
import io.github.bucket4j.local.LocalBucketBuilder;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Basic per-client-IP rate limiting on /api/** using an in-memory token bucket per IP
 * (no Redis needed for a single-instance demo app). Sized conservatively — this exists
 * to demonstrate abuse-protection awareness, not to be a tuned production limiter.
 */
@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private static final int CAPACITY = 60;
    private static final Duration REFILL_PERIOD = Duration.ofMinutes(1);

    private final ConcurrentMap<String, LocalBucket> bucketsByClient = new ConcurrentHashMap<>();

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (!request.getRequestURI().startsWith("/api/")) {
            filterChain.doFilter(request, response);
            return;
        }

        LocalBucket bucket = bucketsByClient.computeIfAbsent(clientKey(request), key -> newBucket());
        if (bucket.tryConsume(1)) {
            filterChain.doFilter(request, response);
        } else {
            response.setStatus(429);
            response.setContentType("application/json");
            response.getWriter().write(
                    "{\"status\":429,\"title\":\"Too Many Requests\",\"detail\":\"Rate limit exceeded, try again later.\"}");
        }
    }

    private LocalBucket newBucket() {
        LocalBucketBuilder builder = io.github.bucket4j.Bucket.builder();
        builder.addLimit(Bandwidth.builder().capacity(CAPACITY).refillGreedy(CAPACITY, REFILL_PERIOD).build());
        return builder.build();
    }

    private String clientKey(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        return (forwardedFor != null && !forwardedFor.isBlank()) ? forwardedFor : request.getRemoteAddr();
    }
}

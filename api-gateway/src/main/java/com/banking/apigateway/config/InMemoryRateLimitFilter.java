package com.banking.apigateway.config;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;
import java.util.concurrent.ConcurrentHashMap;

/** Local token-bucket limiter; keeps the gateway usable without Redis. */
@Component
public class InMemoryRateLimitFilter implements GlobalFilter, Ordered {
    private final ConcurrentHashMap<String, Bucket> buckets = new ConcurrentHashMap<>();

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        int rate = 10;
        int capacity = 20;
        InetSocketAddress remote = exchange.getRequest().getRemoteAddress();
        String ip = remote == null || remote.getAddress() == null ? "unknown" : remote.getAddress().getHostAddress();
        String key = ip + "|banking";
        Bucket bucket = buckets.computeIfAbsent(key, ignored -> new Bucket(capacity, rate));
        if (!bucket.tryConsume()) {
            exchange.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
            return exchange.getResponse().setComplete();
        }
        return chain.filter(exchange);
    }

    @Override public int getOrder() { return Ordered.HIGHEST_PRECEDENCE + 10; }

    private static final class Bucket {
        private final int capacity;
        private final int refillPerSecond;
        private double tokens;
        private long lastRefillNanos = System.nanoTime();
        private Bucket(int capacity, int refillPerSecond) {
            this.capacity = capacity; this.refillPerSecond = refillPerSecond; this.tokens = capacity;
        }
        synchronized boolean tryConsume() {
            long now = System.nanoTime();
            double elapsed = (now - lastRefillNanos) / 1_000_000_000.0;
            tokens = Math.min(capacity, tokens + elapsed * refillPerSecond);
            lastRefillNanos = now;
            if (tokens < 1.0) return false;
            tokens -= 1.0;
            return true;
        }
    }
}

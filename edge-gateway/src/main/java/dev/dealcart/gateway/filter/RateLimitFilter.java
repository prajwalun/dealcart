package dev.dealcart.gateway.filter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Token bucket rate limiter to prevent overload.
 */
@Component
@Order(1)
public class RateLimitFilter extends OncePerRequestFilter {
    private static final Logger logger = LoggerFactory.getLogger(RateLimitFilter.class);
    
    @Value("${rate-limit.enabled}")
    private boolean enabled;
    
    @Value("${rate-limit.qps}")
    private int qps;
    
    private final AtomicLong tokens = new AtomicLong(0);
    private volatile long lastRefillTimestamp = System.currentTimeMillis();
    
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, 
                                   FilterChain filterChain) throws ServletException, IOException {
        
        if (!enabled) {
            filterChain.doFilter(request, response);
            return;
        }
        
        // Refill tokens based on elapsed time
        long now = System.currentTimeMillis();
        long elapsed = now - lastRefillTimestamp;
        
        if (elapsed > 1000) { // Refill every second
            long tokensToAdd = (elapsed / 1000) * qps;
            long currentTokens = tokens.get();
            long newTokens = Math.min(currentTokens + tokensToAdd, qps * 2L); // Max burst = 2x QPS
            tokens.set(newTokens);
            lastRefillTimestamp = now;
        }
        
        // Try to acquire a token
        if (tokens.get() > 0 && tokens.decrementAndGet() >= 0) {
            filterChain.doFilter(request, response);
        } else {
            // Rate limit exceeded
            tokens.incrementAndGet(); // Return the token
            logger.warn("Rate limit exceeded for {}", request.getRequestURI());
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType("application/json");
            response.getWriter().write("{\"error\":\"Rate limit exceeded\",\"retry_after_seconds\":1}");
        }
    }
}


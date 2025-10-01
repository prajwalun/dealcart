package dev.dealcart.pricing;

import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.concurrent.Executors;

/**
 * Simple HTTP server to expose traffic metrics for auto-scaling.
 * Provides JSON endpoint with RPS, latency, and error rate data.
 */
public class MetricsHttpServer {
    private static final Logger logger = LoggerFactory.getLogger(MetricsHttpServer.class);
    private final HttpServer server;
    private final VendorPricingServer pricingServer;
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    public MetricsHttpServer(int port, VendorPricingServer pricingServer) throws IOException {
        this.pricingServer = pricingServer;
        this.server = HttpServer.create(new InetSocketAddress(port), 0);
        
        // Add metrics endpoint
        server.createContext("/metrics", new MetricsHandler());
        server.createContext("/health", new HealthHandler());
        
        // Use thread pool for handling requests
        server.setExecutor(Executors.newFixedThreadPool(2));
        
        logger.info("MetricsHttpServer created on port {}", port);
    }
    
    public void start() {
        server.start();
        logger.info("MetricsHttpServer started");
    }
    
    public void stop() {
        server.stop(5);
        logger.info("MetricsHttpServer stopped");
    }
    
    private class MetricsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            try {
                // Get current traffic metrics
                VendorPricingServer.TrafficMetrics metrics = pricingServer.getCurrentTrafficMetrics();
                
                // Create response object
                MetricsResponse response = new MetricsResponse(
                    metrics.rps,
                    metrics.errorRate,
                    metrics.p50Latency,
                    metrics.p95Latency,
                    metrics.p99Latency,
                    System.currentTimeMillis()
                );
                
                // Convert to JSON
                String jsonResponse = objectMapper.writeValueAsString(response);
                
                // Set headers
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
                
                // Send response
                exchange.sendResponseHeaders(200, jsonResponse.getBytes().length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(jsonResponse.getBytes());
                }
                
            } catch (Exception e) {
                logger.error("Error handling metrics request", e);
                String errorResponse = "{\"error\": \"Internal server error\"}";
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(500, errorResponse.getBytes().length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(errorResponse.getBytes());
                }
            }
        }
    }
    
    private class HealthHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String response = "OK";
            exchange.getResponseHeaders().set("Content-Type", "text/plain");
            exchange.sendResponseHeaders(200, response.getBytes().length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(response.getBytes());
            }
        }
    }
    
    // Response data class
    public static class MetricsResponse {
        public final double rps;
        public final double errorRate;
        public final long p50Latency;
        public final long p95Latency;
        public final long p99Latency;
        public final long timestamp;
        
        public MetricsResponse(double rps, double errorRate, long p50Latency, long p95Latency, long p99Latency, long timestamp) {
            this.rps = rps;
            this.errorRate = errorRate;
            this.p50Latency = p50Latency;
            this.p95Latency = p95Latency;
            this.p99Latency = p99Latency;
            this.timestamp = timestamp;
        }
    }
}

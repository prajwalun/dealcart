package dev.dealcart.pricing;

import dev.dealcart.v1.*;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicReference;
import java.time.Instant;
import java.time.Duration;

/**
 * Vendor pricing aggregator that fans out to multiple vendor backends and streams quotes as they arrive.
 * 
 * Environment variables:
 * - VENDORS: Comma-separated list of "host:port:name" vendor endpoints
 * - PORT: Port to listen on (default: 9100)
 * 
 * Example VENDORS: "localhost:9101:FastVendor,localhost:9102:SlowVendor"
 */
public class VendorPricingServer {
    private static final Logger logger = LoggerFactory.getLogger(VendorPricingServer.class);
    
    private final int port;
    private final List<VendorEndpoint> vendorEndpoints;
    private final AdaptiveThreadPool adaptivePool;
    private final Object streamLock = new Object();
    private Server server;
    private MetricsHttpServer metricsServer;
    
    // Traffic metrics for auto-scaling
    private final AtomicLong totalRequests = new AtomicLong(0);
    private final AtomicLong totalErrors = new AtomicLong(0);
    private final ConcurrentLinkedQueue<RequestMetrics> recentRequests = new ConcurrentLinkedQueue<>();
    private final AtomicReference<Instant> lastMetricsReset = new AtomicReference<>(Instant.now());
    
    // Metrics window (last 60 seconds)
    private static final Duration METRICS_WINDOW = Duration.ofSeconds(60);
    private static final int MAX_METRICS_SAMPLES = 1000;
    
    // Request metrics for traffic analysis
    private static class RequestMetrics {
        final Instant timestamp;
        final long latencyMs;
        final boolean success;
        
        RequestMetrics(long latencyMs, boolean success) {
            this.timestamp = Instant.now();
            this.latencyMs = latencyMs;
            this.success = success;
        }
    }

    public VendorPricingServer(int port, List<VendorEndpoint> vendorEndpoints, AdaptiveThreadPool adaptivePool) {
        this.port = port;
        this.vendorEndpoints = vendorEndpoints;
        this.adaptivePool = adaptivePool;
    }

    public void start() throws IOException {
        server = ServerBuilder.forPort(port)
                .addService(new VendorPricingImpl())
                .build()
                .start();
        
        // Start adaptive thread pool controller
        adaptivePool.startController();
        
        // Start metrics HTTP server
        try {
            metricsServer = new MetricsHttpServer(port + 1000, this); // Use port + 1000 for metrics
            metricsServer.start();
            logger.info("Metrics server started on port {}", port + 1000);
        } catch (IOException e) {
            logger.warn("Failed to start metrics server: {}", e.getMessage());
        }
        
        logger.info("VendorPricingServer started on port {} with {} vendor endpoints", 
                   port, vendorEndpoints.size());
        logger.info("Autoscaler enabled: pool size will adapt based on latency");
        logger.info("Traffic metrics available at http://localhost:{}/metrics", port + 1000);
        
        // Add shutdown hook
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            logger.info("Shutting down VendorPricingServer...");
            try {
                VendorPricingServer.this.stop();
            } catch (InterruptedException e) {
                logger.error("Error during shutdown", e);
                Thread.currentThread().interrupt();
            }
        }));
    }

    public void stop() throws InterruptedException {
        if (server != null) {
            server.shutdown().awaitTermination(5, TimeUnit.SECONDS);
        }
        if (adaptivePool != null) {
            adaptivePool.stop();
        }
        if (metricsServer != null) {
            metricsServer.stop();
        }
    }
    
    // Record request metrics for traffic analysis
    public void recordRequest(long latencyMs, boolean success) {
        totalRequests.incrementAndGet();
        if (!success) {
            totalErrors.incrementAndGet();
        }
        
        // Add to recent requests queue
        recentRequests.offer(new RequestMetrics(latencyMs, success));
        
        // Clean up old metrics (keep only last 60 seconds)
        cleanupOldMetrics();
    }
    
    private void cleanupOldMetrics() {
        Instant cutoff = Instant.now().minus(METRICS_WINDOW);
        while (recentRequests.size() > MAX_METRICS_SAMPLES) {
            recentRequests.poll(); // Remove oldest
        }
        
        // Remove metrics older than 60 seconds
        recentRequests.removeIf(metrics -> metrics.timestamp.isBefore(cutoff));
    }
    
    // Get current traffic metrics for auto-scaling decisions
    public TrafficMetrics getCurrentTrafficMetrics() {
        Instant now = Instant.now();
        Instant cutoff = now.minus(METRICS_WINDOW);
        
        // Filter recent requests (last 60 seconds)
        List<RequestMetrics> recent = new ArrayList<>();
        for (RequestMetrics metrics : recentRequests) {
            if (metrics.timestamp.isAfter(cutoff)) {
                recent.add(metrics);
            }
        }
        
        if (recent.isEmpty()) {
            return new TrafficMetrics(0, 0, 0, 0, 0);
        }
        
        // Calculate RPS (requests per second)
        double rps = recent.size() / 60.0;
        
        // Calculate error rate
        long errors = recent.stream().mapToLong(m -> m.success ? 0 : 1).sum();
        double errorRate = (double) errors / recent.size() * 100.0;
        
        // Calculate latency percentiles
        List<Long> latencies = recent.stream()
            .mapToLong(m -> m.latencyMs)
            .sorted()
            .boxed()
            .collect(java.util.stream.Collectors.toList());
        
        long p50 = latencies.get((int) (latencies.size() * 0.5));
        long p95 = latencies.get((int) (latencies.size() * 0.95));
        long p99 = latencies.get((int) (latencies.size() * 0.99));
        
        return new TrafficMetrics(rps, errorRate, p50, p95, p99);
    }
    
    // Traffic metrics data class
    public static class TrafficMetrics {
        public final double rps;
        public final double errorRate;
        public final long p50Latency;
        public final long p95Latency;
        public final long p99Latency;
        
        public TrafficMetrics(double rps, double errorRate, long p50Latency, long p95Latency, long p99Latency) {
            this.rps = rps;
            this.errorRate = errorRate;
            this.p50Latency = p50Latency;
            this.p95Latency = p95Latency;
            this.p99Latency = p99Latency;
        }
        
        @Override
        public String toString() {
            return String.format("RPS=%.1f, ErrorRate=%.1f%%, P50=%dms, P95=%dms, P99=%dms", 
                               rps, errorRate, p50Latency, p95Latency, p99Latency);
        }
    }

    public void blockUntilShutdown() throws InterruptedException {
        if (server != null) {
            server.awaitTermination();
        }
    }

    /**
     * Implementation of the VendorPricing gRPC service.
     */
    private class VendorPricingImpl extends VendorPricingGrpc.VendorPricingImplBase {
        
        @Override
        public void streamQuotes(QuoteRequest request, StreamObserver<PriceQuote> responseObserver) {
            logger.info("Received stream request for product: {} (quantity: {})", 
                       request.getProductId(), request.getQuantity());
            
            if (vendorEndpoints.isEmpty()) {
                logger.warn("No vendor endpoints configured, closing stream");
                responseObserver.onCompleted();
                return;
            }
            
            // Use CountDownLatch to track completion of all vendor calls
            CountDownLatch latch = new CountDownLatch(vendorEndpoints.size());
            AtomicInteger completedCount = new AtomicInteger(0);
            AtomicInteger errorCount = new AtomicInteger(0);
            
            // Fan out to all vendor endpoints using CompletableFuture with adaptive thread pool
            for (VendorEndpoint endpoint : vendorEndpoints) {
                CompletableFuture.runAsync(() -> {
                    try {
                        callVendorAndStreamQuote(endpoint, request, responseObserver, latch, completedCount, errorCount);
                    } catch (Exception e) {
                        logger.error("Error calling vendor {}: {}", endpoint.name, e.getMessage());
                        errorCount.incrementAndGet();
                        latch.countDown();
                    }
                }, adaptivePool.executor());
            }
            
            // Wait for all vendor calls to complete (or timeout)
            try {
                boolean completed = latch.await(10, TimeUnit.SECONDS);
                if (!completed) {
                    logger.warn("Timeout waiting for vendor responses");
                }
                
                logger.info("Stream completed: {} successful, {} errors", 
                           completedCount.get(), errorCount.get());
                
            } catch (InterruptedException e) {
                logger.error("Interrupted while waiting for vendor responses", e);
                Thread.currentThread().interrupt();
            } finally {
                responseObserver.onCompleted();
            }
        }
    }

    /**
     * Call a single vendor and stream the quote if successful.
     */
    private void callVendorAndStreamQuote(VendorEndpoint endpoint, QuoteRequest request, 
                                        StreamObserver<PriceQuote> responseObserver,
                                        CountDownLatch latch, AtomicInteger completedCount, 
                                        AtomicInteger errorCount) {
        ManagedChannel channel = null;
        // Use monotonic time to avoid clock jumps during tests
        long startNanos = System.nanoTime();
        
        try {
            // Create gRPC channel to vendor using Netty transport explicitly
            channel = NettyChannelBuilder.forAddress(endpoint.host, endpoint.port)
                    .usePlaintext()
                    .defaultLoadBalancingPolicy("pick_first")
                    .build();
            
            // Create stub with deadline and call GetQuote
            VendorBackendGrpc.VendorBackendBlockingStub stub = VendorBackendGrpc.newBlockingStub(channel)
                    .withDeadlineAfter(1500, TimeUnit.MILLISECONDS);
            
            logger.debug("Calling vendor {} at {}:{}", endpoint.name, endpoint.host, endpoint.port);
            
            PriceQuote quote = stub.getQuote(request);
            
            // Record latency for autoscaling (convert nanos to millis)
            long latencyMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);
            adaptivePool.recordLatency(latencyMs);
            
            // Record traffic metrics for auto-scaling
            recordRequest(latencyMs, true);
            
            // Stream the quote to the client (synchronized to prevent race conditions)
            synchronized (streamLock) {
                responseObserver.onNext(quote);
            }
            completedCount.incrementAndGet();
            
            logger.debug("Received quote from {} in {}ms: {} {}", 
                        endpoint.name, latencyMs, quote.getPrice().getAmountCents() / 100.0, 
                        quote.getPrice().getCurrencyCode());
            
        } catch (Exception e) {
            // Still record latency even on failure
            long latencyMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);
            adaptivePool.recordLatency(latencyMs);
            
            // Record traffic metrics for auto-scaling (failure)
            recordRequest(latencyMs, false);
            
            logger.error("Failed to get quote from vendor {} after {}ms: {}", 
                        endpoint.name, latencyMs, e.getMessage());
            errorCount.incrementAndGet();
        } finally {
            // Clean up channel
            if (channel != null) {
                try {
                    channel.shutdown();
                    channel.awaitTermination(1, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            latch.countDown();
        }
    }

    /**
     * Represents a vendor endpoint configuration.
     */
    public static class VendorEndpoint {
        public final String host;
        public final int port;
        public final String name;
        
        public VendorEndpoint(String host, int port, String name) {
            this.host = host;
            this.port = port;
            this.name = name;
        }
        
        @Override
        public String toString() {
            return String.format("%s:%d:%s", host, port, name);
        }
    }

    /**
     * Parse VENDORS environment variable into list of VendorEndpoint objects.
     * Format: "host1:port1:name1,host2:port2:name2"
     */
    public static List<VendorEndpoint> parseVendorEndpoints(String vendorsEnv) {
        List<VendorEndpoint> endpoints = new ArrayList<>();
        
        if (vendorsEnv == null || vendorsEnv.trim().isEmpty()) {
            logger.warn("VENDORS environment variable not set, no vendor endpoints configured");
            return endpoints;
        }
        
        String[] vendorStrings = vendorsEnv.split(",");
        for (String vendorString : vendorStrings) {
            vendorString = vendorString.trim();
            if (vendorString.isEmpty()) continue;
            
            String[] parts = vendorString.split(":");
            if (parts.length != 3) {
                logger.error("Invalid vendor format '{}', expected 'host:port:name'", vendorString);
                continue;
            }
            
            try {
                String host = parts[0].trim();
                int port = Integer.parseInt(parts[1].trim());
                String name = parts[2].trim();
                
                endpoints.add(new VendorEndpoint(host, port, name));
                logger.info("Added vendor endpoint: {}:{}:{}", host, port, name);
                
            } catch (NumberFormatException e) {
                logger.error("Invalid port number in vendor '{}': {}", vendorString, parts[1]);
            }
        }
        
        return endpoints;
    }

    public static void main(String[] args) throws IOException, InterruptedException {
        // Read configuration from environment variables
        int port = 9100; // default port
        String portEnv = System.getenv("PORT");
        if (portEnv != null && !portEnv.trim().isEmpty()) {
            try {
                port = Integer.parseInt(portEnv);
            } catch (NumberFormatException e) {
                logger.warn("Invalid PORT environment variable: {}, using default {}", portEnv, port);
            }
        }
        
        String vendorsEnv = System.getenv("VENDORS");
        List<VendorEndpoint> vendorEndpoints = parseVendorEndpoints(vendorsEnv);
        
        if (vendorEndpoints.isEmpty()) {
            logger.error("No valid vendor endpoints configured. Set VENDORS environment variable.");
            System.exit(1);
        }
        
        // Read autoscaling configuration
        int adaptiveMin = parseEnvInt("ADAPTIVE_MIN", 8);
        int adaptiveMax = parseEnvInt("ADAPTIVE_MAX", 64);
        int adaptiveStep = parseEnvInt("ADAPTIVE_STEP", 8);
        long targetP95Ms = parseEnvLong("TARGET_P95_MS", 250);
        long lowerP95Ms = parseEnvLong("LOWER_P95_MS", 200);
        int latWindow = parseEnvInt("LAT_WINDOW", 2000);
        
        logger.info("Starting VendorPricingServer with port={}, vendors={}", port, vendorEndpoints);
        logger.info("Autoscaling config: min={}, max={}, step={}, targetP95={}ms, lowerP95={}ms, window={}",
                   adaptiveMin, adaptiveMax, adaptiveStep, targetP95Ms, lowerP95Ms, latWindow);
        
        // Create adaptive thread pool
        AdaptiveThreadPool adaptivePool = new AdaptiveThreadPool(
            adaptiveMin, adaptiveMax, adaptiveStep, targetP95Ms, lowerP95Ms, latWindow
        );
        
        VendorPricingServer server = new VendorPricingServer(port, vendorEndpoints, adaptivePool);
        server.start();
        server.blockUntilShutdown();
    }
    
    private static int parseEnvInt(String name, int defaultValue) {
        String value = System.getenv(name);
        if (value != null && !value.trim().isEmpty()) {
            try {
                return Integer.parseInt(value);
            } catch (NumberFormatException e) {
                logger.warn("Invalid {} environment variable: {}, using default {}", name, value, defaultValue);
            }
        }
        return defaultValue;
    }
    
    private static long parseEnvLong(String name, long defaultValue) {
        String value = System.getenv(name);
        if (value != null && !value.trim().isEmpty()) {
            try {
                return Long.parseLong(value);
            } catch (NumberFormatException e) {
                logger.warn("Invalid {} environment variable: {}, using default {}", name, value, defaultValue);
            }
        }
        return defaultValue;
    }
}

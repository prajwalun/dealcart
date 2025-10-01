package dev.dealcart.vendor;

import dev.dealcart.v1.*;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Random;
import java.util.concurrent.TimeUnit;

/**
 * Mock vendor backend that simulates real vendor behavior with configurable latency and pricing.
 * 
 * Environment variables:
 * - VENDOR_NAME: Name of this vendor (default: "MockVendor")
 * - PORT: Port to listen on (default: 50051)
 */
public class VendorMockServer {
    private static final Logger logger = LoggerFactory.getLogger(VendorMockServer.class);
    private static final Random random = new Random();
    
    private final String vendorName;
    private final int port;
    private Server server;

    public VendorMockServer(String vendorName, int port) {
        this.vendorName = vendorName;
        this.port = port;
    }

    public void start() throws IOException {
        server = ServerBuilder.forPort(port)
                .addService(new VendorBackendImpl())
                .build()
                .start();
        
        logger.info("VendorMockServer started: {} on port {}", vendorName, port);
        
        // Add shutdown hook
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            logger.info("Shutting down VendorMockServer...");
            try {
                VendorMockServer.this.stop();
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
    }

    public void blockUntilShutdown() throws InterruptedException {
        if (server != null) {
            server.awaitTermination();
        }
    }

    /**
     * Implementation of the VendorBackend gRPC service.
     */
    private class VendorBackendImpl extends VendorBackendGrpc.VendorBackendImplBase {
        
        @Override
        public void getQuote(QuoteRequest request, StreamObserver<PriceQuote> responseObserver) {
            logger.info("Received quote request for product: {} (quantity: {})", 
                       request.getProductId(), request.getQuantity());
            
            try {
                // Simulate vendor processing time with realistic latency distribution
                // p50 ~80ms, p95 ~220ms (exponential distribution)
                long latencyMs = generateLatency();
                Thread.sleep(latencyMs);
                
                // Generate a realistic price quote
                PriceQuote quote = generatePriceQuote(request);
                
                logger.info("Generated quote for {}: {} {} (latency: {}ms)", 
                           request.getProductId(), quote.getPrice().getAmountCents() / 100.0, 
                           quote.getPrice().getCurrencyCode(), latencyMs);
                
                responseObserver.onNext(quote);
                responseObserver.onCompleted();
                
            } catch (InterruptedException e) {
                logger.error("Quote generation interrupted", e);
                Thread.currentThread().interrupt();
                responseObserver.onError(e);
            } catch (Exception e) {
                logger.error("Error generating quote", e);
                responseObserver.onError(e);
            }
        }
    }

    /**
     * Generate realistic latency using exponential distribution.
     * Target: p50 ~80ms, p95 ~220ms
     */
    private long generateLatency() {
        // Exponential distribution with mean ~80ms
        // This gives us p50 ~55ms, p95 ~240ms (close to target)
        double lambda = 1.0 / 80.0; // rate parameter
        double exponential = -Math.log(1.0 - random.nextDouble()) / lambda;
        
        // Add some base latency and cap at reasonable maximum
        long latency = Math.round(exponential) + 20; // base 20ms
        return Math.min(latency, 500); // cap at 500ms
    }

    /**
     * Generate a realistic price quote based on the request.
     */
    private PriceQuote generatePriceQuote(QuoteRequest request) {
        String productId = request.getProductId();
        int quantity = request.getQuantity();
        String currencyCode = request.getCurrencyCode().isEmpty() ? "USD" : request.getCurrencyCode();
        
        // Generate base price based on product ID hash (deterministic but varied)
        int basePriceCents = generateBasePrice(productId);
        
        // Add some randomness (±10%)
        double variation = 0.9 + (random.nextDouble() * 0.2); // 0.9 to 1.1
        long finalPriceCents = Math.round(basePriceCents * variation * quantity);
        
        // Generate delivery estimate (1-7 days)
        int estimatedDays = 1 + random.nextInt(7);
        
        // Generate timestamp
        long timestampMs = System.currentTimeMillis();
        
        return PriceQuote.newBuilder()
                .setVendorId(vendorName.toLowerCase().replaceAll("[^a-z0-9]", ""))
                .setProductId(productId)
                .setPrice(Money.newBuilder()
                        .setCurrencyCode(currencyCode)
                        .setAmountCents(finalPriceCents)
                        .build())
                .setEstimatedDays(estimatedDays)
                .setVendorName(vendorName)
                .setTimestampMs(timestampMs)
                .build();
    }

    /**
     * Generate a deterministic base price based on product ID.
     * This ensures the same product always has similar pricing across vendors.
     */
    private int generateBasePrice(String productId) {
        // Use product ID hash to generate consistent base price
        int hash = Math.abs(productId.hashCode());
        
        // Map to reasonable price range ($10-$500)
        int basePrice = 1000 + (hash % 49000); // $10.00 to $500.00 in cents
        
        return basePrice;
    }

    public static void main(String[] args) throws IOException, InterruptedException {
        // Read configuration from environment variables
        String vendorName = System.getenv("VENDOR_NAME");
        if (vendorName == null || vendorName.trim().isEmpty()) {
            vendorName = "MockVendor";
        }
        
        int port = 9101; // default port (matches Docker Compose)
        String portEnv = System.getenv("PORT");
        if (portEnv != null && !portEnv.trim().isEmpty()) {
            try {
                port = Integer.parseInt(portEnv);
            } catch (NumberFormatException e) {
                logger.warn("Invalid PORT environment variable: {}, using default {}", portEnv, port);
            }
        }
        
        logger.info("Starting VendorMockServer with vendorName={}, port={}", vendorName, port);
        
        VendorMockServer server = new VendorMockServer(vendorName, port);
        server.start();
        server.blockUntilShutdown();
    }
}

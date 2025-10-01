package dev.dealcart.vendor;

import dev.dealcart.v1.*;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
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
    
    // Realistic product catalog for load testing with 100K requests
    // Format: keyword -> base price in cents (will add variance per vendor)
    private static final Map<String, Integer> PRODUCT_CATALOG = new HashMap<>();
    static {
        // Electronics (high value, $200-2000)
        PRODUCT_CATALOG.put("laptop", 89900);      // $899 base
        PRODUCT_CATALOG.put("macbook", 129900);    // $1299
        PRODUCT_CATALOG.put("iphone", 79900);      // $799
        PRODUCT_CATALOG.put("ipad", 59900);        // $599
        PRODUCT_CATALOG.put("airpods", 19900);     // $199
        PRODUCT_CATALOG.put("watch", 39900);       // $399
        PRODUCT_CATALOG.put("monitor", 34900);     // $349
        PRODUCT_CATALOG.put("keyboard", 12900);    // $129
        PRODUCT_CATALOG.put("mouse", 7900);        // $79
        PRODUCT_CATALOG.put("webcam", 8900);       // $89
        PRODUCT_CATALOG.put("speaker", 14900);     // $149
        PRODUCT_CATALOG.put("headphones", 24900);  // $249
        PRODUCT_CATALOG.put("camera", 89900);      // $899
        PRODUCT_CATALOG.put("drone", 119900);      // $1199
        PRODUCT_CATALOG.put("tablet", 49900);      // $499
        
        // Home & Kitchen ($50-500)
        PRODUCT_CATALOG.put("blender", 7900);      // $79
        PRODUCT_CATALOG.put("toaster", 4900);      // $49
        PRODUCT_CATALOG.put("microwave", 12900);   // $129
        PRODUCT_CATALOG.put("vacuum", 24900);      // $249
        PRODUCT_CATALOG.put("coffee", 9900);       // $99 (coffee maker)
        PRODUCT_CATALOG.put("airfryer", 14900);    // $149
        PRODUCT_CATALOG.put("mixer", 6900);        // $69
        PRODUCT_CATALOG.put("kettle", 5900);       // $59
        PRODUCT_CATALOG.put("toaster-oven", 8900); // $89
        
        // Sports & Outdoors ($30-300)
        PRODUCT_CATALOG.put("bike", 39900);        // $399
        PRODUCT_CATALOG.put("yoga-mat", 2900);     // $29
        PRODUCT_CATALOG.put("dumbbell", 4900);     // $49
        PRODUCT_CATALOG.put("treadmill", 59900);   // $599
        PRODUCT_CATALOG.put("tent", 12900);        // $129
        PRODUCT_CATALOG.put("backpack", 7900);     // $79
        PRODUCT_CATALOG.put("sleeping-bag", 8900); // $89
        PRODUCT_CATALOG.put("hiking-boots", 14900);// $149
        
        // Books & Media ($10-50)
        PRODUCT_CATALOG.put("book", 1999);         // $19.99
        PRODUCT_CATALOG.put("textbook", 4999);     // $49.99
        PRODUCT_CATALOG.put("ebook", 999);         // $9.99
        
        // Clothing ($20-200)
        PRODUCT_CATALOG.put("jacket", 12900);      // $129
        PRODUCT_CATALOG.put("shoes", 8900);        // $89
        PRODUCT_CATALOG.put("jeans", 5900);        // $59
        PRODUCT_CATALOG.put("shirt", 2900);        // $29
        PRODUCT_CATALOG.put("hoodie", 4900);       // $49
        
        // Toys & Games ($15-100)
        PRODUCT_CATALOG.put("lego", 5900);         // $59
        PRODUCT_CATALOG.put("puzzle", 1999);       // $19.99
        PRODUCT_CATALOG.put("boardgame", 3999);    // $39.99
        PRODUCT_CATALOG.put("controller", 5900);   // $59
        
        // Office Supplies ($10-100)
        PRODUCT_CATALOG.put("desk", 19900);        // $199
        PRODUCT_CATALOG.put("chair", 24900);       // $249
        PRODUCT_CATALOG.put("lamp", 4900);         // $49
        PRODUCT_CATALOG.put("organizer", 2900);    // $29
        
        // Beauty & Personal Care ($15-150)
        PRODUCT_CATALOG.put("perfume", 7900);      // $79
        PRODUCT_CATALOG.put("shampoo", 1499);      // $14.99
        PRODUCT_CATALOG.put("razor", 2999);        // $29.99
        PRODUCT_CATALOG.put("trimmer", 4900);      // $49
        
        // Default fallback (medium value)
        PRODUCT_CATALOG.put("default", 4999);      // $49.99
    }
    
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
     * Uses realistic product catalog for known items, hash-based for unknown.
     */
    private int generateBasePrice(String productId) {
        // Normalize product ID to lowercase for catalog lookup
        String normalized = productId.toLowerCase();
        
        // Check if this is a known product keyword
        for (Map.Entry<String, Integer> entry : PRODUCT_CATALOG.entrySet()) {
            if (normalized.contains(entry.getKey())) {
                // Found a match - use catalog price
                // Add vendor-specific variation (±15%) for competitive pricing
                int catalogPrice = entry.getValue();
                double vendorVariation = 0.85 + (random.nextDouble() * 0.30); // 0.85 to 1.15
                return (int) Math.round(catalogPrice * vendorVariation);
            }
        }
        
        // Unknown product - use hash-based pricing
        int hash = Math.abs(productId.hashCode());
        
        // Map to reasonable price range ($10-$300)
        int basePrice = 1000 + (hash % 29000); // $10.00 to $300.00 in cents
        
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

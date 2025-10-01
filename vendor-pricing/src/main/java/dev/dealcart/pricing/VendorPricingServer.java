package dev.dealcart.pricing;

import dev.dealcart.v1.*;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

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
    private Server server;

    public VendorPricingServer(int port, List<VendorEndpoint> vendorEndpoints) {
        this.port = port;
        this.vendorEndpoints = vendorEndpoints;
    }

    public void start() throws IOException {
        server = ServerBuilder.forPort(port)
                .addService(new VendorPricingImpl())
                .build()
                .start();
        
        logger.info("VendorPricingServer started on port {} with {} vendor endpoints", 
                   port, vendorEndpoints.size());
        
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
            
            // Fan out to all vendor endpoints using CompletableFuture
            for (VendorEndpoint endpoint : vendorEndpoints) {
                CompletableFuture.runAsync(() -> {
                    try {
                        callVendorAndStreamQuote(endpoint, request, responseObserver, latch, completedCount, errorCount);
                    } catch (Exception e) {
                        logger.error("Error calling vendor {}: {}", endpoint.name, e.getMessage());
                        errorCount.incrementAndGet();
                        latch.countDown();
                    }
                });
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
        try {
            // Create gRPC channel to vendor
            channel = ManagedChannelBuilder.forAddress(endpoint.host, endpoint.port)
                    .usePlaintext()
                    .build();
            
            // Create stub and call GetQuote
            VendorBackendGrpc.VendorBackendBlockingStub stub = VendorBackendGrpc.newBlockingStub(channel);
            
            logger.debug("Calling vendor {} at {}:{}", endpoint.name, endpoint.host, endpoint.port);
            
            PriceQuote quote = stub.getQuote(request);
            
            // Stream the quote to the client
            responseObserver.onNext(quote);
            completedCount.incrementAndGet();
            
            logger.debug("Received quote from {}: {} {}", 
                        endpoint.name, quote.getPrice().getAmountCents() / 100.0, 
                        quote.getPrice().getCurrencyCode());
            
        } catch (Exception e) {
            logger.error("Failed to get quote from vendor {}: {}", endpoint.name, e.getMessage());
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
        
        logger.info("Starting VendorPricingServer with port={}, vendors={}", port, vendorEndpoints);
        
        VendorPricingServer server = new VendorPricingServer(port, vendorEndpoints);
        server.start();
        server.blockUntilShutdown();
    }
}

package dev.dealcart.gateway.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.dealcart.gateway.dto.PriceQuoteDto;
import dev.dealcart.v1.PriceQuote;
import dev.dealcart.v1.QuoteRequest;
import dev.dealcart.v1.VendorPricingGrpc;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Controller for product search with real-time vendor quotes via SSE.
 */
@RestController
public class SearchController {
    private static final Logger logger = LoggerFactory.getLogger(SearchController.class);
    private final ExecutorService executorService = Executors.newFixedThreadPool(32);
    private final ScheduledExecutorService heartbeatExecutor = Executors.newScheduledThreadPool(4);
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    @Autowired
    private VendorPricingGrpc.VendorPricingStub pricingStub;
    
    @PreDestroy
    public void cleanup() {
        logger.info("Shutting down SearchController executors...");
        executorService.shutdown();
        heartbeatExecutor.shutdown();
        try {
            executorService.awaitTermination(2, TimeUnit.SECONDS);
            heartbeatExecutor.awaitTermination(2, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
    
    /**
     * Search for products with real-time vendor quotes streamed via SSE.
     * 
     * Example: GET /api/search?q=headphones
     */
    @GetMapping(value = "/api/search", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamQuotes(@RequestParam("q") String query) {
        logger.info("Search request for: {}", query);
        
        SseEmitter emitter = new SseEmitter(60000L); // 60 second timeout
        
        // Start heartbeat to keep connection alive
        ScheduledFuture<?> heartbeat = heartbeatExecutor.scheduleAtFixedRate(() -> {
            try {
                emitter.send(SseEmitter.event().comment("heartbeat"));
            } catch (IOException e) {
                // Client disconnected, ignore
            }
        }, 15, 15, TimeUnit.SECONDS);
        
        emitter.onCompletion(() -> heartbeat.cancel(false));
        emitter.onTimeout(() -> heartbeat.cancel(false));
        
        // Map search query to product ID
        String productId = mapQueryToProductId(query);
        
        QuoteRequest request = QuoteRequest.newBuilder()
                .setProductId(productId)
                .setQuantity(1)
                .setCurrencyCode("USD")
                .build();
        
        // Subscribe to pricing service in executor to avoid blocking
        executorService.execute(() -> {
            try {
                // Use 1.5s deadline for pricing calls
                VendorPricingGrpc.VendorPricingStub stub = pricingStub
                        .withDeadlineAfter(1500, TimeUnit.MILLISECONDS);
                
                stub.streamQuotes(request, new StreamObserver<PriceQuote>() {
                    @Override
                    public void onNext(PriceQuote quote) {
                        try {
                            // Convert to DTO and send as SSE event
                            PriceQuoteDto dto = PriceQuoteDto.fromProto(quote);
                            emitter.send(SseEmitter.event()
                                    .name("quote")
                                    .data(objectMapper.writeValueAsString(dto)));
                        } catch (IOException e) {
                            logger.error("Error sending SSE event: {}", e.getMessage());
                            emitter.completeWithError(e);
                        }
                    }
                    
                    @Override
                    public void onError(Throwable t) {
                        logger.error("Error streaming quotes: {}", t.getMessage());
                        emitter.completeWithError(t);
                    }
                    
                    @Override
                    public void onCompleted() {
                        logger.info("Quote stream completed for: {}", query);
                        emitter.complete();
                    }
                });
                
            } catch (Exception e) {
                logger.error("Error initiating quote stream: {}", e.getMessage());
                emitter.completeWithError(e);
            }
        });
        
        return emitter;
    }
    
    /**
     * Map search query to deterministic product ID.
     */
    private String mapQueryToProductId(String query) {
        // Hash query to create consistent product ID
        int hash = Math.abs(query.trim().toLowerCase().hashCode());
        return "sku-" + (hash % 1000);
    }
    
}


package dev.dealcart.gateway.controller;

import dev.dealcart.gateway.dto.PriceQuoteDto;
import dev.dealcart.v1.PriceQuote;
import dev.dealcart.v1.QuoteRequest;
import dev.dealcart.v1.VendorPricingGrpc;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Controller for non-streaming quote endpoint (for JMeter load testing).
 */
@RestController
public class QuoteController {
    private static final Logger logger = LoggerFactory.getLogger(QuoteController.class);
    
    @Autowired
    private VendorPricingGrpc.VendorPricingStub pricingStub;
    
    /**
     * Get quotes for a product (non-streaming, waits for all vendors).
     * 
     * mode=best (default): Returns single cheapest quote
     * mode=all: Returns all quotes
     * 
     * Examples: 
     *   GET /api/quote?productId=sku-123
     *   GET /api/quote?productId=sku-123&mode=best
     *   GET /api/quote?productId=sku-123&mode=all
     */
    @GetMapping("/api/quote")
    public ResponseEntity<Object> getQuote(
            @RequestParam("productId") String productId,
            @RequestParam(value = "mode", defaultValue = "best") String mode) {
        logger.info("Quote request for productId: {}, mode: {}", productId, mode);
        
        QuoteRequest request = QuoteRequest.newBuilder()
                .setProductId(productId)
                .setQuantity(1)
                .setCurrencyCode("USD")
                .build();
        
        CompletableFuture<List<PriceQuote>> future = new CompletableFuture<>();
        List<PriceQuote> quotes = new ArrayList<>();
        
        try {
            // Use 1.5s deadline for pricing calls
            VendorPricingGrpc.VendorPricingStub stub = pricingStub
                    .withDeadlineAfter(1500, TimeUnit.MILLISECONDS);
            
            stub.streamQuotes(request, new StreamObserver<PriceQuote>() {
                @Override
                public void onNext(PriceQuote quote) {
                    synchronized (quotes) {
                        quotes.add(quote);
                    }
                }
                
                @Override
                public void onError(Throwable t) {
                    logger.error("Error getting quotes: {}", t.getMessage());
                    future.completeExceptionally(t);
                }
                
                @Override
                public void onCompleted() {
                    future.complete(quotes);
                }
            });
            
            // Wait for all quotes (with timeout)
            List<PriceQuote> results = future.get(3, TimeUnit.SECONDS);
            
            // Convert to DTOs
            List<PriceQuoteDto> dtos = new ArrayList<>();
            for (PriceQuote quote : results) {
                dtos.add(PriceQuoteDto.fromProto(quote));
            }
            
            // Return based on mode
            if ("all".equals(mode)) {
                // Return all quotes
                Map<String, Object> response = new HashMap<>();
                response.put("productId", productId);
                response.put("quoteCount", dtos.size());
                response.put("quotes", dtos);
                return ResponseEntity.ok(response);
            } else {
                // Return best (cheapest) quote
                if (dtos.isEmpty()) {
                    Map<String, Object> error = new HashMap<>();
                    error.put("error", "No quotes available");
                    return ResponseEntity.notFound().build();
                }
                
                // Find cheapest
                PriceQuoteDto best = dtos.stream()
                        .min(Comparator.comparingDouble(PriceQuoteDto::getPrice))
                        .orElse(dtos.get(0));
                
                return ResponseEntity.ok(best);
            }
            
        } catch (Exception e) {
            logger.error("Error processing quote request: {}", e.getMessage());
            Map<String, Object> error = new HashMap<>();
            error.put("error", "Failed to get quotes");
            error.put("message", e.getMessage());
            return ResponseEntity.internalServerError().body(error);
        }
    }
    
}


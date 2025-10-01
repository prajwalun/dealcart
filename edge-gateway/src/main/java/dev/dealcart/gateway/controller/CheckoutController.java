package dev.dealcart.gateway.controller;

import dev.dealcart.v1.*;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;import java.util.concurrent.atomic.AtomicInteger;

/**
 * Controller for checkout operations.
 */
@RestController
public class CheckoutController {
    private static final Logger logger = LoggerFactory.getLogger(CheckoutController.class);
    private final ExecutorService executorService = Executors.newFixedThreadPool(32);
    private final ScheduledExecutorService heartbeatExecutor = Executors.newScheduledThreadPool(4);
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    @Autowired
    private CheckoutGrpc.CheckoutBlockingStub checkoutStub;
    
    @Autowired
    private CheckoutGrpc.CheckoutStub checkoutAsyncStub;
    
    @PreDestroy
    public void cleanup() {
        logger.info("Shutting down CheckoutController executors...");
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
     * Start a new checkout process.
     * 
     * Example: POST /api/checkout
     * Body: {
     *   "customerId": "cust-123",
     *   "items": [{
     *     "productId": "sku-123",
     *     "quantity": 2,
     *     "unitPrice": {"currencyCode": "USD", "amountCents": 1999},
     *     "vendorId": "vendor1"
     *   }],
     *   "shippingAddress": "123 Main St",
     *   "paymentMethodId": "pm-123"
     * }
     */
    @PostMapping("/api/checkout")
    public ResponseEntity<Map<String, Object>> startCheckout(
            @RequestBody Map<String, Object> requestBody,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        
        logger.info("Checkout request received with idempotency key: {}", idempotencyKey);
        
        try {
            // Parse request body
            CheckoutRequest grpcRequest = buildCheckoutRequest(requestBody);
            
            // Call checkout service with 2s deadline
            CheckoutGrpc.CheckoutBlockingStub stub = checkoutStub
                    .withDeadlineAfter(2, TimeUnit.SECONDS);
            CheckoutResponse response = stub.start(grpcRequest);
            
            // Format response
            Map<String, Object> result = new HashMap<>();
            result.put("checkoutId", response.getCheckoutId());
            result.put("status", response.getStatus().name());
            result.put("message", response.getMessage());
            
            if (response.hasTotalAmount()) {
                result.put("totalAmount", response.getTotalAmount().getAmountCents() / 100.0);
                result.put("currency", response.getTotalAmount().getCurrencyCode());
            }
            
            return ResponseEntity.ok(result);
            
        } catch (Exception e) {
            logger.error("Error processing checkout: {}", e.getMessage(), e);
            Map<String, Object> error = new HashMap<>();
            error.put("error", "Checkout failed");
            error.put("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }
    
    /**
     * Stream checkout status updates via SSE.
     * 
     * Example: GET /api/checkout/checkout-123/stream
     */
    @GetMapping(value = "/api/checkout/{checkoutId}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamCheckoutStatus(@PathVariable("checkoutId") String checkoutId) {
        logger.info("Status stream requested for checkout: {}", checkoutId);
        
        SseEmitter emitter = new SseEmitter(120000L); // 120 second timeout (checkout can take longer)
        
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
        
        CheckoutStatusRequest request = CheckoutStatusRequest.newBuilder()
                .setCheckoutId(checkoutId)
                .build();
        
        // Subscribe to checkout status in executor
        executorService.execute(() -> {
            try {
                CheckoutGrpc.CheckoutStub stub = checkoutAsyncStub
                        .withDeadlineAfter(120, TimeUnit.SECONDS);
                
                stub.getStatus(request, new StreamObserver<NodeStatus>() {
                    @Override
                    public void onNext(NodeStatus status) {
                        try {
                            // Send status as SSE event with formatted JSON
                            String json = formatNodeStatus(status);
                            emitter.send(SseEmitter.event()
                                    .name("status")
                                    .data(json));
                        } catch (IOException e) {
                            logger.error("Error sending SSE event: {}", e.getMessage());
                            emitter.completeWithError(e);
                        }
                    }
                    
                    @Override
                    public void onError(Throwable t) {
                        logger.error("Error streaming checkout status: {}", t.getMessage());
                        emitter.completeWithError(t);
                    }
                    
                    @Override
                    public void onCompleted() {
                        logger.info("Checkout status stream completed for: {}", checkoutId);
                        emitter.complete();
                    }
                });
                
            } catch (Exception e) {
                logger.error("Error initiating status stream: {}", e.getMessage());
                emitter.completeWithError(e);
            }
        });
        
        return emitter;
    }
    
    /**
     * Build gRPC CheckoutRequest from JSON request body.
     */
    private CheckoutRequest buildCheckoutRequest(Map<String, Object> body) {
        CheckoutRequest.Builder builder = CheckoutRequest.newBuilder();
        
        builder.setCustomerId((String) body.get("customerId"));
        builder.setShippingAddress((String) body.get("shippingAddress"));
        builder.setPaymentMethodId((String) body.get("paymentMethodId"));
        
        // Parse items
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) body.get("items");
        
        for (Map<String, Object> itemData : items) {
            @SuppressWarnings("unchecked")
            Map<String, Object> priceData = (Map<String, Object>) itemData.get("unitPrice");
            
            Money unitPrice = Money.newBuilder()
                    .setCurrencyCode((String) priceData.get("currencyCode"))
                    .setAmountCents(((Number) priceData.get("amountCents")).longValue())
                    .build();
            
            CheckoutItem item = CheckoutItem.newBuilder()
                    .setProductId((String) itemData.get("productId"))
                    .setQuantity(((Number) itemData.get("quantity")).intValue())
                    .setUnitPrice(unitPrice)
                    .setVendorId((String) itemData.get("vendorId"))
                    .build();
            
            builder.addItems(item);
        }
        
        return builder.build();
    }
    
    /**
     * Format node status as JSON for SSE.
     */
    private String formatNodeStatus(NodeStatus status) {
        Map<String, Object> data = new HashMap<>();
        data.put("nodeId", status.getNodeId());
        data.put("state", status.getState().name());
        data.put("message", status.getMessage());
        data.put("timestamp", status.getTimestampMs());
        
        if (!status.getErrorCode().isEmpty()) {
            data.put("errorCode", status.getErrorCode());
            data.put("errorMessage", status.getErrorMessage());
        }
        
        try {
            return objectMapper.writeValueAsString(data);
        } catch (Exception e) {
            logger.error("Error formatting node status: {}", e.getMessage());
            return "{}";
        }
    }
}


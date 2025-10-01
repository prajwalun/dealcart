package dev.dealcart.checkout;

import dev.dealcart.v1.*;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Checkout service with promise-graph DAG orchestration and SAGA compensations.
 * 
 * DAG Flow: Reserve → [Price, Tax] → Pay → Confirm
 * SAGA: On failure, compensate with Release and Void operations
 * 
 * Environment variables:
 * - PORT: Port to listen on (default: 9200)
 */
public class CheckoutServer {
    private static final Logger logger = LoggerFactory.getLogger(CheckoutServer.class);
    
    private final int port;
    private final ExecutorService executorService;
    private final InventoryManager inventoryManager;
    private final OrderStatusManager orderStatusManager;
    private Server server;

    public CheckoutServer(int port) {
        this.port = port;
        this.executorService = Executors.newFixedThreadPool(32);
        this.inventoryManager = new InventoryManager();
        this.orderStatusManager = new OrderStatusManager();
    }

    public void start() throws IOException {
        server = ServerBuilder.forPort(port)
                .addService(new CheckoutImpl())
                .build()
                .start();
        
        logger.info("CheckoutServer started on port {}", port);
        
        // Add shutdown hook
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            logger.info("Shutting down CheckoutServer...");
            try {
                CheckoutServer.this.stop();
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
        if (executorService != null) {
            executorService.shutdown();
            executorService.awaitTermination(2, TimeUnit.SECONDS);
        }
    }

    public void blockUntilShutdown() throws InterruptedException {
        if (server != null) {
            server.awaitTermination();
        }
    }

    /**
     * Implementation of the Checkout gRPC service.
     */
    private class CheckoutImpl extends CheckoutGrpc.CheckoutImplBase {
        
        @Override
        public void start(CheckoutRequest request, StreamObserver<CheckoutResponse> responseObserver) {
            String checkoutId = orderStatusManager.generateCheckoutId();
            logger.info("Starting checkout {}: {} items for customer {}", 
                       checkoutId, request.getItemsCount(), request.getCustomerId());
            
            try {
                // Initialize order status tracker
                orderStatusManager.createOrder(checkoutId, request);
                
                // Execute DAG asynchronously
                CompletableFuture.runAsync(() -> executeCheckoutDAG(checkoutId, request), executorService);
                
                // Return immediate response
                CheckoutResponse response = CheckoutResponse.newBuilder()
                        .setCheckoutId(checkoutId)
                        .setStatus(CheckoutStatus.CHECKOUT_STATUS_PENDING)
                        .setMessage("Checkout initiated successfully")
                        .build();
                
                responseObserver.onNext(response);
                responseObserver.onCompleted();
                
            } catch (Exception e) {
                logger.error("Error starting checkout {}: {}", checkoutId, e.getMessage(), e);
                responseObserver.onError(e);
            }
        }
        
        @Override
        public void getStatus(CheckoutStatusRequest request, StreamObserver<NodeStatus> responseObserver) {
            String checkoutId = request.getCheckoutId();
            logger.info("Status stream requested for checkout {}", checkoutId);
            
            OrderStatus orderStatus = orderStatusManager.getOrderStatus(checkoutId);
            if (orderStatus == null) {
                logger.warn("Checkout {} not found", checkoutId);
                responseObserver.onError(new IllegalArgumentException("Checkout ID not found: " + checkoutId));
                return;
            }
            
            // Stream existing statuses
            for (NodeStatus status : orderStatus.getNodeStatuses()) {
                responseObserver.onNext(status);
            }
            
            // Register for future updates
            orderStatus.addStatusObserver(responseObserver);
            
            // Check if checkout is complete
            if (orderStatus.isComplete()) {
                responseObserver.onCompleted();
            }
        }
    }

    /**
     * Execute the checkout DAG with proper orchestration and error handling.
     */
    private void executeCheckoutDAG(String checkoutId, CheckoutRequest request) {
        OrderStatus orderStatus = orderStatusManager.getOrderStatus(checkoutId);
        
        try {
            logger.info("Executing DAG for checkout {}", checkoutId);
            
            // Step 1: Reserve inventory
            if (!executeReserve(checkoutId, request, orderStatus)) {
                failCheckout(checkoutId, orderStatus, "Reservation failed");
                return;
            }
            
            // Step 2: Price and Tax (parallel)
            CompletableFuture<Money> priceFuture = CompletableFuture.supplyAsync(
                    () -> executePrice(checkoutId, request, orderStatus), executorService);
            CompletableFuture<Money> taxFuture = CompletableFuture.supplyAsync(
                    () -> executeTax(checkoutId, request, orderStatus), executorService);
            
            Money totalPrice;
            Money totalTax;
            try {
                totalPrice = priceFuture.get(3, TimeUnit.SECONDS);
                totalTax = taxFuture.get(3, TimeUnit.SECONDS);
            } catch (Exception e) {
                logger.error("Pricing/Tax calculation failed for {}: {}", checkoutId, e.getMessage());
                compensateReserve(checkoutId, request, orderStatus);
                failCheckout(checkoutId, orderStatus, "Pricing/Tax failed");
                return;
            }
            
            Money totalAmount = Money.newBuilder()
                    .setCurrencyCode(totalPrice.getCurrencyCode())
                    .setAmountCents(totalPrice.getAmountCents() + totalTax.getAmountCents())
                    .build();
            
            // Step 3: Pay (with retries)
            if (!executePay(checkoutId, request, totalAmount, orderStatus)) {
                compensateReserve(checkoutId, request, orderStatus);
                failCheckout(checkoutId, orderStatus, "Payment failed");
                return;
            }
            
            // Step 4: Confirm
            if (!executeConfirm(checkoutId, request, orderStatus)) {
                compensatePayment(checkoutId, orderStatus);
                compensateReserve(checkoutId, request, orderStatus);
                failCheckout(checkoutId, orderStatus, "Confirmation failed");
                return;
            }
            
            // Success!
            completeCheckout(checkoutId, orderStatus, totalAmount);
            
        } catch (Exception e) {
            logger.error("Unexpected error in DAG for {}: {}", checkoutId, e.getMessage(), e);
            compensateReserve(checkoutId, request, orderStatus);
            failCheckout(checkoutId, orderStatus, "Unexpected error: " + e.getMessage());
        }
    }

    /**
     * Step 1: Reserve inventory for all items.
     */
    private boolean executeReserve(String checkoutId, CheckoutRequest request, OrderStatus orderStatus) {
        updateNodeStatus(orderStatus, "reserve", NodeState.NODE_STATE_RUNNING, "Reserving inventory", null, null);
        
        try {
            for (CheckoutItem item : request.getItemsList()) {
                boolean reserved = inventoryManager.reserve(item.getProductId(), item.getQuantity());
                if (!reserved) {
                    updateNodeStatus(orderStatus, "reserve", NodeState.NODE_STATE_FAILED, 
                            "Insufficient inventory", "INSUFFICIENT_INVENTORY", 
                            "Product " + item.getProductId() + " not available");
                    return false;
                }
            }
            
            updateNodeStatus(orderStatus, "reserve", NodeState.NODE_STATE_COMPLETED, 
                    "Inventory reserved successfully", null, null);
            return true;
            
        } catch (Exception e) {
            updateNodeStatus(orderStatus, "reserve", NodeState.NODE_STATE_FAILED, 
                    "Reservation error", "RESERVATION_ERROR", e.getMessage());
            return false;
        }
    }

    /**
     * Step 2a: Calculate total price.
     */
    private Money executePrice(String checkoutId, CheckoutRequest request, OrderStatus orderStatus) {
        updateNodeStatus(orderStatus, "price", NodeState.NODE_STATE_RUNNING, "Calculating price", null, null);
        
        try {
            // Simulate pricing calculation
            Thread.sleep(50 + new Random().nextInt(100)); // 50-150ms
            
            long totalCents = 0;
            for (CheckoutItem item : request.getItemsList()) {
                totalCents += item.getUnitPrice().getAmountCents() * item.getQuantity();
            }
            
            Money price = Money.newBuilder()
                    .setCurrencyCode("USD")
                    .setAmountCents(totalCents)
                    .build();
            
            updateNodeStatus(orderStatus, "price", NodeState.NODE_STATE_COMPLETED, 
                    "Price calculated: $" + (totalCents / 100.0), null, null);
            
            return price;
            
        } catch (Exception e) {
            updateNodeStatus(orderStatus, "price", NodeState.NODE_STATE_FAILED, 
                    "Pricing error", "PRICING_ERROR", e.getMessage());
            throw new RuntimeException("Pricing failed", e);
        }
    }

    /**
     * Step 2b: Calculate tax.
     */
    private Money executeTax(String checkoutId, CheckoutRequest request, OrderStatus orderStatus) {
        updateNodeStatus(orderStatus, "tax", NodeState.NODE_STATE_RUNNING, "Calculating tax", null, null);
        
        try {
            // Simulate tax calculation
            Thread.sleep(30 + new Random().nextInt(70)); // 30-100ms
            
            long totalCents = 0;
            for (CheckoutItem item : request.getItemsList()) {
                totalCents += item.getUnitPrice().getAmountCents() * item.getQuantity();
            }
            
            long taxCents = (long) (totalCents * 0.08); // 8% tax
            
            Money tax = Money.newBuilder()
                    .setCurrencyCode("USD")
                    .setAmountCents(taxCents)
                    .build();
            
            updateNodeStatus(orderStatus, "tax", NodeState.NODE_STATE_COMPLETED, 
                    "Tax calculated: $" + (taxCents / 100.0), null, null);
            
            return tax;
            
        } catch (Exception e) {
            updateNodeStatus(orderStatus, "tax", NodeState.NODE_STATE_FAILED, 
                    "Tax calculation error", "TAX_ERROR", e.getMessage());
            throw new RuntimeException("Tax calculation failed", e);
        }
    }

    /**
     * Step 3: Process payment with retries.
     */
    private boolean executePay(String checkoutId, CheckoutRequest request, Money amount, OrderStatus orderStatus) {
        int maxRetries = 2;
        long deadlineMs = 1500;
        
        for (int attempt = 0; attempt <= maxRetries; attempt++) {
            updateNodeStatus(orderStatus, "pay", NodeState.NODE_STATE_RUNNING, 
                    "Processing payment (attempt " + (attempt + 1) + ")", null, null);
            
            try {
                long startTime = System.currentTimeMillis();
                
                // Simulate payment processing with 80% success rate
                Thread.sleep(100 + new Random().nextInt(200)); // 100-300ms
                
                long elapsed = System.currentTimeMillis() - startTime;
                if (elapsed > deadlineMs) {
                    throw new TimeoutException("Payment timeout");
                }
                
                // Simulate occasional payment failures
                if (new Random().nextDouble() < 0.2 && attempt < maxRetries) {
                    throw new RuntimeException("Payment gateway error");
                }
                
                orderStatus.setPaymentTransactionId("txn-" + UUID.randomUUID().toString());
                updateNodeStatus(orderStatus, "pay", NodeState.NODE_STATE_COMPLETED, 
                        "Payment successful: $" + (amount.getAmountCents() / 100.0), null, null);
                return true;
                
            } catch (Exception e) {
                if (attempt < maxRetries) {
                    logger.warn("Payment attempt {} failed for {}: {}, retrying...", 
                               attempt + 1, checkoutId, e.getMessage());
                    try {
                        Thread.sleep(200); // Brief backoff
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                    }
                } else {
                    updateNodeStatus(orderStatus, "pay", NodeState.NODE_STATE_FAILED, 
                            "Payment failed after retries", "PAYMENT_FAILED", e.getMessage());
                    return false;
                }
            }
        }
        
        return false;
    }

    /**
     * Step 4: Confirm order with vendors.
     */
    private boolean executeConfirm(String checkoutId, CheckoutRequest request, OrderStatus orderStatus) {
        updateNodeStatus(orderStatus, "confirm", NodeState.NODE_STATE_RUNNING, "Confirming order", null, null);
        
        try {
            // Simulate vendor confirmation
            Thread.sleep(50 + new Random().nextInt(100)); // 50-150ms
            
            // 95% success rate for confirmation
            if (new Random().nextDouble() < 0.05) {
                throw new RuntimeException("Vendor confirmation failed");
            }
            
            updateNodeStatus(orderStatus, "confirm", NodeState.NODE_STATE_COMPLETED, 
                    "Order confirmed", null, null);
            return true;
            
        } catch (Exception e) {
            updateNodeStatus(orderStatus, "confirm", NodeState.NODE_STATE_FAILED, 
                    "Confirmation failed", "CONFIRMATION_FAILED", e.getMessage());
            return false;
        }
    }

    /**
     * SAGA Compensation: Release inventory reservations.
     */
    private void compensateReserve(String checkoutId, CheckoutRequest request, OrderStatus orderStatus) {
        logger.info("Compensating: Releasing inventory for checkout {}", checkoutId);
        updateNodeStatus(orderStatus, "release", NodeState.NODE_STATE_RUNNING, "Releasing inventory", null, null);
        
        try {
            for (CheckoutItem item : request.getItemsList()) {
                inventoryManager.release(item.getProductId(), item.getQuantity());
            }
            updateNodeStatus(orderStatus, "release", NodeState.NODE_STATE_COMPLETED, 
                    "Inventory released", null, null);
        } catch (Exception e) {
            logger.error("Failed to release inventory for {}: {}", checkoutId, e.getMessage());
            updateNodeStatus(orderStatus, "release", NodeState.NODE_STATE_FAILED, 
                    "Release failed", "RELEASE_FAILED", e.getMessage());
        }
    }

    /**
     * SAGA Compensation: Void payment.
     */
    private void compensatePayment(String checkoutId, OrderStatus orderStatus) {
        String txnId = orderStatus.getPaymentTransactionId();
        if (txnId == null) return;
        
        logger.info("Compensating: Voiding payment {} for checkout {}", txnId, checkoutId);
        updateNodeStatus(orderStatus, "void", NodeState.NODE_STATE_RUNNING, "Voiding payment", null, null);
        
        try {
            Thread.sleep(50); // Simulate void operation
            updateNodeStatus(orderStatus, "void", NodeState.NODE_STATE_COMPLETED, 
                    "Payment voided", null, null);
        } catch (Exception e) {
            logger.error("Failed to void payment for {}: {}", checkoutId, e.getMessage());
            updateNodeStatus(orderStatus, "void", NodeState.NODE_STATE_FAILED, 
                    "Void failed", "VOID_FAILED", e.getMessage());
        }
    }

    /**
     * Mark checkout as failed.
     */
    private void failCheckout(String checkoutId, OrderStatus orderStatus, String message) {
        logger.error("Checkout {} failed: {}", checkoutId, message);
        orderStatus.setCheckoutStatus(CheckoutStatus.CHECKOUT_STATUS_FAILED);
        orderStatus.completeStreams();
    }

    /**
     * Mark checkout as complete.
     */
    private void completeCheckout(String checkoutId, OrderStatus orderStatus, Money totalAmount) {
        logger.info("Checkout {} completed successfully. Total: ${}", 
                   checkoutId, totalAmount.getAmountCents() / 100.0);
        orderStatus.setCheckoutStatus(CheckoutStatus.CHECKOUT_STATUS_COMPLETED);
        orderStatus.setTotalAmount(totalAmount);
        orderStatus.completeStreams();
    }

    /**
     * Update node status and notify observers.
     */
    private void updateNodeStatus(OrderStatus orderStatus, String nodeId, NodeState state, 
                                 String message, String errorCode, String errorMessage) {
        NodeStatus status = NodeStatus.newBuilder()
                .setNodeId(nodeId)
                .setState(state)
                .setMessage(message)
                .setTimestampMs(System.currentTimeMillis())
                .setErrorCode(errorCode == null ? "" : errorCode)
                .setErrorMessage(errorMessage == null ? "" : errorMessage)
                .build();
        
        orderStatus.addNodeStatus(status);
        orderStatus.notifyObservers(status);
    }

    public static void main(String[] args) throws IOException, InterruptedException {
        int port = 9200; // default port
        String portEnv = System.getenv("PORT");
        if (portEnv != null && !portEnv.trim().isEmpty()) {
            try {
                port = Integer.parseInt(portEnv);
            } catch (NumberFormatException e) {
                logger.warn("Invalid PORT environment variable: {}, using default {}", portEnv, port);
            }
        }
        
        logger.info("Starting CheckoutServer with port={}", port);
        
        CheckoutServer server = new CheckoutServer(port);
        server.start();
        server.blockUntilShutdown();
    }
}

/**
 * Manages in-memory inventory (MVP implementation).
 */
class InventoryManager {
    private static final Logger logger = LoggerFactory.getLogger(InventoryManager.class);
    private final ConcurrentHashMap<String, Integer> inventory = new ConcurrentHashMap<>();
    
    public InventoryManager() {
        // Initialize with realistic product inventory for load testing
        // High-demand electronics
        inventory.put("sku-laptop", 5000);
        inventory.put("sku-macbook", 3000);
        inventory.put("sku-iphone", 10000);
        inventory.put("sku-ipad", 7000);
        inventory.put("sku-airpods", 15000);
        inventory.put("sku-watch", 8000);
        inventory.put("sku-monitor", 4000);
        inventory.put("sku-keyboard", 12000);
        inventory.put("sku-mouse", 18000);
        inventory.put("sku-headphones", 6000);
        inventory.put("sku-camera", 2000);
        inventory.put("sku-drone", 1500);
        inventory.put("sku-tablet", 5000);
        
        // Home & Kitchen
        inventory.put("sku-blender", 8000);
        inventory.put("sku-toaster", 10000);
        inventory.put("sku-microwave", 5000);
        inventory.put("sku-vacuum", 4000);
        inventory.put("sku-coffee", 7000);
        inventory.put("sku-airfryer", 6000);
        
        // Sports & Outdoors
        inventory.put("sku-bike", 3000);
        inventory.put("sku-yoga-mat", 15000);
        inventory.put("sku-dumbbell", 10000);
        inventory.put("sku-tent", 4000);
        inventory.put("sku-backpack", 8000);
        
        // Books & Media
        inventory.put("sku-book", 20000);
        inventory.put("sku-textbook", 5000);
        
        // Clothing
        inventory.put("sku-jacket", 7000);
        inventory.put("sku-shoes", 12000);
        inventory.put("sku-jeans", 15000);
        inventory.put("sku-shirt", 20000);
        
        // Legacy test SKUs (for backward compatibility)
        inventory.put("sku-123", 50000);
        inventory.put("sku-456", 50000);
        inventory.put("sku-789", 50000);
    }
    
    public synchronized boolean reserve(String productId, int quantity) {
        // For load testing: default to 100,000 units if product not in catalog
        // This ensures we don't fail checkouts due to inventory during stress tests
        int available = inventory.getOrDefault(productId, 100000);
        
        if (available >= quantity) {
            inventory.put(productId, available - quantity);
            logger.debug("Reserved {} units of {}, {} remaining", quantity, productId, available - quantity);
            return true;
        }
        
        logger.warn("Insufficient inventory for {}: requested {}, available {}", productId, quantity, available);
        return false;
    }
    
    public synchronized void release(String productId, int quantity) {
        int current = inventory.getOrDefault(productId, 0);
        inventory.put(productId, current + quantity);
        logger.debug("Released {} units of {}, {} now available", quantity, productId, current + quantity);
    }
}

/**
 * Tracks order status and manages status observers.
 */
class OrderStatus {
    private final String checkoutId;
    private final CheckoutRequest request;
    private final List<NodeStatus> nodeStatuses = new CopyOnWriteArrayList<>();
    private final List<StreamObserver<NodeStatus>> statusObservers = new CopyOnWriteArrayList<>();
    private volatile CheckoutStatus checkoutStatus = CheckoutStatus.CHECKOUT_STATUS_PENDING;
    private volatile Money totalAmount;
    private volatile String paymentTransactionId;
    private volatile boolean complete = false;
    
    public OrderStatus(String checkoutId, CheckoutRequest request) {
        this.checkoutId = checkoutId;
        this.request = request;
    }
    
    public void addNodeStatus(NodeStatus status) {
        nodeStatuses.add(status);
    }
    
    public List<NodeStatus> getNodeStatuses() {
        return new ArrayList<>(nodeStatuses);
    }
    
    public void addStatusObserver(StreamObserver<NodeStatus> observer) {
        statusObservers.add(observer);
    }
    
    public void notifyObservers(NodeStatus status) {
        for (StreamObserver<NodeStatus> observer : statusObservers) {
            try {
                observer.onNext(status);
            } catch (Exception e) {
                // Observer may have disconnected
                statusObservers.remove(observer);
            }
        }
    }
    
    public void completeStreams() {
        complete = true;
        for (StreamObserver<NodeStatus> observer : statusObservers) {
            try {
                observer.onCompleted();
            } catch (Exception e) {
                // Ignore
            }
        }
        statusObservers.clear();
    }
    
    public boolean isComplete() {
        return complete;
    }
    
    public CheckoutStatus getCheckoutStatus() {
        return checkoutStatus;
    }
    
    public void setCheckoutStatus(CheckoutStatus status) {
        this.checkoutStatus = status;
    }
    
    public void setTotalAmount(Money amount) {
        this.totalAmount = amount;
    }
    
    public String getPaymentTransactionId() {
        return paymentTransactionId;
    }
    
    public void setPaymentTransactionId(String txnId) {
        this.paymentTransactionId = txnId;
    }
}

/**
 * Manages order status tracking across all checkouts.
 */
class OrderStatusManager {
    private final ConcurrentHashMap<String, OrderStatus> orderStatuses = new ConcurrentHashMap<>();
    private final AtomicLong checkoutIdCounter = new AtomicLong(0);
    
    public String generateCheckoutId() {
        return "checkout-" + System.currentTimeMillis() + "-" + checkoutIdCounter.incrementAndGet();
    }
    
    public void createOrder(String checkoutId, CheckoutRequest request) {
        orderStatuses.put(checkoutId, new OrderStatus(checkoutId, request));
    }
    
    public OrderStatus getOrderStatus(String checkoutId) {
        return orderStatuses.get(checkoutId);
    }
}


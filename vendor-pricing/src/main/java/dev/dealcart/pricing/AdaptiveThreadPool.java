package dev.dealcart.pricing;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Adaptive thread pool that scales based on p95 latency.
 * Increases threads when latency is high, decreases when latency is low.
 */
public class AdaptiveThreadPool {
    private static final Logger logger = LoggerFactory.getLogger(AdaptiveThreadPool.class);
    
    private final int minThreads;
    private final int maxThreads;
    private final int stepSize;
    private final long targetP95Ms;
    private final long lowerP95Ms;
    private final int latencyWindowSize;
    
    private ThreadPoolExecutor executor;
    private ScheduledExecutorService controller;
    private final ConcurrentLinkedQueue<Long> latencies;
    private final AtomicInteger currentPoolSize;
    private volatile long lastScaleAtMs = 0; // Cooldown to prevent flapping
    private static final long SCALE_COOLDOWN_MS = 20_000; // 20 seconds between scaling
    
    public AdaptiveThreadPool(int minThreads, int maxThreads, int stepSize, 
                             long targetP95Ms, long lowerP95Ms, int latencyWindowSize) {
        this.minThreads = minThreads;
        this.maxThreads = maxThreads;
        this.stepSize = stepSize;
        this.targetP95Ms = targetP95Ms;
        this.lowerP95Ms = lowerP95Ms;
        this.latencyWindowSize = latencyWindowSize;
        this.latencies = new ConcurrentLinkedQueue<>();
        this.currentPoolSize = new AtomicInteger(minThreads);
        
        // Create executor with min threads initially and bounded queue
        this.executor = new ThreadPoolExecutor(
            minThreads, 
            maxThreads,
            60L, 
            TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(2048), // Bounded queue prevents unbounded backlog
            new ThreadFactory() {
                private final AtomicInteger threadNumber = new AtomicInteger(1);
                @Override
                public Thread newThread(Runnable r) {
                    Thread t = new Thread(r, "adaptive-pool-" + threadNumber.getAndIncrement());
                    t.setDaemon(false);
                    return t;
                }
            }
        );
        
        // Allow core threads to time out when idle (nice idle shrink)
        this.executor.allowCoreThreadTimeOut(true);
        
        logger.info("AdaptiveThreadPool initialized: min={}, max={}, step={}, targetP95={}ms, lowerP95={}ms, queue=2048",
                   minThreads, maxThreads, stepSize, targetP95Ms, lowerP95Ms);
    }
    
    /**
     * Get the executor for running tasks.
     */
    public ExecutorService executor() {
        return executor;
    }
    
    /**
     * Record a latency sample for autoscaling decisions.
     */
    public void recordLatency(long latencyMs) {
        latencies.offer(latencyMs);
        
        // Keep window size bounded
        while (latencies.size() > latencyWindowSize) {
            latencies.poll();
        }
    }
    
    /**
     * Start the autoscaling controller.
     */
    public void startController() {
        controller = Executors.newScheduledThreadPool(1, r -> {
            Thread t = new Thread(r, "autoscale-controller");
            t.setDaemon(true);
            return t;
        });
        
        // Run controller every 5 seconds
        controller.scheduleAtFixedRate(this::adjustPoolSize, 5, 5, TimeUnit.SECONDS);
        
        logger.info("Autoscaler controller started (interval: 5s)");
    }
    
    /**
     * Stop the controller and executor.
     */
    public void stop() {
        if (controller != null) {
            controller.shutdown();
            try {
                if (!controller.awaitTermination(2, TimeUnit.SECONDS)) {
                    controller.shutdownNow();
                }
            } catch (InterruptedException e) {
                controller.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        
        if (executor != null) {
            executor.shutdown();
            try {
                if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                }
            } catch (InterruptedException e) {
                executor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        
        logger.info("AdaptiveThreadPool stopped");
    }
    
    /**
     * Autoscaling logic: adjust pool size based on p95 latency.
     * Includes cooldown period to prevent flapping.
     */
    private void adjustPoolSize() {
        if (latencies.isEmpty()) {
            return;
        }
        
        // Calculate p95 from recent latencies
        long p95 = calculateP95();
        int currentSize = executor.getCorePoolSize();
        int activeThreads = executor.getActiveCount();
        int queueSize = executor.getQueue().size();
        
        // Always log periodic snapshot (helps during JMeter)
        logger.info("Autoscaler snapshot: p95={}ms, pool={}/{}, active={}, queue={}", 
                   p95, currentSize, maxThreads, activeThreads, queueSize);
        
        // Check cooldown to prevent flapping
        long now = System.currentTimeMillis();
        long timeSinceLastScale = now - lastScaleAtMs;
        
        if (timeSinceLastScale < SCALE_COOLDOWN_MS && lastScaleAtMs > 0) {
            logger.debug("Autoscaler cooldown: {}ms remaining", SCALE_COOLDOWN_MS - timeSinceLastScale);
            return;
        }
        
        // Scale up if p95 exceeds target
        if (p95 > targetP95Ms && currentSize < maxThreads) {
            int newSize = Math.min(currentSize + stepSize, maxThreads);
            executor.setCorePoolSize(newSize);
            executor.setMaximumPoolSize(newSize);
            currentPoolSize.set(newSize);
            lastScaleAtMs = now;
            logger.info("Autoscaler: SCALE UP {} → {} threads (p95={}ms > target={}ms)", 
                       currentSize, newSize, p95, targetP95Ms);
        }
        // Scale down if p95 is well below target and we're above min
        else if (p95 < lowerP95Ms && currentSize > minThreads) {
            // Only scale down if we're not heavily loaded
            if (activeThreads < currentSize * 0.7) {
                int newSize = Math.max(currentSize - stepSize, minThreads);
                executor.setCorePoolSize(newSize);
                executor.setMaximumPoolSize(newSize);
                currentPoolSize.set(newSize);
                lastScaleAtMs = now;
                logger.info("Autoscaler: SCALE DOWN {} → {} threads (p95={}ms < lower={}ms)", 
                           currentSize, newSize, p95, lowerP95Ms);
            }
        }
    }
    
    /**
     * Calculate p95 latency from recent samples.
     */
    private long calculateP95() {
        if (latencies.isEmpty()) {
            return 0;
        }
        
        // Copy to list and sort
        Long[] samples = latencies.toArray(new Long[0]);
        if (samples.length == 0) {
            return 0;
        }
        
        java.util.Arrays.sort(samples);
        
        // p95 index
        int p95Index = (int) Math.ceil(samples.length * 0.95) - 1;
        p95Index = Math.max(0, Math.min(p95Index, samples.length - 1));
        
        return samples[p95Index];
    }
    
    /**
     * Get current pool size for monitoring.
     */
    public int getCurrentPoolSize() {
        return executor.getCorePoolSize();
    }
    
    /**
     * Get current active thread count.
     */
    public int getActiveThreads() {
        return executor.getActiveCount();
    }
}


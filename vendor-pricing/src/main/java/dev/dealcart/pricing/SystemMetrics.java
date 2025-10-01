package dev.dealcart.pricing;

import com.sun.management.OperatingSystemMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;

/**
 * Simple system metrics for production-like auto-scaling.
 * Monitors CPU and memory usage - the metrics that real companies actually use.
 */
public class SystemMetrics {
    private final OperatingSystemMXBean osBean;
    private final MemoryMXBean memoryBean;
    
    public SystemMetrics() {
        this.osBean = (OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
        this.memoryBean = ManagementFactory.getMemoryMXBean();
    }
    
    /**
     * Get current CPU usage percentage (0-100)
     */
    public double getCpuUsage() {
        return osBean.getProcessCpuLoad() * 100.0;
    }
    
    /**
     * Get current memory usage percentage (0-100)
     */
    public double getMemoryUsage() {
        long usedMemory = memoryBean.getHeapMemoryUsage().getUsed();
        long maxMemory = memoryBean.getHeapMemoryUsage().getMax();
        return (double) usedMemory / maxMemory * 100.0;
    }
    
    /**
     * Get system load average (1 minute)
     */
    public double getLoadAverage() {
        return osBean.getSystemLoadAverage();
    }
    
    /**
     * Get available processors
     */
    public int getAvailableProcessors() {
        return osBean.getAvailableProcessors();
    }
}

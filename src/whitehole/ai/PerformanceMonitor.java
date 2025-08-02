/*
 * Copyright (C) 2022 Whitehole Team
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package whitehole.ai;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;

/**
 * Performance monitoring utility for AI operations.
 * Tracks timing, memory usage, and cache performance.
 */
public class PerformanceMonitor {
    private static PerformanceMonitor instance;
    
    private final Map<String, AtomicLong> counters;
    private final Map<String, AtomicLong> timings;
    private final Map<String, Long> operationStartTimes;
    private final MemoryMXBean memoryBean;
    
    private PerformanceMonitor() {
        this.counters = new ConcurrentHashMap<>();
        this.timings = new ConcurrentHashMap<>();
        this.operationStartTimes = new ConcurrentHashMap<>();
        this.memoryBean = ManagementFactory.getMemoryMXBean();
    }
    
    /**
     * Gets the singleton instance of the performance monitor.
     */
    public static synchronized PerformanceMonitor getInstance() {
        if (instance == null) {
            instance = new PerformanceMonitor();
        }
        return instance;
    }
    
    /**
     * Starts timing an operation.
     * @param operationName The name of the operation
     */
    public void startOperation(String operationName) {
        operationStartTimes.put(operationName, System.nanoTime());
    }
    
    /**
     * Ends timing an operation and records the duration.
     * @param operationName The name of the operation
     * @return The duration in milliseconds
     */
    public long endOperation(String operationName) {
        Long startTime = operationStartTimes.remove(operationName);
        if (startTime == null) {
            return 0;
        }
        
        long duration = (System.nanoTime() - startTime) / 1_000_000; // Convert to milliseconds
        timings.computeIfAbsent(operationName, k -> new AtomicLong(0)).addAndGet(duration);
        incrementCounter(operationName + "_count");
        
        return duration;
    }
    
    /**
     * Times an operation using a lambda.
     * @param operationName The name of the operation
     * @param operation The operation to time
     * @return The result of the operation
     */
    public <T> T timeOperation(String operationName, java.util.function.Supplier<T> operation) {
        startOperation(operationName);
        try {
            return operation.get();
        } finally {
            endOperation(operationName);
        }
    }
    
    /**
     * Times an operation that doesn't return a value.
     * @param operationName The name of the operation
     * @param operation The operation to time
     */
    public void timeOperation(String operationName, Runnable operation) {
        startOperation(operationName);
        try {
            operation.run();
        } finally {
            endOperation(operationName);
        }
    }
    
    /**
     * Increments a counter.
     * @param counterName The name of the counter
     */
    public void incrementCounter(String counterName) {
        counters.computeIfAbsent(counterName, k -> new AtomicLong(0)).incrementAndGet();
    }
    
    /**
     * Adds a value to a counter.
     * @param counterName The name of the counter
     * @param value The value to add
     */
    public void addToCounter(String counterName, long value) {
        counters.computeIfAbsent(counterName, k -> new AtomicLong(0)).addAndGet(value);
    }
    
    /**
     * Gets the value of a counter.
     * @param counterName The name of the counter
     * @return The counter value
     */
    public long getCounter(String counterName) {
        AtomicLong counter = counters.get(counterName);
        return counter != null ? counter.get() : 0;
    }
    
    /**
     * Gets the total time for an operation.
     * @param operationName The name of the operation
     * @return The total time in milliseconds
     */
    public long getTotalTime(String operationName) {
        AtomicLong timing = timings.get(operationName);
        return timing != null ? timing.get() : 0;
    }
    
    /**
     * Gets the average time for an operation.
     * @param operationName The name of the operation
     * @return The average time in milliseconds
     */
    public double getAverageTime(String operationName) {
        long totalTime = getTotalTime(operationName);
        long count = getCounter(operationName + "_count");
        return count > 0 ? (double) totalTime / count : 0.0;
    }
    
    /**
     * Gets current memory usage information.
     * @return Map containing memory usage statistics
     */
    public Map<String, Object> getMemoryUsage() {
        Map<String, Object> memoryStats = new HashMap<>();
        
        MemoryUsage heapUsage = memoryBean.getHeapMemoryUsage();
        MemoryUsage nonHeapUsage = memoryBean.getNonHeapMemoryUsage();
        
        memoryStats.put("heapUsed", heapUsage.getUsed());
        memoryStats.put("heapMax", heapUsage.getMax());
        memoryStats.put("heapCommitted", heapUsage.getCommitted());
        memoryStats.put("heapUsagePercent", (double) heapUsage.getUsed() / heapUsage.getMax() * 100);
        
        memoryStats.put("nonHeapUsed", nonHeapUsage.getUsed());
        memoryStats.put("nonHeapMax", nonHeapUsage.getMax());
        memoryStats.put("nonHeapCommitted", nonHeapUsage.getCommitted());
        
        return memoryStats;
    }
    
    /**
     * Gets comprehensive performance statistics.
     * @return Map containing all performance data
     */
    public Map<String, Object> getAllStatistics() {
        Map<String, Object> stats = new HashMap<>();
        
        // Timing statistics
        Map<String, Object> timingStats = new HashMap<>();
        for (Map.Entry<String, AtomicLong> entry : timings.entrySet()) {
            String operationName = entry.getKey();
            long totalTime = entry.getValue().get();
            long count = getCounter(operationName + "_count");
            
            Map<String, Object> operationStats = new HashMap<>();
            operationStats.put("totalTime", totalTime);
            operationStats.put("count", count);
            operationStats.put("averageTime", count > 0 ? (double) totalTime / count : 0.0);
            
            timingStats.put(operationName, operationStats);
        }
        stats.put("timings", timingStats);
        
        // Counter statistics
        Map<String, Long> counterStats = new HashMap<>();
        for (Map.Entry<String, AtomicLong> entry : counters.entrySet()) {
            counterStats.put(entry.getKey(), entry.getValue().get());
        }
        stats.put("counters", counterStats);
        
        // Memory statistics
        stats.put("memory", getMemoryUsage());
        
        // AI-specific statistics
        stats.put("aiContextCache", GalaxyContextCache.getInstance().getStatistics());
        stats.put("lazyObjectCache", LazyObjectInfo.getCacheStatistics());
        
        return stats;
    }
    
    /**
     * Gets a summary of the most important performance metrics.
     * @return Map containing key performance indicators
     */
    public Map<String, Object> getPerformanceSummary() {
        Map<String, Object> summary = new HashMap<>();
        
        // Key timing metrics
        summary.put("contextBuildTime", getAverageTime("buildContext"));
        summary.put("jsonSerializationTime", getAverageTime("jsonSerialization"));
        summary.put("aiResponseProcessingTime", getAverageTime("aiResponseProcessing"));
        summary.put("commandExecutionTime", getAverageTime("commandExecution"));
        
        // Key counters
        summary.put("totalCommands", getCounter("ai_commands_processed"));
        summary.put("cacheHits", getCounter("context_cache_hits"));
        summary.put("cacheMisses", getCounter("context_cache_misses"));
        
        // Memory usage
        Map<String, Object> memoryUsage = getMemoryUsage();
        summary.put("heapUsagePercent", memoryUsage.get("heapUsagePercent"));
        summary.put("heapUsedMB", (Long) memoryUsage.get("heapUsed") / (1024 * 1024));
        
        // Cache efficiency
        long hits = getCounter("context_cache_hits");
        long misses = getCounter("context_cache_misses");
        if (hits + misses > 0) {
            summary.put("cacheHitRate", (double) hits / (hits + misses) * 100);
        } else {
            summary.put("cacheHitRate", 0.0);
        }
        
        return summary;
    }
    
    /**
     * Resets all performance statistics.
     */
    public void reset() {
        counters.clear();
        timings.clear();
        operationStartTimes.clear();
    }
    
    /**
     * Logs performance statistics to console.
     */
    public void logStatistics() {
        Map<String, Object> summary = getPerformanceSummary();
        
        System.out.println("=== AI Performance Statistics ===");
        System.out.printf("Context Build Time: %.2f ms avg%n", (Double) summary.get("contextBuildTime"));
        System.out.printf("JSON Serialization Time: %.2f ms avg%n", (Double) summary.get("jsonSerializationTime"));
        System.out.printf("AI Response Processing Time: %.2f ms avg%n", (Double) summary.get("aiResponseProcessingTime"));
        System.out.printf("Command Execution Time: %.2f ms avg%n", (Double) summary.get("commandExecutionTime"));
        System.out.printf("Total Commands Processed: %d%n", (Long) summary.get("totalCommands"));
        System.out.printf("Cache Hit Rate: %.1f%%%n", (Double) summary.get("cacheHitRate"));
        System.out.printf("Heap Usage: %d MB (%.1f%%)%n", 
                         (Long) summary.get("heapUsedMB"), 
                         (Double) summary.get("heapUsagePercent"));
        System.out.println("================================");
    }
    
    /**
     * Checks if performance is within acceptable thresholds.
     * @return true if performance is good, false if there are issues
     */
    public boolean isPerformanceGood() {
        Map<String, Object> summary = getPerformanceSummary();
        
        // Check if average times are reasonable
        double contextBuildTime = (Double) summary.get("contextBuildTime");
        double jsonSerializationTime = (Double) summary.get("jsonSerializationTime");
        double aiResponseTime = (Double) summary.get("aiResponseProcessingTime");
        double heapUsage = (Double) summary.get("heapUsagePercent");
        
        // Performance thresholds
        return contextBuildTime < 1000 &&      // Context build should be under 1 second
               jsonSerializationTime < 500 &&  // JSON serialization should be under 500ms
               aiResponseTime < 2000 &&         // AI response processing should be under 2 seconds
               heapUsage < 80;                  // Heap usage should be under 80%
    }
    
    /**
     * Gets performance warnings if any thresholds are exceeded.
     * @return List of warning messages
     */
    public List<String> getPerformanceWarnings() {
        List<String> warnings = new ArrayList<>();
        Map<String, Object> summary = getPerformanceSummary();
        
        double contextBuildTime = (Double) summary.get("contextBuildTime");
        if (contextBuildTime > 1000) {
            warnings.add(String.format("Context build time is high: %.0f ms", contextBuildTime));
        }
        
        double jsonSerializationTime = (Double) summary.get("jsonSerializationTime");
        if (jsonSerializationTime > 500) {
            warnings.add(String.format("JSON serialization time is high: %.0f ms", jsonSerializationTime));
        }
        
        double aiResponseTime = (Double) summary.get("aiResponseProcessingTime");
        if (aiResponseTime > 2000) {
            warnings.add(String.format("AI response processing time is high: %.0f ms", aiResponseTime));
        }
        
        double heapUsage = (Double) summary.get("heapUsagePercent");
        if (heapUsage > 80) {
            warnings.add(String.format("Heap usage is high: %.1f%%", heapUsage));
        }
        
        double cacheHitRate = (Double) summary.get("cacheHitRate");
        if (cacheHitRate < 50 && getCounter("context_cache_hits") + getCounter("context_cache_misses") > 10) {
            warnings.add(String.format("Cache hit rate is low: %.1f%%", cacheHitRate));
        }
        
        return warnings;
    }
}
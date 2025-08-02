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

import org.json.JSONObject;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;
import java.lang.ref.WeakReference;
import java.util.Map;
import java.util.HashMap;

/**
 * Caching system for galaxy contexts to improve performance of repeated AI commands.
 * Uses weak references to avoid memory leaks and implements LRU-style eviction.
 */
public class GalaxyContextCache {
    private static final int MAX_CACHE_SIZE = 10; // Maximum number of cached contexts
    private static final long CACHE_TTL_MS = 300000; // 5 minutes TTL
    private static final int MAX_JSON_CACHE_SIZE = 50; // Maximum number of cached JSON serializations
    
    private final ConcurrentMap<String, CacheEntry> contextCache;
    private final ConcurrentMap<String, JsonCacheEntry> jsonCache;
    private final AtomicLong accessCounter;
    
    private static GalaxyContextCache instance;
    
    private GalaxyContextCache() {
        this.contextCache = new ConcurrentHashMap<>();
        this.jsonCache = new ConcurrentHashMap<>();
        this.accessCounter = new AtomicLong(0);
    }
    
    /**
     * Gets the singleton instance of the context cache.
     */
    public static synchronized GalaxyContextCache getInstance() {
        if (instance == null) {
            instance = new GalaxyContextCache();
        }
        return instance;
    }
    
    /**
     * Gets a cached galaxy context or returns null if not found or expired.
     * @param galaxyName The galaxy name
     * @param zoneName The zone name (can be null for full galaxy context)
     * @param objectCount The current object count for validation
     * @return Cached GalaxyContext or null if not found/expired
     */
    public GalaxyContext getCachedContext(String galaxyName, String zoneName, int objectCount) {
        String key = createContextKey(galaxyName, zoneName);
        CacheEntry entry = contextCache.get(key);
        
        if (entry == null) {
            return null;
        }
        
        // Check if entry is expired
        if (System.currentTimeMillis() - entry.timestamp > CACHE_TTL_MS) {
            contextCache.remove(key);
            return null;
        }
        
        // Check if object count has changed (indicates galaxy modification)
        if (entry.objectCount != objectCount) {
            contextCache.remove(key);
            return null;
        }
        
        // Get context from weak reference
        GalaxyContext context = entry.contextRef.get();
        if (context == null) {
            // Context was garbage collected
            contextCache.remove(key);
            return null;
        }
        
        // Update access time and counter
        entry.lastAccess = accessCounter.incrementAndGet();
        return context;
    }
    
    /**
     * Caches a galaxy context.
     * @param galaxyName The galaxy name
     * @param zoneName The zone name (can be null for full galaxy context)
     * @param context The context to cache
     */
    public void cacheContext(String galaxyName, String zoneName, GalaxyContext context) {
        if (context == null) return;
        
        String key = createContextKey(galaxyName, zoneName);
        
        // Clean up expired entries before adding new one
        cleanupExpiredEntries();
        
        // If cache is full, remove least recently used entry
        if (contextCache.size() >= MAX_CACHE_SIZE) {
            evictLeastRecentlyUsed();
        }
        
        CacheEntry entry = new CacheEntry(
            new WeakReference<>(context),
            System.currentTimeMillis(),
            accessCounter.incrementAndGet(),
            context.getObjectCount()
        );
        
        contextCache.put(key, entry);
    }
    
    /**
     * Gets cached JSON serialization or returns null if not found or expired.
     * @param context The galaxy context
     * @param serializationType Type of serialization (e.g., "full", "minimal", "spatial")
     * @return Cached JSON string or null if not found/expired
     */
    public JSONObject getCachedJson(GalaxyContext context, String serializationType) {
        String key = createJsonKey(context, serializationType);
        JsonCacheEntry entry = jsonCache.get(key);
        
        if (entry == null) {
            return null;
        }
        
        // Check if entry is expired
        if (System.currentTimeMillis() - entry.timestamp > CACHE_TTL_MS) {
            jsonCache.remove(key);
            return null;
        }
        
        // Update access time
        entry.lastAccess = accessCounter.incrementAndGet();
        return entry.jsonData;
    }
    
    /**
     * Caches JSON serialization of a galaxy context.
     * @param context The galaxy context
     * @param serializationType Type of serialization
     * @param jsonData The JSON data to cache
     */
    public void cacheJson(GalaxyContext context, String serializationType, JSONObject jsonData) {
        if (context == null || jsonData == null) return;
        
        String key = createJsonKey(context, serializationType);
        
        // Clean up expired JSON entries
        cleanupExpiredJsonEntries();
        
        // If JSON cache is full, remove least recently used entry
        if (jsonCache.size() >= MAX_JSON_CACHE_SIZE) {
            evictLeastRecentlyUsedJson();
        }
        
        JsonCacheEntry entry = new JsonCacheEntry(
            jsonData,
            System.currentTimeMillis(),
            accessCounter.incrementAndGet()
        );
        
        jsonCache.put(key, entry);
    }
    
    /**
     * Invalidates all cached contexts for a specific galaxy.
     * Call this when the galaxy is modified.
     * @param galaxyName The galaxy name
     */
    public void invalidateGalaxy(String galaxyName) {
        contextCache.entrySet().removeIf(entry -> 
            entry.getKey().startsWith(galaxyName + ":"));
        jsonCache.entrySet().removeIf(entry -> 
            entry.getKey().contains(":" + galaxyName + ":"));
    }
    
    /**
     * Invalidates all cached contexts for a specific zone.
     * Call this when a zone is modified.
     * @param galaxyName The galaxy name
     * @param zoneName The zone name
     */
    public void invalidateZone(String galaxyName, String zoneName) {
        String keyPrefix = createContextKey(galaxyName, zoneName);
        contextCache.remove(keyPrefix);
        
        // Also invalidate full galaxy context since zone changed
        String fullGalaxyKey = createContextKey(galaxyName, null);
        contextCache.remove(fullGalaxyKey);
        
        // Invalidate related JSON cache entries
        jsonCache.entrySet().removeIf(entry -> 
            entry.getKey().contains(":" + galaxyName + ":"));
    }
    
    /**
     * Clears all cached data.
     */
    public void clearAll() {
        contextCache.clear();
        jsonCache.clear();
    }
    
    /**
     * Gets cache statistics for monitoring and debugging.
     * @return Map containing cache statistics
     */
    public Map<String, Object> getStatistics() {
        Map<String, Object> stats = new HashMap<>();
        
        // Context cache stats
        stats.put("contextCacheSize", contextCache.size());
        stats.put("contextCacheMaxSize", MAX_CACHE_SIZE);
        
        // JSON cache stats
        stats.put("jsonCacheSize", jsonCache.size());
        stats.put("jsonCacheMaxSize", MAX_JSON_CACHE_SIZE);
        
        // Calculate hit rates (would need to track hits/misses for accurate rates)
        stats.put("cacheTtlMs", CACHE_TTL_MS);
        stats.put("totalAccesses", accessCounter.get());
        
        // Memory usage estimation
        long estimatedMemoryUsage = estimateMemoryUsage();
        stats.put("estimatedMemoryUsageBytes", estimatedMemoryUsage);
        
        return stats;
    }
    
    // Private helper methods
    
    private String createContextKey(String galaxyName, String zoneName) {
        if (zoneName == null || zoneName.isEmpty()) {
            return galaxyName + ":full";
        }
        return galaxyName + ":" + zoneName;
    }
    
    private String createJsonKey(GalaxyContext context, String serializationType) {
        return serializationType + ":" + context.getGalaxyName() + ":" + 
               context.getCurrentZone() + ":" + context.getObjectCount();
    }
    
    private void cleanupExpiredEntries() {
        long currentTime = System.currentTimeMillis();
        contextCache.entrySet().removeIf(entry -> 
            currentTime - entry.getValue().timestamp > CACHE_TTL_MS ||
            entry.getValue().contextRef.get() == null);
    }
    
    private void cleanupExpiredJsonEntries() {
        long currentTime = System.currentTimeMillis();
        jsonCache.entrySet().removeIf(entry -> 
            currentTime - entry.getValue().timestamp > CACHE_TTL_MS);
    }
    
    private void evictLeastRecentlyUsed() {
        if (contextCache.isEmpty()) return;
        
        String lruKey = null;
        long oldestAccess = Long.MAX_VALUE;
        
        for (Map.Entry<String, CacheEntry> entry : contextCache.entrySet()) {
            if (entry.getValue().lastAccess < oldestAccess) {
                oldestAccess = entry.getValue().lastAccess;
                lruKey = entry.getKey();
            }
        }
        
        if (lruKey != null) {
            contextCache.remove(lruKey);
        }
    }
    
    private void evictLeastRecentlyUsedJson() {
        if (jsonCache.isEmpty()) return;
        
        String lruKey = null;
        long oldestAccess = Long.MAX_VALUE;
        
        for (Map.Entry<String, JsonCacheEntry> entry : jsonCache.entrySet()) {
            if (entry.getValue().lastAccess < oldestAccess) {
                oldestAccess = entry.getValue().lastAccess;
                lruKey = entry.getKey();
            }
        }
        
        if (lruKey != null) {
            jsonCache.remove(lruKey);
        }
    }
    
    private long estimateMemoryUsage() {
        long total = 0;
        
        // Estimate context cache memory usage
        for (CacheEntry entry : contextCache.values()) {
            GalaxyContext context = entry.contextRef.get();
            if (context != null) {
                // Rough estimation: 100 bytes per object + base overhead
                total += context.getObjectCount() * 100 + 1000;
            }
        }
        
        // Estimate JSON cache memory usage
        for (JsonCacheEntry entry : jsonCache.values()) {
            // Rough estimation based on JSON string length
            total += entry.jsonData.toString().length() * 2; // UTF-16 chars
        }
        
        return total;
    }
    
    /**
     * Cache entry for galaxy contexts.
     */
    private static class CacheEntry {
        final WeakReference<GalaxyContext> contextRef;
        final long timestamp;
        volatile long lastAccess;
        final int objectCount;
        
        CacheEntry(WeakReference<GalaxyContext> contextRef, long timestamp, long lastAccess, int objectCount) {
            this.contextRef = contextRef;
            this.timestamp = timestamp;
            this.lastAccess = lastAccess;
            this.objectCount = objectCount;
        }
    }
    
    /**
     * Cache entry for JSON serializations.
     */
    private static class JsonCacheEntry {
        final JSONObject jsonData;
        final long timestamp;
        volatile long lastAccess;
        
        JsonCacheEntry(JSONObject jsonData, long timestamp, long lastAccess) {
            this.jsonData = jsonData;
            this.timestamp = timestamp;
            this.lastAccess = lastAccess;
        }
    }
}
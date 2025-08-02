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

import whitehole.math.Vec3f;
import whitehole.smg.object.AbstractObj;
import whitehole.db.FieldHashes;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Lazy-loading wrapper for object information to reduce memory usage
 * and improve performance when dealing with large galaxy contexts.
 * Only loads detailed information when actually needed.
 */
public class LazyObjectInfo {
    private final AbstractObj sourceObject;
    private final Supplier<Map<String, Object>> propertiesLoader;
    private final Supplier<List<String>> tagsLoader;
    
    // Cached values to avoid repeated computation
    private volatile Map<String, Object> cachedProperties;
    private volatile List<String> cachedTags;
    private volatile String cachedDisplayName;
    
    // Thread-safe flags to track what has been loaded
    private volatile boolean propertiesLoaded = false;
    private volatile boolean tagsLoaded = false;
    private volatile boolean displayNameLoaded = false;
    
    // Static cache for commonly accessed object database information
    private static final Map<String, String> displayNameCache = new ConcurrentHashMap<>();
    private static final Map<String, List<String>> tagCache = new ConcurrentHashMap<>();
    
    private final GalaxyContext.ObjectInfo baseInfo;
    
    /**
     * Creates a lazy-loading ObjectInfo wrapper.
     * @param sourceObject The source AbstractObj
     */
    public LazyObjectInfo(AbstractObj sourceObject) {
        this.sourceObject = sourceObject;
        this.baseInfo = createBuilder(sourceObject).build();
        this.propertiesLoader = this::loadProperties;
        this.tagsLoader = this::loadTags;
    }
    
    /**
     * Creates the base ObjectInfo builder with essential information only.
     */
    private static GalaxyContext.ObjectInfo.ObjectInfoBuilder createBuilder(AbstractObj obj) {
        GalaxyContext.ObjectInfo.ObjectInfoBuilder builder = new GalaxyContext.ObjectInfo.ObjectInfoBuilder();
        
        // Load only essential information immediately
        builder.setUniqueId(obj.uniqueID);
        builder.setName(obj.name != null ? obj.name : "");
        builder.setType(determineObjectType(obj));
        
        // Position, rotation, scale - these are frequently accessed
        builder.setPosition(obj.position != null ? new Vec3f(obj.position) : new Vec3f(0, 0, 0));
        builder.setRotation(obj.rotation != null ? new Vec3f(obj.rotation) : new Vec3f(0, 0, 0));
        builder.setScale(obj.scale != null ? new Vec3f(obj.scale) : new Vec3f(1, 1, 1));
        
        // Layer and zone information
        builder.setLayer(obj.getLayerName());
        builder.setZone(obj.stage != null ? obj.stage.stageName : "");
        
        // Set empty collections initially - will be loaded lazily
        builder.setProperties(new HashMap<>());
        builder.setTags(new ArrayList<>());
        
        return builder;
    }
    
    // Delegate basic methods to base info
    public int getUniqueId() { return baseInfo.getUniqueId(); }
    public String getName() { return baseInfo.getName(); }
    public String getType() { return baseInfo.getType(); }
    public Vec3f getPosition() { return baseInfo.getPosition(); }
    public Vec3f getRotation() { return baseInfo.getRotation(); }
    public Vec3f getScale() { return baseInfo.getScale(); }
    public String getLayer() { return baseInfo.getLayer(); }
    public String getZone() { return baseInfo.getZone(); }
    
    /**
     * Gets the display name, loading it lazily if needed.
     */
    public String getDisplayName() {
        if (!displayNameLoaded) {
            synchronized (this) {
                if (!displayNameLoaded) {
                    cachedDisplayName = loadDisplayName();
                    displayNameLoaded = true;
                }
            }
        }
        return cachedDisplayName != null ? cachedDisplayName : getName();
    }
    
    /**
     * Gets the properties map, loading it lazily if needed.
     */
    public Map<String, Object> getProperties() {
        if (!propertiesLoaded) {
            synchronized (this) {
                if (!propertiesLoaded) {
                    cachedProperties = propertiesLoader.get();
                    propertiesLoaded = true;
                }
            }
        }
        return cachedProperties != null ? cachedProperties : Collections.emptyMap();
    }
    
    /**
     * Gets the tags list, loading it lazily if needed.
     */
    public List<String> getTags() {
        if (!tagsLoaded) {
            synchronized (this) {
                if (!tagsLoaded) {
                    cachedTags = tagsLoader.get();
                    tagsLoaded = true;
                }
            }
        }
        return cachedTags != null ? cachedTags : Collections.emptyList();
    }
    
    /**
     * Checks if detailed information has been loaded.
     * Useful for memory usage monitoring.
     */
    public boolean isFullyLoaded() {
        return propertiesLoaded && tagsLoaded && displayNameLoaded;
    }
    
    /**
     * Forces loading of all lazy data.
     * Useful when you know you'll need all information.
     */
    public void preloadAll() {
        getDisplayName();
        getProperties();
        getTags();
    }
    
    /**
     * Gets memory usage estimation for this object.
     */
    public long getEstimatedMemoryUsage() {
        long usage = 200; // Base object overhead
        
        if (displayNameLoaded && cachedDisplayName != null) {
            usage += cachedDisplayName.length() * 2; // UTF-16 chars
        }
        
        if (propertiesLoaded && cachedProperties != null) {
            usage += cachedProperties.size() * 50; // Rough estimate per property
        }
        
        if (tagsLoaded && cachedTags != null) {
            usage += cachedTags.size() * 20; // Rough estimate per tag
        }
        
        return usage;
    }
    
    // Private loading methods
    
    private String loadDisplayName() {
        String cacheKey = sourceObject.name + ":" + getType();
        String cached = displayNameCache.get(cacheKey);
        if (cached != null) {
            return cached;
        }
        
        String displayName;
        if (sourceObject.objdbInfo != null && sourceObject.objdbInfo.isValid()) {
            displayName = sourceObject.objdbInfo.toString();
        } else {
            displayName = sourceObject.name;
        }
        
        // Cache for future use, but limit cache size
        if (displayNameCache.size() < 1000) {
            displayNameCache.put(cacheKey, displayName);
        }
        
        return displayName;
    }
    
    private Map<String, Object> loadProperties() {
        Map<String, Object> properties = new HashMap<>();
        
        if (sourceObject.data != null) {
            // Extract common properties from BCSV data
            for (Integer key : sourceObject.data.keySet()) {
                Object value = sourceObject.data.get(key);
                if (value != null) {
                    // Convert hash key to readable name if possible
                    String keyName = FieldHashes.get(key);
                    if (keyName != null && !keyName.startsWith("[")) {
                        properties.put(keyName, value);
                    } else {
                        properties.put(String.format("0x%08X", key), value);
                    }
                }
            }
        }
        
        // Add object database information if available
        if (sourceObject.objdbInfo != null && sourceObject.objdbInfo.isValid()) {
            properties.put("category", sourceObject.objdbInfo.category());
            // Get className for current game (assuming SMG1 = 1, SMG2 = 2)
            int gameType = whitehole.Whitehole.getCurrentGameType();
            properties.put("className", sourceObject.objdbInfo.className(gameType));
        }
        
        return Collections.unmodifiableMap(properties);
    }
    
    private List<String> loadTags() {
        String cacheKey = sourceObject.name + ":" + getType();
        List<String> cached = tagCache.get(cacheKey);
        if (cached != null) {
            return new ArrayList<>(cached);
        }
        
        List<String> tags = new ArrayList<>();
        
        // Add type-based tags
        tags.add(getType());
        
        // Add category tags from object database
        if (sourceObject.objdbInfo != null && sourceObject.objdbInfo.isValid()) {
            String category = sourceObject.objdbInfo.category();
            if (category != null && !category.isEmpty()) {
                tags.add(category.toLowerCase());
            }
        }
        
        // Add layer tag
        String layer = sourceObject.getLayerName();
        if (layer != null && !layer.isEmpty()) {
            tags.add("layer_" + layer.toLowerCase());
        }
        
        // Add name-based tags for common object types
        String name = sourceObject.name.toLowerCase();
        if (name.contains("goomba") || name.contains("kuribo")) {
            tags.add("enemy");
            tags.add("goomba");
        } else if (name.contains("koopa")) {
            tags.add("enemy");
            tags.add("koopa");
        } else if (name.contains("coin")) {
            tags.add("collectible");
            tags.add("coin");
        } else if (name.contains("platform")) {
            tags.add("platform");
        } else if (name.contains("pipe")) {
            tags.add("pipe");
        }
        
        List<String> immutableTags = Collections.unmodifiableList(tags);
        
        // Cache for future use, but limit cache size
        if (tagCache.size() < 1000) {
            tagCache.put(cacheKey, immutableTags);
        }
        
        return new ArrayList<>(immutableTags);
    }
    
    private static String determineObjectType(AbstractObj obj) {
        // Determine object type based on class
        String className = obj.getClass().getSimpleName();
        switch (className) {
            case "LevelObj": return "level";
            case "AreaObj": return "area";
            case "CameraObj": return "camera";
            case "ChildObj": return "child";
            case "CutsceneObj": return "cutscene";
            case "DebugObj": return "debug";
            case "GravityObj": return "gravity";
            case "MapPartObj": return "mappart";
            case "PositionObj": return "position";
            case "SoundObj": return "sound";
            case "StageObj": return "stage";
            case "StartObj": return "start";
            default: return "unknown";
        }
    }
    
    /**
     * Clears static caches to free memory.
     * Should be called periodically or when memory is low.
     */
    public static void clearStaticCaches() {
        displayNameCache.clear();
        tagCache.clear();
    }
    
    /**
     * Gets statistics about static cache usage.
     */
    public static Map<String, Object> getCacheStatistics() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("displayNameCacheSize", displayNameCache.size());
        stats.put("tagCacheSize", tagCache.size());
        
        // Estimate memory usage
        long displayNameMemory = displayNameCache.values().stream()
            .mapToLong(name -> name.length() * 2)
            .sum();
        long tagMemory = tagCache.values().stream()
            .mapToLong(tags -> tags.size() * 20)
            .sum();
        
        stats.put("displayNameCacheMemoryBytes", displayNameMemory);
        stats.put("tagCacheMemoryBytes", tagMemory);
        stats.put("totalCacheMemoryBytes", displayNameMemory + tagMemory);
        
        return stats;
    }
}
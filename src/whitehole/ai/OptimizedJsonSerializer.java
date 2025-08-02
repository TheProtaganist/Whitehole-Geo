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
import org.json.JSONObject;
import org.json.JSONArray;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ForkJoinPool;
import java.util.stream.Collectors;
import java.io.StringWriter;
import java.io.IOException;

/**
 * Optimized JSON serializer for galaxy contexts that provides different
 * serialization levels and caching to improve performance for large galaxies.
 */
public class OptimizedJsonSerializer {
    
    public enum SerializationLevel {
        MINIMAL,    // Only essential object info (id, name, type, position)
        STANDARD,   // Standard info plus basic properties
        FULL,       // All available information including detailed properties
        SPATIAL     // Optimized for spatial queries (position, bounds, relationships)
    }
    
    private final GalaxyContextCache cache;
    
    public OptimizedJsonSerializer() {
        this.cache = GalaxyContextCache.getInstance();
    }
    
    /**
     * Serializes galaxy context to JSON with specified level of detail.
     * Uses caching to avoid repeated serialization of the same context.
     * 
     * @param context The galaxy context to serialize
     * @param level The level of detail for serialization
     * @return JSON representation of the galaxy context
     */
    public JSONObject serialize(GalaxyContext context, SerializationLevel level) {
        if (context == null) {
            return new JSONObject();
        }
        
        // Check cache first
        JSONObject cached = cache.getCachedJson(context, level.name());
        if (cached != null) {
            return cached;
        }
        
        JSONObject result;
        
        // Choose serialization method based on context size and level
        if (context.getObjectCount() > 1000 && level != SerializationLevel.MINIMAL) {
            // Use parallel processing for large contexts
            result = serializeParallel(context, level);
        } else {
            // Use sequential processing for smaller contexts
            result = serializeSequential(context, level);
        }
        
        // Cache the result
        cache.cacheJson(context, level.name(), result);
        
        return result;
    }
    
    /**
     * Asynchronously serializes galaxy context to JSON.
     * 
     * @param context The galaxy context to serialize
     * @param level The level of detail for serialization
     * @return CompletableFuture containing the JSON result
     */
    public CompletableFuture<JSONObject> serializeAsync(GalaxyContext context, SerializationLevel level) {
        return CompletableFuture.supplyAsync(() -> serialize(context, level));
    }
    
    /**
     * Serializes only objects that match specific criteria.
     * Useful for reducing payload size when only certain objects are relevant.
     * 
     * @param context The galaxy context
     * @param level Serialization level
     * @param filter Predicate to filter objects
     * @return Filtered JSON representation
     */
    public JSONObject serializeFiltered(GalaxyContext context, SerializationLevel level, 
                                       java.util.function.Predicate<GalaxyContext.ObjectInfo> filter) {
        if (context == null) {
            return new JSONObject();
        }
        
        JSONObject json = new JSONObject();
        
        // Basic galaxy information
        json.put("galaxyName", context.getGalaxyName());
        json.put("currentZone", context.getCurrentZone());
        
        // Filter and serialize objects
        List<GalaxyContext.ObjectInfo> filteredObjects = context.getObjects().stream()
            .filter(filter)
            .collect(Collectors.toList());
        
        json.put("objectCount", filteredObjects.size());
        json.put("totalObjectCount", context.getObjectCount());
        
        JSONArray objectsArray = new JSONArray();
        for (GalaxyContext.ObjectInfo obj : filteredObjects) {
            objectsArray.put(serializeObject(obj, level));
        }
        json.put("objects", objectsArray);
        
        // Build filtered type index
        if (level != SerializationLevel.MINIMAL) {
            JSONObject objectsByType = new JSONObject();
            Map<String, List<Integer>> typeMap = filteredObjects.stream()
                .collect(Collectors.groupingBy(
                    GalaxyContext.ObjectInfo::getType,
                    Collectors.mapping(GalaxyContext.ObjectInfo::getUniqueId, Collectors.toList())
                ));
            
            for (Map.Entry<String, List<Integer>> entry : typeMap.entrySet()) {
                JSONArray typeArray = new JSONArray();
                for (Integer id : entry.getValue()) {
                    typeArray.put(id);
                }
                objectsByType.put(entry.getKey(), typeArray);
            }
            json.put("objectsByType", objectsByType);
        }
        
        return json;
    }
    
    /**
     * Creates a minimal JSON representation suitable for AI context.
     * Focuses on information most relevant for AI processing.
     * 
     * @param context The galaxy context
     * @param maxObjects Maximum number of objects to include (for size limiting)
     * @return Minimal JSON representation
     */
    public JSONObject serializeForAI(GalaxyContext context, int maxObjects) {
        if (context == null) {
            return new JSONObject();
        }
        
        JSONObject json = new JSONObject();
        
        // Basic galaxy information
        json.put("galaxy", context.getGalaxyName());
        json.put("zone", context.getCurrentZone());
        
        // Get objects sorted by relevance (closer to origin first, then by type)
        List<GalaxyContext.ObjectInfo> sortedObjects = context.getObjects().stream()
            .sorted((a, b) -> {
                // Sort by distance from origin first
                float distA = a.getPosition().length();
                float distB = b.getPosition().length();
                int distCompare = Float.compare(distA, distB);
                if (distCompare != 0) return distCompare;
                
                // Then by type (enemies and collectibles first)
                return getTypeRelevance(a.getType()) - getTypeRelevance(b.getType());
            })
            .limit(maxObjects)
            .collect(Collectors.toList());
        
        // Serialize objects with AI-optimized format
        JSONArray objectsArray = new JSONArray();
        for (GalaxyContext.ObjectInfo obj : sortedObjects) {
            JSONObject objJson = new JSONObject();
            objJson.put("id", obj.getUniqueId());
            objJson.put("name", obj.getName());
            objJson.put("type", obj.getType());
            
            // Position as array for compactness
            JSONArray pos = new JSONArray();
            pos.put(Math.round(obj.getPosition().x * 10) / 10.0); // Round to 1 decimal
            pos.put(Math.round(obj.getPosition().y * 10) / 10.0);
            pos.put(Math.round(obj.getPosition().z * 10) / 10.0);
            objJson.put("pos", pos);
            
            // Include scale only if not default
            Vec3f scale = obj.getScale();
            if (scale.x != 1.0f || scale.y != 1.0f || scale.z != 1.0f) {
                JSONArray scaleArray = new JSONArray();
                scaleArray.put(Math.round(scale.x * 100) / 100.0);
                scaleArray.put(Math.round(scale.y * 100) / 100.0);
                scaleArray.put(Math.round(scale.z * 100) / 100.0);
                objJson.put("scale", scaleArray);
            }
            
            // Include most relevant tags only
            List<String> relevantTags = obj.getTags().stream()
                .filter(tag -> isRelevantTag(tag))
                .limit(3)
                .collect(Collectors.toList());
            if (!relevantTags.isEmpty()) {
                objJson.put("tags", new JSONArray(relevantTags));
            }
            
            objectsArray.put(objJson);
        }
        
        json.put("objects", objectsArray);
        json.put("count", sortedObjects.size());
        
        if (sortedObjects.size() < context.getObjectCount()) {
            json.put("truncated", true);
            json.put("totalCount", context.getObjectCount());
        }
        
        return json;
    }
    
    // Private helper methods
    
    private JSONObject serializeSequential(GalaxyContext context, SerializationLevel level) {
        JSONObject json = new JSONObject();
        
        // Basic galaxy information
        json.put("galaxyName", context.getGalaxyName());
        json.put("currentZone", context.getCurrentZone());
        json.put("objectCount", context.getObjectCount());
        
        // Serialize objects
        JSONArray objectsArray = new JSONArray();
        for (GalaxyContext.ObjectInfo obj : context.getObjects()) {
            objectsArray.put(serializeObject(obj, level));
        }
        json.put("objects", objectsArray);
        
        // Add type index for non-minimal serialization
        if (level != SerializationLevel.MINIMAL) {
            json.put("objectsByType", buildTypeIndex(context));
        }
        
        // Add spatial relationships for spatial serialization
        if (level == SerializationLevel.SPATIAL) {
            json.put("spatialRelationships", buildSpatialRelationships(context));
        }
        
        return json;
    }
    
    private JSONObject serializeParallel(GalaxyContext context, SerializationLevel level) {
        JSONObject json = new JSONObject();
        
        // Basic galaxy information
        json.put("galaxyName", context.getGalaxyName());
        json.put("currentZone", context.getCurrentZone());
        json.put("objectCount", context.getObjectCount());
        
        // Parallel object serialization
        List<GalaxyContext.ObjectInfo> objects = context.getObjects();
        List<JSONObject> serializedObjects = objects.parallelStream()
            .map(obj -> serializeObject(obj, level))
            .collect(Collectors.toList());
        
        JSONArray objectsArray = new JSONArray();
        for (JSONObject objJson : serializedObjects) {
            objectsArray.put(objJson);
        }
        json.put("objects", objectsArray);
        
        // Add type index for non-minimal serialization
        if (level != SerializationLevel.MINIMAL) {
            json.put("objectsByType", buildTypeIndexParallel(context));
        }
        
        // Add spatial relationships for spatial serialization
        if (level == SerializationLevel.SPATIAL) {
            json.put("spatialRelationships", buildSpatialRelationships(context));
        }
        
        return json;
    }
    
    private JSONObject serializeObject(GalaxyContext.ObjectInfo obj, SerializationLevel level) {
        JSONObject json = new JSONObject();
        
        // Always include basic information
        json.put("uniqueId", obj.getUniqueId());
        json.put("name", obj.getName());
        json.put("type", obj.getType());
        
        // Position (always needed)
        JSONObject position = new JSONObject();
        position.put("x", obj.getPosition().x);
        position.put("y", obj.getPosition().y);
        position.put("z", obj.getPosition().z);
        json.put("position", position);
        
        if (level == SerializationLevel.MINIMAL) {
            return json; // Return minimal info only
        }
        
        // Add display name for standard and full
        json.put("displayName", obj.getDisplayName());
        
        // Rotation and scale
        JSONObject rotation = new JSONObject();
        rotation.put("x", obj.getRotation().x);
        rotation.put("y", obj.getRotation().y);
        rotation.put("z", obj.getRotation().z);
        json.put("rotation", rotation);
        
        JSONObject scale = new JSONObject();
        scale.put("x", obj.getScale().x);
        scale.put("y", obj.getScale().y);
        scale.put("z", obj.getScale().z);
        json.put("scale", scale);
        
        // Layer and zone
        json.put("layer", obj.getLayer());
        json.put("zone", obj.getZone());
        
        // Tags
        JSONArray tags = new JSONArray();
        for (String tag : obj.getTags()) {
            tags.put(tag);
        }
        json.put("tags", tags);
        
        if (level == SerializationLevel.FULL) {
            // Include all properties for full serialization
            JSONObject properties = new JSONObject();
            for (Map.Entry<String, Object> entry : obj.getProperties().entrySet()) {
                properties.put(entry.getKey(), entry.getValue());
            }
            json.put("properties", properties);
        } else if (level == SerializationLevel.STANDARD) {
            // Include only essential properties for standard serialization
            JSONObject properties = new JSONObject();
            Map<String, Object> allProps = obj.getProperties();
            
            // Include only commonly used properties
            String[] essentialProps = {"category", "className", "Obj_arg0", "Obj_arg1", "Obj_arg2", "Obj_arg3"};
            for (String prop : essentialProps) {
                if (allProps.containsKey(prop)) {
                    properties.put(prop, allProps.get(prop));
                }
            }
            
            if (properties.length() > 0) {
                json.put("properties", properties);
            }
        }
        
        return json;
    }
    
    private JSONObject buildTypeIndex(GalaxyContext context) {
        JSONObject objectsByType = new JSONObject();
        for (Map.Entry<String, List<GalaxyContext.ObjectInfo>> entry : context.getObjectsByType().entrySet()) {
            JSONArray typeArray = new JSONArray();
            for (GalaxyContext.ObjectInfo obj : entry.getValue()) {
                typeArray.put(obj.getUniqueId());
            }
            objectsByType.put(entry.getKey(), typeArray);
        }
        return objectsByType;
    }
    
    private JSONObject buildTypeIndexParallel(GalaxyContext context) {
        JSONObject objectsByType = new JSONObject();
        
        // Use parallel streams for large type collections
        context.getObjectsByType().entrySet().parallelStream()
            .forEach(entry -> {
                JSONArray typeArray = new JSONArray();
                for (GalaxyContext.ObjectInfo obj : entry.getValue()) {
                    typeArray.put(obj.getUniqueId());
                }
                synchronized (objectsByType) {
                    objectsByType.put(entry.getKey(), typeArray);
                }
            });
        
        return objectsByType;
    }
    
    private JSONObject buildSpatialRelationships(GalaxyContext context) {
        JSONObject relationships = new JSONObject();
        
        // For spatial serialization, include only nearby object relationships
        // to avoid exponential growth in large galaxies
        JSONObject proximityMap = new JSONObject();
        
        for (GalaxyContext.ObjectInfo obj : context.getObjects()) {
            List<GalaxyContext.ObjectInfo> nearby = context.getObjectsNear(obj.getPosition(), 500.0f);
            if (nearby.size() > 1) { // More than just the object itself
                JSONArray nearbyIds = new JSONArray();
                for (GalaxyContext.ObjectInfo nearObj : nearby) {
                    if (nearObj.getUniqueId() != obj.getUniqueId()) {
                        nearbyIds.put(nearObj.getUniqueId());
                    }
                }
                if (nearbyIds.length() > 0) {
                    proximityMap.put(String.valueOf(obj.getUniqueId()), nearbyIds);
                }
            }
        }
        
        relationships.put("proximity", proximityMap);
        return relationships;
    }
    
    private int getTypeRelevance(String type) {
        // Lower numbers = higher relevance for AI processing
        switch (type.toLowerCase()) {
            case "level": return 1;
            case "enemy": return 2;
            case "collectible": return 3;
            case "platform": return 4;
            case "start": return 5;
            case "area": return 6;
            case "camera": return 7;
            case "gravity": return 8;
            default: return 10;
        }
    }
    
    private boolean isRelevantTag(String tag) {
        // Filter out less relevant tags for AI processing
        return !tag.startsWith("layer_") && 
               !tag.equals("unknown") && 
               !tag.equals("debug") &&
               tag.length() > 1;
    }
}
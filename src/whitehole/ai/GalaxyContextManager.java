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

import whitehole.editor.GalaxyEditorForm;
import whitehole.smg.object.*;
import whitehole.smg.StageArchive;
import whitehole.smg.GalaxyArchive;
import whitehole.math.Vec3f;
import whitehole.db.ObjectDB;
import org.json.JSONObject;
import org.json.JSONArray;
import java.util.*;

/**
 * Manages the extraction and organization of galaxy context information
 * for AI processing. This class builds comprehensive context about the
 * current galaxy state including objects, spatial relationships, and metadata.
 */
public class GalaxyContextManager {
    private final GalaxyEditorForm editorForm;
    private final SpatialIndex spatialIndex;
    private final GalaxyContextCache cache;
    private final OptimizedJsonSerializer jsonSerializer;
    
    public GalaxyContextManager(GalaxyEditorForm editorForm) {
        this.editorForm = editorForm;
        this.spatialIndex = new SpatialIndex();
        this.cache = GalaxyContextCache.getInstance();
        this.jsonSerializer = new OptimizedJsonSerializer();
    }
    
    /**
     * Builds a complete galaxy context from the current editor state.
     * Uses caching to improve performance for repeated requests.
     * @return GalaxyContext containing all relevant galaxy information
     */
    public GalaxyContext buildContext() {
        String galaxyName = getGalaxyName();
        String currentZone = getCurrentZone();
        
        // Try to get from cache first
        int estimatedObjectCount = getEstimatedObjectCount();
        GalaxyContext cached = cache.getCachedContext(galaxyName, null, estimatedObjectCount);
        if (cached != null) {
            // Update spatial index with cached context
            spatialIndex.clear();
            for (GalaxyContext.ObjectInfo objInfo : cached.getObjects()) {
                spatialIndex.addObject(objInfo);
            }
            return cached;
        }
        
        GalaxyContext.Builder contextBuilder = new GalaxyContext.Builder();
        
        // Set basic galaxy information
        contextBuilder.setGalaxyName(galaxyName);
        contextBuilder.setCurrentZone(currentZone);
        
        // Clear and rebuild spatial index
        spatialIndex.clear();
        
        // Extract all objects from the current galaxy/zone using lazy loading
        List<GalaxyContext.ObjectInfo> allObjects = extractAllObjectsLazy();
        
        // Add objects to context and spatial index
        for (GalaxyContext.ObjectInfo objInfo : allObjects) {
            contextBuilder.addObject(objInfo);
            spatialIndex.addObject(objInfo);
        }
        
        GalaxyContext context = contextBuilder.build();
        
        // Cache the context for future use
        cache.cacheContext(galaxyName, null, context);
        
        return context;
    }
    
    /**
     * Builds context for a specific zone only.
     * Uses caching to improve performance for repeated requests.
     * @param zoneName The zone to build context for
     * @return GalaxyContext containing zone-specific information
     */
    public GalaxyContext buildZoneContext(String zoneName) {
        String galaxyName = getGalaxyName();
        
        // Try to get from cache first
        int estimatedObjectCount = getEstimatedZoneObjectCount(zoneName);
        GalaxyContext cached = cache.getCachedContext(galaxyName, zoneName, estimatedObjectCount);
        if (cached != null) {
            // Update spatial index with cached context
            spatialIndex.clear();
            for (GalaxyContext.ObjectInfo objInfo : cached.getObjects()) {
                spatialIndex.addObject(objInfo);
            }
            return cached;
        }
        
        GalaxyContext.Builder contextBuilder = new GalaxyContext.Builder();
        
        contextBuilder.setGalaxyName(galaxyName);
        contextBuilder.setCurrentZone(zoneName);
        
        spatialIndex.clear();
        
        List<GalaxyContext.ObjectInfo> zoneObjects = extractZoneObjectsLazy(zoneName);
        
        for (GalaxyContext.ObjectInfo objInfo : zoneObjects) {
            contextBuilder.addObject(objInfo);
            spatialIndex.addObject(objInfo);
        }
        
        GalaxyContext context = contextBuilder.build();
        
        // Cache the context for future use
        cache.cacheContext(galaxyName, zoneName, context);
        
        return context;
    }
    
    /**
     * Serializes galaxy context to AI-friendly JSON format.
     * Uses optimized serializer with caching for better performance.
     * @param context The galaxy context to serialize
     * @return JSON representation of the galaxy context
     */
    public JSONObject serializeToJson(GalaxyContext context) {
        return jsonSerializer.serialize(context, OptimizedJsonSerializer.SerializationLevel.STANDARD);
    }
    
    /**
     * Serializes galaxy context with specified level of detail.
     * @param context The galaxy context to serialize
     * @param level The level of detail for serialization
     * @return JSON representation of the galaxy context
     */
    public JSONObject serializeToJson(GalaxyContext context, OptimizedJsonSerializer.SerializationLevel level) {
        return jsonSerializer.serialize(context, level);
    }
    
    /**
     * Serializes galaxy context optimized for AI processing.
     * @param context The galaxy context to serialize
     * @param maxObjects Maximum number of objects to include
     * @return Optimized JSON representation for AI
     */
    public JSONObject serializeForAI(GalaxyContext context, int maxObjects) {
        return jsonSerializer.serializeForAI(context, maxObjects);
    }
    
    /**
     * Finds objects within a specified distance of a position.
     * @param position The center position
     * @param maxDistance Maximum distance to search
     * @return List of objects within the specified distance
     */
    public List<GalaxyContext.ObjectInfo> findObjectsNear(Vec3f position, float maxDistance) {
        return spatialIndex.findObjectsNear(position, maxDistance);
    }
    
    /**
     * Finds objects within a bounding box.
     * @param min Minimum corner of the bounding box
     * @param max Maximum corner of the bounding box
     * @return List of objects within the bounding box
     */
    public List<GalaxyContext.ObjectInfo> findObjectsInBounds(Vec3f min, Vec3f max) {
        return spatialIndex.findObjectsInBounds(min, max);
    }
    
    // Private helper methods
    
    private String getGalaxyName() {
        try {
            // Use reflection to access private galaxyName field
            java.lang.reflect.Field field = GalaxyEditorForm.class.getDeclaredField("galaxyName");
            field.setAccessible(true);
            return (String) field.get(editorForm);
        } catch (Exception e) {
            return "Unknown Galaxy";
        }
    }
    
    private String getCurrentZone() {
        try {
            // Use reflection to access private curZone field
            java.lang.reflect.Field field = GalaxyEditorForm.class.getDeclaredField("curZone");
            field.setAccessible(true);
            String zone = (String) field.get(editorForm);
            return zone != null ? zone : "";
        } catch (Exception e) {
            return "";
        }
    } 
   
    private List<GalaxyContext.ObjectInfo> extractAllObjects() {
        List<GalaxyContext.ObjectInfo> allObjects = new ArrayList<>();
        
        try {
            // Access the globalObjList from GalaxyEditorForm
            java.lang.reflect.Field field = GalaxyEditorForm.class.getDeclaredField("globalObjList");
            field.setAccessible(true);
            @SuppressWarnings("unchecked")
            HashMap<Integer, AbstractObj> globalObjList = (HashMap<Integer, AbstractObj>) field.get(editorForm);
            
            // Convert each AbstractObj to ObjectInfo
            for (AbstractObj obj : globalObjList.values()) {
                GalaxyContext.ObjectInfo objInfo = convertToObjectInfo(obj);
                if (objInfo != null) {
                    allObjects.add(objInfo);
                }
            }
        } catch (Exception e) {
            System.err.println("Error extracting objects: " + e.getMessage());
        }
        
        return allObjects;
    }
    
    /**
     * Extracts all objects using lazy loading for better memory efficiency.
     */
    private List<GalaxyContext.ObjectInfo> extractAllObjectsLazy() {
        List<GalaxyContext.ObjectInfo> allObjects = new ArrayList<>();
        
        try {
            // Access the globalObjList from GalaxyEditorForm
            java.lang.reflect.Field field = GalaxyEditorForm.class.getDeclaredField("globalObjList");
            field.setAccessible(true);
            @SuppressWarnings("unchecked")
            HashMap<Integer, AbstractObj> globalObjList = (HashMap<Integer, AbstractObj>) field.get(editorForm);
            
            // Convert each AbstractObj to ObjectInfo with lazy loading optimization
            for (AbstractObj obj : globalObjList.values()) {
                GalaxyContext.ObjectInfo objInfo = convertToObjectInfoLazy(obj);
                if (objInfo != null) {
                    allObjects.add(objInfo);
                }
            }
        } catch (Exception e) {
            System.err.println("Error extracting objects: " + e.getMessage());
        }
        
        return allObjects;
    }
    
    private List<GalaxyContext.ObjectInfo> extractZoneObjects(String zoneName) {
        List<GalaxyContext.ObjectInfo> zoneObjects = new ArrayList<>();
        
        try {
            // Access zone archives
            java.lang.reflect.Field field = GalaxyEditorForm.class.getDeclaredField("zoneArchives");
            field.setAccessible(true);
            @SuppressWarnings("unchecked")
            HashMap<String, StageArchive> zoneArchives = (HashMap<String, StageArchive>) field.get(editorForm);
            
            StageArchive stageArchive = zoneArchives.get(zoneName);
            if (stageArchive != null) {
                // Extract objects from all layers in the zone
                for (List<AbstractObj> layerObjects : stageArchive.objects.values()) {
                    for (AbstractObj obj : layerObjects) {
                        GalaxyContext.ObjectInfo objInfo = convertToObjectInfo(obj);
                        if (objInfo != null) {
                            zoneObjects.add(objInfo);
                        }
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Error extracting zone objects: " + e.getMessage());
        }
        
        return zoneObjects;
    }
    
    /**
     * Extracts zone objects using lazy loading for better memory efficiency.
     */
    private List<GalaxyContext.ObjectInfo> extractZoneObjectsLazy(String zoneName) {
        List<GalaxyContext.ObjectInfo> zoneObjects = new ArrayList<>();
        
        try {
            // Access zone archives
            java.lang.reflect.Field field = GalaxyEditorForm.class.getDeclaredField("zoneArchives");
            field.setAccessible(true);
            @SuppressWarnings("unchecked")
            HashMap<String, StageArchive> zoneArchives = (HashMap<String, StageArchive>) field.get(editorForm);
            
            StageArchive stageArchive = zoneArchives.get(zoneName);
            if (stageArchive != null) {
                // Extract objects from all layers in the zone using lazy loading
                for (List<AbstractObj> layerObjects : stageArchive.objects.values()) {
                    for (AbstractObj obj : layerObjects) {
                        GalaxyContext.ObjectInfo objInfo = convertToObjectInfoLazy(obj);
                        if (objInfo != null) {
                            zoneObjects.add(objInfo);
                        }
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Error extracting zone objects: " + e.getMessage());
        }
        
        return zoneObjects;
    }
    
    private GalaxyContext.ObjectInfo convertToObjectInfo(AbstractObj obj) {
        if (obj == null) return null;
        
        try {
            GalaxyContext.ObjectInfo.ObjectInfoBuilder builder = new GalaxyContext.ObjectInfo.ObjectInfoBuilder();
            
            // Basic object information
            builder.setUniqueId(obj.uniqueID);
            builder.setName(obj.name != null ? obj.name : "");
            builder.setDisplayName(getDisplayName(obj));
            builder.setType(getObjectType(obj));
            
            // Position, rotation, scale
            builder.setPosition(obj.position != null ? new Vec3f(obj.position) : new Vec3f(0, 0, 0));
            builder.setRotation(obj.rotation != null ? new Vec3f(obj.rotation) : new Vec3f(0, 0, 0));
            builder.setScale(obj.scale != null ? new Vec3f(obj.scale) : new Vec3f(1, 1, 1));
            
            // Layer and zone information
            builder.setLayer(obj.getLayerName());
            builder.setZone(getObjectZone(obj));
            
            // Extract properties from BCSV data
            Map<String, Object> properties = extractObjectProperties(obj);
            builder.setProperties(properties);
            
            // Generate tags based on object database info and properties
            List<String> tags = generateObjectTags(obj);
            builder.setTags(tags);
            
            return builder.build();
        } catch (Exception e) {
            System.err.println("Error converting object " + obj.name + ": " + e.getMessage());
            return null;
        }
    }
    
    /**
     * Converts AbstractObj to ObjectInfo with lazy loading optimization.
     * Only loads essential information immediately, defers expensive operations.
     */
    private GalaxyContext.ObjectInfo convertToObjectInfoLazy(AbstractObj obj) {
        if (obj == null) return null;
        
        try {
            GalaxyContext.ObjectInfo.ObjectInfoBuilder builder = new GalaxyContext.ObjectInfo.ObjectInfoBuilder();
            
            // Load only essential information immediately
            builder.setUniqueId(obj.uniqueID);
            builder.setName(obj.name != null ? obj.name : "");
            builder.setType(getObjectType(obj));
            
            // Position, rotation, scale - these are frequently accessed
            builder.setPosition(obj.position != null ? new Vec3f(obj.position) : new Vec3f(0, 0, 0));
            builder.setRotation(obj.rotation != null ? new Vec3f(obj.rotation) : new Vec3f(0, 0, 0));
            builder.setScale(obj.scale != null ? new Vec3f(obj.scale) : new Vec3f(1, 1, 1));
            
            // Layer and zone information
            builder.setLayer(obj.getLayerName());
            builder.setZone(getObjectZone(obj));
            
            // Set minimal display name (can be enhanced later if needed)
            builder.setDisplayName(obj.name != null ? obj.name : "");
            
            // Set empty collections initially - will be populated on demand
            builder.setProperties(new HashMap<>());
            builder.setTags(Arrays.asList(getObjectType(obj))); // Just basic type tag
            
            return builder.build();
        } catch (Exception e) {
            System.err.println("Error converting object " + obj.name + ": " + e.getMessage());
            return null;
        }
    }
    
    private String getDisplayName(AbstractObj obj) {
        if (obj.objdbInfo != null && obj.objdbInfo.isValid()) {
            return obj.objdbInfo.toString();
        }
        return obj.name;
    }
    
    private String getObjectType(AbstractObj obj) {
        // Determine object type based on class
        if (obj instanceof LevelObj) return "level";
        if (obj instanceof AreaObj) return "area";
        if (obj instanceof CameraObj) return "camera";
        if (obj instanceof ChildObj) return "child";
        if (obj instanceof CutsceneObj) return "cutscene";
        if (obj instanceof DebugObj) return "debug";
        if (obj instanceof GravityObj) return "gravity";
        if (obj instanceof MapPartObj) return "mappart";
        if (obj instanceof PositionObj) return "position";
        if (obj instanceof SoundObj) return "sound";
        if (obj instanceof StageObj) return "stage";
        if (obj instanceof StartObj) return "start";
        
        return "unknown";
    }
    
    private String getObjectZone(AbstractObj obj) {
        if (obj.stage != null) {
            return obj.stage.stageName;
        }
        return getCurrentZone();
    }
    
    private Map<String, Object> extractObjectProperties(AbstractObj obj) {
        Map<String, Object> properties = new HashMap<>();
        
        if (obj.data != null) {
            // Extract common properties from BCSV data
            for (Integer key : obj.data.keySet()) {
                Object value = obj.data.get(key);
                if (value != null) {
                    // Convert hash key to readable name if possible
                    String keyName = whitehole.db.FieldHashes.get(key);
                    if (keyName != null && !keyName.startsWith("[")) {
                        properties.put(keyName, value);
                    } else {
                        properties.put(String.format("0x%08X", key), value);
                    }
                }
            }
        }
        
        // Add object database information if available
        if (obj.objdbInfo != null && obj.objdbInfo.isValid()) {
            properties.put("category", obj.objdbInfo.category());
            // Get className for current game (assuming SMG1 = 1, SMG2 = 2)
            int gameType = whitehole.Whitehole.getCurrentGameType();
            properties.put("className", obj.objdbInfo.className(gameType));
        }
        
        return properties;
    }
    
    private List<String> generateObjectTags(AbstractObj obj) {
        List<String> tags = new ArrayList<>();
        
        // Add type-based tags
        tags.add(getObjectType(obj));
        
        // Add category tags from object database
        if (obj.objdbInfo != null && obj.objdbInfo.isValid()) {
            String category = obj.objdbInfo.category();
            if (category != null && !category.isEmpty()) {
                tags.add(category.toLowerCase());
            }
        }
        
        // Add layer tag
        String layer = obj.getLayerName();
        if (layer != null && !layer.isEmpty()) {
            tags.add("layer_" + layer.toLowerCase());
        }
        
        // Add name-based tags for common object types
        String name = obj.name.toLowerCase();
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
        
        return tags;
    }
    
    private JSONObject serializeObjectInfo(GalaxyContext.ObjectInfo obj) {
        JSONObject json = new JSONObject();
        
        json.put("uniqueId", obj.getUniqueId());
        json.put("name", obj.getName());
        json.put("displayName", obj.getDisplayName());
        json.put("type", obj.getType());
        
        // Position
        JSONObject position = new JSONObject();
        position.put("x", obj.getPosition().x);
        position.put("y", obj.getPosition().y);
        position.put("z", obj.getPosition().z);
        json.put("position", position);
        
        // Rotation
        JSONObject rotation = new JSONObject();
        rotation.put("x", obj.getRotation().x);
        rotation.put("y", obj.getRotation().y);
        rotation.put("z", obj.getRotation().z);
        json.put("rotation", rotation);
        
        // Scale
        JSONObject scale = new JSONObject();
        scale.put("x", obj.getScale().x);
        scale.put("y", obj.getScale().y);
        scale.put("z", obj.getScale().z);
        json.put("scale", scale);
        
        json.put("layer", obj.getLayer());
        json.put("zone", obj.getZone());
        
        // Properties
        JSONObject properties = new JSONObject();
        for (Map.Entry<String, Object> entry : obj.getProperties().entrySet()) {
            properties.put(entry.getKey(), entry.getValue());
        }
        json.put("properties", properties);
        
        // Tags
        JSONArray tags = new JSONArray();
        for (String tag : obj.getTags()) {
            tags.put(tag);
        }
        json.put("tags", tags);
        
        return json;
    }
    
    private JSONObject buildSpatialRelationships(GalaxyContext context) {
        JSONObject relationships = new JSONObject();
        
        // Build proximity relationships
        JSONObject proximityMap = new JSONObject();
        for (GalaxyContext.ObjectInfo obj : context.getObjects()) {
            List<GalaxyContext.ObjectInfo> nearby = findObjectsNear(obj.getPosition(), 500.0f);
            JSONArray nearbyIds = new JSONArray();
            for (GalaxyContext.ObjectInfo nearObj : nearby) {
                if (nearObj.getUniqueId() != obj.getUniqueId()) {
                    nearbyIds.put(nearObj.getUniqueId());
                }
            }
            proximityMap.put(String.valueOf(obj.getUniqueId()), nearbyIds);
        }
        relationships.put("proximity", proximityMap);
        
        return relationships;
    }
    
    /**
     * Invalidates cached contexts when the galaxy is modified.
     * Call this method when objects are added, removed, or modified.
     */
    public void invalidateCache() {
        String galaxyName = getGalaxyName();
        cache.invalidateGalaxy(galaxyName);
    }
    
    /**
     * Invalidates cached contexts for a specific zone.
     * Call this method when a zone is modified.
     * @param zoneName The zone that was modified
     */
    public void invalidateZoneCache(String zoneName) {
        String galaxyName = getGalaxyName();
        cache.invalidateZone(galaxyName, zoneName);
    }
    
    /**
     * Gets estimated object count for cache validation.
     */
    private int getEstimatedObjectCount() {
        try {
            java.lang.reflect.Field field = GalaxyEditorForm.class.getDeclaredField("globalObjList");
            field.setAccessible(true);
            @SuppressWarnings("unchecked")
            HashMap<Integer, AbstractObj> globalObjList = (HashMap<Integer, AbstractObj>) field.get(editorForm);
            return globalObjList.size();
        } catch (Exception e) {
            return 0;
        }
    }
    
    /**
     * Gets estimated object count for a specific zone.
     */
    private int getEstimatedZoneObjectCount(String zoneName) {
        try {
            java.lang.reflect.Field field = GalaxyEditorForm.class.getDeclaredField("zoneArchives");
            field.setAccessible(true);
            @SuppressWarnings("unchecked")
            HashMap<String, StageArchive> zoneArchives = (HashMap<String, StageArchive>) field.get(editorForm);
            
            StageArchive stageArchive = zoneArchives.get(zoneName);
            if (stageArchive != null) {
                int count = 0;
                for (List<AbstractObj> layerObjects : stageArchive.objects.values()) {
                    count += layerObjects.size();
                }
                return count;
            }
        } catch (Exception e) {
            // Ignore
        }
        return 0;
    }
    
    /**
     * Gets performance statistics for monitoring.
     */
    public Map<String, Object> getPerformanceStatistics() {
        Map<String, Object> stats = new HashMap<>();
        
        // Cache statistics
        stats.putAll(cache.getStatistics());
        
        // Spatial index statistics
        stats.putAll(spatialIndex.getStatistics());
        
        // Lazy loading statistics
        stats.putAll(LazyObjectInfo.getCacheStatistics());
        
        return stats;
    }
    
    /**
     * Clears all caches to free memory.
     * Should be called when memory usage is high.
     */
    public void clearCaches() {
        cache.clearAll();
        LazyObjectInfo.clearStaticCaches();
    }
}
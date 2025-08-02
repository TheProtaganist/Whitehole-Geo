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
import java.util.*;

/**
 * Contains contextual information about the current galaxy state
 * for AI processing.
 */
public class GalaxyContext {
    private final String galaxyName;
    private final String currentZone;
    private final List<ObjectInfo> objects;
    private final Map<String, List<ObjectInfo>> objectsByType;
    private final Map<String, List<ObjectInfo>> objectsByName;
    
    private GalaxyContext(Builder builder) {
        this.galaxyName = builder.galaxyName;
        this.currentZone = builder.currentZone;
        this.objects = Collections.unmodifiableList(new ArrayList<>(builder.objects));
        this.objectsByType = Collections.unmodifiableMap(new HashMap<>(builder.objectsByType));
        this.objectsByName = Collections.unmodifiableMap(new HashMap<>(builder.objectsByName));
    }
    
    /**
     * Gets the name of the galaxy.
     */
    public String getGalaxyName() {
        return galaxyName;
    }
    
    /**
     * Gets the current zone name.
     */
    public String getCurrentZone() {
        return currentZone;
    }
    
    /**
     * Gets all objects in the galaxy.
     */
    public List<ObjectInfo> getObjects() {
        return objects;
    }
    
    /**
     * Gets objects grouped by type.
     */
    public Map<String, List<ObjectInfo>> getObjectsByType() {
        return objectsByType;
    }
    
    /**
     * Gets objects grouped by name.
     */
    public Map<String, List<ObjectInfo>> getObjectsByName() {
        return objectsByName;
    }
    
    /**
     * Gets objects of a specific type.
     */
    public List<ObjectInfo> getObjectsOfType(String type) {
        return objectsByType.getOrDefault(type, Collections.emptyList());
    }
    
    /**
     * Gets objects with a specific name.
     */
    public List<ObjectInfo> getObjectsWithName(String name) {
        return objectsByName.getOrDefault(name, Collections.emptyList());
    }
    
    /**
     * Finds objects within a certain distance of a position.
     */
    public List<ObjectInfo> getObjectsNear(Vec3f position, float maxDistance) {
        List<ObjectInfo> nearbyObjects = new ArrayList<>();
        for (ObjectInfo obj : objects) {
            Vec3f diff = new Vec3f(obj.getPosition());
            diff.subtract(position);
            float distance = diff.length();
            if (distance <= maxDistance) {
                nearbyObjects.add(obj);
            }
        }
        return nearbyObjects;
    }
    
    /**
     * Gets the total number of objects in the galaxy.
     */
    public int getObjectCount() {
        return objects.size();
    }
    
    /**
     * Builder class for creating GalaxyContext instances.
     */
    public static class Builder {
        private String galaxyName = "";
        private String currentZone = "";
        private List<ObjectInfo> objects = new ArrayList<>();
        private Map<String, List<ObjectInfo>> objectsByType = new HashMap<>();
        private Map<String, List<ObjectInfo>> objectsByName = new HashMap<>();
        
        public Builder setGalaxyName(String galaxyName) {
            this.galaxyName = galaxyName != null ? galaxyName : "";
            return this;
        }
        
        public Builder setCurrentZone(String currentZone) {
            this.currentZone = currentZone != null ? currentZone : "";
            return this;
        }
        
        public Builder addObject(ObjectInfo object) {
            this.objects.add(object);
            
            // Update type index
            String type = object.getType();
            this.objectsByType.computeIfAbsent(type, k -> new ArrayList<>()).add(object);
            
            // Update name index
            String name = object.getName();
            if (name != null && !name.isEmpty()) {
                this.objectsByName.computeIfAbsent(name, k -> new ArrayList<>()).add(object);
            }
            
            return this;
        }
        
        public Builder setObjects(List<ObjectInfo> objects) {
            this.objects.clear();
            this.objectsByType.clear();
            this.objectsByName.clear();
            
            for (ObjectInfo object : objects) {
                addObject(object);
            }
            
            return this;
        }
        
        public GalaxyContext build() {
            return new GalaxyContext(this);
        }
    }
    
    /**
     * Information about a single object in the galaxy.
     */
    public static class ObjectInfo {
        private final int uniqueId;
        private final String name;
        private final String displayName;
        private final String type;
        private final Vec3f position;
        private final Vec3f rotation;
        private final Vec3f scale;
        private final String layer;
        private final String zone;
        private final Map<String, Object> properties;
        private final List<String> tags;
        
        private ObjectInfo(ObjectInfoBuilder builder) {
            this.uniqueId = builder.uniqueId;
            this.name = builder.name;
            this.displayName = builder.displayName;
            this.type = builder.type;
            this.position = builder.position;
            this.rotation = builder.rotation;
            this.scale = builder.scale;
            this.layer = builder.layer;
            this.zone = builder.zone;
            this.properties = Collections.unmodifiableMap(new HashMap<>(builder.properties));
            this.tags = Collections.unmodifiableList(new ArrayList<>(builder.tags));
        }
        
        // Getters
        public int getUniqueId() { return uniqueId; }
        public String getName() { return name; }
        public String getDisplayName() { return displayName; }
        public String getType() { return type; }
        public Vec3f getPosition() { return position; }
        public Vec3f getRotation() { return rotation; }
        public Vec3f getScale() { return scale; }
        public String getLayer() { return layer; }
        public String getZone() { return zone; }
        public Map<String, Object> getProperties() { return properties; }
        public List<String> getTags() { return tags; }
        
        /**
         * Builder for ObjectInfo instances.
         */
        public static class ObjectInfoBuilder {
            private int uniqueId;
            private String name = "";
            private String displayName = "";
            private String type = "";
            private Vec3f position = new Vec3f(0, 0, 0);
            private Vec3f rotation = new Vec3f(0, 0, 0);
            private Vec3f scale = new Vec3f(1, 1, 1);
            private String layer = "";
            private String zone = "";
            private Map<String, Object> properties = new HashMap<>();
            private List<String> tags = new ArrayList<>();
            
            public ObjectInfoBuilder setUniqueId(int uniqueId) {
                this.uniqueId = uniqueId;
                return this;
            }
            
            public ObjectInfoBuilder setName(String name) {
                this.name = name != null ? name : "";
                return this;
            }
            
            public ObjectInfoBuilder setDisplayName(String displayName) {
                this.displayName = displayName != null ? displayName : "";
                return this;
            }
            
            public ObjectInfoBuilder setType(String type) {
                this.type = type != null ? type : "";
                return this;
            }
            
            public ObjectInfoBuilder setPosition(Vec3f position) {
                this.position = position != null ? position : new Vec3f(0, 0, 0);
                return this;
            }
            
            public ObjectInfoBuilder setRotation(Vec3f rotation) {
                this.rotation = rotation != null ? rotation : new Vec3f(0, 0, 0);
                return this;
            }
            
            public ObjectInfoBuilder setScale(Vec3f scale) {
                this.scale = scale != null ? scale : new Vec3f(1, 1, 1);
                return this;
            }
            
            public ObjectInfoBuilder setLayer(String layer) {
                this.layer = layer != null ? layer : "";
                return this;
            }
            
            public ObjectInfoBuilder setZone(String zone) {
                this.zone = zone != null ? zone : "";
                return this;
            }
            
            public ObjectInfoBuilder addProperty(String key, Object value) {
                this.properties.put(key, value);
                return this;
            }
            
            public ObjectInfoBuilder setProperties(Map<String, Object> properties) {
                this.properties = new HashMap<>(properties);
                return this;
            }
            
            public ObjectInfoBuilder addTag(String tag) {
                this.tags.add(tag);
                return this;
            }
            
            public ObjectInfoBuilder setTags(List<String> tags) {
                this.tags = new ArrayList<>(tags);
                return this;
            }
            
            public ObjectInfo build() {
                return new ObjectInfo(this);
            }
        }
    }
}
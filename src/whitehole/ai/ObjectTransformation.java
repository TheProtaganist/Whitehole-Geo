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
import java.util.HashMap;
import java.util.Map;

/**
 * Represents a transformation to be applied to a galaxy object.
 */
public class ObjectTransformation {
    
    public enum TransformationType {
        TRANSLATE,      // Move object by delta
        ROTATE,         // Rotate object by delta
        SCALE,          // Scale object by factor
        SET_POSITION,   // Set absolute position
        SET_ROTATION,   // Set absolute rotation
        SET_SCALE,      // Set absolute scale
        ADD,            // Add a new object to the level
        PROPERTY_CHANGE,// Change object properties
        BATCH_OPERATION // Multiple operations on same object
    }
    
    private final int objectId;
    private final TransformationType type;
    private final Vec3f vectorValue;
    private final Map<String, Object> propertyChanges;
    private final String addObjectType;
    private final String description;

    
    private ObjectTransformation(Builder builder) {
        this.objectId = builder.objectId;
        this.type = builder.type;
        this.vectorValue = builder.vectorValue;
        this.propertyChanges = new HashMap<>(builder.propertyChanges);
        this.addObjectType = builder.addObjectType;
        this.description = builder.description;
    }
    
    /**
     * Gets the ID of the object to transform.
     */
    public int getObjectId() {
        return objectId;
    }
    
    /**
     * Gets the type of transformation.
     */
    public TransformationType getType() {
        return type;
    }
    
    /**
     * Gets the vector value for position, rotation, or scale transformations.
     */
    public Vec3f getVectorValue() {
        return vectorValue;
    }
    
    /**
     * Gets the property changes for PROPERTY_CHANGE transformations.
     */
    public String getAddObjectType() {
        return addObjectType;
    }
    
    public Map<String, Object> getPropertyChanges() {
        return new HashMap<>(propertyChanges);
    }
    
    /**
     * Gets a human-readable description of this transformation.
     */
    public String getDescription() {
        return description;
    }
    
    /**
     * Builder class for creating ObjectTransformation instances.
     */
    public static class Builder {
        private int objectId;
        private TransformationType type;
        private Vec3f vectorValue;
        private Map<String, Object> propertyChanges = new HashMap<>();
        private String addObjectType = "";
        private String description = "";
        
        public Builder setObjectId(int objectId) {
            this.objectId = objectId;
            return this;
        }
        
        public Builder setType(TransformationType type) {
            this.type = type;
            return this;
        }
        
        public Builder setVectorValue(Vec3f vectorValue) {
            this.vectorValue = vectorValue;
            return this;
        }
        
        public Builder setVectorValue(float x, float y, float z) {
            this.vectorValue = new Vec3f(x, y, z);
            return this;
        }
        
        public Builder addPropertyChange(String property, Object value) {
            this.propertyChanges.put(property, value);
            return this;
        }
        
        public Builder setPropertyChanges(Map<String, Object> propertyChanges) {
            this.propertyChanges = new HashMap<>(propertyChanges);
            return this;
        }
        
        public Builder setAddObjectType(String objectType) {
            this.addObjectType = objectType;
            return this;
        }
        
        public Builder setDescription(String description) {
            this.description = description != null ? description : "";
            return this;
        }
        
        public ObjectTransformation build() {
            if (type == null) {
                throw new IllegalStateException("TransformationType must be set");
            }
            return new ObjectTransformation(this);
        }
    }
    
    /**
     * Creates a translation transformation.
     */
    public static ObjectTransformation translate(int objectId, Vec3f delta, String description) {
        return new Builder()
                .setObjectId(objectId)
                .setType(TransformationType.TRANSLATE)
                .setVectorValue(delta)
                .setDescription(description)
                .build();
    }
    
    /**
     * Creates a rotation transformation.
     */
    public static ObjectTransformation rotate(int objectId, Vec3f delta, String description) {
        return new Builder()
                .setObjectId(objectId)
                .setType(TransformationType.ROTATE)
                .setVectorValue(delta)
                .setDescription(description)
                .build();
    }
    
    /**
     * Creates a scale transformation.
     */
    public static ObjectTransformation scale(int objectId, Vec3f factor, String description) {
        return new Builder()
                .setObjectId(objectId)
                .setType(TransformationType.SCALE)
                .setVectorValue(factor)
                .setDescription(description)
                .build();
    }
    
    /**
     * Creates a property change transformation.
     */
    public static ObjectTransformation addObject(String objectType, Vec3f position, String description) {
        return new Builder()
                .setAddObjectType(objectType)
                .setType(TransformationType.ADD)
                .setVectorValue(position)
                .setDescription(description)
                .build();
    }
    
    public static ObjectTransformation changeProperty(int objectId, Map<String, Object> properties, String description) {
        return new Builder()
                .setObjectId(objectId)
                .setType(TransformationType.PROPERTY_CHANGE)
                .setPropertyChanges(properties)
                .setDescription(description)
                .build();
    }
}
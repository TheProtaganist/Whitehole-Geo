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
import whitehole.smg.object.PathObj;
import whitehole.smg.object.PathPointObj;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * ObjectConstraintValidator provides validation for object transformations
 * to ensure they don't violate game constraints or create invalid states.
 */
public class ObjectConstraintValidator {
    
    // Default constraint values - these can be adjusted based on game requirements
    private static final float MAX_COORDINATE = 1000000f;
    private static final float MIN_SCALE = 0.001f;
    private static final float MAX_SCALE = 1000f;
    private static final float MAX_ROTATION = 360f;
    
    /**
     * Validates a position transformation.
     * 
     * @param obj The object being transformed
     * @param newPosition The new position to validate
     * @return ValidationResult containing any issues found
     */
    public static ValidationResult validatePosition(AbstractObj obj, Vec3f newPosition) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        
        if (newPosition == null) {
            errors.add("Position cannot be null");
            return new ValidationResult(false, errors, warnings);
        }
        
        // Check for NaN or infinite values
        if (!Float.isFinite(newPosition.x) || !Float.isFinite(newPosition.y) || !Float.isFinite(newPosition.z)) {
            errors.add("Position contains invalid values (NaN or infinite)");
        }
        
        // Check coordinate bounds
        if (Math.abs(newPosition.x) > MAX_COORDINATE || 
            Math.abs(newPosition.y) > MAX_COORDINATE || 
            Math.abs(newPosition.z) > MAX_COORDINATE) {
            warnings.add("Position coordinates are very large and may cause issues");
        }
        
        // Object-specific position validation
        validateObjectSpecificPosition(obj, newPosition, warnings);
        
        return new ValidationResult(errors.isEmpty(), errors, warnings);
    }
    
    /**
     * Validates a rotation transformation.
     * 
     * @param obj The object being transformed
     * @param newRotation The new rotation to validate
     * @return ValidationResult containing any issues found
     */
    public static ValidationResult validateRotation(AbstractObj obj, Vec3f newRotation) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        
        if (newRotation == null) {
            errors.add("Rotation cannot be null");
            return new ValidationResult(false, errors, warnings);
        }
        
        // Check for NaN or infinite values
        if (!Float.isFinite(newRotation.x) || !Float.isFinite(newRotation.y) || !Float.isFinite(newRotation.z)) {
            errors.add("Rotation contains invalid values (NaN or infinite)");
        }
        
        // Normalize rotation values and warn about large rotations
        if (Math.abs(newRotation.x) > MAX_ROTATION || 
            Math.abs(newRotation.y) > MAX_ROTATION || 
            Math.abs(newRotation.z) > MAX_ROTATION) {
            warnings.add("Rotation values are large - consider normalizing to 0-360 degrees");
        }
        
        return new ValidationResult(errors.isEmpty(), errors, warnings);
    }
    
    /**
     * Validates a scale transformation.
     * 
     * @param obj The object being transformed
     * @param newScale The new scale to validate
     * @return ValidationResult containing any issues found
     */
    public static ValidationResult validateScale(AbstractObj obj, Vec3f newScale) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        
        if (newScale == null) {
            errors.add("Scale cannot be null");
            return new ValidationResult(false, errors, warnings);
        }
        
        // Check for NaN or infinite values
        if (!Float.isFinite(newScale.x) || !Float.isFinite(newScale.y) || !Float.isFinite(newScale.z)) {
            errors.add("Scale contains invalid values (NaN or infinite)");
        }
        
        // Check scale bounds
        if (newScale.x <= 0 || newScale.y <= 0 || newScale.z <= 0) {
            errors.add("Scale values must be positive");
        } else {
            if (newScale.x < MIN_SCALE || newScale.y < MIN_SCALE || newScale.z < MIN_SCALE) {
                warnings.add("Scale values are very small and may cause rendering issues");
            }
            if (newScale.x > MAX_SCALE || newScale.y > MAX_SCALE || newScale.z > MAX_SCALE) {
                warnings.add("Scale values are very large and may cause performance issues");
            }
        }
        
        return new ValidationResult(errors.isEmpty(), errors, warnings);
    }
    
    /**
     * Validates property changes for an object.
     * 
     * @param obj The object being modified
     * @param propertyChanges The properties to change
     * @return ValidationResult containing any issues found
     */
    public static ValidationResult validatePropertyChanges(AbstractObj obj, Map<String, Object> propertyChanges) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        
        if (propertyChanges == null || propertyChanges.isEmpty()) {
            errors.add("Property changes cannot be null or empty");
            return new ValidationResult(false, errors, warnings);
        }
        
        for (Map.Entry<String, Object> entry : propertyChanges.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            
            if (key == null || key.trim().isEmpty()) {
                errors.add("Property key cannot be null or empty");
                continue;
            }
            
            // Validate specific property types
            ValidationResult propertyResult = validatePropertyValue(obj, key, value);
            errors.addAll(propertyResult.getErrors());
            warnings.addAll(propertyResult.getWarnings());
        }
        
        return new ValidationResult(errors.isEmpty(), errors, warnings);
    }
    
    /**
     * Validates object relationships and integrity.
     * 
     * @param obj The object to validate
     * @param globalObjList All objects in the galaxy for relationship checking
     * @return ValidationResult containing any issues found
     */
    public static ValidationResult validateObjectRelationships(AbstractObj obj, Map<Integer, AbstractObj> globalObjList) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        
        // Check path relationships for path objects
        // Note: PathObj validation would go here if needed
        // This requires access to path-specific data structures
        
        // Check group relationships
        Object groupId = obj.data.get("GroupId");
        if (groupId instanceof Number && ((Number) groupId).intValue() >= 0) {
            // Validate that other objects in the group still exist
            // This is a placeholder for more complex group validation
        }
        
        // Check linked object references
        validateLinkedObjects(obj, globalObjList, warnings);
        
        return new ValidationResult(errors.isEmpty(), errors, warnings);
    }
    
    /**
     * Performs object-specific position validation.
     */
    private static void validateObjectSpecificPosition(AbstractObj obj, Vec3f position, List<String> warnings) {
        // Add object-specific position constraints here
        // For example, certain objects might need to be above ground level
        
        if (obj.name != null) {
            // Example: Water objects should typically be at or below y=0
            if (obj.name.toLowerCase().contains("water") && position.y > 100) {
                warnings.add("Water object positioned high above typical water level");
            }
            
            // Example: Ground objects should typically be near y=0
            if (obj.name.toLowerCase().contains("ground") && Math.abs(position.y) > 1000) {
                warnings.add("Ground object positioned far from typical ground level");
            }
        }
    }
    
    /**
     * Validates a specific property value.
     */
    private static ValidationResult validatePropertyValue(AbstractObj obj, String property, Object value) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        
        if (value == null) {
            warnings.add("Property '" + property + "' is being set to null");
            return new ValidationResult(true, errors, warnings);
        }
        
        // Validate numeric properties
        if (value instanceof Float) {
            float floatValue = (Float) value;
            if (!Float.isFinite(floatValue)) {
                errors.add("Property '" + property + "' contains invalid float value");
            }
        } else if (value instanceof Integer) {
            // Integer validation if needed
        } else if (value instanceof String) {
            String stringValue = (String) value;
            if (stringValue.length() > 1000) {
                warnings.add("Property '" + property + "' has very long string value");
            }
        }
        
        // Property-specific validation
        validateSpecificProperty(obj, property, value, errors, warnings);
        
        return new ValidationResult(errors.isEmpty(), errors, warnings);
    }
    
    /**
     * Validates specific known properties.
     */
    private static void validateSpecificProperty(AbstractObj obj, String property, Object value, 
                                               List<String> errors, List<String> warnings) {
        switch (property.toLowerCase()) {
            case "l_id":
                if (value instanceof Number && ((Number) value).intValue() < 0) {
                    warnings.add("Layer ID is negative, which may cause issues");
                }
                break;
            case "sw_appear":
            case "sw_dead":
            case "sw_a":
            case "sw_b":
                if (value instanceof Number) {
                    int switchValue = ((Number) value).intValue();
                    if (switchValue < -1 || switchValue > 999) {
                        warnings.add("Switch value " + switchValue + " is outside typical range (-1 to 999)");
                    }
                }
                break;
            case "groupid":
                if (value instanceof Number && ((Number) value).intValue() < -1) {
                    warnings.add("Group ID should typically be -1 or positive");
                }
                break;
        }
    }
    
    /**
     * Validates linked object references.
     */
    private static void validateLinkedObjects(AbstractObj obj, Map<Integer, AbstractObj> globalObjList, 
                                            List<String> warnings) {
        // Check if referenced objects still exist
        Object groupId = obj.data.get("GroupId");
        if (groupId instanceof Number && ((Number) groupId).intValue() >= 0) {
            // Could check if other objects with same group ID exist
            // This is a placeholder for more sophisticated relationship checking
        }
        
        // Check path references for objects that use paths
        Object pathId = obj.data.get("CommonPath_ID");
        if (pathId instanceof Number && ((Number) pathId).intValue() >= 0) {
            // Could validate that the referenced path exists
            warnings.add("Object references path - ensure path integrity is maintained");
        }
    }
    
    /**
     * Validates a batch of transformations for conflicts and impossible operations.
     * This method checks for transformations that might conflict with each other
     * or create impossible states when applied together.
     */
    public static ValidationResult validateBatchTransformations(List<ObjectTransformation> transformations, 
                                                              Map<Integer, AbstractObj> globalObjList) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        
        if (transformations == null || transformations.isEmpty()) {
            return new ValidationResult(true, errors, warnings);
        }
        
        // Group transformations by object ID to detect conflicts
        Map<Integer, List<ObjectTransformation>> transformsByObject = new java.util.HashMap<>();
        for (ObjectTransformation transform : transformations) {
            int objectId = transform.getObjectId();
            transformsByObject.computeIfAbsent(objectId, k -> new ArrayList<>()).add(transform);
        }
        
        // Check for conflicting transformations on the same object
        for (Map.Entry<Integer, List<ObjectTransformation>> entry : transformsByObject.entrySet()) {
            int objectId = entry.getKey();
            List<ObjectTransformation> objectTransforms = entry.getValue();
            
            if (objectTransforms.size() > 1) {
                ValidationResult conflictResult = validateTransformationConflicts(objectId, objectTransforms, globalObjList);
                errors.addAll(conflictResult.getErrors());
                warnings.addAll(conflictResult.getWarnings());
            }
        }
        
        // Check for impossible operations
        for (ObjectTransformation transform : transformations) {
            ValidationResult impossibleResult = validateImpossibleOperations(transform, globalObjList);
            errors.addAll(impossibleResult.getErrors());
            warnings.addAll(impossibleResult.getWarnings());
        }
        
        // Check for ADD transformations that might create too many objects
        long addCount = transformations.stream()
            .filter(t -> t.getType() == ObjectTransformation.TransformationType.ADD)
            .count();
        
        if (addCount > 100) {
            warnings.add("Creating " + addCount + " new objects - this may impact performance");
        } else if (addCount > 500) {
            errors.add("Attempting to create " + addCount + " objects - this exceeds recommended limits");
        }
        
        return new ValidationResult(errors.isEmpty(), errors, warnings);
    }
    
    /**
     * Validates transformations on a single object for conflicts.
     */
    private static ValidationResult validateTransformationConflicts(int objectId, 
                                                                  List<ObjectTransformation> transforms,
                                                                  Map<Integer, AbstractObj> globalObjList) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        
        AbstractObj obj = globalObjList.get(objectId);
        if (obj == null) {
            errors.add("Object " + objectId + " not found for transformation validation");
            return new ValidationResult(false, errors, warnings);
        }
        
        // Check for conflicting position transformations
        boolean hasTranslate = false;
        boolean hasSetPosition = false;
        boolean hasRotate = false;
        boolean hasSetRotation = false;
        boolean hasScale = false;
        boolean hasSetScale = false;
        
        for (ObjectTransformation transform : transforms) {
            switch (transform.getType()) {
                case TRANSLATE:
                    hasTranslate = true;
                    break;
                case SET_POSITION:
                    hasSetPosition = true;
                    break;
                case ROTATE:
                    hasRotate = true;
                    break;
                case SET_ROTATION:
                    hasSetRotation = true;
                    break;
                case SCALE:
                    hasScale = true;
                    break;
                case SET_SCALE:
                    hasSetScale = true;
                    break;
            }
        }
        
        // Warn about potentially conflicting operations
        if (hasTranslate && hasSetPosition) {
            warnings.add("Object " + obj.name + " has both translate and set position operations - final position may be unexpected");
        }
        
        if (hasRotate && hasSetRotation) {
            warnings.add("Object " + obj.name + " has both rotate and set rotation operations - final rotation may be unexpected");
        }
        
        if (hasScale && hasSetScale) {
            warnings.add("Object " + obj.name + " has both scale and set scale operations - final scale may be unexpected");
        }
        
        return new ValidationResult(errors.isEmpty(), errors, warnings);
    }
    
    /**
     * Validates for impossible operations that cannot be performed.
     */
    private static ValidationResult validateImpossibleOperations(ObjectTransformation transform,
                                                               Map<Integer, AbstractObj> globalObjList) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        
        if (transform.getType() == ObjectTransformation.TransformationType.ADD) {
            // Validate ADD operations
            String objectType = transform.getAddObjectType();
            if (objectType == null || objectType.trim().isEmpty()) {
                errors.add("Cannot add object with empty or null type");
            } else {
                // Check if object type is known/valid
                if (!isValidObjectType(objectType)) {
                    warnings.add("Object type '" + objectType + "' may not be recognized by the game");
                }
            }
            
            // Check position validity for new objects
            Vec3f position = transform.getVectorValue();
            if (position != null) {
                if (Math.abs(position.x) > MAX_COORDINATE || 
                    Math.abs(position.y) > MAX_COORDINATE || 
                    Math.abs(position.z) > MAX_COORDINATE) {
                    errors.add("Cannot create object at extreme coordinates: " + formatVector(position));
                }
            }
        } else {
            // Validate operations on existing objects
            AbstractObj obj = globalObjList.get(transform.getObjectId());
            if (obj == null) {
                errors.add("Cannot transform non-existent object with ID " + transform.getObjectId());
            } else {
                // Check if the object can be transformed
                if (isReadOnlyObject(obj)) {
                    errors.add("Cannot modify read-only object: " + obj.name);
                }
            }
        }
        
        return new ValidationResult(errors.isEmpty(), errors, warnings);
    }
    
    /**
     * Checks if an object type is valid/recognized.
     */
    private static boolean isValidObjectType(String objectType) {
        if (objectType == null) return false;
        
        String lowerType = objectType.toLowerCase();
        
        // Common valid object types
        String[] validTypes = {
            "coin", "goomba", "koopa", "star", "platform", "block", "pipe", "switch",
            "enemy", "item", "decoration", "camera", "area", "path", "light", "sound",
            "powerup", "collectible", "hazard", "npc", "boss", "vehicle", "effect"
        };
        
        for (String validType : validTypes) {
            if (lowerType.contains(validType)) {
                return true;
            }
        }
        
        // If not in common types, it might still be valid but warn the user
        return false;
    }
    
    /**
     * Checks if an object is read-only and cannot be modified.
     */
    private static boolean isReadOnlyObject(AbstractObj obj) {
        if (obj == null) return true;
        
        // Some objects might be read-only based on their type or properties
        // This is a placeholder for more sophisticated read-only detection
        return false;
    }
    
    /**
     * Formats a vector for display in error messages.
     */
    private static String formatVector(Vec3f vector) {
        return String.format("(%.1f, %.1f, %.1f)", vector.x, vector.y, vector.z);
    }
    
    /**
     * Result of validation operations.
     */
    public static class ValidationResult {
        private final boolean valid;
        private final List<String> errors;
        private final List<String> warnings;
        
        public ValidationResult(boolean valid, List<String> errors, List<String> warnings) {
            this.valid = valid;
            this.errors = errors != null ? new ArrayList<>(errors) : new ArrayList<>();
            this.warnings = warnings != null ? new ArrayList<>(warnings) : new ArrayList<>();
        }
        
        public boolean isValid() { return valid; }
        public List<String> getErrors() { return new ArrayList<>(errors); }
        public List<String> getWarnings() { return new ArrayList<>(warnings); }
        public boolean hasErrors() { return !errors.isEmpty(); }
        public boolean hasWarnings() { return !warnings.isEmpty(); }
        
        /**
         * Combines this result with another validation result.
         */
        public ValidationResult combine(ValidationResult other) {
            List<String> combinedErrors = new ArrayList<>(this.errors);
            combinedErrors.addAll(other.errors);
            
            List<String> combinedWarnings = new ArrayList<>(this.warnings);
            combinedWarnings.addAll(other.warnings);
            
            return new ValidationResult(this.valid && other.valid, combinedErrors, combinedWarnings);
        }
    }
}
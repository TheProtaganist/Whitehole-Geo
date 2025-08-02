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
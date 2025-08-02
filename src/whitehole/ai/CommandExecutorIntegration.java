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
import whitehole.math.Vec3f;
import whitehole.smg.object.AbstractObj;
import whitehole.util.PropertyGrid;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * CommandExecutorIntegration provides helper methods for integrating
 * the CommandExecutor with the existing GalaxyEditorForm.
 */
public class CommandExecutorIntegration {
    
    /**
     * Creates a CommandExecutor instance from a GalaxyEditorForm.
     * This method uses reflection to access the necessary fields from GalaxyEditorForm.
     * 
     * @param editorForm The galaxy editor form
     * @return A configured CommandExecutor instance
     */
    public static CommandExecutor createFromEditorForm(GalaxyEditorForm editorForm) {
        try {
            // Access the globalObjList field using reflection
            java.lang.reflect.Field globalObjListField = GalaxyEditorForm.class.getDeclaredField("globalObjList");
            globalObjListField.setAccessible(true);
            @SuppressWarnings("unchecked")
            HashMap<Integer, AbstractObj> globalObjList = (HashMap<Integer, AbstractObj>) globalObjListField.get(editorForm);
            
            // Access the pnlObjectSettings field using reflection
            java.lang.reflect.Field pnlObjectSettingsField = GalaxyEditorForm.class.getDeclaredField("pnlObjectSettings");
            pnlObjectSettingsField.setAccessible(true);
            PropertyGrid propertyGrid = (PropertyGrid) pnlObjectSettingsField.get(editorForm);
            
            return new CommandExecutor(editorForm, globalObjList, propertyGrid);
            
        } catch (Exception e) {
            throw new RuntimeException("Failed to create CommandExecutor from GalaxyEditorForm", e);
        }
    }
    
    /**
     * Executes AI transformations and provides user feedback.
     * 
     * @param editorForm The galaxy editor form
     * @param transformations List of transformations to apply
     * @param commandDescription Description of the AI command
     * @return ExecutionSummary with results and feedback
     */
    public static ExecutionSummary executeAICommand(GalaxyEditorForm editorForm, 
                                                   List<ObjectTransformation> transformations,
                                                   String commandDescription) {
        
        CommandExecutor executor = createFromEditorForm(editorForm);
        CommandExecutor.ExecutionResult result = executor.executeTransformations(transformations);
        
        // Create summary for user feedback
        ExecutionSummary summary = new ExecutionSummary(
            result.isSuccess() || result.isPartialSuccess(),
            result.getMessage(),
            result.getSuccessCount(),
            transformations.size(),
            result.getErrors(),
            result.getWarnings(),
            commandDescription
        );
        
        // Update editor UI if needed
        if (summary.isSuccess() && summary.getSuccessCount() > 0) {
            // Trigger selection change to update property grid
            try {
                java.lang.reflect.Method selectionChangedMethod = GalaxyEditorForm.class.getDeclaredMethod("selectionChanged");
                selectionChangedMethod.setAccessible(true);
                selectionChangedMethod.invoke(editorForm);
            } catch (Exception e) {
                System.err.println("Warning: Could not update selection after AI command: " + e.getMessage());
            }
        }
        
        return summary;
    }
    
    /**
     * Validates transformations before execution.
     * 
     * @param editorForm The galaxy editor form
     * @param transformations List of transformations to validate
     * @return ValidationSummary with validation results
     */
    public static ValidationSummary validateTransformations(GalaxyEditorForm editorForm,
                                                           List<ObjectTransformation> transformations) {
        try {
            // Access the globalObjList field
            java.lang.reflect.Field globalObjListField = GalaxyEditorForm.class.getDeclaredField("globalObjList");
            globalObjListField.setAccessible(true);
            @SuppressWarnings("unchecked")
            HashMap<Integer, AbstractObj> globalObjList = (HashMap<Integer, AbstractObj>) globalObjListField.get(editorForm);
            
            int validCount = 0;
            int invalidCount = 0;
            StringBuilder errors = new StringBuilder();
            StringBuilder warnings = new StringBuilder();
            
            for (ObjectTransformation transformation : transformations) {
                AbstractObj obj = globalObjList.get(transformation.getObjectId());
                if (obj == null) {
                    invalidCount++;
                    errors.append("Object ").append(transformation.getObjectId()).append(" not found. ");
                    continue;
                }
                
                // Perform validation based on transformation type
                ObjectConstraintValidator.ValidationResult validationResult = validateSingleTransformation(obj, transformation);
                
                if (validationResult.isValid()) {
                    validCount++;
                } else {
                    invalidCount++;
                    errors.append("Object ").append(transformation.getObjectId()).append(": ")
                          .append(String.join(", ", validationResult.getErrors())).append(" ");
                }
                
                if (validationResult.hasWarnings()) {
                    warnings.append("Object ").append(transformation.getObjectId()).append(": ")
                            .append(String.join(", ", validationResult.getWarnings())).append(" ");
                }
            }
            
            return new ValidationSummary(
                invalidCount == 0,
                validCount,
                invalidCount,
                errors.toString().trim(),
                warnings.toString().trim()
            );
            
        } catch (Exception e) {
            return new ValidationSummary(false, 0, transformations.size(), 
                                       "Validation failed: " + e.getMessage(), "");
        }
    }
    
    /**
     * Validates a single transformation.
     */
    private static ObjectConstraintValidator.ValidationResult validateSingleTransformation(AbstractObj obj, ObjectTransformation transformation) {
        switch (transformation.getType()) {
            case TRANSLATE:
                Vec3f newPos = new Vec3f(obj.position);
                newPos.add(transformation.getVectorValue());
                return ObjectConstraintValidator.validatePosition(obj, newPos);
            case SET_POSITION:
                return ObjectConstraintValidator.validatePosition(obj, transformation.getVectorValue());
            case ROTATE:
                Vec3f newRot = new Vec3f(obj.rotation);
                newRot.add(transformation.getVectorValue());
                return ObjectConstraintValidator.validateRotation(obj, newRot);
            case SET_ROTATION:
                return ObjectConstraintValidator.validateRotation(obj, transformation.getVectorValue());
            case SCALE:
                Vec3f newScale = new Vec3f(obj.scale);
                Vec3f factor = transformation.getVectorValue();
                newScale.x *= factor.x;
                newScale.y *= factor.y;
                newScale.z *= factor.z;
                return ObjectConstraintValidator.validateScale(obj, newScale);
            case SET_SCALE:
                return ObjectConstraintValidator.validateScale(obj, transformation.getVectorValue());
            case PROPERTY_CHANGE:
                return ObjectConstraintValidator.validatePropertyChanges(obj, transformation.getPropertyChanges());
            default:
                return new ObjectConstraintValidator.ValidationResult(false, 
                    List.of("Unknown transformation type: " + transformation.getType()), List.of());
        }
    }
    
    /**
     * Summary of command execution results.
     */
    public static class ExecutionSummary {
        private final boolean success;
        private final String message;
        private final int successCount;
        private final int totalCount;
        private final List<String> errors;
        private final List<String> warnings;
        private final String commandDescription;
        
        public ExecutionSummary(boolean success, String message, int successCount, int totalCount,
                               List<String> errors, List<String> warnings, String commandDescription) {
            this.success = success;
            this.message = message;
            this.successCount = successCount;
            this.totalCount = totalCount;
            this.errors = errors;
            this.warnings = warnings;
            this.commandDescription = commandDescription;
        }
        
        public boolean isSuccess() { return success; }
        public String getMessage() { return message; }
        public int getSuccessCount() { return successCount; }
        public int getTotalCount() { return totalCount; }
        public List<String> getErrors() { return errors; }
        public List<String> getWarnings() { return warnings; }
        public String getCommandDescription() { return commandDescription; }
        public boolean hasErrors() { return errors != null && !errors.isEmpty(); }
        public boolean hasWarnings() { return warnings != null && !warnings.isEmpty(); }
        
        /**
         * Gets a user-friendly summary message.
         */
        public String getUserFeedback() {
            if (success) {
                return String.format("Successfully applied %d transformations. %s", successCount, message);
            } else if (successCount > 0) {
                return String.format("Partially successful: applied %d of %d transformations. %s", 
                                   successCount, totalCount, message);
            } else {
                return String.format("Failed to apply transformations. %s", message);
            }
        }
    }
    
    /**
     * Summary of validation results.
     */
    public static class ValidationSummary {
        private final boolean allValid;
        private final int validCount;
        private final int invalidCount;
        private final String errors;
        private final String warnings;
        
        public ValidationSummary(boolean allValid, int validCount, int invalidCount, String errors, String warnings) {
            this.allValid = allValid;
            this.validCount = validCount;
            this.invalidCount = invalidCount;
            this.errors = errors;
            this.warnings = warnings;
        }
        
        public boolean isAllValid() { return allValid; }
        public int getValidCount() { return validCount; }
        public int getInvalidCount() { return invalidCount; }
        public String getErrors() { return errors; }
        public String getWarnings() { return warnings; }
        public boolean hasErrors() { return errors != null && !errors.trim().isEmpty(); }
        public boolean hasWarnings() { return warnings != null && !warnings.trim().isEmpty(); }
    }
}
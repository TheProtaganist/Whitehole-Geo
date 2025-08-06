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
import whitehole.editor.GalaxyEditorForm.IUndo;
import whitehole.math.Vec3f;
import whitehole.smg.object.AbstractObj;
import whitehole.util.PropertyGrid;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * CommandExecutor applies AI-generated transformations to galaxy objects
 * and integrates with the existing undo system.
 * Supports batch operations with progress tracking and cancellation.
 */
public class CommandExecutor {
    
    private final GalaxyEditorForm editorForm;
    private final Map<Integer, AbstractObj> globalObjList;
    private final PropertyGrid propertyGrid;
    
    // Batch operation configuration
    private static final int BATCH_SIZE_THRESHOLD = 50; // When to use batch processing
    private static final int PROGRESS_UPDATE_INTERVAL = 10; // Update progress every N operations
    private static final int MAX_CONCURRENT_VALIDATIONS = 4; // Parallel validation threads
    
    // Current batch operation state
    private volatile BatchOperationContext currentBatchContext;
    
    /**
     * Creates a new CommandExecutor.
     * 
     * @param editorForm The galaxy editor form
     * @param globalObjList The global object list from the editor
     * @param propertyGrid The property grid for UI updates
     */
    public CommandExecutor(GalaxyEditorForm editorForm, Map<Integer, AbstractObj> globalObjList, PropertyGrid propertyGrid) {
        this.editorForm = editorForm;
        this.globalObjList = globalObjList;
        this.propertyGrid = propertyGrid;
    }
    
    /**
     * Executes a list of transformations, creating appropriate undo entries.
     * 
     * @param transformations List of transformations to apply
     * @param commandDescription Description of the AI command for undo history
     * @return ExecutionResult containing success status and any errors
     */
    public ExecutionResult executeTransformations(List<ObjectTransformation> transformations, String commandDescription) {
        if (transformations == null || transformations.isEmpty()) {
            return ExecutionResult.success("No transformations to apply", 0);
        }
        
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        List<IUndo> undoEntries = new ArrayList<>();
        int successCount = 0;
        
        // Perform batch validation before executing any transformations
        ObjectConstraintValidator.ValidationResult batchValidation = 
            ObjectConstraintValidator.validateBatchTransformations(transformations, globalObjList);
        
        if (batchValidation.hasErrors()) {
            errors.addAll(batchValidation.getErrors());
            return ExecutionResult.failure("Batch validation failed", errors, batchValidation.getWarnings());
        }
        
        if (batchValidation.hasWarnings()) {
            warnings.addAll(batchValidation.getWarnings());
        }
        
        // Start multi-undo entry for batch operations
        boolean isMultiOperation = transformations.size() > 1;
        if (isMultiOperation) {
            editorForm.startUndoMulti();
        }
        
        try {
            // Track progress for batch operations
            int totalTransformations = transformations.size();
            int processedCount = 0;
            
            for (ObjectTransformation transformation : transformations) {
                processedCount++;
                try {
                    IUndo undoEntry = executeTransformation(transformation);
                    if (undoEntry != null) {
                        undoEntries.add(undoEntry);
                        successCount++;
                        
                        // Log progress for large batches
                        if (totalTransformations > 10 && processedCount % 5 == 0) {
                            System.out.println("Progress: " + processedCount + "/" + totalTransformations + 
                                             " transformations completed (" + successCount + " successful)");
                        }
                    } else {
                        errors.add("Failed to apply transformation to object " + transformation.getObjectId());
                    }
                } catch (ObjectNotFoundException e) {
                    errors.add("Object not found: " + transformation.getObjectId());
                } catch (ValidationException e) {
                    errors.add("Validation failed for object " + transformation.getObjectId() + ": " + e.getMessage());
                } catch (Exception e) {
                    errors.add("Unexpected error for object " + transformation.getObjectId() + ": " + e.getMessage());
                }
            }
            
            // Complete multi-undo entry
            if (successCount > 0) {
                if (isMultiOperation) {
                    editorForm.endUndoMulti();
                }
                // Note: For single operations, the individual undo entries are already added
                // by the addUndoEntry calls in createUndoEntry method
            }
            
            // Update UI if any transformations succeeded
            if (successCount > 0) {
                editorForm.addRerenderTask("allobjects");
                updatePropertyGridIfNeeded();
            }
            
        } catch (Exception e) {
            // If something goes wrong, try to clean up the undo state
            if (isMultiOperation) {
                try {
                    editorForm.endUndoMulti();
                } catch (Exception cleanupError) {
                    // Ignore cleanup errors
                }
            }
            errors.add("Critical error during execution: " + e.getMessage());
        }
        
        if (errors.isEmpty()) {
            String detailedMessage = generateDetailedSuccessMessage(transformations, successCount);
            return ExecutionResult.success(detailedMessage, successCount, warnings);
        } else if (successCount > 0) {
            String detailedMessage = generateDetailedPartialSuccessMessage(transformations, successCount);
            return ExecutionResult.partialSuccess(detailedMessage, successCount, errors, warnings);
        } else {
            return ExecutionResult.failure("Failed to apply any transformations", errors, warnings);
        }
    }
    
    /**
     * Executes a list of transformations with default command description.
     * 
     * @param transformations List of transformations to apply
     * @return ExecutionResult containing success status and any errors
     */
    public ExecutionResult executeTransformations(List<ObjectTransformation> transformations) {
        return executeTransformations(transformations, "AI Command");
    }
    
    /**
     * Executes transformations with batch processing support, progress tracking, and cancellation.
     * 
     * @param transformations List of transformations to apply
     * @param commandDescription Description of the AI command for undo history
     * @param progressListener Optional progress listener for batch operations
     * @return BatchExecutionResult with detailed operation information
     */
    public BatchExecutionResult executeBatchTransformations(List<ObjectTransformation> transformations, 
                                                           String commandDescription,
                                                           BatchProgressListener progressListener) {
        if (transformations == null || transformations.isEmpty()) {
            return new BatchExecutionResult(true, false, "No transformations to apply", 0, 
                                          new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), 0, false);
        }
        
        long startTime = System.currentTimeMillis();
        BatchOperationContext context = new BatchOperationContext(transformations.size());
        context.setProgressListener(progressListener);
        currentBatchContext = context;
        
        try {
            // Determine if we should use batch processing optimizations
            boolean useBatchOptimizations = transformations.size() >= BATCH_SIZE_THRESHOLD;
            
            if (useBatchOptimizations) {
                return executeLargeBatch(transformations, commandDescription, context);
            } else {
                return executeSmallBatch(transformations, commandDescription, context);
            }
        } finally {
            currentBatchContext = null;
            if (progressListener != null) {
                progressListener.onComplete(context);
            }
        }
    }
    
    /**
     * Executes batch transformations with default progress listener.
     */
    public BatchExecutionResult executeBatchTransformations(List<ObjectTransformation> transformations, 
                                                           String commandDescription) {
        return executeBatchTransformations(transformations, commandDescription, null);
    }
    
    /**
     * Cancels the currently running batch operation.
     */
    public void cancelCurrentBatch() {
        BatchOperationContext context = currentBatchContext;
        if (context != null) {
            context.cancel();
        }
    }
    
    /**
     * Gets the current batch operation context, if any.
     */
    public BatchOperationContext getCurrentBatchContext() {
        return currentBatchContext;
    }
    
    /**
     * Executes a large batch with performance optimizations.
     */
    private BatchExecutionResult executeLargeBatch(List<ObjectTransformation> transformations,
                                                 String commandDescription,
                                                 BatchOperationContext context) {
        List<TransformationResult> results = new ArrayList<>();
        List<IUndo> undoEntries = new ArrayList<>();
        
        // Start multi-undo entry for the entire batch
        editorForm.startUndoMulti();
        
        try {
            // Pre-validate transformations in parallel for better performance
            List<ObjectTransformation> validTransformations = preValidateTransformations(transformations, context);
            
            if (context.isCancelled()) {
                return createCancelledResult(context, results);
            }
            
            // Group transformations by object for better cache locality
            Map<Integer, List<ObjectTransformation>> transformationsByObject = groupTransformationsByObject(validTransformations);
            
            // Process each object's transformations together
            for (Map.Entry<Integer, List<ObjectTransformation>> entry : transformationsByObject.entrySet()) {
                if (context.isCancelled()) {
                    break;
                }
                
                processObjectTransformations(entry.getKey(), entry.getValue(), context, results, undoEntries);
            }
            
            // Complete the multi-undo entry if we have successful operations
            if (context.getSuccessful() > 0) {
                editorForm.endUndoMulti();
                
                // Batch UI updates for better performance
                editorForm.addRerenderTask("allobjects");
                updatePropertyGridIfNeeded();
            }
            
        } catch (Exception e) {
            context.addError("Critical error during batch execution: " + e.getMessage());
            try {
                editorForm.endUndoMulti();
            } catch (Exception cleanupError) {
                // Ignore cleanup errors
            }
        }
        
        return createBatchResult(context, results);
    }
    
    /**
     * Executes a small batch using the standard approach.
     */
    private BatchExecutionResult executeSmallBatch(List<ObjectTransformation> transformations,
                                                 String commandDescription,
                                                 BatchOperationContext context) {
        List<TransformationResult> results = new ArrayList<>();
        List<IUndo> undoEntries = new ArrayList<>();
        
        // Start multi-undo entry
        editorForm.startUndoMulti();
        
        try {
            for (ObjectTransformation transformation : transformations) {
                if (context.isCancelled()) {
                    break;
                }
                
                long transformationStart = System.currentTimeMillis();
                TransformationResult result = executeSingleTransformationWithResult(transformation, context);
                results.add(result);
                
                context.incrementProcessed();
                if (result.isSuccess()) {
                    context.incrementSuccessful();
                } else {
                    context.incrementFailed();
                    context.addError(result.getError());
                }
            }
            
            // Complete multi-undo entry if we have successful operations
            if (context.getSuccessful() > 0) {
                editorForm.endUndoMulti();
                editorForm.addRerenderTask("allobjects");
                updatePropertyGridIfNeeded();
            }
            
        } catch (Exception e) {
            context.addError("Critical error during batch execution: " + e.getMessage());
            try {
                editorForm.endUndoMulti();
            } catch (Exception cleanupError) {
                // Ignore cleanup errors
            }
        }
        
        return createBatchResult(context, results);
    }
    
    /**
     * Pre-validates transformations in parallel to catch errors early.
     */
    private List<ObjectTransformation> preValidateTransformations(List<ObjectTransformation> transformations,
                                                                BatchOperationContext context) {
        List<ObjectTransformation> validTransformations = new ArrayList<>();
        ExecutorService validationExecutor = Executors.newFixedThreadPool(MAX_CONCURRENT_VALIDATIONS);
        
        try {
            List<Future<ValidationResult>> validationFutures = new ArrayList<>();
            
            // Submit validation tasks
            for (ObjectTransformation transformation : transformations) {
                if (context.isCancelled()) {
                    break;
                }
                
                validationFutures.add(validationExecutor.submit(() -> {
                    try {
                        AbstractObj obj = globalObjList.get(transformation.getObjectId());
                        if (obj == null) {
                            return new ValidationResult(transformation, false, 
                                "Object not found: " + transformation.getObjectId(), null);
                        }
                        
                        validateTransformation(obj, transformation);
                        return new ValidationResult(transformation, true, null, null);
                    } catch (ValidationException e) {
                        return new ValidationResult(transformation, false, e.getMessage(), null);
                    } catch (Exception e) {
                        return new ValidationResult(transformation, false, 
                            "Validation error: " + e.getMessage(), null);
                    }
                }));
            }
            
            // Collect validation results
            for (Future<ValidationResult> future : validationFutures) {
                if (context.isCancelled()) {
                    break;
                }
                
                try {
                    ValidationResult result = future.get(1, TimeUnit.SECONDS);
                    if (result.isValid()) {
                        validTransformations.add(result.getTransformation());
                    } else {
                        context.addError("Validation failed for object " + 
                            result.getTransformation().getObjectId() + ": " + result.getError());
                        context.incrementFailed();
                    }
                } catch (TimeoutException e) {
                    context.addError("Validation timeout for transformation");
                    context.incrementFailed();
                } catch (Exception e) {
                    context.addError("Validation error: " + e.getMessage());
                    context.incrementFailed();
                }
            }
            
        } finally {
            validationExecutor.shutdown();
            try {
                if (!validationExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                    validationExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                validationExecutor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        
        return validTransformations;
    }
    
    /**
     * Groups transformations by object ID for better cache locality.
     */
    private Map<Integer, List<ObjectTransformation>> groupTransformationsByObject(List<ObjectTransformation> transformations) {
        Map<Integer, List<ObjectTransformation>> grouped = new HashMap<>();
        
        for (ObjectTransformation transformation : transformations) {
            grouped.computeIfAbsent(transformation.getObjectId(), k -> new ArrayList<>()).add(transformation);
        }
        
        return grouped;
    }
    
    /**
     * Processes all transformations for a single object.
     */
    private void processObjectTransformations(int objectId, List<ObjectTransformation> transformations,
                                            BatchOperationContext context, List<TransformationResult> results,
                                            List<IUndo> undoEntries) {
        AbstractObj obj = globalObjList.get(objectId);
        if (obj == null) {
            for (ObjectTransformation transformation : transformations) {
                results.add(new TransformationResult(transformation, false, 
                    "Object not found: " + objectId, null, 0));
                context.incrementProcessed();
                context.incrementFailed();
                context.addError("Object not found: " + objectId);
            }
            return;
        }
        
        // Process transformations for this object
        for (ObjectTransformation transformation : transformations) {
            if (context.isCancelled()) {
                break;
            }
            
            TransformationResult result = executeSingleTransformationWithResult(transformation, context);
            results.add(result);
            
            context.incrementProcessed();
            if (result.isSuccess()) {
                context.incrementSuccessful();
            } else {
                context.incrementFailed();
                context.addError(result.getError());
            }
        }
    }
    
    /**
     * Executes a single transformation and returns detailed result.
     */
    private TransformationResult executeSingleTransformationWithResult(ObjectTransformation transformation,
                                                                      BatchOperationContext context) {
        long startTime = System.currentTimeMillis();
        List<String> warnings = new ArrayList<>();
        
        try {
            IUndo undoEntry = executeTransformation(transformation);
            long executionTime = System.currentTimeMillis() - startTime;
            
            if (undoEntry != null) {
                return new TransformationResult(transformation, true, null, warnings, executionTime);
            } else {
                return new TransformationResult(transformation, false, 
                    "Failed to apply transformation", warnings, executionTime);
            }
        } catch (ObjectNotFoundException e) {
            long executionTime = System.currentTimeMillis() - startTime;
            return new TransformationResult(transformation, false, 
                "Object not found: " + transformation.getObjectId(), warnings, executionTime);
        } catch (ValidationException e) {
            long executionTime = System.currentTimeMillis() - startTime;
            return new TransformationResult(transformation, false, 
                "Validation failed: " + e.getMessage(), warnings, executionTime);
        } catch (Exception e) {
            long executionTime = System.currentTimeMillis() - startTime;
            return new TransformationResult(transformation, false, 
                "Unexpected error: " + e.getMessage(), warnings, executionTime);
        }
    }
    
    /**
     * Creates a batch result from the context and individual results.
     */
    private BatchExecutionResult createBatchResult(BatchOperationContext context, List<TransformationResult> results) {
        long totalTime = context.getElapsedTime();
        boolean success = context.getFailed() == 0 && !context.isCancelled();
        boolean partialSuccess = context.getSuccessful() > 0 && (context.getFailed() > 0 || context.isCancelled());
        
        String message;
        if (context.isCancelled()) {
            message = String.format("Operation cancelled. Processed %d of %d transformations (%d successful, %d failed)",
                context.getProcessed(), context.getTotalOperations(), context.getSuccessful(), context.getFailed());
        } else if (success) {
            message = String.format("Successfully applied %d transformations in %dms", 
                context.getSuccessful(), totalTime);
        } else if (partialSuccess) {
            message = String.format("Applied %d of %d transformations (%d failed) in %dms",
                context.getSuccessful(), context.getTotalOperations(), context.getFailed(), totalTime);
        } else {
            message = String.format("Failed to apply any of %d transformations in %dms",
                context.getTotalOperations(), totalTime);
        }
        
        return new BatchExecutionResult(success, partialSuccess, message, context.getSuccessful(),
            context.getErrors(), context.getWarnings(), results, totalTime, context.isCancelled());
    }
    
    /**
     * Creates a result for cancelled operations.
     */
    private BatchExecutionResult createCancelledResult(BatchOperationContext context, List<TransformationResult> results) {
        long totalTime = context.getElapsedTime();
        String message = String.format("Operation cancelled after %dms. Processed %d of %d transformations",
            totalTime, context.getProcessed(), context.getTotalOperations());
        
        return new BatchExecutionResult(false, context.getSuccessful() > 0, message, context.getSuccessful(),
            context.getErrors(), context.getWarnings(), results, totalTime, true);
    }
    
    /**
     * Helper class for validation results.
     */
    private static class ValidationResult {
        private final ObjectTransformation transformation;
        private final boolean valid;
        private final String error;
        private final List<String> warnings;
        
        public ValidationResult(ObjectTransformation transformation, boolean valid, String error, List<String> warnings) {
            this.transformation = transformation;
            this.valid = valid;
            this.error = error;
            this.warnings = warnings;
        }
        
        public ObjectTransformation getTransformation() { return transformation; }
        public boolean isValid() { return valid; }
        public String getError() { return error; }
        public List<String> getWarnings() { return warnings; }
    }
    
    /**
     * Executes a single transformation on an object.
     * 
     * @param transformation The transformation to apply
     * @return IUndo entry if successful, null otherwise
     * @throws ObjectNotFoundException if the object doesn't exist
     * @throws ValidationException if validation fails
     */
    private IUndo executeTransformation(ObjectTransformation transformation) 
            throws ObjectNotFoundException, ValidationException {
        
        if (transformation.getType() == ObjectTransformation.TransformationType.ADD) {
            return applyAdd(transformation);
        }
        
        AbstractObj obj = globalObjList.get(transformation.getObjectId());
        if (obj == null) {
            throw new ObjectNotFoundException("Object with ID " + transformation.getObjectId() + " not found");
        }
        
        // Validate transformation before applying
        validateTransformation(obj, transformation);
        
        // Create appropriate undo entry before making changes
        IUndo undoEntry = createUndoEntry(obj, transformation);
        
        // Apply the transformation
        boolean success;
        switch (transformation.getType()) {
            case TRANSLATE:
                success = applyTranslation(obj, transformation);
                break;
            case ROTATE:
                success = applyRotation(obj, transformation);
                break;
            case SCALE:
                success = applyScaling(obj, transformation);
                break;
            case SET_POSITION:
                System.out.println("DEBUG: Processing SET_POSITION transformation for object ID " + transformation.getObjectId());
                success = applySetPosition(obj, transformation);
                System.out.println("DEBUG: SET_POSITION result: " + success);
                break;
            case SET_ROTATION:
                success = applySetRotation(obj, transformation);
                break;
            case SET_SCALE:
                success = applySetScale(obj, transformation);
                break;
            case PROPERTY_CHANGE:
                success = applyPropertyChanges(obj, transformation);
                break;
            case BATCH_OPERATION:
                // BATCH_OPERATION should not be processed individually
                throw new IllegalArgumentException("BATCH_OPERATION transformations should be processed through batch methods");
            default:
                throw new IllegalArgumentException("Unknown transformation type: " + transformation.getType());
        }
        
        if (!success) {
            System.err.println("CommandExecutor: Transformation failed to apply - Type: " + transformation.getType() + 
                              ", Object ID: " + transformation.getObjectId());
        }
        return success ? undoEntry : null;
    }
    
    /**
     * Validates a transformation before applying it.
     */
    private void validateTransformation(AbstractObj obj, ObjectTransformation transformation) 
            throws ValidationException {
        
        ObjectConstraintValidator.ValidationResult result;
        
        switch (transformation.getType()) {
            case TRANSLATE:
                Vec3f newPos = new Vec3f(obj.position);
                newPos.add(transformation.getVectorValue());
                result = ObjectConstraintValidator.validatePosition(obj, newPos);
                break;
            case SET_POSITION:
                result = ObjectConstraintValidator.validatePosition(obj, transformation.getVectorValue());
                break;
            case ROTATE:
                Vec3f newRot = new Vec3f(obj.rotation);
                newRot.add(transformation.getVectorValue());
                result = ObjectConstraintValidator.validateRotation(obj, newRot);
                break;
            case SET_ROTATION:
                result = ObjectConstraintValidator.validateRotation(obj, transformation.getVectorValue());
                break;
            case SCALE:
                Vec3f newScale = new Vec3f(obj.scale);
                Vec3f factor = transformation.getVectorValue();
                newScale.x *= factor.x;
                newScale.y *= factor.y;
                newScale.z *= factor.z;
                result = ObjectConstraintValidator.validateScale(obj, newScale);
                break;
            case SET_SCALE:
                result = ObjectConstraintValidator.validateScale(obj, transformation.getVectorValue());
                break;
            case PROPERTY_CHANGE:
                result = ObjectConstraintValidator.validatePropertyChanges(obj, transformation.getPropertyChanges());
                break;
            default:
                throw new ValidationException("Unknown transformation type: " + transformation.getType());
        }
        
        if (!result.isValid()) {
            throw new ValidationException("Validation failed: " + String.join(", ", result.getErrors()));
        }
        
        // Log warnings if any
        if (result.hasWarnings()) {
            System.out.println("Transformation warnings for object " + obj.uniqueID + ": " + 
                             String.join(", ", result.getWarnings()));
        }
    }
    
    /**
     * Creates appropriate undo entry for the transformation.
     * Returns a placeholder undo entry for tracking purposes.
     */
    private IUndo createUndoEntry(AbstractObj obj, ObjectTransformation transformation) {
        IUndo.Action action;
        switch (transformation.getType()) {
            case TRANSLATE:
            case SET_POSITION:
                action = IUndo.Action.TRANSLATE;
                break;
            case ROTATE:
            case SET_ROTATION:
                action = IUndo.Action.ROTATE;
                break;
            case SCALE:
            case SET_SCALE:
                action = IUndo.Action.SCALE;
                break;
            case PROPERTY_CHANGE:
                action = IUndo.Action.PARAMETER;
                break;
            case ADD:
                action = IUndo.Action.ADD;
                break;
            case BATCH_OPERATION:
                // For batch operations, use a generic action
                action = IUndo.Action.PARAMETER;
                break;
            default:
                throw new IllegalArgumentException("Unknown transformation type: " + transformation.getType());
        }
        
        // Add the undo entry to the editor form
        editorForm.addUndoEntry(action, obj);
        
        // Return a placeholder undo entry for tracking
        return new PlaceholderUndoEntry(action.toString(), obj.uniqueID);
    }
    
    /**
     * Placeholder undo entry for tracking AI command operations.
     */
    public static class PlaceholderUndoEntry implements IUndo {
        private final String action;
        private final int objectId;
        
        public PlaceholderUndoEntry(String action, int objectId) {
            this.action = action;
            this.objectId = objectId;
        }
        
        @Override
        public void performUndo() {
            // This is a placeholder - actual undo is handled by the real undo entries
            // created by the GalaxyEditorForm.addUndoEntry method
        }
        
        @Override
        public String toString() {
            return "PlaceholderUndoEntry{" + action + ", obj=" + objectId + "}";
        }
    }
    
    /**
     * Applies translation transformation.
     */
    private boolean applyTranslation(AbstractObj obj, ObjectTransformation transformation) {
        Vec3f delta = transformation.getVectorValue();
        if (delta == null) {
            System.err.println("CommandExecutor: Translation delta is null");
            return false;
        }
        
        obj.position.add(delta);
        
        // Add specific render tasks for position changes
        if (obj.renderer != null && obj.renderer.hasSpecialPosition()) {
            editorForm.addRerenderTask("object:" + obj.uniqueID);
        }
        editorForm.addRerenderTask("zone:" + obj.stage.stageName);
        
        return true;
    }
    
    /**
     * Applies rotation transformation.
     */
    private boolean applyRotation(AbstractObj obj, ObjectTransformation transformation) {
        Vec3f delta = transformation.getVectorValue();
        if (delta == null) {
            System.err.println("CommandExecutor: Rotation delta is null");
            return false;
        }
        
        obj.rotation.add(delta);
        
        // Add specific render tasks for rotation changes
        if (obj.renderer != null && obj.renderer.hasSpecialRotation()) {
            editorForm.addRerenderTask("object:" + obj.uniqueID);
        }
        editorForm.addRerenderTask("zone:" + obj.stage.stageName);
        
        return true;
    }
    
    /**
     * Applies scaling transformation.
     */
    private boolean applyScaling(AbstractObj obj, ObjectTransformation transformation) {
        Vec3f factor = transformation.getVectorValue();
        if (factor == null) {
            System.err.println("CommandExecutor: Scaling factor is null");
            return false;
        }
        
        obj.scale.x *= factor.x;
        obj.scale.y *= factor.y;
        obj.scale.z *= factor.z;
        
        // Add specific render tasks for scale changes
        if (obj.renderer != null && obj.renderer.hasSpecialScaling()) {
            editorForm.addRerenderTask("object:" + obj.uniqueID);
        }
        editorForm.addRerenderTask("zone:" + obj.stage.stageName);
        
        return true;
    }
    
    /**
     * Sets absolute position.
     */
    private boolean applySetPosition(AbstractObj obj, ObjectTransformation transformation) {
        Vec3f position = transformation.getVectorValue();
        if (position == null) {
            System.err.println("DEBUG: SET_POSITION failed - position vector is null");
            return false;
        }
        
        System.out.println("DEBUG: Applying SET_POSITION to object " + obj.name + " (ID: " + obj.uniqueID + ")");
        System.out.println("DEBUG: Old position: " + obj.position);
        System.out.println("DEBUG: New position: " + position);
        
        obj.position.set(position);
        
        // Add specific render tasks for position changes (like GalaxyEditorForm does)
        if (obj.renderer != null && obj.renderer.hasSpecialPosition()) {
            editorForm.addRerenderTask("object:" + obj.uniqueID);
        }
        editorForm.addRerenderTask("zone:" + obj.stage.stageName);
        
        System.out.println("DEBUG: Position after set: " + obj.position);
        return true;
    }
    
    /**
     * Sets absolute rotation.
     */
    private boolean applySetRotation(AbstractObj obj, ObjectTransformation transformation) {
        Vec3f rotation = transformation.getVectorValue();
        if (rotation == null) return false;
        
        obj.rotation.set(rotation);
        
        // Add specific render tasks for rotation changes
        if (obj.renderer != null && obj.renderer.hasSpecialRotation()) {
            editorForm.addRerenderTask("object:" + obj.uniqueID);
        }
        editorForm.addRerenderTask("zone:" + obj.stage.stageName);
        
        return true;
    }
    
    /**
     * Sets absolute scale.
     */
    private boolean applySetScale(AbstractObj obj, ObjectTransformation transformation) {
        Vec3f scale = transformation.getVectorValue();
        if (scale == null) return false;
        
        obj.scale.set(scale);
        
        // Add specific render tasks for scale changes
        if (obj.renderer != null && obj.renderer.hasSpecialScaling()) {
            editorForm.addRerenderTask("object:" + obj.uniqueID);
        }
        editorForm.addRerenderTask("zone:" + obj.stage.stageName);
        
        return true;
    }
    

     // Handles ADD transformations - creates a new object via GalaxyEditorForm
    private IUndo applyAdd(ObjectTransformation transformation) throws ValidationException {
        String objType = transformation.getAddObjectType();
        Vec3f pos = transformation.getVectorValue() != null ? transformation.getVectorValue() : new Vec3f(0,0,0);
        if (objType == null || objType.isEmpty()) {
            System.err.println("CommandExecutor: Object type is null or empty for ADD transformation");
            return null;
        }
        // Delegate to editor form helper
        AbstractObj newObj = editorForm.addObjectFromAI(objType, pos);
        if (newObj == null) {
            System.err.println("CommandExecutor: Failed to create object of type: " + objType);
            throw new ValidationException("Failed to create object of type " + objType);
        }
        // Register undo entry
        IUndo undoEntry = editorForm.new UndoObjectAddEntry(newObj);
        return undoEntry;
    }
    
    private boolean applyPropertyChanges(AbstractObj obj, ObjectTransformation transformation) {
        Map<String, Object> propertyChanges = transformation.getPropertyChanges();
        if (propertyChanges == null || propertyChanges.isEmpty()) {
            System.err.println("CommandExecutor: Property changes are null or empty");
            return false;
        }
        
        for (Map.Entry<String, Object> entry : propertyChanges.entrySet()) {
            obj.data.put(entry.getKey(), entry.getValue());
        }
        return true;
    }
    
    /**
     * Updates the property grid if a selected object was modified.
     */
    private void updatePropertyGridIfNeeded() {
        if (propertyGrid != null) {
            propertyGrid.repaint();
        }
    }
    
    /**
     * Exception thrown when an object is not found.
     */
    public static class ObjectNotFoundException extends Exception {
        public ObjectNotFoundException(String message) {
            super(message);
        }
    }
    
    /**
     * Exception thrown when validation fails.
     */
    public static class ValidationException extends Exception {
        public ValidationException(String message) {
            super(message);
        }
    }
    
    /**
     * Context for tracking batch operations with progress and cancellation support.
     */
    public static class BatchOperationContext {
        private final AtomicBoolean cancelled = new AtomicBoolean(false);
        private final AtomicInteger processed = new AtomicInteger(0);
        private final AtomicInteger successful = new AtomicInteger(0);
        private final AtomicInteger failed = new AtomicInteger(0);
        private final int totalOperations;
        private final List<String> errors = Collections.synchronizedList(new ArrayList<>());
        private final List<String> warnings = Collections.synchronizedList(new ArrayList<>());
        private final long startTime;
        private volatile BatchProgressListener progressListener;
        
        public BatchOperationContext(int totalOperations) {
            this.totalOperations = totalOperations;
            this.startTime = System.currentTimeMillis();
        }
        
        public void cancel() {
            cancelled.set(true);
        }
        
        public boolean isCancelled() {
            return cancelled.get();
        }
        
        public void incrementProcessed() {
            int current = processed.incrementAndGet();
            if (progressListener != null && current % PROGRESS_UPDATE_INTERVAL == 0) {
                progressListener.onProgress(current, totalOperations, getElapsedTime());
            }
        }
        
        public void incrementSuccessful() {
            successful.incrementAndGet();
        }
        
        public void incrementFailed() {
            failed.incrementAndGet();
        }
        
        public void addError(String error) {
            errors.add(error);
        }
        
        public void addWarning(String warning) {
            warnings.add(warning);
        }
        
        public int getProcessed() { return processed.get(); }
        public int getSuccessful() { return successful.get(); }
        public int getFailed() { return failed.get(); }
        public int getTotalOperations() { return totalOperations; }
        public List<String> getErrors() { return new ArrayList<>(errors); }
        public List<String> getWarnings() { return new ArrayList<>(warnings); }
        public long getElapsedTime() { return System.currentTimeMillis() - startTime; }
        
        public void setProgressListener(BatchProgressListener listener) {
            this.progressListener = listener;
        }
        
        public double getProgressPercentage() {
            return totalOperations > 0 ? (double) processed.get() / totalOperations * 100.0 : 0.0;
        }
        
        public boolean isComplete() {
            return processed.get() >= totalOperations || cancelled.get();
        }
    }
    
    /**
     * Interface for receiving batch operation progress updates.
     */
    public interface BatchProgressListener {
        void onProgress(int processed, int total, long elapsedTimeMs);
        void onComplete(BatchOperationContext context);
        void onError(String error);
    }
    
    /**
     * Detailed result for individual transformation within a batch.
     */
    public static class TransformationResult {
        private final ObjectTransformation transformation;
        private final boolean success;
        private final String error;
        private final List<String> warnings;
        private final long executionTimeMs;
        
        public TransformationResult(ObjectTransformation transformation, boolean success, 
                                  String error, List<String> warnings, long executionTimeMs) {
            this.transformation = transformation;
            this.success = success;
            this.error = error;
            this.warnings = warnings != null ? new ArrayList<>(warnings) : new ArrayList<>();
            this.executionTimeMs = executionTimeMs;
        }
        
        public ObjectTransformation getTransformation() { return transformation; }
        public boolean isSuccess() { return success; }
        public String getError() { return error; }
        public List<String> getWarnings() { return new ArrayList<>(warnings); }
        public long getExecutionTimeMs() { return executionTimeMs; }
    }
    
    /**
     * Enhanced execution result with detailed batch operation information.
     */
    public static class BatchExecutionResult extends ExecutionResult {
        private final List<TransformationResult> transformationResults;
        private final long totalExecutionTimeMs;
        private final boolean wasCancelled;
        
        public BatchExecutionResult(boolean success, boolean partialSuccess, String message,
                                  int successCount, List<String> errors, List<String> warnings,
                                  List<TransformationResult> transformationResults,
                                  long totalExecutionTimeMs, boolean wasCancelled) {
            super(success, partialSuccess, message, successCount, errors, warnings);
            this.transformationResults = transformationResults != null ? 
                new ArrayList<>(transformationResults) : new ArrayList<>();
            this.totalExecutionTimeMs = totalExecutionTimeMs;
            this.wasCancelled = wasCancelled;
        }
        
        public List<TransformationResult> getTransformationResults() {
            return new ArrayList<>(transformationResults);
        }
        
        public long getTotalExecutionTimeMs() { return totalExecutionTimeMs; }
        public boolean wasCancelled() { return wasCancelled; }
        
        public double getAverageExecutionTimeMs() {
            return transformationResults.isEmpty() ? 0.0 : 
                transformationResults.stream().mapToLong(TransformationResult::getExecutionTimeMs).average().orElse(0.0);
        }
        
        public List<TransformationResult> getFailedTransformations() {
            return transformationResults.stream()
                .filter(r -> !r.isSuccess())
                .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
        }
    }
    
    /**
     * Generates a detailed success message describing what transformations were applied.
     */
    private String generateDetailedSuccessMessage(List<ObjectTransformation> transformations, int successCount) {
        Map<ObjectTransformation.TransformationType, Integer> typeCounts = new HashMap<>();
        Map<String, Integer> addObjectCounts = new HashMap<>();
        
        for (ObjectTransformation transform : transformations) {
            typeCounts.put(transform.getType(), typeCounts.getOrDefault(transform.getType(), 0) + 1);
            
            if (transform.getType() == ObjectTransformation.TransformationType.ADD) {
                String objType = transform.getAddObjectType();
                addObjectCounts.put(objType, addObjectCounts.getOrDefault(objType, 0) + 1);
            }
        }
        
        StringBuilder message = new StringBuilder();
        message.append("Successfully applied ").append(successCount).append(" transformations:\n");
        
        for (Map.Entry<ObjectTransformation.TransformationType, Integer> entry : typeCounts.entrySet()) {
            ObjectTransformation.TransformationType type = entry.getKey();
            int count = entry.getValue();
            
            switch (type) {
                case ADD:
                    message.append("- Added ").append(count).append(" objects");
                    if (!addObjectCounts.isEmpty()) {
                        message.append(" (");
                        boolean first = true;
                        for (Map.Entry<String, Integer> objEntry : addObjectCounts.entrySet()) {
                            if (!first) message.append(", ");
                            message.append(objEntry.getValue()).append(" ").append(objEntry.getKey());
                            first = false;
                        }
                        message.append(")");
                    }
                    message.append("\n");
                    break;
                case TRANSLATE:
                    message.append("- Moved ").append(count).append(" objects\n");
                    break;
                case ROTATE:
                    message.append("- Rotated ").append(count).append(" objects\n");
                    break;
                case SCALE:
                    message.append("- Scaled ").append(count).append(" objects\n");
                    break;
                case SET_POSITION:
                    message.append("- Set position of ").append(count).append(" objects\n");
                    break;
                case SET_ROTATION:
                    message.append("- Set rotation of ").append(count).append(" objects\n");
                    break;
                case SET_SCALE:
                    message.append("- Set scale of ").append(count).append(" objects\n");
                    break;
                case PROPERTY_CHANGE:
                    message.append("- Changed properties of ").append(count).append(" objects\n");
                    break;
                default:
                    message.append("- Applied ").append(count).append(" ").append(type).append(" operations\n");
                    break;
            }
        }
        
        return message.toString().trim();
    }
    
    /**
     * Generates a detailed partial success message.
     */
    private String generateDetailedPartialSuccessMessage(List<ObjectTransformation> transformations, int successCount) {
        String detailedSuccess = generateDetailedSuccessMessage(transformations, successCount);
        int totalCount = transformations.size();
        int failedCount = totalCount - successCount;
        
        return detailedSuccess + "\n\nNote: " + failedCount + " of " + totalCount + " transformations failed.";
    }
    
    /**
     * Result of executing transformations.
     */
    public static class ExecutionResult {
        private final boolean success;
        private final boolean partialSuccess;
        private final String message;
        private final int successCount;
        private final List<String> errors;
        private final List<String> warnings;
        
        protected ExecutionResult(boolean success, boolean partialSuccess, String message, 
                              int successCount, List<String> errors, List<String> warnings) {
            this.success = success;
            this.partialSuccess = partialSuccess;
            this.message = message;
            this.successCount = successCount;
            this.errors = errors != null ? new ArrayList<>(errors) : new ArrayList<>();
            this.warnings = warnings != null ? new ArrayList<>(warnings) : new ArrayList<>();
        }
        
        public boolean isSuccess() { return success; }
        public boolean isPartialSuccess() { return partialSuccess; }
        public String getMessage() { return message; }
        public int getSuccessCount() { return successCount; }
        public List<String> getErrors() { return new ArrayList<>(errors); }
        public List<String> getWarnings() { return new ArrayList<>(warnings); }
        public boolean hasErrors() { return !errors.isEmpty(); }
        public boolean hasWarnings() { return !warnings.isEmpty(); }
        
        public static ExecutionResult success(String message, int successCount) {
            return new ExecutionResult(true, false, message, successCount, null, null);
        }
        
        public static ExecutionResult success(String message, int successCount, List<String> warnings) {
            return new ExecutionResult(true, false, message, successCount, null, warnings);
        }
        
        public static ExecutionResult partialSuccess(String message, int successCount, 
                                                   List<String> errors, List<String> warnings) {
            return new ExecutionResult(false, true, message, successCount, errors, warnings);
        }
        
        public static ExecutionResult failure(String message, List<String> errors, List<String> warnings) {
            return new ExecutionResult(false, false, message, 0, errors, warnings);
        }
    }
}
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
import org.json.JSONArray;
import org.json.JSONException;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import javax.swing.SwingUtilities;

/**
 * Optimized AI response processor that handles parsing and validation
 * of AI responses with improved performance and UI responsiveness.
 */
public class OptimizedAIResponseProcessor {
    
    private static final int PROCESSING_TIMEOUT_MS = 10000; // 10 seconds
    private static final int MAX_TRANSFORMATIONS = 1000; // Prevent excessive operations
    
    // Pre-compiled patterns for better performance
    private static final Pattern JSON_PATTERN = Pattern.compile("\\{.*\\}", Pattern.DOTALL);
    private static final Pattern TRANSFORMATION_VALIDATION_PATTERN = Pattern.compile(
        "\"type\"\\s*:\\s*\"(TRANSLATE|ROTATE|SCALE|SET_POSITION)\"", Pattern.CASE_INSENSITIVE);
    
    private final ExecutorService processingExecutor;
    private final Map<String, CompletableFuture<List<ObjectTransformation>>> activeProcessing;
    
    public OptimizedAIResponseProcessor() {
        // Use a dedicated thread pool for AI response processing
        this.processingExecutor = Executors.newFixedThreadPool(2, r -> {
            Thread t = new Thread(r, "AI-Response-Processor");
            t.setDaemon(true);
            return t;
        });
        this.activeProcessing = new ConcurrentHashMap<>();
    }
    
    /**
     * Processes AI response asynchronously to avoid blocking the UI thread.
     * 
     * @param aiResponse The AI response to process
     * @param context The galaxy context for validation
     * @param callback Callback to receive the results on the EDT
     * @return CompletableFuture for the processing operation
     */
    public CompletableFuture<List<ObjectTransformation>> processResponseAsync(
            String aiResponse, GalaxyContext context, 
            java.util.function.Consumer<List<ObjectTransformation>> callback) {
        
        String requestId = generateRequestId(aiResponse, context);
        
        // Check if we're already processing this exact request
        CompletableFuture<List<ObjectTransformation>> existing = activeProcessing.get(requestId);
        if (existing != null && !existing.isDone()) {
            return existing;
        }
        
        CompletableFuture<List<ObjectTransformation>> future = CompletableFuture
            .supplyAsync(() -> processResponseInternal(aiResponse, context), processingExecutor)
            .orTimeout(PROCESSING_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            .whenComplete((result, throwable) -> {
                // Remove from active processing
                activeProcessing.remove(requestId);
                
                // Call callback on EDT if provided
                if (callback != null) {
                    SwingUtilities.invokeLater(() -> {
                        if (throwable != null) {
                            callback.accept(Collections.emptyList());
                        } else {
                            callback.accept(result);
                        }
                    });
                }
            });
        
        activeProcessing.put(requestId, future);
        return future;
    }
    
    /**
     * Processes AI response synchronously with optimizations.
     * 
     * @param aiResponse The AI response to process
     * @param context The galaxy context for validation
     * @return List of parsed transformations
     */
    public List<ObjectTransformation> processResponse(String aiResponse, GalaxyContext context) {
        return processResponseInternal(aiResponse, context);
    }
    
    /**
     * Pre-validates AI response format before full processing.
     * This is a quick check to avoid expensive processing of obviously invalid responses.
     * 
     * @param aiResponse The AI response to validate
     * @return true if response appears to be valid JSON with transformations
     */
    public boolean preValidateResponse(String aiResponse) {
        if (aiResponse == null || aiResponse.trim().isEmpty()) {
            return false;
        }
        
        // Quick check for JSON structure - handle markdown code blocks
        String trimmed = aiResponse.trim();
        
        // Check if it's wrapped in markdown code blocks
        boolean hasMarkdownWrapper = trimmed.startsWith("```") && trimmed.endsWith("```");
        boolean hasDirectJson = trimmed.startsWith("{") && trimmed.endsWith("}");
        
        if (!hasMarkdownWrapper && !hasDirectJson) {
            return false;
        }
        
        // Quick check for transformations array
        if (!trimmed.contains("transformations")) {
            return false;
        }
        
        // Quick check for valid transformation types
        Matcher matcher = TRANSFORMATION_VALIDATION_PATTERN.matcher(trimmed);
        return matcher.find();
    }
    
    /**
     * Extracts and validates JSON from AI response with error recovery.
     * 
     * @param aiResponse The raw AI response
     * @return Extracted JSONObject or null if invalid
     */
    public JSONObject extractAndValidateJson(String aiResponse) {
        if (aiResponse == null || aiResponse.trim().isEmpty()) {
            return null;
        }
        
        try {
            // First, try to find JSON in the response (AI might add extra text)
            String jsonStr = extractJsonFromResponse(aiResponse);
            if (jsonStr == null) {
                return null;
            }
            
            // Parse JSON
            JSONObject json = new JSONObject(jsonStr);
            
            // Validate structure - be more flexible about what we accept
            if (json.has("transformations")) {
                JSONArray transformations = json.getJSONArray("transformations");
                if (transformations.length() > 0) {
                    return json;
                }
            }
            
            // If no transformations array, try to create one from the response
            return attemptJsonRecovery(aiResponse);
            
        } catch (JSONException e) {
            // Try to recover from common JSON errors
            return attemptJsonRecovery(aiResponse);
        }
    }
    
    /**
     * Extracts JSON from AI response that might contain extra text.
     */
    private String extractJsonFromResponse(String aiResponse) {
        // Try multiple approaches to find JSON
        
        // 1. Look for complete JSON object with braces
        Matcher matcher = JSON_PATTERN.matcher(aiResponse);
        if (matcher.find()) {
            String jsonStr = matcher.group();
            if (jsonStr.contains("transformations") || jsonStr.contains("type")) {
                return jsonStr;
            }
        }
        
        // 2. Look for JSON that starts with { and ends with }
        int startBrace = aiResponse.indexOf('{');
        int endBrace = aiResponse.lastIndexOf('}');
        if (startBrace != -1 && endBrace != -1 && endBrace > startBrace) {
            String jsonStr = aiResponse.substring(startBrace, endBrace + 1);
            try {
                // Test if it's valid JSON
                new JSONObject(jsonStr);
                return jsonStr;
            } catch (JSONException e) {
                // Not valid JSON, continue trying
            }
        }
        
        // 3. Try to find JSON-like content even without perfect braces
        if (aiResponse.contains("transformations") && aiResponse.contains("type")) {
            // Try to construct JSON from the response
            return attemptJsonConstruction(aiResponse);
        }
        
        return null;
    }
    
    /**
     * Attempts to construct JSON from AI response that contains transformation info.
     */
    private String attemptJsonConstruction(String aiResponse) {
        try {
            // Look for transformation-like patterns in the response
            if (aiResponse.toLowerCase().contains("add") || aiResponse.toLowerCase().contains("create")) {
                // Try to create an ADD transformation
                return constructAddTransformation(aiResponse);
            }
            
            if (aiResponse.toLowerCase().contains("move") || aiResponse.toLowerCase().contains("translate")) {
                // Try to create a TRANSLATE transformation
                return constructMoveTransformation(aiResponse);
            }
            
            if (aiResponse.toLowerCase().contains("rotate")) {
                // Try to create a ROTATE transformation
                return constructRotateTransformation(aiResponse);
            }
            
            if (aiResponse.toLowerCase().contains("scale")) {
                // Try to create a SCALE transformation
                return constructScaleTransformation(aiResponse);
            }
            
        } catch (Exception e) {
            System.err.println("Failed to construct JSON from AI response: " + e.getMessage());
        }
        
        return null;
    }
    
    /**
     * Constructs an ADD transformation JSON from AI response.
     */
    private String constructAddTransformation(String aiResponse) {
        // This is a fallback - we'll return null to let fallback parsing handle it
        // The fallback parsing is already working well for ADD commands
        return null;
    }
    
    /**
     * Constructs a MOVE transformation JSON from AI response.
     */
    private String constructMoveTransformation(String aiResponse) {
        // This is a fallback - we'll return null to let fallback parsing handle it
        // The fallback parsing is already working well for MOVE commands
        return null;
    }
    
    /**
     * Constructs a ROTATE transformation JSON from AI response.
     */
    private String constructRotateTransformation(String aiResponse) {
        // This is a fallback - we'll return null to let fallback parsing handle it
        return null;
    }
    
    /**
     * Constructs a SCALE transformation JSON from AI response.
     */
    private String constructScaleTransformation(String aiResponse) {
        // This is a fallback - we'll return null to let fallback parsing handle it
        return null;
    }
    
    /**
     * Cancels all active processing operations.
     */
    public void cancelAllProcessing() {
        for (CompletableFuture<?> future : activeProcessing.values()) {
            future.cancel(true);
        }
        activeProcessing.clear();
    }
    
    /**
     * Shuts down the processor and cleans up resources.
     */
    public void shutdown() {
        cancelAllProcessing();
        processingExecutor.shutdown();
        try {
            if (!processingExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                processingExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            processingExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
    
    // Private implementation methods
    
    private List<ObjectTransformation> processResponseInternal(String aiResponse, GalaxyContext context) {
        List<ObjectTransformation> transformations = new ArrayList<>();
        
        try {
            // Pre-validate response
            if (!preValidateResponse(aiResponse)) {
                return transformations;
            }
            
            // Extract and validate JSON
            JSONObject json = extractAndValidateJson(aiResponse);
            if (json == null) {
                return transformations;
            }
            
            JSONArray transformArray = json.getJSONArray("transformations");
            
            // Limit number of transformations to prevent excessive operations
            int maxTransforms = Math.min(transformArray.length(), MAX_TRANSFORMATIONS);
            
            // Process transformations in batches for better performance
            int batchSize = 50;
            for (int i = 0; i < maxTransforms; i += batchSize) {
                int endIndex = Math.min(i + batchSize, maxTransforms);
                
                // Process batch
                for (int j = i; j < endIndex; j++) {
                    try {
                        JSONObject transformObj = transformArray.getJSONObject(j);
                        ObjectTransformation transformation = parseTransformationOptimized(transformObj, context);
                        if (transformation != null) {
                            transformations.add(transformation);
                        }
                    } catch (Exception e) {
                        // Skip malformed transformation but continue processing
                        System.err.println("Skipping malformed transformation at index " + j + ": " + e.getMessage());
                    }
                }
                
                // Check for interruption between batches
                if (Thread.currentThread().isInterrupted()) {
                    break;
                }
            }
            
        } catch (Exception e) {
            System.err.println("Error processing AI response: " + e.getMessage());
        }
        
        return transformations;
    }
    
    private ObjectTransformation parseTransformationOptimized(JSONObject transformObj, GalaxyContext context) 
            throws JSONException {
        
        // Fast validation of required fields
        if (!transformObj.has("objectId") || !transformObj.has("type")) {
            return null;
        }
        
        int objectId = transformObj.getInt("objectId");
        String typeStr = transformObj.getString("type");
        
        // Check for failure indicator (objectId 0)
        if (objectId == 0) {
            String description = transformObj.optString("description", "Object not found");
            System.out.println("DEBUG: AI indicated object not found: " + description);
            return null;
        }
        
        // Quick object existence check using context
        if (!objectExistsInContext(objectId, context)) {
            return null;
        }
        
        // Parse transformation type
        ObjectTransformation.TransformationType type;
        try {
            type = ObjectTransformation.TransformationType.valueOf(typeStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
        
        // Parse vector values with defaults
        float x = (float) transformObj.optDouble("x", 0.0);
        float y = (float) transformObj.optDouble("y", 0.0);
        float z = (float) transformObj.optDouble("z", 0.0);
        
        // Validate numeric values
        if (!isValidFloat(x) || !isValidFloat(y) || !isValidFloat(z)) {
            return null;
        }
        
        String description = transformObj.optString("description", "");
        
        return new ObjectTransformation.Builder()
            .setObjectId(objectId)
            .setType(type)
            .setVectorValue(x, y, z)
            .setDescription(description)
            .build();
    }
    
    private boolean objectExistsInContext(int objectId, GalaxyContext context) {
        // Optimized object existence check
        return context.getObjects().stream()
            .anyMatch(obj -> obj.getUniqueId() == objectId);
    }
    
    private boolean isValidFloat(float value) {
        return !Float.isNaN(value) && !Float.isInfinite(value) && Math.abs(value) < 1e6;
    }
    
    private String generateRequestId(String aiResponse, GalaxyContext context) {
        // Generate a simple hash-based ID for deduplication
        int hash = Objects.hash(
            aiResponse != null ? aiResponse.hashCode() : 0,
            context != null ? context.getGalaxyName() : "",
            context != null ? context.getCurrentZone() : "",
            context != null ? context.getObjectCount() : 0
        );
        return String.valueOf(hash);
    }
    
    private JSONObject attemptJsonRecovery(String aiResponse) {
        try {
            // Common recovery strategies
            String cleaned = aiResponse.trim();
            
            // Remove common prefixes/suffixes that AI might add
            if (cleaned.startsWith("```json")) {
                cleaned = cleaned.substring(7);
            }
            if (cleaned.endsWith("```")) {
                cleaned = cleaned.substring(0, cleaned.length() - 3);
            }
            
            // Remove leading/trailing text that isn't JSON
            int start = cleaned.indexOf('{');
            int end = cleaned.lastIndexOf('}');
            
            if (start != -1 && end != -1 && end > start) {
                cleaned = cleaned.substring(start, end + 1);
            }
            
            // Try to fix common JSON issues
            cleaned = cleaned.replaceAll(",\\s*}", "}"); // Remove trailing commas
            cleaned = cleaned.replaceAll(",\\s*]", "]");
            
            return new JSONObject(cleaned);
            
        } catch (JSONException e) {
            return null;
        }
    }
    
    /**
     * Gets statistics about the processor performance.
     */
    public Map<String, Object> getStatistics() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("activeProcessingCount", activeProcessing.size());
        stats.put("processingTimeoutMs", PROCESSING_TIMEOUT_MS);
        stats.put("maxTransformations", MAX_TRANSFORMATIONS);
        
        // Thread pool statistics
        if (processingExecutor instanceof ThreadPoolExecutor) {
            ThreadPoolExecutor tpe = (ThreadPoolExecutor) processingExecutor;
            stats.put("threadPoolSize", tpe.getPoolSize());
            stats.put("activeThreads", tpe.getActiveCount());
            stats.put("completedTasks", tpe.getCompletedTaskCount());
        }
        
        return stats;
    }
    
    /**
     * Post-processes transformations to fix common issues like overlapping object positions.
     * This method detects when multiple ADD transformations create objects at the same position
     * and distributes them to avoid overlap.
     */
    public List<ObjectTransformation> postProcessTransformations(List<ObjectTransformation> transformations) {
        if (transformations == null || transformations.isEmpty()) {
            return transformations;
        }
        
        List<ObjectTransformation> processed = new ArrayList<>();
        Map<String, List<ObjectTransformation>> addTransformsByPosition = new HashMap<>();
        
        // Separate ADD transformations by position and collect others
        for (ObjectTransformation transform : transformations) {
            if (transform.getType() == ObjectTransformation.TransformationType.ADD) {
                String positionKey = formatPositionKey(transform.getVectorValue());
                addTransformsByPosition.computeIfAbsent(positionKey, k -> new ArrayList<>()).add(transform);
            } else {
                processed.add(transform);
            }
        }
        
        // Process ADD transformations, distributing overlapping ones
        for (Map.Entry<String, List<ObjectTransformation>> entry : addTransformsByPosition.entrySet()) {
            List<ObjectTransformation> transformsAtPosition = entry.getValue();
            
            if (transformsAtPosition.size() == 1) {
                // Single object, no distribution needed
                processed.add(transformsAtPosition.get(0));
            } else {
                // Multiple objects at same position, distribute them
                List<ObjectTransformation> distributed = distributeOverlappingObjects(transformsAtPosition);
                processed.addAll(distributed);
            }
        }
        
        return processed;
    }
    
    /**
     * Creates a position key for grouping transformations by location.
     */
    private String formatPositionKey(whitehole.math.Vec3f position) {
        // Round to nearest 10 units to group nearby positions
        int x = Math.round(position.x / 10.0f) * 10;
        int y = Math.round(position.y / 10.0f) * 10;
        int z = Math.round(position.z / 10.0f) * 10;
        return x + "," + y + "," + z;
    }
    
    /**
     * Distributes overlapping ADD transformations to avoid object overlap.
     */
    private List<ObjectTransformation> distributeOverlappingObjects(List<ObjectTransformation> overlappingTransforms) {
        List<ObjectTransformation> distributed = new ArrayList<>();
        
        if (overlappingTransforms.isEmpty()) {
            return distributed;
        }
        
        // Get the base position from the first transformation
        whitehole.math.Vec3f basePosition = overlappingTransforms.get(0).getVectorValue();
        int quantity = overlappingTransforms.size();
        
        // Determine object type for spacing (use first object's type)
        String objectType = overlappingTransforms.get(0).getAddObjectType();
        float spacing = getObjectSpacing(objectType);
        
        // Generate distributed positions
        List<whitehole.math.Vec3f> distributedPositions = distributePositions(basePosition, quantity, spacing);
        
        // Create new transformations with distributed positions
        for (int i = 0; i < overlappingTransforms.size(); i++) {
            ObjectTransformation original = overlappingTransforms.get(i);
            whitehole.math.Vec3f newPosition = distributedPositions.get(i);
            
            // Create new transformation with distributed position
            ObjectTransformation distributed_transform = ObjectTransformation.addObject(
                original.getAddObjectType(),
                newPosition,
                original.getDescription() + " (distributed)"
            );
            distributed.add(distributed_transform);
        }
        
        return distributed;
    }
    
    /**
     * Distributes positions in a pattern to avoid overlap.
     */
    private List<whitehole.math.Vec3f> distributePositions(whitehole.math.Vec3f basePosition, int quantity, float spacing) {
        List<whitehole.math.Vec3f> positions = new ArrayList<>();
        
        if (quantity == 1) {
            positions.add(new whitehole.math.Vec3f(basePosition));
            return positions;
        }
        
        if (quantity <= 4) {
            // Line distribution
            float startOffset = -(quantity - 1) * spacing / 2.0f;
            for (int i = 0; i < quantity; i++) {
                float offset = startOffset + i * spacing;
                positions.add(new whitehole.math.Vec3f(basePosition.x + offset, basePosition.y, basePosition.z));
            }
        } else if (quantity <= 9) {
            // Grid distribution
            int cols = (int) Math.ceil(Math.sqrt(quantity));
            int rows = (int) Math.ceil((double) quantity / cols);
            
            float startX = -(cols - 1) * spacing / 2.0f;
            float startZ = -(rows - 1) * spacing / 2.0f;
            
            int objectIndex = 0;
            for (int row = 0; row < rows && objectIndex < quantity; row++) {
                for (int col = 0; col < cols && objectIndex < quantity; col++) {
                    float x = basePosition.x + startX + col * spacing;
                    float z = basePosition.z + startZ + row * spacing;
                    positions.add(new whitehole.math.Vec3f(x, basePosition.y, z));
                    objectIndex++;
                }
            }
        } else {
            // Circular distribution
            float radius = (quantity * spacing) / (2.0f * (float) Math.PI);
            for (int i = 0; i < quantity; i++) {
                double angle = 2.0 * Math.PI * i / quantity;
                float x = basePosition.x + radius * (float) Math.cos(angle);
                float z = basePosition.z + radius * (float) Math.sin(angle);
                positions.add(new whitehole.math.Vec3f(x, basePosition.y, z));
            }
        }
        
        return positions;
    }
    
    /**
     * Gets appropriate spacing for different object types.
     */
    private float getObjectSpacing(String objectType) {
        if (objectType == null) return 75.0f;
        
        String lowerType = objectType.toLowerCase();
        
        // Smaller objects need less spacing
        if (lowerType.contains("coin") || lowerType.contains("star") || lowerType.contains("bit")) {
            return 50.0f;
        }
        // Medium objects
        else if (lowerType.contains("goomba") || lowerType.contains("koopa") || lowerType.contains("block")) {
            return 100.0f;
        }
        // Large objects
        else if (lowerType.contains("platform") || lowerType.contains("pipe") || lowerType.contains("ship")) {
            return 200.0f;
        }
        // Default spacing
        else {
            return 75.0f;
        }
    }
}
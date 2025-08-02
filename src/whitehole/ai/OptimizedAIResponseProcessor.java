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
        
        // Quick check for JSON structure
        String trimmed = aiResponse.trim();
        if (!trimmed.startsWith("{") || !trimmed.endsWith("}")) {
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
            // Try to extract JSON using regex first (faster than parsing)
            Matcher matcher = JSON_PATTERN.matcher(aiResponse);
            if (!matcher.find()) {
                return null;
            }
            
            String jsonStr = matcher.group();
            
            // Quick validation before parsing
            if (!jsonStr.contains("transformations")) {
                return null;
            }
            
            // Parse JSON
            JSONObject json = new JSONObject(jsonStr);
            
            // Validate structure
            if (!json.has("transformations")) {
                return null;
            }
            
            JSONArray transformations = json.getJSONArray("transformations");
            if (transformations.length() == 0) {
                return null;
            }
            
            // Validate first transformation to ensure proper format
            if (transformations.length() > 0) {
                JSONObject firstTransform = transformations.getJSONObject(0);
                if (!firstTransform.has("objectId") || !firstTransform.has("type")) {
                    return null;
                }
            }
            
            return json;
            
        } catch (JSONException e) {
            // Try to recover from common JSON errors
            return attemptJsonRecovery(aiResponse);
        }
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
}
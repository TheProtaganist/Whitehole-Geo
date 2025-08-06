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
package whitehole.ai.providers;

import whitehole.ai.EnhancedAIProvider;
import whitehole.ai.AIResponse;
import whitehole.ai.AIProviderException;
import whitehole.ai.GalaxyContext;
import whitehole.ai.ObjectDatabaseContext;
import org.json.JSONObject;
import org.json.JSONArray;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;
import java.awt.image.BufferedImage;

/**
 * OpenAI AI provider implementation.
 * Supports GPT-4, GPT-3.5-turbo, and other OpenAI models with streaming and vision capabilities.
 */
public class OpenAIProvider implements EnhancedAIProvider {
    
    private String apiKey;
    private String endpoint = "https://api.openai.com/v1/chat/completions";
    private String modelsEndpoint = "https://api.openai.com/v1/models";
    private String selectedModel = "gpt-4";
    private boolean configured = false;
    private ModelInfo[] cachedModels = null;
    private long modelsCacheTime = 0;
    private static final long MODELS_CACHE_DURATION = 300000; // 5 minutes
    
    // Popular OpenAI models with their information
    private static final ModelInfo[] POPULAR_MODELS = {
        new ModelInfo("gpt-4", "GPT-4", 
            "Most capable GPT-4 model", false, 8192, 0.03, 0.06),
        new ModelInfo("gpt-4-turbo", "GPT-4 Turbo", 
            "Faster GPT-4 with larger context window", false, 128000, 0.01, 0.03),
        new ModelInfo("gpt-4-turbo-preview", "GPT-4 Turbo Preview", 
            "Latest GPT-4 Turbo preview model", false, 128000, 0.01, 0.03),
        new ModelInfo("gpt-4-vision-preview", "GPT-4 Vision", 
            "GPT-4 with vision capabilities", true, 128000, 0.01, 0.03),
        new ModelInfo("gpt-3.5-turbo", "GPT-3.5 Turbo", 
            "Fast and cost-effective model", false, 16385, 0.0015, 0.002),
        new ModelInfo("gpt-3.5-turbo-16k", "GPT-3.5 Turbo 16K", 
            "GPT-3.5 with larger context window", false, 16385, 0.003, 0.004),
        new ModelInfo("gpt-3.5-turbo-instruct", "GPT-3.5 Turbo Instruct", 
            "Instruction-following GPT-3.5 model", false, 4096, 0.0015, 0.002)
    };
    
    @Override
    public void configure(Map<String, String> config) throws AIProviderException {
        try {
            this.apiKey = config.get("apiKey");
            
            if (config.containsKey("endpoint")) {
                this.endpoint = config.get("endpoint");
            }
            
            if (config.containsKey("model")) {
                this.selectedModel = config.get("model");
            }
            
            if (apiKey == null || apiKey.trim().isEmpty()) {
                throw new AIProviderException("OpenAI API key is required", 
                    AIProviderException.ErrorType.CONFIGURATION_ERROR);
            }
            
            this.configured = true;
            
        } catch (ClassCastException e) {
            throw new AIProviderException("Invalid configuration format", 
                AIProviderException.ErrorType.CONFIGURATION_ERROR);
        }
    }
    
    @Override
    public AIResponse processCommand(String command, GalaxyContext context) throws AIProviderException {
        if (!configured) {
            throw new AIProviderException("OpenAI provider is not configured", 
                AIProviderException.ErrorType.CONFIGURATION_ERROR);
        }
        
        try {
            String contextStr = context != null ? serializeContextToString(context) : "";
            String rawResponse = sendRequest(command, contextStr);
            
            return new AIResponse.Builder()
                .setSuccess(true)
                .setFeedback(rawResponse)
                .build();
                
        } catch (Exception e) {
            return AIResponse.failure("Failed to process command: " + e.getMessage());
        }
    }
    
    @Override
    public boolean supportsStreaming() {
        return true; // OpenAI supports streaming
    }
    
    @Override
    public AIResponse processStreamingCommand(String command, GalaxyContext context, StreamingCallback callback) throws AIProviderException {
        if (!configured) {
            throw new AIProviderException("OpenAI provider is not configured", 
                AIProviderException.ErrorType.CONFIGURATION_ERROR);
        }
        
        try {
            String contextStr = context != null ? serializeContextToString(context) : "";
            String rawResponse = sendStreamingRequest(command, contextStr, callback);
            
            return new AIResponse.Builder()
                .setSuccess(true)
                .setFeedback(rawResponse)
                .build();
                
        } catch (Exception e) {
            callback.onError(e);
            return AIResponse.failure("Failed to process streaming command: " + e.getMessage());
        }
    }
    
    @Override
    public ModelInfo[] getAvailableModels() throws AIProviderException {
        // Return cached models if still valid
        long currentTime = System.currentTimeMillis();
        if (cachedModels != null && (currentTime - modelsCacheTime) < MODELS_CACHE_DURATION) {
            return cachedModels;
        }
        
        if (!isAvailable()) {
            // Return popular models as fallback
            return POPULAR_MODELS;
        }
        
        try {
            URL url = new URL(modelsEndpoint);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setRequestProperty("Authorization", "Bearer " + apiKey);
            connection.setRequestProperty("User-Agent", "Whitehole-Neo/1.0");
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(15000);
            
            int responseCode = connection.getResponseCode();
            if (responseCode != HttpURLConnection.HTTP_OK) {
                String errorBody = readResponse(connection.getErrorStream());
                System.err.println("Failed to fetch OpenAI models (HTTP " + responseCode + "): " + errorBody);
                return POPULAR_MODELS; // Fallback to popular models
            }
            
            String responseBody = readResponse(connection.getInputStream());
            JSONObject response = new JSONObject(responseBody);
            
            if (!response.has("data")) {
                return POPULAR_MODELS;
            }
            
            JSONArray modelsArray = response.getJSONArray("data");
            List<ModelInfo> modelsList = new ArrayList<>();
            
            for (int i = 0; i < modelsArray.length(); i++) {
                JSONObject modelObj = modelsArray.getJSONObject(i);
                
                String id = modelObj.getString("id");
                
                // Only include chat models
                if (!id.startsWith("gpt-") && !id.contains("turbo")) {
                    continue;
                }
                
                // Get model info from popular models or create basic info
                ModelInfo modelInfo = getPopularModelInfo(id);
                if (modelInfo == null) {
                    String name = id.replace("-", " ").toUpperCase();
                    boolean supportsVision = id.contains("vision");
                    int maxTokens = id.contains("16k") ? 16385 : (id.contains("turbo") ? 4096 : 8192);
                    double inputCost = id.contains("gpt-4") ? 0.03 : 0.0015;
                    double outputCost = id.contains("gpt-4") ? 0.06 : 0.002;
                    
                    modelInfo = new ModelInfo(id, name, "OpenAI " + name + " model", 
                                            supportsVision, maxTokens, inputCost, outputCost);
                }
                
                modelsList.add(modelInfo);
            }
            
            cachedModels = modelsList.toArray(new ModelInfo[0]);
            modelsCacheTime = currentTime;
            
            return cachedModels;
            
        } catch (Exception e) {
            System.err.println("Failed to fetch OpenAI models: " + e.getMessage());
            return POPULAR_MODELS; // Fallback to popular models
        }
    }
    
    @Override
    public boolean testConnection() {
        if (!isAvailable()) {
            return false;
        }
        
        try {
            // Test with a simple request
            String response = sendRequest("Test connection", "");
            return response != null && !response.isEmpty();
        } catch (AIProviderException e) {
            return false;
        }
    }
    
    @Override
    public boolean isAvailable() {
        return configured && apiKey != null && !apiKey.trim().isEmpty();
    }
    
    @Override
    public String getProviderName() {
        return "OpenAI";
    }
    
    @Override
    public String getConfigurationStatus() {
        if (!configured) {
            return "Not configured - API key required";
        }
        if (apiKey == null || apiKey.trim().isEmpty()) {
            return "Missing API key";
        }
        return "Ready - Model: " + selectedModel;
    }
    
    /**
     * Processes an image-based command using vision-capable models.
     */
    public AIResponse processImageCommand(String command, BufferedImage image, GalaxyContext context) throws AIProviderException {
        if (!configured) {
            throw new AIProviderException("OpenAI provider is not configured", 
                AIProviderException.ErrorType.CONFIGURATION_ERROR);
        }
        
        if (!supportsVision()) {
            throw new AIProviderException("Current model does not support vision capabilities", 
                AIProviderException.ErrorType.CONFIGURATION_ERROR);
        }
        
        try {
            String contextStr = context != null ? serializeContextToString(context) : "";
            String rawResponse = sendImageRequest(command, image, contextStr);
            
            return new AIResponse.Builder()
                .setSuccess(true)
                .setFeedback(rawResponse)
                .build();
                
        } catch (Exception e) {
            return AIResponse.failure("Failed to process image command: " + e.getMessage());
        }
    }
    
    /**
     * Checks if the current model supports vision capabilities.
     */
    public boolean supportsVision() {
        return selectedModel.contains("vision") || selectedModel.contains("gpt-4-turbo");
    }
    
    /**
     * Sends a request to the OpenAI API.
     */
    public String sendRequest(String prompt, String context) throws AIProviderException {
        if (!isAvailable()) {
            throw new AIProviderException("OpenAI provider is not configured", 
                AIProviderException.ErrorType.CONFIGURATION_ERROR);
        }
        
        try {
            URL url = new URL(endpoint);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("Authorization", "Bearer " + apiKey);
            connection.setRequestProperty("User-Agent", "Whitehole-Neo/1.0");
            connection.setDoOutput(true);
            connection.setConnectTimeout(30000); // 30 seconds
            connection.setReadTimeout(60000); // 60 seconds
            
            // Build request body
            JSONObject requestBody = buildRequestBody(prompt, context, false);
            
            // Send request
            try (OutputStream os = connection.getOutputStream()) {
                byte[] input = requestBody.toString().getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
            }
            
            // Read response
            int responseCode = connection.getResponseCode();
            String responseBody;
            
            if (responseCode == HttpURLConnection.HTTP_OK) {
                responseBody = readResponse(connection.getInputStream());
            } else {
                responseBody = readResponse(connection.getErrorStream());
                handleErrorResponse(responseCode, responseBody);
            }
            
            // Parse response and return content
            return parseResponse(responseBody);
            
        } catch (IOException e) {
            throw new AIProviderException("Network error: " + e.getMessage(), 
                AIProviderException.ErrorType.NETWORK_ERROR);
        } catch (Exception e) {
            throw new AIProviderException("Unexpected error: " + e.getMessage(), 
                AIProviderException.ErrorType.UNKNOWN_ERROR);
        }
    }  
  
    /**
     * Sends a streaming request to the OpenAI API.
     */
    private String sendStreamingRequest(String prompt, String context, StreamingCallback callback) throws AIProviderException {
        if (!isAvailable()) {
            throw new AIProviderException("OpenAI provider is not configured", 
                AIProviderException.ErrorType.CONFIGURATION_ERROR);
        }
        
        try {
            URL url = new URL(endpoint);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("Authorization", "Bearer " + apiKey);
            connection.setRequestProperty("User-Agent", "Whitehole-Neo/1.0");
            connection.setDoOutput(true);
            connection.setConnectTimeout(30000);
            connection.setReadTimeout(120000); // Longer timeout for streaming
            
            // Build request body with streaming enabled
            JSONObject requestBody = buildRequestBody(prompt, context, true);
            
            // Send request
            try (OutputStream os = connection.getOutputStream()) {
                byte[] input = requestBody.toString().getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
            }
            
            // Read streaming response
            int responseCode = connection.getResponseCode();
            if (responseCode != HttpURLConnection.HTTP_OK) {
                String errorBody = readResponse(connection.getErrorStream());
                handleErrorResponse(responseCode, errorBody);
            }
            
            return readStreamingResponse(connection.getInputStream(), callback);
            
        } catch (IOException e) {
            throw new AIProviderException("Network error: " + e.getMessage(), 
                AIProviderException.ErrorType.NETWORK_ERROR);
        } catch (Exception e) {
            throw new AIProviderException("Unexpected error: " + e.getMessage(), 
                AIProviderException.ErrorType.UNKNOWN_ERROR);
        }
    }
    
    /**
     * Sends an image request to the OpenAI API.
     */
    private String sendImageRequest(String prompt, BufferedImage image, String context) throws AIProviderException {
        if (!isAvailable()) {
            throw new AIProviderException("OpenAI provider is not configured", 
                AIProviderException.ErrorType.CONFIGURATION_ERROR);
        }
        
        try {
            URL url = new URL(endpoint);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("Authorization", "Bearer " + apiKey);
            connection.setRequestProperty("User-Agent", "Whitehole-Neo/1.0");
            connection.setDoOutput(true);
            connection.setConnectTimeout(30000);
            connection.setReadTimeout(90000); // Longer timeout for image processing
            
            // Build request body with image
            JSONObject requestBody = buildImageRequestBody(prompt, image, context);
            
            // Send request
            try (OutputStream os = connection.getOutputStream()) {
                byte[] input = requestBody.toString().getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
            }
            
            // Read response
            int responseCode = connection.getResponseCode();
            String responseBody;
            
            if (responseCode == HttpURLConnection.HTTP_OK) {
                responseBody = readResponse(connection.getInputStream());
            } else {
                responseBody = readResponse(connection.getErrorStream());
                handleErrorResponse(responseCode, responseBody);
            }
            
            // Parse response and return content
            return parseResponse(responseBody);
            
        } catch (IOException e) {
            throw new AIProviderException("Network error: " + e.getMessage(), 
                AIProviderException.ErrorType.NETWORK_ERROR);
        } catch (Exception e) {
            throw new AIProviderException("Unexpected error: " + e.getMessage(), 
                AIProviderException.ErrorType.UNKNOWN_ERROR);
        }
    }
    
    /**
     * Builds the JSON request body for the OpenAI API.
     */
    private JSONObject buildRequestBody(String prompt, String context, boolean stream) {
        JSONObject requestBody = new JSONObject();
        
        requestBody.put("model", selectedModel);
        requestBody.put("stream", stream);
        
        // Build messages array
        JSONArray messages = new JSONArray();
        
        // System message with context
        if (!context.isEmpty()) {
            JSONObject systemMessage = new JSONObject();
            systemMessage.put("role", "system");
            systemMessage.put("content", "You are an AI assistant helping with Mario Galaxy level editing. " +
                "Use the following context to understand the current state of the level:\n\n" + context);
            messages.put(systemMessage);
        }
        
        // User message
        JSONObject userMessage = new JSONObject();
        userMessage.put("role", "user");
        userMessage.put("content", prompt);
        messages.put(userMessage);
        
        requestBody.put("messages", messages);
        
        // Add generation parameters
        requestBody.put("temperature", 0.7);
        requestBody.put("max_tokens", 2048);
        requestBody.put("top_p", 0.9);
        requestBody.put("frequency_penalty", 0.0);
        requestBody.put("presence_penalty", 0.0);
        
        return requestBody;
    }
    
    /**
     * Builds the JSON request body for image requests.
     */
    private JSONObject buildImageRequestBody(String prompt, BufferedImage image, String context) {
        JSONObject requestBody = new JSONObject();
        
        // Use vision-capable model
        String visionModel = selectedModel.contains("vision") ? selectedModel : "gpt-4-vision-preview";
        requestBody.put("model", visionModel);
        requestBody.put("stream", false);
        
        // Build messages array
        JSONArray messages = new JSONArray();
        
        // System message with context
        if (!context.isEmpty()) {
            JSONObject systemMessage = new JSONObject();
            systemMessage.put("role", "system");
            systemMessage.put("content", "You are an AI assistant helping with Mario Galaxy level editing. " +
                "Use the following context to understand the current state of the level:\n\n" + context);
            messages.put(systemMessage);
        }
        
        // User message with image
        JSONObject userMessage = new JSONObject();
        userMessage.put("role", "user");
        
        JSONArray content = new JSONArray();
        
        // Text content
        JSONObject textContent = new JSONObject();
        textContent.put("type", "text");
        textContent.put("text", prompt);
        content.put(textContent);
        
        // Image content (base64 encoded)
        JSONObject imageContent = new JSONObject();
        imageContent.put("type", "image_url");
        JSONObject imageUrl = new JSONObject();
        imageUrl.put("url", "data:image/png;base64," + encodeImageToBase64(image));
        imageContent.put("image_url", imageUrl);
        content.put(imageContent);
        
        userMessage.put("content", content);
        messages.put(userMessage);
        
        requestBody.put("messages", messages);
        
        // Add generation parameters
        requestBody.put("temperature", 0.7);
        requestBody.put("max_tokens", 2048);
        
        return requestBody;
    }
    
    /**
     * Parses the JSON response from the OpenAI API.
     */
    private String parseResponse(String responseBody) throws AIProviderException {
        try {
            JSONObject response = new JSONObject(responseBody);
            
            // Check for error
            if (response.has("error")) {
                JSONObject error = response.getJSONObject("error");
                String message = error.optString("message", "Unknown error");
                String type = error.optString("type", "unknown");
                
                AIProviderException.ErrorType errorType = mapErrorType(type);
                throw new AIProviderException("OpenAI API error: " + message, errorType);
            }
            
            // Extract content from choices
            if (!response.has("choices")) {
                throw new AIProviderException("No choices in response", 
                    AIProviderException.ErrorType.INVALID_RESPONSE);
            }
            
            JSONArray choices = response.getJSONArray("choices");
            if (choices.length() == 0) {
                throw new AIProviderException("Empty choices array", 
                    AIProviderException.ErrorType.INVALID_RESPONSE);
            }
            
            JSONObject choice = choices.getJSONObject(0);
            JSONObject message = choice.getJSONObject("message");
            String content = message.getString("content");
            
            return content;
                
        } catch (Exception e) {
            throw new AIProviderException("Failed to parse response: " + e.getMessage(), 
                AIProviderException.ErrorType.INVALID_RESPONSE);
        }
    }
    
    /**
     * Reads a streaming response from the OpenAI API.
     */
    private String readStreamingResponse(InputStream inputStream, StreamingCallback callback) throws IOException {
        StringBuilder fullResponse = new StringBuilder();
        
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("data: ")) {
                    String data = line.substring(6);
                    
                    if ("[DONE]".equals(data)) {
                        callback.onComplete();
                        break;
                    }
                    
                    try {
                        JSONObject chunk = new JSONObject(data);
                        if (chunk.has("choices")) {
                            JSONArray choices = chunk.getJSONArray("choices");
                            if (choices.length() > 0) {
                                JSONObject choice = choices.getJSONObject(0);
                                if (choice.has("delta")) {
                                    JSONObject delta = choice.getJSONObject("delta");
                                    if (delta.has("content")) {
                                        String content = delta.getString("content");
                                        fullResponse.append(content);
                                        callback.onChunk(content);
                                    }
                                }
                            }
                        }
                    } catch (Exception e) {
                        // Skip malformed chunks
                        System.err.println("Skipping malformed streaming chunk: " + e.getMessage());
                    }
                }
            }
        }
        
        return fullResponse.toString();
    }
    
    /**
     * Handles error responses from the OpenAI API.
     */
    private void handleErrorResponse(int responseCode, String responseBody) throws AIProviderException {
        try {
            JSONObject errorResponse = new JSONObject(responseBody);
            if (errorResponse.has("error")) {
                JSONObject error = errorResponse.getJSONObject("error");
                String message = error.optString("message", "Unknown error");
                String type = error.optString("type", "unknown");
                
                AIProviderException.ErrorType errorType = mapErrorType(type);
                throw new AIProviderException("OpenAI API error (HTTP " + responseCode + "): " + message, errorType);
            }
        } catch (Exception e) {
            // If we can't parse the error, use the response code
            AIProviderException.ErrorType errorType = mapHttpErrorCode(responseCode);
            throw new AIProviderException("OpenAI API error (HTTP " + responseCode + "): " + responseBody, errorType);
        }
    }
    
    /**
     * Maps OpenAI error types to our error types.
     */
    private AIProviderException.ErrorType mapErrorType(String openaiErrorType) {
        switch (openaiErrorType.toLowerCase()) {
            case "invalid_api_key":
            case "invalid_organization":
                return AIProviderException.ErrorType.AUTHENTICATION_ERROR;
            case "rate_limit_exceeded":
                return AIProviderException.ErrorType.RATE_LIMIT_ERROR;
            case "insufficient_quota":
                return AIProviderException.ErrorType.RATE_LIMIT_ERROR;
            case "model_not_found":
            case "invalid_request_error":
                return AIProviderException.ErrorType.CONFIGURATION_ERROR;
            case "server_error":
                return AIProviderException.ErrorType.SERVICE_UNAVAILABLE;
            default:
                return AIProviderException.ErrorType.UNKNOWN_ERROR;
        }
    }
    
    /**
     * Maps HTTP error codes to our error types.
     */
    private AIProviderException.ErrorType mapHttpErrorCode(int responseCode) {
        switch (responseCode) {
            case 401:
                return AIProviderException.ErrorType.AUTHENTICATION_ERROR;
            case 429:
                return AIProviderException.ErrorType.RATE_LIMIT_ERROR;
            case 400:
            case 404:
                return AIProviderException.ErrorType.CONFIGURATION_ERROR;
            case 500:
            case 502:
            case 503:
                return AIProviderException.ErrorType.SERVICE_UNAVAILABLE;
            case 408:
                return AIProviderException.ErrorType.TIMEOUT_ERROR;
            default:
                return AIProviderException.ErrorType.NETWORK_ERROR;
        }
    }
    
    /**
     * Encodes a BufferedImage to base64 string.
     */
    private String encodeImageToBase64(BufferedImage image) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            javax.imageio.ImageIO.write(image, "png", baos);
            byte[] imageBytes = baos.toByteArray();
            return java.util.Base64.getEncoder().encodeToString(imageBytes);
        } catch (IOException e) {
            throw new RuntimeException("Failed to encode image to base64", e);
        }
    }
    
    /**
     * Serializes galaxy context to a string for AI processing.
     */
    private String serializeContextToString(GalaxyContext context) {
        if (context == null) return "";
        
        StringBuilder sb = new StringBuilder();
        
        // Add object database context
        try {
            ObjectDatabaseContext objContext = ObjectDatabaseContext.getInstance();
            sb.append("=== OBJECT DATABASE CONTEXT ===\n");
            sb.append(objContext.getMinimalContext());
            sb.append("\n");
        } catch (Exception e) {
            // If object database context fails, continue without it
            System.err.println("Failed to load object database context: " + e.getMessage());
        }
        
        // Add galaxy context
        sb.append("=== GALAXY CONTEXT ===\n");
        sb.append("Galaxy: ").append(context.getGalaxyName()).append("\n");
        sb.append("Current Zone: ").append(context.getCurrentZone()).append("\n");
        sb.append("Objects (").append(context.getObjectCount()).append("):\n");
        
        for (GalaxyContext.ObjectInfo obj : context.getObjects()) {
            sb.append("- ").append(obj.getName())
              .append(" (").append(obj.getType()).append(")")
              .append(" at ").append(obj.getPosition())
              .append("\n");
        }
        
        return sb.toString();
    }
    
    /**
     * Reads the response from an InputStream.
     */
    private String readResponse(InputStream inputStream) throws IOException {
        if (inputStream == null) {
            return "";
        }
        
        StringBuilder response = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                response.append(line);
            }
        }
        return response.toString();
    }
    
    /**
     * Gets the currently selected model.
     */
    public String getSelectedModel() {
        return selectedModel;
    }
    
    /**
     * Sets the model to use.
     */
    public void setSelectedModel(String model) {
        this.selectedModel = model;
    }
    
    /**
     * Gets model information by ID.
     */
    public ModelInfo getModelInfo(String modelId) throws AIProviderException {
        ModelInfo[] models = getAvailableModels();
        for (ModelInfo model : models) {
            if (model.getId().equals(modelId)) {
                return model;
            }
        }
        return null;
    }
    
    /**
     * Gets popular models that are known to work well.
     */
    public ModelInfo[] getPopularModels() {
        return POPULAR_MODELS;
    }
    
    /**
     * Gets model info from popular models list.
     */
    private ModelInfo getPopularModelInfo(String modelId) {
        for (ModelInfo model : POPULAR_MODELS) {
            if (model.getId().equals(modelId)) {
                return model;
            }
        }
        return null;
    }
    
    /**
     * Checks if a specific model is available.
     */
    public boolean isModelAvailable(String modelId) {
        try {
            return getModelInfo(modelId) != null;
        } catch (AIProviderException e) {
            return false;
        }
    }
}
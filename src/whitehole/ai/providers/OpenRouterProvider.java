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

/**
 * OpenRouter AI provider implementation.
 * Supports multiple AI models through the OpenRouter API including Claude, GPT-4, Llama, and more.
 */
public class OpenRouterProvider implements EnhancedAIProvider {
    
    private String apiKey;
    private String endpoint = "https://openrouter.ai/api/v1/chat/completions";
    private String modelsEndpoint = "https://openrouter.ai/api/v1/models";
    private String selectedModel = "anthropic/claude-3-haiku";
    private boolean configured = false;
    private ModelInfo[] cachedModels = null;
    private long modelsCacheTime = 0;
    private static final long MODELS_CACHE_DURATION = 300000; // 5 minutes
    
    // Popular models with their information
    private static final ModelInfo[] POPULAR_MODELS = {
        new ModelInfo("anthropic/claude-3-haiku", "Claude 3 Haiku", 
            "Fast and efficient Claude model", false, 200000, 0.00025, 0.00125),
        new ModelInfo("anthropic/claude-3-sonnet", "Claude 3 Sonnet", 
            "Balanced Claude model", false, 200000, 0.003, 0.015),
        new ModelInfo("anthropic/claude-3-opus", "Claude 3 Opus", 
            "Most capable Claude model", false, 200000, 0.015, 0.075),
        new ModelInfo("openai/gpt-4", "GPT-4", 
            "OpenAI's most capable model", false, 8192, 0.03, 0.06),
        new ModelInfo("openai/gpt-4-turbo", "GPT-4 Turbo", 
            "Faster GPT-4 with larger context", false, 128000, 0.01, 0.03),
        new ModelInfo("openai/gpt-3.5-turbo", "GPT-3.5 Turbo", 
            "Fast and cost-effective OpenAI model", false, 16385, 0.0015, 0.002),
        new ModelInfo("meta-llama/llama-3-70b-instruct", "Llama 3 70B", 
            "Meta's large language model", false, 8192, 0.00059, 0.00079),
        new ModelInfo("meta-llama/llama-3-8b-instruct", "Llama 3 8B", 
            "Smaller, faster Llama model", false, 8192, 0.00018, 0.00018),
        new ModelInfo("google/gemini-pro", "Gemini Pro", 
            "Google's advanced language model", false, 32768, 0.000125, 0.000375),
        new ModelInfo("mistralai/mixtral-8x7b-instruct", "Mixtral 8x7B", 
            "Mistral's mixture of experts model", false, 32768, 0.00024, 0.00024)
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
                throw new AIProviderException("OpenRouter API key is required", 
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
            throw new AIProviderException("OpenRouter provider is not configured", 
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
        return true; // OpenRouter supports streaming
    }
    
    @Override
    public AIResponse processStreamingCommand(String command, GalaxyContext context, StreamingCallback callback) throws AIProviderException {
        if (!configured) {
            throw new AIProviderException("OpenRouter provider is not configured", 
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
            connection.setRequestProperty("HTTP-Referer", "https://whitehole-neo.com");
            connection.setRequestProperty("X-Title", "Whitehole Neo");
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(15000);
            
            int responseCode = connection.getResponseCode();
            if (responseCode != HttpURLConnection.HTTP_OK) {
                String errorBody = readResponse(connection.getErrorStream());
                System.err.println("Failed to fetch OpenRouter models (HTTP " + responseCode + "): " + errorBody);
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
                String name = modelObj.optString("name", id);
                String description = modelObj.optString("description", "");
                
                // Parse pricing information
                double inputCost = 0.0;
                double outputCost = 0.0;
                if (modelObj.has("pricing")) {
                    JSONObject pricing = modelObj.getJSONObject("pricing");
                    if (pricing.has("prompt")) {
                        inputCost = Double.parseDouble(pricing.getString("prompt"));
                    }
                    if (pricing.has("completion")) {
                        outputCost = Double.parseDouble(pricing.getString("completion"));
                    }
                }
                
                // Parse context length
                int maxTokens = modelObj.optInt("context_length", 4096);
                
                // Check if model supports vision (basic heuristic)
                boolean supportsVision = name.toLowerCase().contains("vision") || 
                                       description.toLowerCase().contains("vision") ||
                                       id.toLowerCase().contains("vision");
                
                ModelInfo modelInfo = new ModelInfo(id, name, description, supportsVision, 
                                                  maxTokens, inputCost, outputCost);
                modelsList.add(modelInfo);
            }
            
            cachedModels = modelsList.toArray(new ModelInfo[0]);
            modelsCacheTime = currentTime;
            
            return cachedModels;
            
        } catch (Exception e) {
            System.err.println("Failed to fetch OpenRouter models: " + e.getMessage());
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
        return "OpenRouter";
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
     * Sends a request to the OpenRouter API.
     */
    public String sendRequest(String prompt, String context) throws AIProviderException {
        if (!isAvailable()) {
            throw new AIProviderException("OpenRouter provider is not configured", 
                AIProviderException.ErrorType.CONFIGURATION_ERROR);
        }
        
        try {
            URL url = new URL(endpoint);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("Authorization", "Bearer " + apiKey);
            connection.setRequestProperty("HTTP-Referer", "https://whitehole-neo.com");
            connection.setRequestProperty("X-Title", "Whitehole Neo");
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
                throw new AIProviderException("OpenRouter API error (HTTP " + responseCode + "): " + responseBody, 
                    AIProviderException.ErrorType.NETWORK_ERROR);
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
     * Sends a streaming request to the OpenRouter API.
     */
    private String sendStreamingRequest(String prompt, String context, StreamingCallback callback) throws AIProviderException {
        if (!isAvailable()) {
            throw new AIProviderException("OpenRouter provider is not configured", 
                AIProviderException.ErrorType.CONFIGURATION_ERROR);
        }
        
        try {
            URL url = new URL(endpoint);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("Authorization", "Bearer " + apiKey);
            connection.setRequestProperty("HTTP-Referer", "https://whitehole-neo.com");
            connection.setRequestProperty("X-Title", "Whitehole Neo");
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
                throw new AIProviderException("OpenRouter API error (HTTP " + responseCode + "): " + errorBody, 
                    AIProviderException.ErrorType.NETWORK_ERROR);
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
     * Builds the JSON request body for the OpenRouter API.
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
        
        return requestBody;
    }
    
    /**
     * Parses the JSON response from the OpenRouter API.
     */
    private String parseResponse(String responseBody) throws AIProviderException {
        try {
            JSONObject response = new JSONObject(responseBody);
            
            // Check for error
            if (response.has("error")) {
                JSONObject error = response.getJSONObject("error");
                String message = error.optString("message", "Unknown error");
                throw new AIProviderException("OpenRouter API error: " + message, 
                    AIProviderException.ErrorType.NETWORK_ERROR);
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
     * Reads a streaming response from the OpenRouter API.
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
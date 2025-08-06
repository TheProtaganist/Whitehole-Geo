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
 * Claude AI provider implementation.
 * Supports Claude-3 models (Haiku, Sonnet, Opus) through the Anthropic API.
 */
public class ClaudeProvider implements EnhancedAIProvider {
    
    private String apiKey;
    private String endpoint = "https://api.anthropic.com/v1/messages";
    private String selectedModel = "claude-3-haiku-20240307";
    private boolean configured = false;
    private int maxTokens = 4096;
    private String systemPrompt = "";
    private long totalInputTokens = 0;
    private long totalOutputTokens = 0;
    
    // Claude-3 models with their information
    private static final ModelInfo[] CLAUDE_MODELS = {
        new ModelInfo("claude-3-haiku-20240307", "Claude 3 Haiku", 
            "Fast and efficient Claude model for quick responses", false, 200000, 0.00025, 0.00125),
        new ModelInfo("claude-3-sonnet-20240229", "Claude 3 Sonnet", 
            "Balanced Claude model with good performance and capability", false, 200000, 0.003, 0.015),
        new ModelInfo("claude-3-opus-20240229", "Claude 3 Opus", 
            "Most capable Claude model for complex tasks", false, 200000, 0.015, 0.075),
        new ModelInfo("claude-3-5-sonnet-20241022", "Claude 3.5 Sonnet", 
            "Latest Claude model with enhanced capabilities", false, 200000, 0.003, 0.015)
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
            
            if (config.containsKey("maxTokens")) {
                try {
                    this.maxTokens = Integer.parseInt(config.get("maxTokens"));
                } catch (NumberFormatException e) {
                    this.maxTokens = 4096;
                }
            }
            
            if (config.containsKey("systemPrompt")) {
                this.systemPrompt = config.get("systemPrompt");
            }
            
            if (apiKey == null || apiKey.trim().isEmpty()) {
                throw new AIProviderException("Claude API key is required", 
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
            throw new AIProviderException("Claude provider is not configured", 
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
        return true; // Claude supports streaming
    }
    
    @Override
    public AIResponse processStreamingCommand(String command, GalaxyContext context, StreamingCallback callback) throws AIProviderException {
        if (!configured) {
            throw new AIProviderException("Claude provider is not configured", 
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
        return CLAUDE_MODELS;
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
        return "Claude";
    }
    
    @Override
    public String getConfigurationStatus() {
        if (!configured) {
            return "Not configured - API key required";
        }
        if (apiKey == null || apiKey.trim().isEmpty()) {
            return "Missing API key";
        }
        return "Ready - Model: " + selectedModel + " (Max tokens: " + maxTokens + ")";
    }
    
    /**
     * Sends a request to the Claude API.
     */
    public String sendRequest(String prompt, String context) throws AIProviderException {
        if (!isAvailable()) {
            throw new AIProviderException("Claude provider is not configured", 
                AIProviderException.ErrorType.CONFIGURATION_ERROR);
        }
        
        try {
            URL url = new URL(endpoint);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("x-api-key", apiKey);
            connection.setRequestProperty("anthropic-version", "2023-06-01");
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
     * Sends a streaming request to the Claude API.
     */
    private String sendStreamingRequest(String prompt, String context, StreamingCallback callback) throws AIProviderException {
        if (!isAvailable()) {
            throw new AIProviderException("Claude provider is not configured", 
                AIProviderException.ErrorType.CONFIGURATION_ERROR);
        }
        
        try {
            URL url = new URL(endpoint);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("x-api-key", apiKey);
            connection.setRequestProperty("anthropic-version", "2023-06-01");
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
     * Builds the JSON request body for the Claude API.
     */
    private JSONObject buildRequestBody(String prompt, String context, boolean stream) {
        JSONObject requestBody = new JSONObject();
        
        requestBody.put("model", selectedModel);
        requestBody.put("max_tokens", maxTokens);
        requestBody.put("stream", stream);
        
        // Build system prompt
        String fullSystemPrompt = "You are an AI assistant helping with Mario Galaxy level editing.";
        if (!systemPrompt.isEmpty()) {
            fullSystemPrompt = systemPrompt;
        }
        if (!context.isEmpty()) {
            fullSystemPrompt += "\n\nUse the following context to understand the current state of the level:\n\n" + context;
        }
        requestBody.put("system", fullSystemPrompt);
        
        // Build messages array
        JSONArray messages = new JSONArray();
        
        // User message
        JSONObject userMessage = new JSONObject();
        userMessage.put("role", "user");
        userMessage.put("content", prompt);
        messages.put(userMessage);
        
        requestBody.put("messages", messages);
        
        // Add generation parameters
        requestBody.put("temperature", 0.7);
        requestBody.put("top_p", 0.9);
        
        return requestBody;
    }
    
    /**
     * Parses the JSON response from the Claude API.
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
                throw new AIProviderException("Claude API error: " + message, errorType);
            }
            
            // Extract content from response
            if (!response.has("content")) {
                throw new AIProviderException("No content in response", 
                    AIProviderException.ErrorType.INVALID_RESPONSE);
            }
            
            JSONArray content = response.getJSONArray("content");
            if (content.length() == 0) {
                throw new AIProviderException("Empty content array", 
                    AIProviderException.ErrorType.INVALID_RESPONSE);
            }
            
            // Find text content
            StringBuilder result = new StringBuilder();
            for (int i = 0; i < content.length(); i++) {
                JSONObject contentItem = content.getJSONObject(i);
                if ("text".equals(contentItem.optString("type"))) {
                    result.append(contentItem.getString("text"));
                }
            }
            
            // Update token usage
            if (response.has("usage")) {
                JSONObject usage = response.getJSONObject("usage");
                totalInputTokens += usage.optLong("input_tokens", 0);
                totalOutputTokens += usage.optLong("output_tokens", 0);
            }
            
            return result.toString();
                
        } catch (Exception e) {
            throw new AIProviderException("Failed to parse response: " + e.getMessage(), 
                AIProviderException.ErrorType.INVALID_RESPONSE);
        }
    }    

    /**
     * Reads a streaming response from the Claude API.
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
                        String type = chunk.optString("type");
                        
                        if ("content_block_delta".equals(type)) {
                            JSONObject delta = chunk.getJSONObject("delta");
                            if (delta.has("text")) {
                                String text = delta.getString("text");
                                fullResponse.append(text);
                                callback.onChunk(text);
                            }
                        } else if ("message_stop".equals(type)) {
                            callback.onComplete();
                            break;
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
     * Handles error responses from the Claude API.
     */
    private void handleErrorResponse(int responseCode, String responseBody) throws AIProviderException {
        try {
            JSONObject errorResponse = new JSONObject(responseBody);
            if (errorResponse.has("error")) {
                JSONObject error = errorResponse.getJSONObject("error");
                String message = error.optString("message", "Unknown error");
                String type = error.optString("type", "unknown");
                
                AIProviderException.ErrorType errorType = mapErrorType(type);
                throw new AIProviderException("Claude API error (HTTP " + responseCode + "): " + message, errorType);
            }
        } catch (Exception e) {
            // If we can't parse the error, use the response code
            AIProviderException.ErrorType errorType = mapHttpErrorCode(responseCode);
            throw new AIProviderException("Claude API error (HTTP " + responseCode + "): " + responseBody, errorType);
        }
    }
    
    /**
     * Maps Claude error types to our error types.
     */
    private AIProviderException.ErrorType mapErrorType(String claudeErrorType) {
        switch (claudeErrorType.toLowerCase()) {
            case "authentication_error":
            case "permission_error":
                return AIProviderException.ErrorType.AUTHENTICATION_ERROR;
            case "rate_limit_error":
                return AIProviderException.ErrorType.RATE_LIMIT_ERROR;
            case "invalid_request_error":
                return AIProviderException.ErrorType.CONFIGURATION_ERROR;
            case "api_error":
            case "overloaded_error":
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
            case 403:
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
     * Gets the maximum number of tokens for responses.
     */
    public int getMaxTokens() {
        return maxTokens;
    }
    
    /**
     * Sets the maximum number of tokens for responses.
     */
    public void setMaxTokens(int maxTokens) {
        this.maxTokens = Math.max(1, Math.min(maxTokens, 200000)); // Claude's max context
    }
    
    /**
     * Checks if this provider supports system prompts.
     */
    public boolean supportsSystemPrompts() {
        return true;
    }
    
    /**
     * Gets the current system prompt.
     */
    public String getSystemPrompt() {
        return systemPrompt;
    }
    
    /**
     * Sets the system prompt for conversations.
     */
    public void setSystemPrompt(String systemPrompt) {
        this.systemPrompt = systemPrompt != null ? systemPrompt : "";
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
     * Gets all available Claude models.
     */
    public ModelInfo[] getClaudeModels() {
        return CLAUDE_MODELS;
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
    
    /**
     * Gets total input tokens used.
     */
    public long getTotalInputTokens() {
        return totalInputTokens;
    }
    
    /**
     * Gets total output tokens used.
     */
    public long getTotalOutputTokens() {
        return totalOutputTokens;
    }
    
    /**
     * Gets total tokens used (input + output).
     */
    public long getTotalTokens() {
        return totalInputTokens + totalOutputTokens;
    }
    
    /**
     * Resets token usage counters.
     */
    public void resetTokenUsage() {
        totalInputTokens = 0;
        totalOutputTokens = 0;
    }
    
    /**
     * Gets estimated cost based on token usage.
     */
    public double getEstimatedCost() throws AIProviderException {
        ModelInfo modelInfo = getModelInfo(selectedModel);
        if (modelInfo == null) {
            return 0.0;
        }
        
        double inputCost = (totalInputTokens / 1000.0) * modelInfo.getInputCostPer1kTokens();
        double outputCost = (totalOutputTokens / 1000.0) * modelInfo.getOutputCostPer1kTokens();
        
        return inputCost + outputCost;
    }
    
    /**
     * Gets usage statistics as a formatted string.
     */
    public String getUsageStatistics() {
        try {
            double cost = getEstimatedCost();
            return String.format("Tokens: %d input, %d output (Total: %d) | Estimated cost: $%.4f", 
                totalInputTokens, totalOutputTokens, getTotalTokens(), cost);
        } catch (AIProviderException e) {
            return String.format("Tokens: %d input, %d output (Total: %d)", 
                totalInputTokens, totalOutputTokens, getTotalTokens());
        }
    }
}
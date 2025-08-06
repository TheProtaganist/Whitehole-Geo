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

import whitehole.ai.AIProvider;
import whitehole.ai.AIResponse;
import whitehole.ai.AIProviderException;
import whitehole.ai.GalaxyContext;

import org.json.JSONObject;
import org.json.JSONArray;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Ollama AI provider implementation.
 * Supports local Ollama server with various models like Llama 2, Code Llama, etc.
 */
public class OllamaProvider implements AIProvider {
    
    private String serverUrl = "http://localhost:11434";
    private String model = "llama2";
    private boolean configured = false;
    private int timeoutMs = 480000; // 8 minutes
    
    @Override
    public void configure(Map<String, String> config) throws AIProviderException {
        try {
            if (config.containsKey("serverUrl")) {
                this.serverUrl = (String) config.get("serverUrl");
            }
            
            if (config.containsKey("model")) {
                this.model = (String) config.get("model");
            }
            
            if (config.containsKey("timeout")) {
                this.timeoutMs = Integer.parseInt(config.get("timeout"));
            }
            
            // Validate configuration
            if (serverUrl == null || serverUrl.trim().isEmpty()) {
                throw new AIProviderException("Ollama server URL is required", 
                    AIProviderException.ErrorType.CONFIGURATION_ERROR);
            }
            
            if (model == null || model.trim().isEmpty()) {
                throw new AIProviderException("Ollama model is required", 
                    AIProviderException.ErrorType.CONFIGURATION_ERROR);
            }
            
            // Ensure server URL doesn't end with slash
            if (serverUrl.endsWith("/")) {
                serverUrl = serverUrl.substring(0, serverUrl.length() - 1);
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
            throw new AIProviderException("Ollama provider is not configured", 
                AIProviderException.ErrorType.CONFIGURATION_ERROR);
        }
        
        try {
            // Send the command directly to Ollama and return the raw response
            String rawResponse = sendRequest(command, "");
            
            return new AIResponse.Builder()
                .setSuccess(true)
                .setFeedback(rawResponse)
                .build();
                
        } catch (Exception e) {
            return AIResponse.failure("Failed to process command: " + e.getMessage());
        }
    }
    
    /**
     * Sends a raw request to the Ollama API.
     */
    public String sendRequest(String prompt, String context) throws AIProviderException {
        if (!isAvailable()) {
            throw new AIProviderException("Ollama provider is not configured", 
                AIProviderException.ErrorType.CONFIGURATION_ERROR);
        }
        
        try {
            // Build the request URL
            String requestUrl = serverUrl + "/api/generate";
            URL url = new URL(requestUrl);
            
            // Create HTTP connection
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setDoOutput(true);
            connection.setConnectTimeout(timeoutMs);
            connection.setReadTimeout(timeoutMs);
            
            // Build request body
            JSONObject requestBody = buildRequestBody(prompt, context);
            
            // Send request
            try (OutputStream os = connection.getOutputStream()) {
                byte[] input = requestBody.toString().getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
            }
            
            // Read response
            int responseCode = connection.getResponseCode();
            String responseBody;
            
            if (responseCode == HttpURLConnection.HTTP_OK) {
                responseBody = readStreamingResponse(connection.getInputStream());
            } else {
                responseBody = readResponse(connection.getErrorStream());
                throw new AIProviderException("Ollama API error (HTTP " + responseCode + "): " + responseBody, 
                    AIProviderException.ErrorType.NETWORK_ERROR);
            }
            
            // Parse response and return content
            return parseRawResponse(responseBody);
            
        } catch (IOException e) {
            throw new AIProviderException("Network error: " + e.getMessage(), 
                AIProviderException.ErrorType.NETWORK_ERROR);
        } catch (Exception e) {
            throw new AIProviderException("Unexpected error: " + e.getMessage(), 
                AIProviderException.ErrorType.UNKNOWN_ERROR);
        }
    }
    
    @Override
    public boolean isAvailable() {
        return configured && serverUrl != null && !serverUrl.trim().isEmpty() && 
               model != null && !model.trim().isEmpty();
    }
    
    @Override
    public String getProviderName() {
        return "Ollama";
    }
    
    @Override
    public String getConfigurationStatus() {
        if (!configured) {
            return "Not configured - server URL and model required";
        }
        if (serverUrl == null || serverUrl.trim().isEmpty()) {
            return "Missing server URL";
        }
        if (model == null || model.trim().isEmpty()) {
            return "Missing model name";
        }
        return "Ready";
    }
    
    public boolean testConnection() {
        if (!isAvailable()) {
            return false;
        }
        
        try {
            // Test with a simple ping to the server
            String requestUrl = serverUrl + "/api/tags";
            URL url = new URL(requestUrl);
            
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(5000); // 5 seconds for test
            connection.setReadTimeout(5000);
            
            int responseCode = connection.getResponseCode();
            return responseCode == HttpURLConnection.HTTP_OK;
            
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * Builds the JSON request body for the Ollama API.
     */
    private JSONObject buildRequestBody(String prompt, String context) {
        JSONObject requestBody = new JSONObject();
        
        // Build the full prompt with context
        String fullPrompt = context.isEmpty() ? prompt : context + "\n\n" + prompt;
        
        requestBody.put("model", model);
        requestBody.put("prompt", fullPrompt);
        requestBody.put("stream", false); // We want a single response, not streaming
        
        // Add options for better responses
        JSONObject options = new JSONObject();
        options.put("temperature", 0.7);
        options.put("top_p", 0.9);
        options.put("top_k", 40);
        options.put("num_predict", 2048); // Max tokens to generate
        
        requestBody.put("options", options);
        
        return requestBody;
    }
    
    /**
     * Parses the JSON response from the Ollama API and returns raw text.
     */
    private String parseRawResponse(String responseBody) throws AIProviderException {
        try {
            JSONObject response = new JSONObject(responseBody);
            
            // Check for error
            if (response.has("error")) {
                String error = response.getString("error");
                throw new AIProviderException("Ollama API error: " + error, 
                    AIProviderException.ErrorType.NETWORK_ERROR);
            }
            
            // Extract response text
            if (!response.has("response")) {
                throw new AIProviderException("No response field in Ollama response", 
                    AIProviderException.ErrorType.INVALID_RESPONSE);
            }
            
            String text = response.getString("response");
            
            // Check if the response was completed
            boolean done = response.optBoolean("done", false);
            if (!done) {
                throw new AIProviderException("Incomplete response from Ollama", 
                    AIProviderException.ErrorType.INVALID_RESPONSE);
            }
            
            return text;
                
        } catch (Exception e) {
            throw new AIProviderException("Failed to parse response: " + e.getMessage(), 
                AIProviderException.ErrorType.INVALID_RESPONSE);
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
            whitehole.ai.ObjectDatabaseContext objContext = whitehole.ai.ObjectDatabaseContext.getInstance();
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
     * Reads streaming response from Ollama (handles JSONL format).
     */
    private String readStreamingResponse(InputStream inputStream) throws IOException {
        if (inputStream == null) {
            return "";
        }
        
        StringBuilder fullResponse = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.trim().isEmpty()) {
                    try {
                        JSONObject chunk = new JSONObject(line);
                        if (chunk.has("response")) {
                            fullResponse.append(chunk.getString("response"));
                        }
                        
                        // If this is the final chunk, return the complete response
                        if (chunk.optBoolean("done", false)) {
                            // Return the final complete response object
                            return line;
                        }
                    } catch (Exception e) {
                        // If we can't parse a chunk, continue reading
                        continue;
                    }
                }
            }
        }
        
        // If we didn't get a final "done" chunk, create one
        JSONObject finalResponse = new JSONObject();
        finalResponse.put("response", fullResponse.toString());
        finalResponse.put("done", true);
        return finalResponse.toString();
    }
    
    /**
     * Gets the current model being used.
     */
    public String getModel() {
        return model;
    }
    
    /**
     * Sets the model to use.
     */
    public void setModel(String model) {
        this.model = model;
    }
    
    /**
     * Gets the server URL.
     */
    public String getServerUrl() {
        return serverUrl;
    }
    
    /**
     * Sets the server URL.
     */
    public void setServerUrl(String serverUrl) {
        this.serverUrl = serverUrl;
        if (this.serverUrl.endsWith("/")) {
            this.serverUrl = this.serverUrl.substring(0, this.serverUrl.length() - 1);
        }
    }
    
    /**
     * Gets available models from the Ollama server.
     */
    public String[] getAvailableModels() throws AIProviderException {
        if (!isAvailable()) {
            throw new AIProviderException("Provider not configured", 
                AIProviderException.ErrorType.CONFIGURATION_ERROR);
        }
        
        try {
            String requestUrl = serverUrl + "/api/tags";
            URL url = new URL(requestUrl);
            
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(timeoutMs);
            connection.setReadTimeout(timeoutMs);
            
            int responseCode = connection.getResponseCode();
            if (responseCode != HttpURLConnection.HTTP_OK) {
                String errorBody = readResponse(connection.getErrorStream());
                throw new AIProviderException("Failed to get models (HTTP " + responseCode + "): " + errorBody, 
                    AIProviderException.ErrorType.NETWORK_ERROR);
            }
            
            String responseBody = readResponse(connection.getInputStream());
            JSONObject response = new JSONObject(responseBody);
            
            if (!response.has("models")) {
                return new String[0];
            }
            
            JSONArray models = response.getJSONArray("models");
            java.util.List<String> modelNames = new java.util.ArrayList<>();
            
            for (int i = 0; i < models.length(); i++) {
                JSONObject modelObj = models.getJSONObject(i);
                String name = modelObj.getString("name");
                
                // Add model info if available
                String displayName = name;
                if (modelObj.has("details")) {
                    JSONObject details = modelObj.getJSONObject("details");
                    if (details.has("parameter_size")) {
                        displayName += " (" + details.getString("parameter_size") + ")";
                    }
                }
                
                modelNames.add(name); // Use actual name for API calls
            }
            
            return modelNames.toArray(new String[0]);
            
        } catch (Exception e) {
            throw new AIProviderException("Failed to get available models: " + e.getMessage(), 
                AIProviderException.ErrorType.NETWORK_ERROR);
        }
    }
    
    /**
     * Gets detailed information about available models.
     */
    public ModelInfo[] getAvailableModelsWithInfo() throws AIProviderException {
        if (!isAvailable()) {
            throw new AIProviderException("Provider not configured", 
                AIProviderException.ErrorType.CONFIGURATION_ERROR);
        }
        
        try {
            String requestUrl = serverUrl + "/api/tags";
            URL url = new URL(requestUrl);
            
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(timeoutMs);
            connection.setReadTimeout(timeoutMs);
            
            int responseCode = connection.getResponseCode();
            if (responseCode != HttpURLConnection.HTTP_OK) {
                String errorBody = readResponse(connection.getErrorStream());
                throw new AIProviderException("Failed to get models (HTTP " + responseCode + "): " + errorBody, 
                    AIProviderException.ErrorType.NETWORK_ERROR);
            }
            
            String responseBody = readResponse(connection.getInputStream());
            JSONObject response = new JSONObject(responseBody);
            
            if (!response.has("models")) {
                return new ModelInfo[0];
            }
            
            JSONArray models = response.getJSONArray("models");
            java.util.List<ModelInfo> modelInfos = new java.util.ArrayList<>();
            
            for (int i = 0; i < models.length(); i++) {
                JSONObject modelObj = models.getJSONObject(i);
                String name = modelObj.getString("name");
                String size = "Unknown";
                String modified = "";
                
                if (modelObj.has("details")) {
                    JSONObject details = modelObj.getJSONObject("details");
                    if (details.has("parameter_size")) {
                        size = details.getString("parameter_size");
                    }
                }
                
                if (modelObj.has("modified_at")) {
                    modified = modelObj.getString("modified_at");
                }
                
                modelInfos.add(new ModelInfo(name, size, modified));
            }
            
            return modelInfos.toArray(new ModelInfo[0]);
            
        } catch (Exception e) {
            throw new AIProviderException("Failed to get available models: " + e.getMessage(), 
                AIProviderException.ErrorType.NETWORK_ERROR);
        }
    }
    
    /**
     * Tests if a specific model is available on the server.
     */
    public boolean isModelAvailable(String modelName) {
        try {
            String[] availableModels = getAvailableModels();
            for (String available : availableModels) {
                if (available.equals(modelName)) {
                    return true;
                }
            }
            return false;
        } catch (AIProviderException e) {
            return false;
        }
    }
    
    /**
     * Information about an Ollama model.
     */
    public static class ModelInfo {
        private final String name;
        private final String size;
        private final String modified;
        
        public ModelInfo(String name, String size, String modified) {
            this.name = name;
            this.size = size;
            this.modified = modified;
        }
        
        public String getName() { return name; }
        public String getSize() { return size; }
        public String getModified() { return modified; }
        
        @Override
        public String toString() {
            return name + " (" + size + ")";
        }
    }
}

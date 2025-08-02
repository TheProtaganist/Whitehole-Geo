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
 * Google Gemini AI provider implementation.
 * Supports Gemini Pro and other Gemini models via the Google AI API.
 */
public class GeminiProvider implements AIProvider {
    
    private String apiKey;
    private String endpoint = "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent";
    private GeminiModel model = GeminiModel.GEMINI_1_5_FLASH_LATEST;
    private boolean configured = false;
    
    /**
     * Supported Gemini models (verified available via API).
     */
    public enum GeminiModel {
        // Core models that are confirmed working
        GEMINI_1_5_FLASH("gemini-1.5-flash"),
        GEMINI_1_5_FLASH_LATEST("gemini-1.5-flash-latest"),
        GEMINI_1_5_PRO("gemini-1.5-pro"),
        GEMINI_1_5_PRO_LATEST("gemini-1.5-pro-latest"),
        
        // Vision models
        GEMINI_PRO_VISION("gemini-pro-vision"),
        GEMINI_1_0_PRO_VISION_LATEST("gemini-1.0-pro-vision-latest"),
        
        // Latest 2.0 models
        GEMINI_2_0_FLASH("gemini-2.0-flash"),
        GEMINI_2_0_FLASH_EXP("gemini-2.0-flash-exp"),
        
        // Latest 2.5 models (newest available!)
        GEMINI_2_5_FLASH("gemini-2.5-flash"),
        GEMINI_2_5_PRO("gemini-2.5-pro"),
        
        // Experimental models
        GEMINI_EXP_1206("gemini-exp-1206"),
        GEMINI_2_0_FLASH_THINKING_EXP("gemini-2.0-flash-thinking-exp");
        
        private final String modelName;
        
        GeminiModel(String modelName) {
            this.modelName = modelName;
        }
        
        public String getModelName() {
            return modelName;
        }
        
        public static GeminiModel fromString(String modelName) {
            for (GeminiModel model : values()) {
                if (model.getModelName().equals(modelName)) {
                    return model;
                }
            }
            return GEMINI_1_5_FLASH_LATEST; // Default to latest working model
        }
    }
    
    @Override
    public void configure(Map<String, String> config) throws AIProviderException {
        try {
            this.apiKey = (String) config.get("apiKey");
            
            if (config.containsKey("endpoint")) {
                this.endpoint = (String) config.get("endpoint");
            }
            
            if (config.containsKey("model")) {
                String modelStr = (String) config.get("model");
                this.model = GeminiModel.fromString(modelStr);
                // Update endpoint for the selected model
                updateEndpointForModel();
            }
            
            if (apiKey == null || apiKey.trim().isEmpty()) {
                throw new AIProviderException("Gemini API key is required", 
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
            throw new AIProviderException("Gemini provider is not configured", 
                AIProviderException.ErrorType.CONFIGURATION_ERROR);
        }
        
        // For now, convert to simple text-based processing
        // TODO: Implement proper command parsing and object transformation
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
    
    /**
     * Sends a raw request to the Gemini API.
     */
    public String sendRequest(String prompt, String context) throws AIProviderException {
        if (!isAvailable()) {
            throw new AIProviderException("Gemini provider is not configured", 
                AIProviderException.ErrorType.CONFIGURATION_ERROR);
        }
        
        try {
            // Build the request URL with API key
            String requestUrl = endpoint + "?key=" + apiKey;
            URL url = new URL(requestUrl);
            
            // Create HTTP connection
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setDoOutput(true);
            
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
                responseBody = readResponse(connection.getInputStream());
            } else {
                responseBody = readResponse(connection.getErrorStream());
                throw new AIProviderException("Gemini API error (HTTP " + responseCode + "): " + responseBody, 
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
        return configured && apiKey != null && !apiKey.trim().isEmpty();
    }
    
    @Override
    public String getProviderName() {
        return "Google Gemini";
    }
    
    @Override
    public String getConfigurationStatus() {
        if (!configured) {
            return "Not configured - API key required";
        }
        if (apiKey == null || apiKey.trim().isEmpty()) {
            return "Missing API key";
        }
        return "Ready";
    }
    
    public boolean testConnection() {
        if (!isAvailable()) {
            return false;
        }
        
        try {
            String response = sendRequest("Test connection", "");
            return response != null && !response.isEmpty();
        } catch (AIProviderException e) {
            return false;
        }
    }
    
    /**
     * Builds the JSON request body for the Gemini API.
     */
    private JSONObject buildRequestBody(String prompt, String context) {
        JSONObject requestBody = new JSONObject();
        
        // Build the full prompt with context
        String fullPrompt = context.isEmpty() ? prompt : context + "\n\n" + prompt;
        
        // Create contents array
        JSONArray contents = new JSONArray();
        JSONObject content = new JSONObject();
        
        JSONArray parts = new JSONArray();
        JSONObject part = new JSONObject();
        part.put("text", fullPrompt);
        parts.put(part);
        
        content.put("parts", parts);
        contents.put(content);
        
        requestBody.put("contents", contents);
        
        // Add generation config
        JSONObject generationConfig = new JSONObject();
        generationConfig.put("temperature", 0.7);
        generationConfig.put("topK", 40);
        generationConfig.put("topP", 0.95);
        generationConfig.put("maxOutputTokens", 2048);
        
        requestBody.put("generationConfig", generationConfig);
        
        // Add safety settings
        JSONArray safetySettings = new JSONArray();
        String[] categories = {
            "HARM_CATEGORY_HARASSMENT",
            "HARM_CATEGORY_HATE_SPEECH", 
            "HARM_CATEGORY_SEXUALLY_EXPLICIT",
            "HARM_CATEGORY_DANGEROUS_CONTENT"
        };
        
        for (String category : categories) {
            JSONObject safetySetting = new JSONObject();
            safetySetting.put("category", category);
            safetySetting.put("threshold", "BLOCK_MEDIUM_AND_ABOVE");
            safetySettings.put(safetySetting);
        }
        
        requestBody.put("safetySettings", safetySettings);
        
        return requestBody;
    }
    
    /**
     * Parses the JSON response from the Gemini API and returns raw text.
     */
    private String parseRawResponse(String responseBody) throws AIProviderException {
        try {
            JSONObject response = new JSONObject(responseBody);
            
            // Check for error
            if (response.has("error")) {
                JSONObject error = response.getJSONObject("error");
                String message = error.optString("message", "Unknown error");
                throw new AIProviderException("Gemini API error: " + message, 
                    AIProviderException.ErrorType.NETWORK_ERROR);
            }
            
            // Extract content from candidates
            if (!response.has("candidates")) {
                throw new AIProviderException("No candidates in response", 
                    AIProviderException.ErrorType.INVALID_RESPONSE);
            }
            
            JSONArray candidates = response.getJSONArray("candidates");
            if (candidates.length() == 0) {
                throw new AIProviderException("Empty candidates array", 
                    AIProviderException.ErrorType.INVALID_RESPONSE);
            }
            
            JSONObject candidate = candidates.getJSONObject(0);
            
            // Check finish reason
            String finishReason = candidate.optString("finishReason", "");
            if ("SAFETY".equals(finishReason)) {
                throw new AIProviderException("Response blocked by safety filters", 
                    AIProviderException.ErrorType.INVALID_RESPONSE);
            }
            
            // Extract content
            if (!candidate.has("content")) {
                throw new AIProviderException("No content in candidate", 
                    AIProviderException.ErrorType.INVALID_RESPONSE);
            }
            
            JSONObject content = candidate.getJSONObject("content");
            JSONArray parts = content.getJSONArray("parts");
            
            if (parts.length() == 0) {
                throw new AIProviderException("No parts in content", 
                    AIProviderException.ErrorType.INVALID_RESPONSE);
            }
            
            String text = parts.getJSONObject(0).getString("text");
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
     * Gets the current model being used.
     */
    public GeminiModel getModel() {
        return model;
    }
    
    /**
     * Sets the model to use.
     */
    public void setModel(GeminiModel model) {
        this.model = model;
        updateEndpointForModel();
    }
    
    /**
     * Sets the model to use by name.
     */
    public void setModel(String modelName) {
        this.model = GeminiModel.fromString(modelName);
        updateEndpointForModel();
    }
    
    /**
     * Updates the endpoint URL based on the current model.
     */
    private void updateEndpointForModel() {
        this.endpoint = "https://generativelanguage.googleapis.com/v1beta/models/" + 
                       model.getModelName() + ":generateContent";
    }
    
    /**
     * Gets available Gemini models from the API.
     */
    public String[] getAvailableModels() throws AIProviderException {
        if (!isAvailable()) {
            throw new AIProviderException("Provider not configured", 
                AIProviderException.ErrorType.CONFIGURATION_ERROR);
        }
        
        try {
            String requestUrl = "https://generativelanguage.googleapis.com/v1beta/models?key=" + apiKey;
            URL url = new URL(requestUrl);
            
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(10000); // 10 seconds
            connection.setReadTimeout(10000);
            
            int responseCode = connection.getResponseCode();
            if (responseCode != HttpURLConnection.HTTP_OK) {
                String errorBody = readResponse(connection.getErrorStream());
                throw new AIProviderException("Failed to get models (HTTP " + responseCode + "): " + errorBody, 
                    AIProviderException.ErrorType.NETWORK_ERROR);
            }
            
            String responseBody = readResponse(connection.getInputStream());
            JSONObject response = new JSONObject(responseBody);
            
            if (!response.has("models")) {
                return getStaticModelList(); // Fallback to static list
            }
            
            JSONArray models = response.getJSONArray("models");
            java.util.List<String> modelNames = new java.util.ArrayList<>();
            
            for (int i = 0; i < models.length(); i++) {
                JSONObject modelObj = models.getJSONObject(i);
                String name = modelObj.getString("name");
                
                // Extract model name from full path (e.g., "models/gemini-pro" -> "gemini-pro")
                if (name.startsWith("models/")) {
                    name = name.substring(7);
                }
                
                // Only include generative models that support generateContent
                if (modelObj.has("supportedGenerationMethods")) {
                    JSONArray methods = modelObj.getJSONArray("supportedGenerationMethods");
                    boolean supportsGenerate = false;
                    for (int j = 0; j < methods.length(); j++) {
                        if ("generateContent".equals(methods.getString(j))) {
                            supportsGenerate = true;
                            break;
                        }
                    }
                    if (supportsGenerate) {
                        modelNames.add(name);
                    }
                }
            }
            
            return modelNames.toArray(new String[0]);
            
        } catch (Exception e) {
            // Fallback to static model list if API call fails
            System.err.println("Failed to fetch Gemini models from API: " + e.getMessage());
            return getStaticModelList();
        }
    }
    
    /**
     * Gets a static list of known working Gemini models as fallback.
     */
    private String[] getStaticModelList() {
        return new String[] {
            // Core working models
            "gemini-1.5-flash",
            "gemini-1.5-flash-latest", 
            "gemini-1.5-pro",
            "gemini-1.5-pro-latest",
            
            // Vision models
            "gemini-pro-vision",
            "gemini-1.0-pro-vision-latest",
            
            // 2.0 models
            "gemini-2.0-flash",
            "gemini-2.0-flash-exp",
            
            // Latest 2.5 models
            "gemini-2.5-flash",
            "gemini-2.5-pro",
            
            // Experimental
            "gemini-exp-1206",
            "gemini-2.0-flash-thinking-exp"
        };
    }
    
    /**
     * Tests if a specific model is available.
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
}
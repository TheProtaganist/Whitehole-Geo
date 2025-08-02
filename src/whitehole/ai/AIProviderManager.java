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

import whitehole.Settings;
import whitehole.ai.providers.GeminiProvider;
import whitehole.ai.providers.OllamaProvider;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages AI providers and handles switching between them based on configuration.
 */
public class AIProviderManager {
    private static AIProviderManager instance;
    private final Map<Settings.AIProviderType, AIProvider> providers;
    private AIProvider currentProvider;
    
    private AIProviderManager() {
        this.providers = new ConcurrentHashMap<>();
        
        // Clear any invalid settings that might cause issues
        Settings.clearInvalidAISettings();
        
        initializeProviders();
        updateCurrentProvider();
    }
    
    /**
     * Gets the singleton instance of AIProviderManager.
     */
    public static synchronized AIProviderManager getInstance() {
        if (instance == null) {
            instance = new AIProviderManager();
        }
        return instance;
    }
    
    /**
     * Initializes all available AI providers.
     */
    private void initializeProviders() {
        providers.put(Settings.AIProviderType.GEMINI, new GeminiProvider());
        providers.put(Settings.AIProviderType.OLLAMA, new OllamaProvider());
        
        // Configure each provider with current settings
        configureAllProviders();
    }
    
    /**
     * Configures all providers with current settings.
     */
    private void configureAllProviders() {
        // Configure Gemini provider
        AIProvider geminiProvider = providers.get(Settings.AIProviderType.GEMINI);
        if (geminiProvider != null) {
            Map<String, String> geminiConfig = new HashMap<>();
            geminiConfig.put("apiKey", Settings.getGeminiApiKey());
            
            // Ensure endpoint is valid and reset if it contains test-endpoint.com
            String endpoint = Settings.getGeminiEndpoint();
            if (endpoint.contains("test-endpoint.com")) {
                endpoint = "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent";
                Settings.setGeminiEndpoint(endpoint);
            }
            geminiConfig.put("endpoint", endpoint);
            
            try {
                geminiProvider.configure(geminiConfig);
            } catch (AIProviderException e) {
                System.err.println("Failed to configure Gemini provider: " + e.getMessage());
            }
        }
        
        // Configure Ollama provider
        AIProvider ollamaProvider = providers.get(Settings.AIProviderType.OLLAMA);
        if (ollamaProvider != null) {
            Map<String, String> ollamaConfig = new HashMap<>();
            ollamaConfig.put("serverUrl", Settings.getOllamaServerUrl());
            ollamaConfig.put("model", Settings.getOllamaModel());
            
            try {
                ollamaProvider.configure(ollamaConfig);
            } catch (AIProviderException e) {
                System.err.println("Failed to configure Ollama provider: " + e.getMessage());
            }
        }
    }
    
    /**
     * Updates the current provider based on settings.
     */
    private void updateCurrentProvider() {
        Settings.AIProviderType activeType = Settings.getActiveAIProvider();
        currentProvider = providers.get(activeType);
    }
    
    /**
     * Gets the currently active AI provider.
     */
    public AIProvider getCurrentProvider() {
        if (currentProvider == null) {
            updateCurrentProvider();
        }
        return currentProvider;
    }
    
    /**
     * Gets a specific AI provider by type.
     */
    public AIProvider getProvider(Settings.AIProviderType type) {
        return providers.get(type);
    }
    
    /**
     * Switches to a different AI provider.
     */
    public void switchProvider(Settings.AIProviderType type) {
        Settings.setActiveAIProvider(type);
        updateCurrentProvider();
    }
    
    /**
     * Processes a command using the current AI provider.
     */
    public AIResponse processCommand(String command, GalaxyContext context) throws AIProviderException {
        AIProvider provider = getCurrentProvider();
        if (provider == null) {
            throw new AIProviderException("No AI provider available", AIProviderException.ErrorType.CONFIGURATION_ERROR);
        }
        
        if (!provider.isAvailable()) {
            throw new AIProviderException("Current AI provider is not available: " + provider.getConfigurationStatus(), 
                                        AIProviderException.ErrorType.SERVICE_UNAVAILABLE);
        }
        
        return provider.processCommand(command, context);
    }
    
    /**
     * Checks if any AI provider is available and configured.
     */
    public boolean isAnyProviderAvailable() {
        return providers.values().stream().anyMatch(AIProvider::isAvailable);
    }
    
    /**
     * Checks if the current provider is available.
     */
    public boolean isCurrentProviderAvailable() {
        AIProvider provider = getCurrentProvider();
        return provider != null && provider.isAvailable();
    }
    
    /**
     * Gets the configuration status of the current provider.
     */
    public String getCurrentProviderStatus() {
        AIProvider provider = getCurrentProvider();
        if (provider == null) {
            return "No provider selected";
        }
        return provider.getConfigurationStatus();
    }
    
    /**
     * Gets the name of the current provider.
     */
    public String getCurrentProviderName() {
        AIProvider provider = getCurrentProvider();
        if (provider == null) {
            return "None";
        }
        return provider.getProviderName();
    }
    
    /**
     * Refreshes provider configurations from settings.
     * Call this after settings have been updated.
     */
    public void refreshConfiguration() {
        configureAllProviders();
        updateCurrentProvider();
    }
    
    /**
     * Gets configuration status for all providers.
     */
    public Map<Settings.AIProviderType, String> getAllProviderStatuses() {
        Map<Settings.AIProviderType, String> statuses = new HashMap<>();
        for (Map.Entry<Settings.AIProviderType, AIProvider> entry : providers.entrySet()) {
            AIProvider provider = entry.getValue();
            statuses.put(entry.getKey(), provider.getConfigurationStatus());
        }
        return statuses;
    }
    
    /**
     * Tests connectivity for a specific provider.
     */
    public boolean testProvider(Settings.AIProviderType type) {
        AIProvider provider = providers.get(type);
        return provider != null && provider.isAvailable();
    }
    
    /**
     * Tests connectivity for the current provider.
     */
    public boolean testCurrentProvider() {
        return isCurrentProviderAvailable();
    }
    
    /**
     * Resets Gemini configuration to default values.
     * Call this if there are configuration issues.
     */
    public void resetGeminiConfiguration() {
        // Clear any invalid endpoint
        String currentEndpoint = Settings.getGeminiEndpoint();
        if (currentEndpoint.contains("test-endpoint.com") || currentEndpoint.isEmpty()) {
            Settings.setGeminiEndpoint("https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent");
        }
        
        // Force clear the API key completely
        Settings.forceClearGeminiApiKey();
        
        // Refresh configuration
        refreshConfiguration();
    }
}
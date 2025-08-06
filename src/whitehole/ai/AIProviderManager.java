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
import whitehole.ai.providers.OpenRouterProvider;
import whitehole.ai.providers.OpenAIProvider;
import whitehole.ai.providers.ClaudeProvider;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages AI providers and handles switching between them based on configuration.
 * Supports provider fallback mechanism and health checking.
 */
public class AIProviderManager {
    private static AIProviderManager instance;
    private final Map<Settings.AIProviderType, AIProvider> providers;
    private final Map<Settings.AIProviderType, EnhancedAIProvider> enhancedProviders;
    private AIProvider currentProvider;
    private List<Settings.AIProviderType> fallbackOrder;
    private boolean enableFallback = true;
    
    private AIProviderManager() {
        this.providers = new ConcurrentHashMap<>();
        this.enhancedProviders = new ConcurrentHashMap<>();
        this.fallbackOrder = new ArrayList<>();
        
        // Clear any invalid settings that might cause issues
        Settings.clearInvalidAISettings();
        
        initializeProviders();
        setupFallbackOrder();
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
        // Initialize basic providers
        providers.put(Settings.AIProviderType.GEMINI, new GeminiProvider());
        providers.put(Settings.AIProviderType.OLLAMA, new OllamaProvider());
        
        // Initialize enhanced providers
        OpenRouterProvider openRouterProvider = new OpenRouterProvider();
        providers.put(Settings.AIProviderType.OPENROUTER, openRouterProvider);
        enhancedProviders.put(Settings.AIProviderType.OPENROUTER, openRouterProvider);
        
        OpenAIProvider openAIProvider = new OpenAIProvider();
        providers.put(Settings.AIProviderType.OPENAI, openAIProvider);
        enhancedProviders.put(Settings.AIProviderType.OPENAI, openAIProvider);
        
        ClaudeProvider claudeProvider = new ClaudeProvider();
        providers.put(Settings.AIProviderType.CLAUDE, claudeProvider);
        enhancedProviders.put(Settings.AIProviderType.CLAUDE, claudeProvider);
        
        // Configure each provider with current settings
        configureAllProviders();
    }
    
    /**
     * Configures all providers with current settings.
     */
    private void configureAllProviders() {
        // Configure Gemini provider
        configureProvider(Settings.AIProviderType.GEMINI, () -> {
            Map<String, String> config = new HashMap<>();
            config.put("apiKey", Settings.getGeminiApiKey());
            
            // Ensure endpoint is valid and reset if it contains test-endpoint.com
            String endpoint = Settings.getGeminiEndpoint();
            if (endpoint.contains("test-endpoint.com")) {
                endpoint = "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent";
                Settings.setGeminiEndpoint(endpoint);
            }
            config.put("endpoint", endpoint);
            return config;
        });
        
        // Configure Ollama provider
        configureProvider(Settings.AIProviderType.OLLAMA, () -> {
            Map<String, String> config = new HashMap<>();
            config.put("serverUrl", Settings.getOllamaServerUrl());
            config.put("model", Settings.getSelectedOllamaModel());
            return config;
        });
        
        // Configure OpenRouter provider
        configureProvider(Settings.AIProviderType.OPENROUTER, () -> {
            Map<String, String> config = new HashMap<>();
            config.put("apiKey", Settings.getOpenRouterApiKey());
            config.put("endpoint", Settings.getOpenRouterEndpoint());
            config.put("model", Settings.getSelectedOpenRouterModel());
            return config;
        });
        
        // Configure OpenAI provider
        configureProvider(Settings.AIProviderType.OPENAI, () -> {
            Map<String, String> config = new HashMap<>();
            config.put("apiKey", Settings.getOpenAIApiKey());
            config.put("endpoint", Settings.getOpenAIEndpoint());
            config.put("model", Settings.getSelectedOpenAIModel());
            return config;
        });
        
        // Configure Claude provider
        configureProvider(Settings.AIProviderType.CLAUDE, () -> {
            Map<String, String> config = new HashMap<>();
            config.put("apiKey", Settings.getClaudeApiKey());
            config.put("endpoint", Settings.getClaudeEndpoint());
            config.put("model", Settings.getSelectedClaudeModel());
            config.put("maxTokens", String.valueOf(Settings.getClaudeMaxTokens()));
            config.put("systemPrompt", Settings.getClaudeSystemPrompt());
            return config;
        });
    }
    
    /**
     * Helper method to configure a provider with error handling.
     */
    private void configureProvider(Settings.AIProviderType type, ConfigSupplier configSupplier) {
        AIProvider provider = providers.get(type);
        if (provider != null) {
            try {
                Map<String, String> config = configSupplier.get();
                provider.configure(config);
            } catch (AIProviderException e) {
                System.err.println("Failed to configure " + type + " provider: " + e.getMessage());
            } catch (Exception e) {
                System.err.println("Unexpected error configuring " + type + " provider: " + e.getMessage());
            }
        }
    }
    
    /**
     * Functional interface for configuration suppliers.
     */
    @FunctionalInterface
    private interface ConfigSupplier {
        Map<String, String> get() throws Exception;
    }
    
    /**
     * Sets up the fallback order for providers.
     */
    private void setupFallbackOrder() {
        fallbackOrder.clear();
        fallbackOrder.add(Settings.AIProviderType.OPENROUTER);
        fallbackOrder.add(Settings.AIProviderType.OPENAI);
        fallbackOrder.add(Settings.AIProviderType.CLAUDE);
        fallbackOrder.add(Settings.AIProviderType.GEMINI);
        fallbackOrder.add(Settings.AIProviderType.OLLAMA);
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
     * Processes a command using the current AI provider with fallback support.
     */
    public AIResponse processCommand(String command, GalaxyContext context) throws AIProviderException {
        AIProvider provider = getCurrentProvider();
        if (provider == null) {
            throw new AIProviderException("No AI provider available", AIProviderException.ErrorType.CONFIGURATION_ERROR);
        }
        
        // Try current provider first
        if (provider.isAvailable()) {
            try {
                return provider.processCommand(command, context);
            } catch (AIProviderException e) {
                if (!enableFallback) {
                    throw e;
                }
                System.err.println("Primary provider failed: " + e.getMessage() + ". Trying fallback providers...");
            }
        }
        
        // Try fallback providers if enabled
        if (enableFallback) {
            for (Settings.AIProviderType fallbackType : fallbackOrder) {
                if (fallbackType == Settings.getActiveAIProvider()) {
                    continue; // Skip the current provider we already tried
                }
                
                AIProvider fallbackProvider = providers.get(fallbackType);
                if (fallbackProvider != null && fallbackProvider.isAvailable()) {
                    try {
                        System.out.println("Using fallback provider: " + fallbackProvider.getProviderName());
                        return fallbackProvider.processCommand(command, context);
                    } catch (AIProviderException e) {
                        System.err.println("Fallback provider " + fallbackProvider.getProviderName() + " failed: " + e.getMessage());
                        continue;
                    }
                }
            }
        }
        
        throw new AIProviderException("All AI providers are unavailable or failed", 
                                    AIProviderException.ErrorType.SERVICE_UNAVAILABLE);
    }
    
    /**
     * Processes a streaming command using enhanced providers.
     */
    public AIResponse processStreamingCommand(String command, GalaxyContext context, 
                                            EnhancedAIProvider.StreamingCallback callback) throws AIProviderException {
        Settings.AIProviderType currentType = Settings.getActiveAIProvider();
        EnhancedAIProvider enhancedProvider = enhancedProviders.get(currentType);
        
        if (enhancedProvider != null && enhancedProvider.isAvailable() && enhancedProvider.supportsStreaming()) {
            try {
                return enhancedProvider.processStreamingCommand(command, context, callback);
            } catch (AIProviderException e) {
                if (!enableFallback) {
                    throw e;
                }
                System.err.println("Primary streaming provider failed: " + e.getMessage() + ". Trying fallback...");
            }
        }
        
        // Try fallback enhanced providers if enabled
        if (enableFallback) {
            for (Settings.AIProviderType fallbackType : fallbackOrder) {
                if (fallbackType == currentType) {
                    continue; // Skip the current provider we already tried
                }
                
                EnhancedAIProvider fallbackProvider = enhancedProviders.get(fallbackType);
                if (fallbackProvider != null && fallbackProvider.isAvailable() && fallbackProvider.supportsStreaming()) {
                    try {
                        System.out.println("Using fallback streaming provider: " + fallbackProvider.getProviderName());
                        return fallbackProvider.processStreamingCommand(command, context, callback);
                    } catch (AIProviderException e) {
                        System.err.println("Fallback streaming provider " + fallbackProvider.getProviderName() + " failed: " + e.getMessage());
                        continue;
                    }
                }
            }
        }
        
        // Fall back to regular processing if no streaming providers work
        return processCommand(command, context);
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
     * Gets an enhanced AI provider by type.
     */
    public EnhancedAIProvider getEnhancedProvider(Settings.AIProviderType type) {
        return enhancedProviders.get(type);
    }
    
    /**
     * Gets the current enhanced provider if available.
     */
    public EnhancedAIProvider getCurrentEnhancedProvider() {
        Settings.AIProviderType currentType = Settings.getActiveAIProvider();
        return enhancedProviders.get(currentType);
    }
    
    /**
     * Checks if the current provider supports streaming.
     */
    public boolean currentProviderSupportsStreaming() {
        EnhancedAIProvider enhancedProvider = getCurrentEnhancedProvider();
        return enhancedProvider != null && enhancedProvider.supportsStreaming();
    }
    
    /**
     * Gets available models for the current provider.
     */
    public EnhancedAIProvider.ModelInfo[] getCurrentProviderModels() throws AIProviderException {
        EnhancedAIProvider enhancedProvider = getCurrentEnhancedProvider();
        if (enhancedProvider != null) {
            return enhancedProvider.getAvailableModels();
        }
        return new EnhancedAIProvider.ModelInfo[0];
    }
    
    /**
     * Tests connection for a specific provider.
     */
    public boolean testProviderConnection(Settings.AIProviderType type) {
        EnhancedAIProvider enhancedProvider = enhancedProviders.get(type);
        if (enhancedProvider != null) {
            return enhancedProvider.testConnection();
        }
        
        AIProvider provider = providers.get(type);
        return provider != null && provider.isAvailable();
    }
    
    /**
     * Performs health check on all providers.
     */
    public Map<Settings.AIProviderType, Boolean> performHealthCheck() {
        Map<Settings.AIProviderType, Boolean> healthStatus = new HashMap<>();
        
        for (Settings.AIProviderType type : Settings.AIProviderType.values()) {
            boolean isHealthy = testProviderConnection(type);
            healthStatus.put(type, isHealthy);
        }
        
        return healthStatus;
    }
    
    /**
     * Gets the first available provider from the fallback order.
     */
    public Settings.AIProviderType getFirstAvailableProvider() {
        for (Settings.AIProviderType type : fallbackOrder) {
            AIProvider provider = providers.get(type);
            if (provider != null && provider.isAvailable()) {
                return type;
            }
        }
        return null;
    }
    
    /**
     * Automatically switches to the first available provider.
     */
    public boolean autoSwitchToAvailableProvider() {
        Settings.AIProviderType availableType = getFirstAvailableProvider();
        if (availableType != null) {
            switchProvider(availableType);
            return true;
        }
        return false;
    }
    
    /**
     * Enables or disables provider fallback mechanism.
     */
    public void setFallbackEnabled(boolean enabled) {
        this.enableFallback = enabled;
    }
    
    /**
     * Checks if fallback is enabled.
     */
    public boolean isFallbackEnabled() {
        return enableFallback;
    }
    
    /**
     * Sets custom fallback order.
     */
    public void setFallbackOrder(List<Settings.AIProviderType> order) {
        this.fallbackOrder = new ArrayList<>(order);
    }
    
    /**
     * Gets current fallback order.
     */
    public List<Settings.AIProviderType> getFallbackOrder() {
        return new ArrayList<>(fallbackOrder);
    }
    
    /**
     * Gets detailed status for all providers.
     */
    public Map<Settings.AIProviderType, ProviderStatus> getDetailedProviderStatuses() {
        Map<Settings.AIProviderType, ProviderStatus> statuses = new HashMap<>();
        
        for (Settings.AIProviderType type : Settings.AIProviderType.values()) {
            AIProvider provider = providers.get(type);
            EnhancedAIProvider enhancedProvider = enhancedProviders.get(type);
            
            if (provider != null) {
                boolean isAvailable = provider.isAvailable();
                boolean supportsStreaming = enhancedProvider != null && enhancedProvider.supportsStreaming();
                boolean connectionOk = testProviderConnection(type);
                String configStatus = provider.getConfigurationStatus();
                
                ProviderStatus status = new ProviderStatus(
                    provider.getProviderName(),
                    isAvailable,
                    supportsStreaming,
                    connectionOk,
                    configStatus
                );
                
                statuses.put(type, status);
            }
        }
        
        return statuses;
    }
    
    /**
     * Provider status information.
     */
    public static class ProviderStatus {
        private final String name;
        private final boolean available;
        private final boolean supportsStreaming;
        private final boolean connectionOk;
        private final String configurationStatus;
        
        public ProviderStatus(String name, boolean available, boolean supportsStreaming, 
                            boolean connectionOk, String configurationStatus) {
            this.name = name;
            this.available = available;
            this.supportsStreaming = supportsStreaming;
            this.connectionOk = connectionOk;
            this.configurationStatus = configurationStatus;
        }
        
        public String getName() { return name; }
        public boolean isAvailable() { return available; }
        public boolean supportsStreaming() { return supportsStreaming; }
        public boolean isConnectionOk() { return connectionOk; }
        public String getConfigurationStatus() { return configurationStatus; }
        
        @Override
        public String toString() {
            return String.format("%s: %s (Streaming: %s, Connection: %s) - %s", 
                name, available ? "Available" : "Unavailable", 
                supportsStreaming ? "Yes" : "No",
                connectionOk ? "OK" : "Failed",
                configurationStatus);
        }
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
    
    /**
     * Resets all provider configurations to default values.
     */
    public void resetAllProviderConfigurations() {
        Settings.clearInvalidAISettings();
        refreshConfiguration();
    }
}
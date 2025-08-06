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

import whitehole.ai.providers.GeminiProvider;
import whitehole.ai.providers.OllamaProvider;
import whitehole.ai.providers.OpenRouterProvider;
import whitehole.ai.providers.OpenAIProvider;
import whitehole.ai.providers.ClaudeProvider;
import whitehole.Settings;
import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.HashMap;
import java.util.Map;

/**
 * Utility class for creating AI model selection UI components.
 * Provides dropdowns for all AI providers with dynamic model discovery.
 */
public class AIModelSelector {
    
    private final AIProviderManager providerManager;
    private JComboBox<String> geminiModelCombo;
    private JComboBox<String> ollamaModelCombo;
    private JComboBox<String> openRouterModelCombo;
    private JComboBox<String> openAIModelCombo;
    private JComboBox<String> claudeModelCombo;
    private JButton refreshGeminiButton;
    private JButton refreshOllamaButton;
    private JButton refreshOpenRouterButton;
    private JButton refreshOpenAIButton;
    private JButton refreshClaudeButton;
    private JButton testGeminiButton;
    private JButton testOllamaButton;
    private JButton testOpenRouterButton;
    private JButton testOpenAIButton;
    private JButton testClaudeButton;
    
    public AIModelSelector(AIProviderManager providerManager) {
        this.providerManager = providerManager;
    }
    
    /**
     * Creates a panel with Gemini model selection controls.
     */
    public JPanel createGeminiModelPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createTitledBorder("Gemini Models"));
        
        // API Key panel
        JPanel apiKeyPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JLabel apiKeyLabel = new JLabel("API Key:");
        apiKeyLabel.setToolTipText("<html><b>Google AI Studio API Key</b><br/>" +
                                  "Get your free API key from:<br/>" +
                                  "https://makersuite.google.com/app/apikey</html>");
        JPasswordField apiKeyField = new JPasswordField(30);
        apiKeyField.setText(Settings.getGeminiApiKey());
        apiKeyField.setToolTipText("<html><b>Gemini API Key</b><br/>" +
                                  "Enter your Google AI Studio API key here.<br/>" +
                                  "The key is stored securely in system preferences.<br/><br/>" +
                                  "<b>To get an API key:</b><br/>" +
                                  "1. Visit https://makersuite.google.com/app/apikey<br/>" +
                                  "2. Sign in with your Google account<br/>" +
                                  "3. Create a new API key<br/>" +
                                  "4. Copy and paste it here</html>");
        
        JButton saveKeyButton = new JButton("Save Key");
        saveKeyButton.setToolTipText("Save the API key and configure the Gemini provider");
        saveKeyButton.addActionListener(e -> {
            String apiKey = new String(apiKeyField.getPassword());
            Settings.setGeminiApiKey(apiKey);
            
            // Reconfigure provider with new key
            try {
                GeminiProvider provider = (GeminiProvider) providerManager.getProvider(Settings.AIProviderType.GEMINI);
                if (provider != null) {
                    Map<String, String> config = new HashMap<>();
                    config.put("apiKey", apiKey);
                    config.put("model", Settings.getSelectedGeminiModel());
                    provider.configure(config);
                    
                    JOptionPane.showMessageDialog(panel, "API key saved and provider configured!", 
                        "Success", JOptionPane.INFORMATION_MESSAGE);
                    
                    // Refresh models with new key
                    refreshGeminiModels();
                }
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(panel, "Failed to configure provider: " + ex.getMessage(), 
                    "Error", JOptionPane.ERROR_MESSAGE);
            }
        });
        
        apiKeyPanel.add(apiKeyLabel);
        apiKeyPanel.add(apiKeyField);
        apiKeyPanel.add(saveKeyButton);
        
        // Model selection panel
        JPanel modelPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        
        // Model dropdown
        geminiModelCombo = new JComboBox<>();
        geminiModelCombo.setPreferredSize(new Dimension(250, 25));
        geminiModelCombo.setToolTipText("<html><b>Gemini Model Selection</b><br/>" +
                                       "Choose which Gemini model to use for AI commands.<br/><br/>" +
                                       "<b>Recommended models:</b><br/>" +
                                       "\u2022 gemini-1.5-flash-latest: Latest fast and efficient (default)<br/>" +
                                       "\u2022 gemini-1.5-flash: Stable fast and efficient<br/>" +
                                       "\u2022 gemini-1.5-pro: More accurate but slower<br/>" +
                                       "\u2022 gemini-1.0-pro: Stable baseline model<br/><br/>" +
                                       "Click 'Refresh Models' to update the list.</html>");
        geminiModelCombo.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                String selectedModel = (String) geminiModelCombo.getSelectedItem();
                if (selectedModel != null && !selectedModel.startsWith("Error") && !selectedModel.startsWith("Provider")) {
                    updateGeminiModel(selectedModel);
                }
            }
        });
        
        // Refresh button
        refreshGeminiButton = new JButton("Refresh Models");
        refreshGeminiButton.setToolTipText("Refresh the list of available Gemini models from Google AI");
        refreshGeminiButton.addActionListener(e -> refreshGeminiModels());
        
        // Test button
        testGeminiButton = new JButton("Test Connection");
        testGeminiButton.setToolTipText("Test the connection to Gemini AI with current settings");
        testGeminiButton.addActionListener(e -> testGeminiConnection());
        
        modelPanel.add(new JLabel("Model:"));
        modelPanel.add(geminiModelCombo);
        modelPanel.add(refreshGeminiButton);
        modelPanel.add(testGeminiButton);
        
        // Status panel
        JPanel statusPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JLabel statusLabel = new JLabel("Status: " + getGeminiStatus());
        statusPanel.add(statusLabel);
        
        // Combine panels
        JPanel topPanel = new JPanel(new BorderLayout());
        topPanel.add(apiKeyPanel, BorderLayout.NORTH);
        topPanel.add(modelPanel, BorderLayout.CENTER);
        
        panel.add(topPanel, BorderLayout.CENTER);
        panel.add(statusPanel, BorderLayout.SOUTH);
        
        // Load initial models
        refreshGeminiModels();
        
        return panel;
    }
    
    /**
     * Creates a panel with Ollama model selection controls.
     */
    public JPanel createOllamaModelPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createTitledBorder("Ollama Models"));
        
        // Top panel with controls
        JPanel controlPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        
        // Model dropdown with better sizing
        ollamaModelCombo = new JComboBox<>();
        ollamaModelCombo.setPreferredSize(new Dimension(300, 25));
        ollamaModelCombo.setToolTipText("<html><b>Ollama Model Selection</b><br/>" +
                                       "Choose which locally installed Ollama model to use.<br/><br/>" +
                                       "<b>Popular models:</b><br/>" +
                                       "\u2022 llama2: General purpose, good balance<br/>" +
                                       "\u2022 codellama: Optimized for code understanding<br/>" +
                                       "\u2022 mistral: Fast and efficient<br/>" +
                                       "\u2022 phi: Lightweight, good for simple tasks<br/><br/>" +
                                       "Install models with: ollama pull [model-name]</html>");
        ollamaModelCombo.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                    boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (value != null) {
                    String modelName = value.toString();
                    // Show model name with size info if available
                    setText(modelName);
                    setToolTipText("Ollama model: " + modelName);
                }
                return this;
            }
        });
        
        ollamaModelCombo.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                String selectedModel = (String) ollamaModelCombo.getSelectedItem();
                if (selectedModel != null && !selectedModel.startsWith("Error") && !selectedModel.startsWith("No models")) {
                    updateOllamaModel(selectedModel);
                }
            }
        });
        
        // Refresh button
        refreshOllamaButton = new JButton("Refresh Models");
        refreshOllamaButton.setToolTipText("Refresh the list of locally installed Ollama models");
        refreshOllamaButton.addActionListener(e -> refreshOllamaModels());
        
        // Test button
        testOllamaButton = new JButton("Test Connection");
        testOllamaButton.setToolTipText("Test the connection to your local Ollama server");
        testOllamaButton.addActionListener(e -> testOllamaConnection());
        
        controlPanel.add(new JLabel("Model:"));
        controlPanel.add(ollamaModelCombo);
        controlPanel.add(refreshOllamaButton);
        controlPanel.add(testOllamaButton);
        
        panel.add(controlPanel, BorderLayout.NORTH);
        
        // Server URL panel
        JPanel serverPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JLabel serverLabel = new JLabel("Server URL:");
        serverLabel.setToolTipText("The URL where your Ollama server is running");
        JTextField serverField = new JTextField(Settings.getOllamaServerUrl(), 20);
        serverField.setToolTipText("<html><b>Ollama Server URL</b><br/>" +
                                  "The URL where your Ollama server is running.<br/>" +
                                  "Default: http://localhost:11434<br/><br/>" +
                                  "<b>Setup:</b><br/>" +
                                  "1. Install Ollama from https://ollama.ai<br/>" +
                                  "2. Start server: ollama serve<br/>" +
                                  "3. Download models: ollama pull llama2</html>");
        
        JButton saveServerButton = new JButton("Save URL");
        saveServerButton.setToolTipText("Save the Ollama server URL and reconfigure the provider");
        saveServerButton.addActionListener(e -> {
            String serverUrl = serverField.getText().trim();
            Settings.setOllamaServerUrl(serverUrl);
            
            // Reconfigure provider with new URL
            try {
                OllamaProvider provider = (OllamaProvider) providerManager.getProvider(Settings.AIProviderType.OLLAMA);
                if (provider != null) {
                    Map<String, String> config = new HashMap<>();
                    config.put("serverUrl", serverUrl);
                    config.put("model", Settings.getSelectedOllamaModel());
                    provider.configure(config);
                    
                    JOptionPane.showMessageDialog(panel, "Server URL saved and provider configured!", 
                        "Success", JOptionPane.INFORMATION_MESSAGE);
                    
                    // Refresh models with new URL
                    refreshOllamaModels();
                }
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(panel, "Failed to configure provider: " + ex.getMessage(), 
                    "Error", JOptionPane.ERROR_MESSAGE);
            }
        });
        
        serverPanel.add(serverLabel);
        serverPanel.add(serverField);
        serverPanel.add(saveServerButton);
        
        // Status panel
        JPanel statusPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JLabel statusLabel = new JLabel("Status: " + getOllamaStatus());
        statusPanel.add(statusLabel);
        
        // Combine panels
        JPanel topPanel = new JPanel(new BorderLayout());
        topPanel.add(serverPanel, BorderLayout.NORTH);
        topPanel.add(controlPanel, BorderLayout.CENTER);
        
        panel.add(topPanel, BorderLayout.CENTER);
        panel.add(statusPanel, BorderLayout.SOUTH);
        
        // Load initial models
        refreshOllamaModels();
        
        return panel;
    }
    
    /**
     * Creates a panel with OpenRouter model selection controls.
     */
    public JPanel createOpenRouterModelPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createTitledBorder("OpenRouter Models"));
        
        // API Key panel
        JPanel apiKeyPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JLabel apiKeyLabel = new JLabel("API Key:");
        apiKeyLabel.setToolTipText("<html><b>OpenRouter API Key</b><br/>" +
                                  "Get your API key from:<br/>" +
                                  "https://openrouter.ai/keys</html>");
        JPasswordField apiKeyField = new JPasswordField(30);
        apiKeyField.setText(Settings.getOpenRouterApiKey());
        apiKeyField.setToolTipText("<html><b>OpenRouter API Key</b><br/>" +
                                  "Enter your OpenRouter API key here.<br/>" +
                                  "OpenRouter provides access to multiple AI models.<br/><br/>" +
                                  "<b>To get an API key:</b><br/>" +
                                  "1. Visit https://openrouter.ai<br/>" +
                                  "2. Create an account<br/>" +
                                  "3. Go to Keys section<br/>" +
                                  "4. Create a new API key</html>");
        
        JButton saveKeyButton = new JButton("Save Key");
        saveKeyButton.addActionListener(e -> {
            String apiKey = new String(apiKeyField.getPassword());
            Settings.setOpenRouterApiKey(apiKey);
            configureOpenRouterProvider();
            JOptionPane.showMessageDialog(panel, "API key saved!", "Success", JOptionPane.INFORMATION_MESSAGE);
            refreshOpenRouterModels();
        });
        
        apiKeyPanel.add(apiKeyLabel);
        apiKeyPanel.add(apiKeyField);
        apiKeyPanel.add(saveKeyButton);
        
        // Model selection panel
        JPanel modelPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        openRouterModelCombo = new JComboBox<>();
        openRouterModelCombo.setPreferredSize(new Dimension(300, 25));
        openRouterModelCombo.addActionListener(e -> {
            String selectedModel = (String) openRouterModelCombo.getSelectedItem();
            if (selectedModel != null && !selectedModel.startsWith("Error")) {
                Settings.setSelectedOpenRouterModel(selectedModel);
                configureOpenRouterProvider();
            }
        });
        
        refreshOpenRouterButton = new JButton("Refresh Models");
        refreshOpenRouterButton.addActionListener(e -> refreshOpenRouterModels());
        
        testOpenRouterButton = new JButton("Test Connection");
        testOpenRouterButton.addActionListener(e -> testOpenRouterConnection());
        
        modelPanel.add(new JLabel("Model:"));
        modelPanel.add(openRouterModelCombo);
        modelPanel.add(refreshOpenRouterButton);
        modelPanel.add(testOpenRouterButton);
        
        panel.add(apiKeyPanel, BorderLayout.NORTH);
        panel.add(modelPanel, BorderLayout.CENTER);
        
        refreshOpenRouterModels();
        return panel;
    }
    
    /**
     * Creates a panel with OpenAI model selection controls.
     */
    public JPanel createOpenAIModelPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createTitledBorder("OpenAI Models"));
        
        // API Key panel
        JPanel apiKeyPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JLabel apiKeyLabel = new JLabel("API Key:");
        JPasswordField apiKeyField = new JPasswordField(30);
        apiKeyField.setText(Settings.getOpenAIApiKey());
        
        JButton saveKeyButton = new JButton("Save Key");
        saveKeyButton.addActionListener(e -> {
            String apiKey = new String(apiKeyField.getPassword());
            Settings.setOpenAIApiKey(apiKey);
            configureOpenAIProvider();
            JOptionPane.showMessageDialog(panel, "API key saved!", "Success", JOptionPane.INFORMATION_MESSAGE);
            refreshOpenAIModels();
        });
        
        apiKeyPanel.add(apiKeyLabel);
        apiKeyPanel.add(apiKeyField);
        apiKeyPanel.add(saveKeyButton);
        
        // Model selection panel
        JPanel modelPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        openAIModelCombo = new JComboBox<>();
        openAIModelCombo.setPreferredSize(new Dimension(250, 25));
        openAIModelCombo.addActionListener(e -> {
            String selectedModel = (String) openAIModelCombo.getSelectedItem();
            if (selectedModel != null && !selectedModel.startsWith("Error")) {
                Settings.setSelectedOpenAIModel(selectedModel);
                configureOpenAIProvider();
            }
        });
        
        refreshOpenAIButton = new JButton("Refresh Models");
        refreshOpenAIButton.addActionListener(e -> refreshOpenAIModels());
        
        testOpenAIButton = new JButton("Test Connection");
        testOpenAIButton.addActionListener(e -> testOpenAIConnection());
        
        modelPanel.add(new JLabel("Model:"));
        modelPanel.add(openAIModelCombo);
        modelPanel.add(refreshOpenAIButton);
        modelPanel.add(testOpenAIButton);
        
        panel.add(apiKeyPanel, BorderLayout.NORTH);
        panel.add(modelPanel, BorderLayout.CENTER);
        
        refreshOpenAIModels();
        return panel;
    }
    
    /**
     * Creates a panel with Claude model selection controls.
     */
    public JPanel createClaudeModelPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createTitledBorder("Claude Models"));
        
        // API Key panel
        JPanel apiKeyPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JLabel apiKeyLabel = new JLabel("API Key:");
        JPasswordField apiKeyField = new JPasswordField(30);
        apiKeyField.setText(Settings.getClaudeApiKey());
        
        JButton saveKeyButton = new JButton("Save Key");
        saveKeyButton.addActionListener(e -> {
            String apiKey = new String(apiKeyField.getPassword());
            Settings.setClaudeApiKey(apiKey);
            configureClaudeProvider();
            JOptionPane.showMessageDialog(panel, "API key saved!", "Success", JOptionPane.INFORMATION_MESSAGE);
            refreshClaudeModels();
        });
        
        apiKeyPanel.add(apiKeyLabel);
        apiKeyPanel.add(apiKeyField);
        apiKeyPanel.add(saveKeyButton);
        
        // Model selection panel
        JPanel modelPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        claudeModelCombo = new JComboBox<>();
        claudeModelCombo.setPreferredSize(new Dimension(250, 25));
        claudeModelCombo.addActionListener(e -> {
            String selectedModel = (String) claudeModelCombo.getSelectedItem();
            if (selectedModel != null && !selectedModel.startsWith("Error")) {
                Settings.setSelectedClaudeModel(selectedModel);
                configureClaudeProvider();
            }
        });
        
        refreshClaudeButton = new JButton("Refresh Models");
        refreshClaudeButton.addActionListener(e -> refreshClaudeModels());
        
        testClaudeButton = new JButton("Test Connection");
        testClaudeButton.addActionListener(e -> testClaudeConnection());
        
        modelPanel.add(new JLabel("Model:"));
        modelPanel.add(claudeModelCombo);
        modelPanel.add(refreshClaudeButton);
        modelPanel.add(testClaudeButton);
        
        panel.add(apiKeyPanel, BorderLayout.NORTH);
        panel.add(modelPanel, BorderLayout.CENTER);
        
        refreshClaudeModels();
        return panel;
    }
    
    /**
     * Creates a panel showing only the currently selected provider's settings.
     */
    public JPanel createCombinedModelPanel() {
        JPanel mainPanel = new JPanel(new BorderLayout());
        
        // Show only the currently selected provider's panel
        Settings.AIProviderType currentProvider = Settings.getActiveAIProvider();
        JPanel providerPanel;
        
        switch (currentProvider) {
            case GEMINI:
                providerPanel = createGeminiModelPanel();
                break;
            case OLLAMA:
                providerPanel = createOllamaModelPanel();
                break;
            case OPENROUTER:
                providerPanel = createOpenRouterModelPanel();
                break;
            case OPENAI:
                providerPanel = createOpenAIModelPanel();
                break;
            case CLAUDE:
                providerPanel = createClaudeModelPanel();
                break;
            default:
                providerPanel = createGeminiModelPanel();
                break;
        }
        
        mainPanel.add(providerPanel, BorderLayout.CENTER);
        
        return mainPanel;
    }
    
    /**
     * Refreshes the combined panel to show the currently selected provider.
     */
    public void refreshForCurrentProvider() {
        // This method can be called when the provider selection changes
        // The parent container should recreate the panel
    }
    
    /**
     * Refreshes the Gemini models dropdown.
     */
    private void refreshGeminiModels() {
        SwingUtilities.invokeLater(() -> {
            refreshGeminiButton.setEnabled(false);
            refreshGeminiButton.setText("Loading...");
        });
        
        // Run in background thread
        new Thread(() -> {
            try {
                GeminiProvider provider = (GeminiProvider) providerManager.getProvider(Settings.AIProviderType.GEMINI);
                if (provider != null && provider.isAvailable()) {
                    String[] models = provider.getAvailableModels();
                    
                    SwingUtilities.invokeLater(() -> {
                        geminiModelCombo.removeAllItems();
                        for (String model : models) {
                            geminiModelCombo.addItem(model);
                        }
                        
                        // Try to select the default model (gemini-1.5-flash-latest) first
                        String defaultModel = "gemini-1.5-flash-latest";
                        boolean defaultFound = false;
                        for (int i = 0; i < geminiModelCombo.getItemCount(); i++) {
                            if (defaultModel.equals(geminiModelCombo.getItemAt(i))) {
                                geminiModelCombo.setSelectedIndex(i);
                                defaultFound = true;
                                break;
                            }
                        }
                        
                        // If default model not found, select current model if available
                        if (!defaultFound) {
                            String currentModel = provider.getModel().getModelName();
                            geminiModelCombo.setSelectedItem(currentModel);
                        }
                    });
                } else {
                    SwingUtilities.invokeLater(() -> {
                        geminiModelCombo.removeAllItems();
                        geminiModelCombo.addItem("Provider not configured");
                    });
                }
            } catch (Exception e) {
                SwingUtilities.invokeLater(() -> {
                    geminiModelCombo.removeAllItems();
                    geminiModelCombo.addItem("Error loading models");
                    JOptionPane.showMessageDialog(null, "Failed to load Gemini models: " + e.getMessage(), 
                        "Error", JOptionPane.ERROR_MESSAGE);
                });
            } finally {
                SwingUtilities.invokeLater(() -> {
                    refreshGeminiButton.setEnabled(true);
                    refreshGeminiButton.setText("Refresh");
                });
            }
        }).start();
    }
    
    /**
     * Refreshes the Ollama models dropdown.
     */
    private void refreshOllamaModels() {
        SwingUtilities.invokeLater(() -> {
            refreshOllamaButton.setEnabled(false);
            refreshOllamaButton.setText("Loading...");
        });
        
        // Run in background thread
        new Thread(() -> {
            try {
                OllamaProvider provider = (OllamaProvider) providerManager.getProvider(Settings.AIProviderType.OLLAMA);
                if (provider != null && provider.isAvailable()) {
                    String[] models = provider.getAvailableModels();
                    
                    SwingUtilities.invokeLater(() -> {
                        ollamaModelCombo.removeAllItems();
                        if (models.length == 0) {
                            ollamaModelCombo.addItem("No models found");
                        } else {
                            for (String model : models) {
                                ollamaModelCombo.addItem(model);
                            }
                            
                            // Select current model if available
                            String currentModel = provider.getModel();
                            ollamaModelCombo.setSelectedItem(currentModel);
                        }
                    });
                } else {
                    SwingUtilities.invokeLater(() -> {
                        ollamaModelCombo.removeAllItems();
                        ollamaModelCombo.addItem("Provider not configured");
                    });
                }
            } catch (Exception e) {
                SwingUtilities.invokeLater(() -> {
                    ollamaModelCombo.removeAllItems();
                    ollamaModelCombo.addItem("Error loading models");
                    JOptionPane.showMessageDialog(null, "Failed to load Ollama models: " + e.getMessage(), 
                        "Error", JOptionPane.ERROR_MESSAGE);
                });
            } finally {
                SwingUtilities.invokeLater(() -> {
                    refreshOllamaButton.setEnabled(true);
                    refreshOllamaButton.setText("Refresh");
                });
            }
        }).start();
    }
    
    /**
     * Updates the Gemini model selection.
     */
    private void updateGeminiModel(String modelName) {
        try {
            GeminiProvider provider = (GeminiProvider) providerManager.getProvider(Settings.AIProviderType.GEMINI);
            if (provider != null) {
                provider.setModel(modelName);
                
                // Update configuration
                Map<String, String> config = new HashMap<>();
                config.put("apiKey", Settings.getGeminiApiKey());
                config.put("endpoint", Settings.getGeminiEndpoint());
                config.put("model", modelName);
                provider.configure(config);
                
                JOptionPane.showMessageDialog(null, "Gemini model updated to: " + modelName, 
                    "Success", JOptionPane.INFORMATION_MESSAGE);
            }
        } catch (Exception e) {
            JOptionPane.showMessageDialog(null, "Failed to update Gemini model: " + e.getMessage(), 
                "Error", JOptionPane.ERROR_MESSAGE);
        }
    }
    
    /**
     * Updates the Ollama model selection.
     */
    private void updateOllamaModel(String modelName) {
        try {
            // Save the selected model to settings
            Settings.setSelectedOllamaModel(modelName);
            
            OllamaProvider provider = (OllamaProvider) providerManager.getProvider(Settings.AIProviderType.OLLAMA);
            if (provider != null) {
                provider.setModel(modelName);
                
                // Update configuration
                Map<String, String> config = new HashMap<>();
                config.put("serverUrl", Settings.getOllamaServerUrl());
                config.put("model", modelName);
                provider.configure(config);
                
                JOptionPane.showMessageDialog(null, "Ollama model updated to: " + modelName, 
                    "Success", JOptionPane.INFORMATION_MESSAGE);
            }
        } catch (Exception e) {
            JOptionPane.showMessageDialog(null, "Failed to update Ollama model: " + e.getMessage(), 
                "Error", JOptionPane.ERROR_MESSAGE);
        }
    }
    
    /**
     * Tests the Gemini connection.
     */
    private void testGeminiConnection() {
        testGeminiButton.setEnabled(false);
        testGeminiButton.setText("Testing...");
        
        new Thread(() -> {
            try {
                GeminiProvider provider = (GeminiProvider) providerManager.getProvider(Settings.AIProviderType.GEMINI);
                if (provider != null) {
                    boolean success = provider.testConnection();
                    
                    SwingUtilities.invokeLater(() -> {
                        if (success) {
                            JOptionPane.showMessageDialog(null, 
                                "Gemini connection successful!\nModel: " + provider.getModel().getModelName(), 
                                "Connection Test", JOptionPane.INFORMATION_MESSAGE);
                        } else {
                            JOptionPane.showMessageDialog(null, "Gemini connection failed", 
                                "Connection Test", JOptionPane.ERROR_MESSAGE);
                        }
                    });
                } else {
                    SwingUtilities.invokeLater(() -> {
                        JOptionPane.showMessageDialog(null, "Gemini provider not available", 
                            "Connection Test", JOptionPane.ERROR_MESSAGE);
                    });
                }
            } catch (Exception e) {
                SwingUtilities.invokeLater(() -> {
                    JOptionPane.showMessageDialog(null, "Connection test failed: " + e.getMessage(), 
                        "Connection Test", JOptionPane.ERROR_MESSAGE);
                });
            } finally {
                SwingUtilities.invokeLater(() -> {
                    testGeminiButton.setEnabled(true);
                    testGeminiButton.setText("Test");
                });
            }
        }).start();
    }
    
    /**
     * Tests the Ollama connection.
     */
    private void testOllamaConnection() {
        testOllamaButton.setEnabled(false);
        testOllamaButton.setText("Testing...");
        
        new Thread(() -> {
            try {
                OllamaProvider provider = (OllamaProvider) providerManager.getProvider(Settings.AIProviderType.OLLAMA);
                if (provider != null) {
                    boolean success = provider.testConnection();
                    
                    SwingUtilities.invokeLater(() -> {
                        if (success) {
                            JOptionPane.showMessageDialog(null, 
                                "Ollama connection successful!\nServer: " + provider.getServerUrl() + 
                                "\nModel: " + provider.getModel(), 
                                "Connection Test", JOptionPane.INFORMATION_MESSAGE);
                        } else {
                            JOptionPane.showMessageDialog(null, "Ollama connection failed", 
                                "Connection Test", JOptionPane.ERROR_MESSAGE);
                        }
                    });
                } else {
                    SwingUtilities.invokeLater(() -> {
                        JOptionPane.showMessageDialog(null, "Ollama provider not available", 
                            "Connection Test", JOptionPane.ERROR_MESSAGE);
                    });
                }
            } catch (Exception e) {
                SwingUtilities.invokeLater(() -> {
                    JOptionPane.showMessageDialog(null, "Connection test failed: " + e.getMessage(), 
                        "Connection Test", JOptionPane.ERROR_MESSAGE);
                });
            } finally {
                SwingUtilities.invokeLater(() -> {
                    testOllamaButton.setEnabled(true);
                    testOllamaButton.setText("Test");
                });
            }
        }).start();
    }
    
    /**
     * Gets the currently selected Gemini model.
     */
    public String getSelectedGeminiModel() {
        return geminiModelCombo != null ? (String) geminiModelCombo.getSelectedItem() : null;
    }
    
    /**
     * Gets the currently selected Ollama model.
     */
    public String getSelectedOllamaModel() {
        return ollamaModelCombo != null ? (String) ollamaModelCombo.getSelectedItem() : null;
    }
    
    /**
     * Gets the current Gemini provider status.
     */
    private String getGeminiStatus() {
        try {
            GeminiProvider provider = (GeminiProvider) providerManager.getProvider(Settings.AIProviderType.GEMINI);
            return provider != null ? provider.getConfigurationStatus() : "Not available";
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }
    
    /**
     * Gets the current Ollama provider status.
     */
    private String getOllamaStatus() {
        try {
            OllamaProvider provider = (OllamaProvider) providerManager.getProvider(Settings.AIProviderType.OLLAMA);
            return provider != null ? provider.getConfigurationStatus() : "Not available";
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }
    
    /**
     * Refreshes the OpenRouter models dropdown.
     */
    private void refreshOpenRouterModels() {
        SwingUtilities.invokeLater(() -> {
            refreshOpenRouterButton.setEnabled(false);
            refreshOpenRouterButton.setText("Loading...");
        });
        
        new Thread(() -> {
            try {
                EnhancedAIProvider provider = providerManager.getEnhancedProvider(Settings.AIProviderType.OPENROUTER);
                if (provider != null && provider.isAvailable()) {
                    EnhancedAIProvider.ModelInfo[] models = provider.getAvailableModels();
                    
                    SwingUtilities.invokeLater(() -> {
                        openRouterModelCombo.removeAllItems();
                        for (EnhancedAIProvider.ModelInfo model : models) {
                            openRouterModelCombo.addItem(model.getId());
                        }
                        openRouterModelCombo.setSelectedItem(Settings.getSelectedOpenRouterModel());
                    });
                } else {
                    SwingUtilities.invokeLater(() -> {
                        openRouterModelCombo.removeAllItems();
                        openRouterModelCombo.addItem("anthropic/claude-3-haiku");
                        openRouterModelCombo.addItem("anthropic/claude-3-sonnet");
                        openRouterModelCombo.addItem("openai/gpt-4");
                        openRouterModelCombo.addItem("openai/gpt-3.5-turbo");
                        openRouterModelCombo.setSelectedItem(Settings.getSelectedOpenRouterModel());
                    });
                }
            } catch (Exception e) {
                SwingUtilities.invokeLater(() -> {
                    openRouterModelCombo.removeAllItems();
                    openRouterModelCombo.addItem("Error loading models");
                });
            } finally {
                SwingUtilities.invokeLater(() -> {
                    refreshOpenRouterButton.setEnabled(true);
                    refreshOpenRouterButton.setText("Refresh Models");
                });
            }
        }).start();
    }
    
    /**
     * Refreshes the OpenAI models dropdown.
     */
    private void refreshOpenAIModels() {
        SwingUtilities.invokeLater(() -> {
            refreshOpenAIButton.setEnabled(false);
            refreshOpenAIButton.setText("Loading...");
        });
        
        new Thread(() -> {
            try {
                EnhancedAIProvider provider = providerManager.getEnhancedProvider(Settings.AIProviderType.OPENAI);
                if (provider != null && provider.isAvailable()) {
                    EnhancedAIProvider.ModelInfo[] models = provider.getAvailableModels();
                    
                    SwingUtilities.invokeLater(() -> {
                        openAIModelCombo.removeAllItems();
                        for (EnhancedAIProvider.ModelInfo model : models) {
                            openAIModelCombo.addItem(model.getId());
                        }
                        openAIModelCombo.setSelectedItem(Settings.getSelectedOpenAIModel());
                    });
                } else {
                    SwingUtilities.invokeLater(() -> {
                        openAIModelCombo.removeAllItems();
                        openAIModelCombo.addItem("gpt-4");
                        openAIModelCombo.addItem("gpt-3.5-turbo");
                        openAIModelCombo.addItem("gpt-4-turbo");
                        openAIModelCombo.setSelectedItem(Settings.getSelectedOpenAIModel());
                    });
                }
            } catch (Exception e) {
                SwingUtilities.invokeLater(() -> {
                    openAIModelCombo.removeAllItems();
                    openAIModelCombo.addItem("Error loading models");
                });
            } finally {
                SwingUtilities.invokeLater(() -> {
                    refreshOpenAIButton.setEnabled(true);
                    refreshOpenAIButton.setText("Refresh Models");
                });
            }
        }).start();
    }
    
    /**
     * Refreshes the Claude models dropdown.
     */
    private void refreshClaudeModels() {
        SwingUtilities.invokeLater(() -> {
            refreshClaudeButton.setEnabled(false);
            refreshClaudeButton.setText("Loading...");
        });
        
        new Thread(() -> {
            try {
                EnhancedAIProvider provider = providerManager.getEnhancedProvider(Settings.AIProviderType.CLAUDE);
                if (provider != null && provider.isAvailable()) {
                    EnhancedAIProvider.ModelInfo[] models = provider.getAvailableModels();
                    
                    SwingUtilities.invokeLater(() -> {
                        claudeModelCombo.removeAllItems();
                        for (EnhancedAIProvider.ModelInfo model : models) {
                            claudeModelCombo.addItem(model.getId());
                        }
                        claudeModelCombo.setSelectedItem(Settings.getSelectedClaudeModel());
                    });
                } else {
                    SwingUtilities.invokeLater(() -> {
                        claudeModelCombo.removeAllItems();
                        claudeModelCombo.addItem("claude-3-haiku-20240307");
                        claudeModelCombo.addItem("claude-3-sonnet-20240229");
                        claudeModelCombo.addItem("claude-3-opus-20240229");
                        claudeModelCombo.setSelectedItem(Settings.getSelectedClaudeModel());
                    });
                }
            } catch (Exception e) {
                SwingUtilities.invokeLater(() -> {
                    claudeModelCombo.removeAllItems();
                    claudeModelCombo.addItem("Error loading models");
                });
            } finally {
                SwingUtilities.invokeLater(() -> {
                    refreshClaudeButton.setEnabled(true);
                    refreshClaudeButton.setText("Refresh Models");
                });
            }
        }).start();
    }
    
    /**
     * Configures the OpenRouter provider.
     */
    private void configureOpenRouterProvider() {
        try {
            OpenRouterProvider provider = (OpenRouterProvider) providerManager.getProvider(Settings.AIProviderType.OPENROUTER);
            if (provider != null) {
                Map<String, String> config = new HashMap<>();
                config.put("apiKey", Settings.getOpenRouterApiKey());
                config.put("endpoint", Settings.getOpenRouterEndpoint());
                config.put("model", Settings.getSelectedOpenRouterModel());
                provider.configure(config);
            }
        } catch (Exception e) {
            System.err.println("Failed to configure OpenRouter provider: " + e.getMessage());
        }
    }
    
    /**
     * Configures the OpenAI provider.
     */
    private void configureOpenAIProvider() {
        try {
            OpenAIProvider provider = (OpenAIProvider) providerManager.getProvider(Settings.AIProviderType.OPENAI);
            if (provider != null) {
                Map<String, String> config = new HashMap<>();
                config.put("apiKey", Settings.getOpenAIApiKey());
                config.put("endpoint", Settings.getOpenAIEndpoint());
                config.put("model", Settings.getSelectedOpenAIModel());
                provider.configure(config);
            }
        } catch (Exception e) {
            System.err.println("Failed to configure OpenAI provider: " + e.getMessage());
        }
    }
    
    /**
     * Configures the Claude provider.
     */
    private void configureClaudeProvider() {
        try {
            ClaudeProvider provider = (ClaudeProvider) providerManager.getProvider(Settings.AIProviderType.CLAUDE);
            if (provider != null) {
                Map<String, String> config = new HashMap<>();
                config.put("apiKey", Settings.getClaudeApiKey());
                config.put("endpoint", Settings.getClaudeEndpoint());
                config.put("model", Settings.getSelectedClaudeModel());
                config.put("maxTokens", String.valueOf(Settings.getClaudeMaxTokens()));
                config.put("systemPrompt", Settings.getClaudeSystemPrompt());
                provider.configure(config);
            }
        } catch (Exception e) {
            System.err.println("Failed to configure Claude provider: " + e.getMessage());
        }
    }
    
    /**
     * Tests the OpenRouter connection.
     */
    private void testOpenRouterConnection() {
        testOpenRouterButton.setEnabled(false);
        testOpenRouterButton.setText("Testing...");
        
        new Thread(() -> {
            try {
                boolean success = providerManager.testProviderConnection(Settings.AIProviderType.OPENROUTER);
                
                SwingUtilities.invokeLater(() -> {
                    if (success) {
                        JOptionPane.showMessageDialog(null, "OpenRouter connection successful!", 
                            "Connection Test", JOptionPane.INFORMATION_MESSAGE);
                    } else {
                        JOptionPane.showMessageDialog(null, "OpenRouter connection failed", 
                            "Connection Test", JOptionPane.ERROR_MESSAGE);
                    }
                });
            } catch (Exception e) {
                SwingUtilities.invokeLater(() -> {
                    JOptionPane.showMessageDialog(null, "Connection test failed: " + e.getMessage(), 
                        "Connection Test", JOptionPane.ERROR_MESSAGE);
                });
            } finally {
                SwingUtilities.invokeLater(() -> {
                    testOpenRouterButton.setEnabled(true);
                    testOpenRouterButton.setText("Test Connection");
                });
            }
        }).start();
    }
    
    /**
     * Tests the OpenAI connection.
     */
    private void testOpenAIConnection() {
        testOpenAIButton.setEnabled(false);
        testOpenAIButton.setText("Testing...");
        
        new Thread(() -> {
            try {
                boolean success = providerManager.testProviderConnection(Settings.AIProviderType.OPENAI);
                
                SwingUtilities.invokeLater(() -> {
                    if (success) {
                        JOptionPane.showMessageDialog(null, "OpenAI connection successful!", 
                            "Connection Test", JOptionPane.INFORMATION_MESSAGE);
                    } else {
                        JOptionPane.showMessageDialog(null, "OpenAI connection failed", 
                            "Connection Test", JOptionPane.ERROR_MESSAGE);
                    }
                });
            } catch (Exception e) {
                SwingUtilities.invokeLater(() -> {
                    JOptionPane.showMessageDialog(null, "Connection test failed: " + e.getMessage(), 
                        "Connection Test", JOptionPane.ERROR_MESSAGE);
                });
            } finally {
                SwingUtilities.invokeLater(() -> {
                    testOpenAIButton.setEnabled(true);
                    testOpenAIButton.setText("Test Connection");
                });
            }
        }).start();
    }
    
    /**
     * Tests the Claude connection.
     */
    private void testClaudeConnection() {
        testClaudeButton.setEnabled(false);
        testClaudeButton.setText("Testing...");
        
        new Thread(() -> {
            try {
                boolean success = providerManager.testProviderConnection(Settings.AIProviderType.CLAUDE);
                
                SwingUtilities.invokeLater(() -> {
                    if (success) {
                        JOptionPane.showMessageDialog(null, "Claude connection successful!", 
                            "Connection Test", JOptionPane.INFORMATION_MESSAGE);
                    } else {
                        JOptionPane.showMessageDialog(null, "Claude connection failed", 
                            "Connection Test", JOptionPane.ERROR_MESSAGE);
                    }
                });
            } catch (Exception e) {
                SwingUtilities.invokeLater(() -> {
                    JOptionPane.showMessageDialog(null, "Connection test failed: " + e.getMessage(), 
                        "Connection Test", JOptionPane.ERROR_MESSAGE);
                });
            } finally {
                SwingUtilities.invokeLater(() -> {
                    testClaudeButton.setEnabled(true);
                    testClaudeButton.setText("Test Connection");
                });
            }
        }).start();
    }
}
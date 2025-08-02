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
import whitehole.Settings;
import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.HashMap;
import java.util.Map;

/**
 * Utility class for creating AI model selection UI components.
 * Provides dropdowns for Gemini and Ollama model selection with dynamic model discovery.
 */
public class AIModelSelector {
    
    private final AIProviderManager providerManager;
    private JComboBox<String> geminiModelCombo;
    private JComboBox<String> ollamaModelCombo;
    private JButton refreshGeminiButton;
    private JButton refreshOllamaButton;
    private JButton testGeminiButton;
    private JButton testOllamaButton;
    
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
     * Creates a combined panel with both provider model selections.
     */
    public JPanel createCombinedModelPanel() {
        JPanel mainPanel = new JPanel(new BorderLayout());
        
        JPanel geminiPanel = createGeminiModelPanel();
        JPanel ollamaPanel = createOllamaModelPanel();
        
        JPanel providersPanel = new JPanel(new GridLayout(2, 1, 5, 5));
        providersPanel.add(geminiPanel);
        providersPanel.add(ollamaPanel);
        
        mainPanel.add(providersPanel, BorderLayout.CENTER);
        
        return mainPanel;
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
}
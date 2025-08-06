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

import whitehole.editor.GalaxyEditorForm;
import whitehole.Settings;
import whitehole.ai.CommandExecutor;
import whitehole.ai.CommandExecutorIntegration;
import whitehole.math.Vec3f;
import javax.swing.*;
import javax.swing.border.TitledBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.ListSelectionListener;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import java.awt.*;
import java.awt.event.*;
import java.util.*;
import java.util.List;
import java.util.Map;
import javax.swing.SwingWorker;

/**
 * AICommandPanel provides the user interface for AI-powered object manipulation.
 * Features include text input, command history, auto-completion, and real-time feedback.
 */
public class AICommandPanel extends JPanel {
    
    private final GalaxyEditorForm editorForm;
    private final CommandParser commandParser;

    private final GalaxyContextManager contextManager;
    
    // UI Components
    private JTextField txtCommand;
    private JButton btnExecute;
    private JButton btnCancel;
    private JTextArea txtStatus;
    private JScrollPane scrollStatus;
    private JList<String> listHistory;
    private DefaultListModel<String> historyModel;
    private JPopupMenu suggestionPopup;
    private JProgressBar progressBar;
    private JLabel lblConnectionStatus;
    
    // Enhanced UI Components for command management
    private JList<String> listFavorites;
    private DefaultListModel<String> favoritesModel;
    private JButton btnAddToFavorites;
    private JButton btnRemoveFromFavorites;
    
    // Command processing
    private SwingWorker<Void, String> currentTask;
    private final List<String> commandHistory = new ArrayList<>();
    private final List<String> favoriteCommands = new ArrayList<>();
    private int historyIndex = -1;
    private final List<String> suggestions = new ArrayList<>();
    private javax.swing.Timer connectionStatusTimer;
    
    // Auto-completion data - Common command patterns
    private static final String[] COMMAND_TEMPLATES = {
        // Object creation commands
        "add {object}",
        "add {object} at {x}, {y}, {z}",
        "add {number} {object} at {x}, {y}, {z}",
        "add {object} near {reference}",
        "create {object} at position {x}, {y}, {z}",
        
        // Movement commands
        "move the {object} {distance} units {direction}",
        "move all {type} {distance} units {direction}",
        "move {object} to position {x}, {y}, {z}",
        "move {object} near the {reference}",
        "translate {object} by {x}, {y}, {z}",
        
        // Rotation commands
        "rotate {object} {degrees} degrees",
        "rotate all {type} {degrees} degrees",
        "rotate {object} {degrees} degrees clockwise",
        "turn {object} around",
        "spin {object} {degrees} degrees",
        
        // Scaling commands
        "scale {object} by {factor}x",
        "make {object} {factor} times bigger",
        "resize {object} to {factor}x",
        "make all {type} twice as big",
        "shrink {object} by half",
        
        // Property changes
        "change {object} color to {color}",
        "set {object} {property} to {value}",
        "change all {type} {property} to {value}",
        "activate {object}",
        "deactivate {object}",
        
        // Batch operations
        "move all objects near {reference} {distance} units {direction}",
        "rotate all {type} in this zone {degrees} degrees",
        "scale all {type} by {factor}x",
        "delete all {type}",
        
        // Selection and visibility
        "select {object}",
        "select all {type}",
        "hide {object}",
        "show {object}",
        "copy {object}",
        "duplicate {object}"
    };
    
    private static final String[] DIRECTIONS = {
        "right", "left", "up", "down", "forward", "backward", "north", "south", "east", "west"
    };
    
    private static final String[] OBJECT_TYPES = {
        "enemies", "platforms", "coins", "stars", "pipes", "blocks", "switches", "cameras", "areas"
    };
    
    public AICommandPanel(GalaxyEditorForm editorForm) {
        this.editorForm = editorForm;
        this.contextManager = new GalaxyContextManager(editorForm);
        this.commandParser = new CommandParser(AIProviderManager.getInstance(), contextManager);
        
        initializeComponents();
        setupEventHandlers();
        loadCommandHistory();
        loadFavoriteCommands();
        updateConnectionStatus();
        startConnectionStatusMonitoring();
    }
    
    private void initializeComponents() {
        setLayout(new BorderLayout());
        setBorder(new TitledBorder("AI Commands"));
        
        // Create main panels
        JPanel inputPanel = createInputPanel();
        JPanel centerPanel = createStatusPanel();
        JPanel bottomPanel = createHistoryAndFavoritesPanel();
        
        add(inputPanel, BorderLayout.NORTH);
        add(centerPanel, BorderLayout.CENTER);
        add(bottomPanel, BorderLayout.SOUTH);
    }
    
    private JPanel createInputPanel() {
        JPanel panel = new JPanel(new BorderLayout(5, 5));
        panel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        
        // Command input field
        txtCommand = new JTextField();
        txtCommand.setColumns(50); // increased width
        txtCommand.setToolTipText("<html><b>AI Command Input</b><br/>" +
                                 "Enter natural language commands to manipulate objects.<br/><br/>" +
                                 "<b>Examples:</b><br/>" +
                                 "\u2022 move the Goomba 10 units right<br/>" +
                                 "\u2022 rotate all coins 45 degrees<br/>" +
                                 "\u2022 scale the platform by 1.5x<br/>" +
                                 "\u2022 change the Koopa color to red<br/><br/>" +
                                 "<b>Tips:</b><br/>" +
                                 "\u2022 Use specific object names or types<br/>" +
                                 "\u2022 Be clear about directions and amounts<br/>" +
                                 "\u2022 Press Enter to execute or click Help for more info</html>");
        txtCommand.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        
        // Execute button
        btnExecute = new JButton("Execute");
        btnExecute.setPreferredSize(new Dimension(80, txtCommand.getPreferredSize().height));
        btnExecute.setEnabled(false);
        btnExecute.setToolTipText("Execute the AI command (or press Enter in the command field)");
        
        // Cancel button
        btnCancel = new JButton("Cancel");
        btnCancel.setPreferredSize(new Dimension(80, txtCommand.getPreferredSize().height));
        btnCancel.setEnabled(false);
        btnCancel.setToolTipText("Cancel the current AI command execution");
        
        // Help button
        JButton btnHelp = new JButton("Help");
        btnHelp.setPreferredSize(new Dimension(60, txtCommand.getPreferredSize().height));
        btnHelp.setToolTipText("Show AI command help and documentation");
        btnHelp.addActionListener(e -> AIHelpSystem.showHelpDialog(this));
        
        // Connection status
        lblConnectionStatus = new JLabel("\u2022");
        lblConnectionStatus.setToolTipText("AI Provider Status - Click to refresh");
        lblConnectionStatus.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 16));
        lblConnectionStatus.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        lblConnectionStatus.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                updateConnectionStatus();
            }
        });
        
        // Button panel
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 2, 0));
        buttonPanel.add(lblConnectionStatus);
        buttonPanel.add(btnHelp);
        buttonPanel.add(btnExecute);
        buttonPanel.add(btnCancel);
        
        panel.add(new JLabel("Command:"), BorderLayout.WEST);
        panel.add(txtCommand, BorderLayout.CENTER);
        panel.add(buttonPanel, BorderLayout.EAST);
        
        return panel;
    }
    
    private JPanel createStatusPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createTitledBorder("Status"));
        
        // Status text area
        txtStatus = new JTextArea(8, 40);
        txtStatus.setEditable(false);
        txtStatus.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 11));
        txtStatus.setBackground(getBackground());
        txtStatus.setText("Ready. Enter a command above to get started.\n\n" + 
                         AIHelpSystem.getQuickHelpText());
        
        scrollStatus = new JScrollPane(txtStatus);
        scrollStatus.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        scrollStatus.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        
        // Progress bar
        progressBar = new JProgressBar();
        progressBar.setStringPainted(true);
        progressBar.setVisible(false);
        
        panel.add(scrollStatus, BorderLayout.CENTER);
        panel.add(progressBar, BorderLayout.SOUTH);
        
        return panel;
    }
    
    private JPanel createHistoryAndFavoritesPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setPreferredSize(new Dimension(0, 140));
        
        // Create tabbed pane for history and favorites
        JTabbedPane tabbedPane = new JTabbedPane();
        
        // History tab
        JPanel historyPanel = new JPanel(new BorderLayout());
        historyModel = new DefaultListModel<>();
        listHistory = new JList<>(historyModel);
        listHistory.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        listHistory.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 11));
        listHistory.setToolTipText("Double-click to reuse a command from history");
        
        JScrollPane scrollHistory = new JScrollPane(listHistory);
        historyPanel.add(scrollHistory, BorderLayout.CENTER);
        
        // History buttons
        JPanel historyButtonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 2, 2));
        btnAddToFavorites = new JButton("Add to Favorites");
        btnAddToFavorites.setToolTipText("Add selected command to favorites");
        btnAddToFavorites.setEnabled(false);
        historyButtonPanel.add(btnAddToFavorites);
        
        JButton btnClearHistory = new JButton("Clear");
        btnClearHistory.setToolTipText("Clear command history");
        btnClearHistory.addActionListener(e -> clearHistory());
        historyButtonPanel.add(btnClearHistory);
        
        historyPanel.add(historyButtonPanel, BorderLayout.SOUTH);
        tabbedPane.addTab("History", historyPanel);
        
        // Favorites tab
        JPanel favoritesPanel = new JPanel(new BorderLayout());
        favoritesModel = new DefaultListModel<>();
        listFavorites = new JList<>(favoritesModel);
        listFavorites.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        listFavorites.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 11));
        listFavorites.setToolTipText("Double-click to use a favorite command");
        
        JScrollPane scrollFavorites = new JScrollPane(listFavorites);
        favoritesPanel.add(scrollFavorites, BorderLayout.CENTER);
        
        // Favorites buttons
        JPanel favoritesButtonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 2, 2));
        btnRemoveFromFavorites = new JButton("Remove");
        btnRemoveFromFavorites.setToolTipText("Remove selected command from favorites");
        btnRemoveFromFavorites.setEnabled(false);
        favoritesButtonPanel.add(btnRemoveFromFavorites);
        
        JButton btnClearFavorites = new JButton("Clear");
        btnClearFavorites.setToolTipText("Clear all favorite commands");
        btnClearFavorites.addActionListener(e -> clearFavorites());
        favoritesButtonPanel.add(btnClearFavorites);
        
        favoritesPanel.add(favoritesButtonPanel, BorderLayout.SOUTH);
        tabbedPane.addTab("Favorites", favoritesPanel);
        
        panel.add(tabbedPane, BorderLayout.CENTER);
        
        return panel;
    }
    
    private void setupEventHandlers() {
        // Command input handlers
        txtCommand.addActionListener(e -> executeCommand());
        txtCommand.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) { updateExecuteButton(); }
            @Override
            public void removeUpdate(DocumentEvent e) { updateExecuteButton(); }
            @Override
            public void changedUpdate(DocumentEvent e) { updateExecuteButton(); }
        });
        
        // Key handlers for history navigation and auto-completion
        txtCommand.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                handleKeyPress(e);
            }
        });
        
        // Button handlers
        btnExecute.addActionListener(e -> executeCommand());
        btnCancel.addActionListener(e -> cancelCommand());
        
        // History selection handler
        listHistory.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    String selected = listHistory.getSelectedValue();
                    if (selected != null) {
                        txtCommand.setText(selected);
                        txtCommand.requestFocus();
                    }
                }
            }
        });
        
        listHistory.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                btnAddToFavorites.setEnabled(listHistory.getSelectedValue() != null);
            }
        });
        
        // Favorites selection handler
        listFavorites.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    String selected = listFavorites.getSelectedValue();
                    if (selected != null) {
                        txtCommand.setText(selected);
                        txtCommand.requestFocus();
                    }
                }
            }
        });
        
        listFavorites.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                btnRemoveFromFavorites.setEnabled(listFavorites.getSelectedValue() != null);
            }
        });
        
        // Favorites management handlers
        btnAddToFavorites.addActionListener(e -> addToFavorites());
        btnRemoveFromFavorites.addActionListener(e -> removeFromFavorites());
        
        // Focus handler to update suggestions
        txtCommand.addFocusListener(new FocusAdapter() {
            @Override
            public void focusGained(FocusEvent e) {
                updateSuggestions();
            }
        });
    }
    
    private void handleKeyPress(KeyEvent e) {
        switch (e.getKeyCode()) {
            case KeyEvent.VK_UP:
                if (e.isControlDown()) {
                    navigateHistory(-1);
                    e.consume();
                }
                break;
                
            case KeyEvent.VK_DOWN:
                if (e.isControlDown()) {
                    navigateHistory(1);
                    e.consume();
                }
                break;
                
            case KeyEvent.VK_SPACE:
                if (e.isControlDown()) {
                    showSuggestions();
                    e.consume();
                }
                break;
                
            case KeyEvent.VK_ESCAPE:
                hideSuggestions();
                break;
        }
    }
    
    private void navigateHistory(int direction) {
        if (commandHistory.isEmpty()) return;
        
        historyIndex += direction;
        if (historyIndex < 0) {
            historyIndex = 0;
        } else if (historyIndex >= commandHistory.size()) {
            historyIndex = commandHistory.size() - 1;
        }
        
        if (historyIndex >= 0 && historyIndex < commandHistory.size()) {
            txtCommand.setText(commandHistory.get(commandHistory.size() - 1 - historyIndex));
            txtCommand.setCaretPosition(txtCommand.getText().length());
        }
    }
    
    private void updateExecuteButton() {
        String text = txtCommand.getText().trim();
        btnExecute.setEnabled(!text.isEmpty() && currentTask == null);
    }
    
    private void updateConnectionStatus() {
        SwingUtilities.invokeLater(() -> {
            try {
                AIProviderManager manager = AIProviderManager.getInstance();
                boolean connected = manager.isCurrentProviderAvailable();
                String providerName = manager.getCurrentProviderName();
                
                lblConnectionStatus.setForeground(connected ? Color.GREEN : Color.RED);
                lblConnectionStatus.setToolTipText(String.format("%s: %s - %s", 
                    providerName,
                    connected ? "Connected" : "Unavailable",
                    connected ? "Click to refresh" : "Check settings"));
                

                
            } catch (Exception e) {
                lblConnectionStatus.setForeground(Color.RED);
                lblConnectionStatus.setToolTipText("AI Provider Error: " + e.getMessage());
            }
        });
    }
    
    private void executeCommand() {
        String command = txtCommand.getText().trim();
        if (command.isEmpty() || currentTask != null) return;
        
        // Add to history
        addToHistory(command);
        
        // Update UI state
        btnExecute.setEnabled(false);
        btnCancel.setEnabled(true);
        progressBar.setVisible(true);
        progressBar.setIndeterminate(true);
        progressBar.setString("Processing command...");
        
        appendStatus("Executing: " + command);
        
        // Execute command in background with timeout handling
        currentTask = new SwingWorker<Void, String>() {
            private TimeoutHandler.TimeoutMonitor timeoutMonitor;
            
            @Override
            protected Void doInBackground() throws Exception {
                // Set up timeout monitoring
                timeoutMonitor = TimeoutHandler.monitorTask(this, AICommandPanel.this, "AI Command Processing");
                
                try {
                    publish("Analyzing command with AI...");
                    
                    // Check AI provider availability first
                    if (!AIProviderManager.getInstance().isCurrentProviderAvailable()) {
                        throw new AIProviderException(
                            "AI provider is not available: " + AIProviderManager.getInstance().getCurrentProviderStatus(),
                            AIProviderException.ErrorType.SERVICE_UNAVAILABLE
                        );
                    }
                    
                    // Get galaxy context
                    GalaxyContext context = contextManager.buildContext();
                    
                    // Parse command with AI
                    CommandResult result = commandParser.processCommand(command, context);
                    
                    if (result.isSuccess()) {
                        publish("Command executed successfully!");
                        
                        // Show the actual AI-generated response
                        String aiResponse = result.getUserFeedback();
                        if (aiResponse != null && !aiResponse.trim().isEmpty()) {
                            publish("AI Assistant: " + aiResponse);
                        } else {
                            publish("AI Assistant: Command processed successfully!");
                        }
                        
                        // Show detailed transformation information
                        if (!result.getTransformations().isEmpty()) {
                            publish("Changes made:");
                            for (ObjectTransformation transform : result.getTransformations()) {
                                String changeDesc = getTransformationDescription(transform);
                                publish("   - " + changeDesc);
                            }
                        }
                        
                        // Show warnings if any
                        if (result.hasWarnings()) {
                            for (String warning : result.getWarnings()) {
                                publish("Warning: " + warning);
                            }
                        }
                        
                        // For now, just show what would be executed
                        // TODO: Implement actual execution when CommandExecutor is properly integrated
                        CommandExecutor executor = getCommandExecutor();
                        if (executor != null) {
                            CommandExecutor.ExecutionResult execResult = 
                                executor.executeTransformations(result.getTransformations(), command);
                            
                            if (execResult.isSuccess()) {
                                publish("Command executed successfully!");
                                publish("Summary: " + execResult.getMessage());
                                publish("Modified " + execResult.getSuccessCount() + " object(s)");
                            } else {
                                publish("✗ Execution failed: " + execResult.getMessage());
                                if (execResult.hasErrors()) {
                                    for (String error : execResult.getErrors()) {
                                        publish("  - " + error);
                                    }
                                }
                            }
                        } else {
                            publish("✗ Command execution not available - CommandExecutor not initialized");
                            publish("This is a UI integration issue that needs to be resolved");
                        }
                    } else {
                        // Show the AI-generated response for failed commands
                        String aiResponse = result.getUserFeedback();
                        if (aiResponse != null && !aiResponse.trim().isEmpty()) {
                            publish("AI Assistant: " + aiResponse);
                        } else {
                            publish("AI Assistant: I couldn't process that command, but I can help you with other things!");
                            publish("Try commands like:");
                            publish("   - 'move the Goomba 10 units right'");
                            publish("   - 'rotate the platform 45 degrees'");
                            publish("   - 'scale the block by 2x'");
                            publish("   - 'add a coin at position 100, 50, 0' or 'add 5 coins at 100 | 50 | 0'");
                            publish("   - 'add 5 coins above RedBlueExStepA' or 'add coins near Goomba'");
                        }
                        
                        if (result.hasErrors()) {
                            publish("Note: " + String.join(", ", result.getErrors()));
                        }
                    }
                    
                } catch (AIProviderException e) {
                    // Check if it's a configuration issue and try to fix it
                    if (e.getMessage().contains("test-endpoint.com") || e.getMessage().contains("Network error")) {
                        publish("Detected configuration issue, attempting to fix...");
                        try {
                            AIProviderManager.getInstance().resetGeminiConfiguration();
                            publish("Configuration reset. Please try your command again.");
                        } catch (Exception resetError) {
                            publish("Could not automatically fix configuration.");
                        }
                    }
                    
                    // Provide helpful response for provider errors
                    publish("I'm having trouble connecting to my AI service right now, but I can still help you!");
                    publish("Here are some things you can try:");
                    publish("   - 'move the Goomba 10 units right'");
                    publish("   - 'rotate the platform 45 degrees'");
                    publish("   - 'scale the block by 2x'");
                    publish("   - 'add a coin at position 100, 50, 0' or 'add 5 coins at 100 | 50 | 0'");
                    publish("   - 'add 5 coins above RedBlueExStepA' or 'add coins near Goomba'");
                    
                    publish("Note: " + e.getUserFriendlyMessage());
                    
                } catch (Exception e) {
                    // Instead of showing error dialog, provide helpful response
                    publish("I encountered a technical issue, but I'm still here to help!");
                    publish("Try these commands:");
                    publish("   - 'move the Goomba 10 units right'");
                    publish("   - 'rotate the platform 45 degrees'");
                    publish("   - 'scale the block by 2x'");
                    publish("   - 'add a coin at position 100, 50, 0' or 'add 5 coins at 100 | 50 | 0'");
                    publish("   - 'add 5 coins above RedBlueExStepA' or 'add coins near Goomba'");
                    publish("Note: There was a technical issue, but you can still try your commands!");
                }
                
                return null;
            }
            
            @Override
            protected void process(List<String> chunks) {
                for (String message : chunks) {
                    appendStatus(message);
                }
            }
            
            @Override
            protected void done() {
                // Mark timeout monitor as completed
                if (timeoutMonitor != null) {
                    timeoutMonitor.markCompleted();
                }
                
                // Reset UI state
                currentTask = null;
                btnExecute.setEnabled(true);
                btnCancel.setEnabled(false);
                progressBar.setVisible(false);
                progressBar.setIndeterminate(false);
                
                // Clear command field only if not cancelled
                if (!isCancelled()) {
                    txtCommand.setText("");
                }
                txtCommand.requestFocus();
                
                // Update connection status
                updateConnectionStatus();
                
                appendStatus("Ready for next command.\n");
            }
        };
        
        currentTask.execute();
    }
    
    /**
     * Validates a command before processing.
     */
    private List<String> validateCommand(String command) {
        List<String> errors = new ArrayList<>();
        
        if (command == null || command.trim().isEmpty()) {
            errors.add("Command cannot be empty");
            return errors;
        }
        
        String trimmed = command.trim();
        
        // Only block completely empty commands (additional empty-check happens earlier).
        if (trimmed.isEmpty()) {
            errors.add("Command cannot be empty");
        }
        
        return errors;
    }
    
    /**
     * Checks if command contains an action word.
     */
    private boolean containsAction(String command) {
        String lower = command.toLowerCase();
        String[] actions = {"add", "move", "rotate", "scale", "change", "set", "delete", "copy", "hide", "show", "turn"};
        
        for (String action : actions) {
            if (lower.contains(action)) {
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * Checks if command contains object reference.
     */
    private boolean containsObject(String command) {
        String lower = command.toLowerCase();
        
        // Check for common object indicators
        return lower.contains("the ") || lower.contains("all ") || 
               lower.matches(".*\\b(goomba|koopa|coin|star|platform|block|pipe|enemy|enemies)\\b.*");
    }
    
    /**
     * Handles the result of error handling dialogs.
     */
    private void handleErrorResult(ErrorHandler.ErrorResult result, String originalCommand) {
        switch (result.getAction()) {
            case RETRY:
                // Put the command back in the input field for retry
                txtCommand.setText(originalCommand);
                txtCommand.requestFocus();
                break;
                
            case OPEN_SETTINGS:
                // TODO: Open settings dialog - would need reference to main application
                appendStatus("Please open Settings from the main menu to configure AI providers");
                break;
                
            case ACKNOWLEDGE:
            case CANCEL:
            default:
                // Just acknowledge the error
                break;
        }
    }
    
    private void cancelCommand() {
        if (currentTask != null) {
            currentTask.cancel(true);
            appendStatus("✗ Command cancelled by user");
            
            // Reset UI state immediately
            btnExecute.setEnabled(true);
            btnCancel.setEnabled(false);
            progressBar.setVisible(false);
            progressBar.setIndeterminate(false);
            
            currentTask = null;
        }
    }
    
    private void appendStatus(String message) {
        SwingUtilities.invokeLater(() -> {
            txtStatus.append(message + "\n");
            txtStatus.setCaretPosition(txtStatus.getDocument().getLength());
        });
    }
    
    private void addToHistory(String command) {
        // Remove if already exists to avoid duplicates
        commandHistory.remove(command);
        
        // Add to end
        commandHistory.add(command);
        
        // Limit history size
        if (commandHistory.size() > 50) {
            commandHistory.remove(0);
        }
        
        // Update UI
        historyModel.clear();
        for (int i = commandHistory.size() - 1; i >= 0; i--) {
            historyModel.addElement(commandHistory.get(i));
        }
        
        // Reset history navigation
        historyIndex = -1;
        
        // Save to settings
        saveCommandHistory();
    }
    
    private void updateSuggestions() {
        suggestions.clear();
        
        String currentText = txtCommand.getText().toLowerCase().trim();
        
        // If empty, show most common commands first
        if (currentText.isEmpty()) {
            suggestions.add("move the {object} {distance} units {direction}");
            suggestions.add("rotate {object} {degrees} degrees");
            suggestions.add("scale {object} by {factor}x");
            suggestions.add("change {object} color to {color}");
            suggestions.add("move all {type} {distance} units {direction}");
            return;
        }
        
        // Smart matching based on command start
        if (currentText.startsWith("move")) {
            addMatchingSuggestions(currentText, new String[]{
                "move the {object} {distance} units {direction}",
                "move all {type} {distance} units {direction}",
                "move {object} to position {x}, {y}, {z}",
                "move {object} near the {reference}"
            });
        } else if (currentText.startsWith("rotate") || currentText.startsWith("turn") || currentText.startsWith("spin")) {
            addMatchingSuggestions(currentText, new String[]{
                "rotate {object} {degrees} degrees",
                "rotate all {type} {degrees} degrees",
                "turn {object} around",
                "spin {object} {degrees} degrees"
            });
        } else if (currentText.startsWith("scale") || currentText.startsWith("resize") || currentText.startsWith("make")) {
            addMatchingSuggestions(currentText, new String[]{
                "scale {object} by {factor}x",
                "make {object} {factor} times bigger",
                "resize {object} to {factor}x",
                "make all {type} twice as big"
            });
        } else if (currentText.startsWith("change") || currentText.startsWith("set")) {
            addMatchingSuggestions(currentText, new String[]{
                "change {object} color to {color}",
                "set {object} {property} to {value}",
                "change all {type} {property} to {value}"
            });
        } else {
            // General matching for all templates
            for (String template : COMMAND_TEMPLATES) {
                if (template.toLowerCase().contains(currentText)) {
                    suggestions.add(template);
                }
            }
        }
        
        // Add recent commands that match
        for (String cmd : commandHistory) {
            if (cmd.toLowerCase().contains(currentText) && !suggestions.contains(cmd)) {
                suggestions.add(cmd);
                if (suggestions.size() >= 15) break; // Limit suggestions
            }
        }
        
        // Add contextual suggestions based on current galaxy objects
        addContextualSuggestions(currentText);
    }
    
    private void addMatchingSuggestions(String currentText, String[] templates) {
        for (String template : templates) {
            if (template.toLowerCase().contains(currentText)) {
                suggestions.add(template);
            }
        }
    }
    
    private void addContextualSuggestions(String currentText) {
        // This could be enhanced to suggest actual object names from the current galaxy
        // For now, we'll add common object type suggestions
        if (currentText.contains("all ") || currentText.contains("the ")) {
            for (String type : OBJECT_TYPES) {
                if (type.contains(currentText.replaceAll(".*\\b(all|the)\\s+", ""))) {
                    String suggestion = currentText.replaceAll("\\{type\\}", type);
                    if (!suggestions.contains(suggestion)) {
                        suggestions.add(suggestion);
                    }
                }
            }
        }
    }
    
    private void showSuggestions() {
        updateSuggestions();
        
        if (suggestions.isEmpty()) return;
        
        if (suggestionPopup == null) {
            suggestionPopup = new JPopupMenu();
        }
        
        suggestionPopup.removeAll();
        
        for (String suggestion : suggestions.subList(0, Math.min(suggestions.size(), 10))) {
            JMenuItem item = new JMenuItem(suggestion);
            item.addActionListener(e -> {
                txtCommand.setText(suggestion);
                txtCommand.setCaretPosition(suggestion.length());
                hideSuggestions();
                txtCommand.requestFocus();
            });
            suggestionPopup.add(item);
        }
        
        Point location = txtCommand.getLocationOnScreen();
        suggestionPopup.show(txtCommand, 0, txtCommand.getHeight());
    }
    
    private void hideSuggestions() {
        if (suggestionPopup != null && suggestionPopup.isVisible()) {
            suggestionPopup.setVisible(false);
        }
    }
    
    private void loadCommandHistory() {
        commandHistory.clear();
        historyModel.clear();
        
        String historyStr = java.util.prefs.Preferences.userRoot().get("whitehole_aiCommandHistory", "");
        if (!historyStr.isEmpty()) {
            String[] commands = historyStr.split("\n");
            for (String command : commands) {
                if (!command.trim().isEmpty()) {
                    commandHistory.add(command);
                }
            }
            
            // Update UI (show most recent first)
            for (int i = commandHistory.size() - 1; i >= 0; i--) {
                historyModel.addElement(commandHistory.get(i));
            }
        }
    }
    
    private void saveCommandHistory() {
        // Save command history to preferences
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < commandHistory.size(); i++) {
            if (i > 0) sb.append("\n");
            sb.append(commandHistory.get(i));
        }
        java.util.prefs.Preferences.userRoot().put("whitehole_aiCommandHistory", sb.toString());
    }
    
    private void loadFavoriteCommands() {
        favoriteCommands.clear();
        favoritesModel.clear();
        
        String favoritesStr = java.util.prefs.Preferences.userRoot().get("whitehole_aiFavoriteCommands", "");
        if (!favoritesStr.isEmpty()) {
            String[] favorites = favoritesStr.split("\n");
            for (String favorite : favorites) {
                if (!favorite.trim().isEmpty()) {
                    favoriteCommands.add(favorite);
                    favoritesModel.addElement(favorite);
                }
            }
        }
    }
    
    private void saveFavoriteCommands() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < favoriteCommands.size(); i++) {
            if (i > 0) sb.append("\n");
            sb.append(favoriteCommands.get(i));
        }
        java.util.prefs.Preferences.userRoot().put("whitehole_aiFavoriteCommands", sb.toString());
    }
    

    

    
    private void addToFavorites() {
        String selected = listHistory.getSelectedValue();
        if (selected != null && !favoriteCommands.contains(selected)) {
            favoriteCommands.add(selected);
            favoritesModel.addElement(selected);
            saveFavoriteCommands();
            appendStatus("Added to favorites: " + selected);
        }
    }
    
    private void removeFromFavorites() {
        String selected = listFavorites.getSelectedValue();
        if (selected != null) {
            favoriteCommands.remove(selected);
            favoritesModel.removeElement(selected);
            saveFavoriteCommands();
            appendStatus("Removed from favorites: " + selected);
        }
    }
    
    private void clearHistory() {
        int result = JOptionPane.showConfirmDialog(this, 
            "Are you sure you want to clear the command history?", 
            "Clear History", 
            JOptionPane.YES_NO_OPTION);
        
        if (result == JOptionPane.YES_OPTION) {
            commandHistory.clear();
            historyModel.clear();
            saveCommandHistory();
            appendStatus("Command history cleared");
        }
    }
    
    private void clearFavorites() {
        int result = JOptionPane.showConfirmDialog(this, 
            "Are you sure you want to clear all favorite commands?", 
            "Clear Favorites", 
            JOptionPane.YES_NO_OPTION);
        
        if (result == JOptionPane.YES_OPTION) {
            favoriteCommands.clear();
            favoritesModel.clear();
            saveFavoriteCommands();
            appendStatus("Favorite commands cleared");
        }
    }
    
    private void startConnectionStatusMonitoring() {
        // Update connection status every 30 seconds
        connectionStatusTimer = new javax.swing.Timer(30000, e -> updateConnectionStatus());
        connectionStatusTimer.start();
    }
    
    private void stopConnectionStatusMonitoring() {
        if (connectionStatusTimer != null) {
            connectionStatusTimer.stop();
            connectionStatusTimer = null;
        }
    }
    
    /**
     * Updates the panel when the galaxy context changes
     */
    public void refreshContext() {
        updateConnectionStatus();
        // Update status to show current context
        SwingUtilities.invokeLater(() -> {
            appendStatus("Galaxy context updated - ready for commands");
        });
    }
    
    /**
     * Clears the status display
     */
    public void clearStatus() {
        txtStatus.setText("Ready. Enter a command above to get started.");
    }
    
    /**
     * Sets focus to the command input field
     */
    public void focusCommandInput() {
        txtCommand.requestFocus();
    }
    
    /**
     * Gets or creates a CommandExecutor instance with current editor state
     */
    private CommandExecutor getCommandExecutor() {
        return CommandExecutorIntegration.createFromEditorForm(editorForm);
    }

    // removed old placeholder
    /*private CommandExecutor getCommandExecutorOld() {
        // TODO: This needs to be properly implemented to get the actual
        // globalObjList and propertyGrid from the GalaxyEditorForm
        // For now, we'll return null to indicate the feature is not fully integrated
        return null;
    }
    */
    
    /**
     * Generates a user-friendly description of a transformation.
     */
    private String getTransformationDescription(ObjectTransformation transform) {
        switch (transform.getType()) {
            case ADD:
                return "Added " + transform.getAddObjectType() + " at position " + 
                       formatVector(transform.getVectorValue());
            case TRANSLATE:
                return "Moved object " + transform.getObjectId() + " by " + 
                       formatVector(transform.getVectorValue());
            case ROTATE:
                return "Rotated object " + transform.getObjectId() + " by " + 
                       formatVector(transform.getVectorValue()) + " degrees";
            case SCALE:
                return "Scaled object " + transform.getObjectId() + " by factor " + 
                       formatVector(transform.getVectorValue());
            case SET_POSITION:
                return "Set position of object " + transform.getObjectId() + " to " + 
                       formatVector(transform.getVectorValue());
            case SET_ROTATION:
                return "Set rotation of object " + transform.getObjectId() + " to " + 
                       formatVector(transform.getVectorValue()) + " degrees";
            case SET_SCALE:
                return "Set scale of object " + transform.getObjectId() + " to " + 
                       formatVector(transform.getVectorValue());
            case PROPERTY_CHANGE:
                return "Changed properties of object " + transform.getObjectId() + 
                       " (" + transform.getPropertyChanges().size() + " properties)";
            default:
                return "Applied " + transform.getType() + " to object " + transform.getObjectId();
        }
    }
    
    /**
     * Formats a vector for display.
     */
    private String formatVector(Vec3f vec) {
        if (vec == null) return "unknown";
        return String.format("(%.1f, %.1f, %.1f)", vec.x, vec.y, vec.z);
    }
    
    /**
     * Cleanup method to be called when the panel is disposed.
     */
    public void cleanup() {
        stopConnectionStatusMonitoring();
        if (currentTask != null && !currentTask.isDone()) {
            currentTask.cancel(true);
        }
    }
    
    /**
     * Gets the current provider status for external use.
     */
    public String getCurrentProviderStatusText() {
        AIProviderManager manager = AIProviderManager.getInstance();
        return String.format("%s: %s", 
            manager.getCurrentProviderName(),
            manager.isCurrentProviderAvailable() ? "Available" : "Unavailable");
    }
    

    
    /**
     * Adds a command to favorites programmatically.
     */
    public void addCommandToFavorites(String command) {
        if (command != null && !command.trim().isEmpty() && !favoriteCommands.contains(command)) {
            favoriteCommands.add(command);
            favoritesModel.addElement(command);
            saveFavoriteCommands();
        }
    }
    
    /**
     * Gets the list of favorite commands.
     */
    public List<String> getFavoriteCommands() {
        return new ArrayList<>(favoriteCommands);
    }
    
    /**
     * Gets the command history.
     */
    public List<String> getCommandHistory() {
        return new ArrayList<>(commandHistory);
    }
}
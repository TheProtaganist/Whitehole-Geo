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

import javax.swing.*;
import java.awt.*;
import java.util.List;
import java.util.ArrayList;

/**
 * ErrorHandler provides comprehensive error handling and user feedback for AI operations.
 * It handles different types of errors with appropriate user messages and recovery suggestions.
 */
public class ErrorHandler {
    
    /**
     * Handles AI provider exceptions with user-friendly messages and recovery options.
     */
    public static ErrorResult handleAIProviderError(AIProviderException exception, Component parent) {
        String title = "AI Provider Error";
        String message = exception.getUserFriendlyMessage();
        List<String> suggestions = new ArrayList<>();
        
        switch (exception.getErrorType()) {
            case CONFIGURATION_ERROR:
                suggestions.add("Check your AI provider settings in the Settings menu");
                suggestions.add("Ensure your API key is correctly entered");
                suggestions.add("Verify the server URL is correct (for Ollama)");
                return showErrorWithSuggestions(parent, title, message, suggestions, true);
                
            case NETWORK_ERROR:
                suggestions.add("Check your internet connection");
                suggestions.add("Try again in a few moments");
                suggestions.add("Check if the AI service is accessible");
                return showErrorWithSuggestions(parent, title, message, suggestions, false);
                
            case AUTHENTICATION_ERROR:
                suggestions.add("Verify your API key is valid and not expired");
                suggestions.add("Check if your account has sufficient credits");
                suggestions.add("Re-enter your API key in Settings");
                return showErrorWithSuggestions(parent, title, message, suggestions, true);
                
            case RATE_LIMIT_ERROR:
                suggestions.add("Wait a few minutes before trying again");
                suggestions.add("Consider upgrading your API plan");
                suggestions.add("Try using a different AI provider");
                return showErrorWithSuggestions(parent, title, message, suggestions, false);
                
            case SERVICE_UNAVAILABLE:
                suggestions.add("Try again in a few minutes");
                suggestions.add("Check the AI provider's status page");
                suggestions.add("Switch to a different AI provider if available");
                return showErrorWithSuggestions(parent, title, message, suggestions, false);
                
            case TIMEOUT_ERROR:
                suggestions.add("Try a simpler command");
                suggestions.add("Check your network connection");
                suggestions.add("The AI service may be experiencing high load");
                return showErrorWithSuggestions(parent, title, message, suggestions, false);
                
            case INVALID_RESPONSE:
                suggestions.add("Try rephrasing your command");
                suggestions.add("Use simpler language");
                suggestions.add("Try again - this may be a temporary issue");
                return showErrorWithSuggestions(parent, title, message, suggestions, false);
                
            default:
                suggestions.add("Try again in a few moments");
                suggestions.add("Check your AI provider settings");
                suggestions.add("Contact support if the problem persists");
                return showErrorWithSuggestions(parent, title, message, suggestions, false);
        }
    }
    
    /**
     * Handles command validation errors with helpful suggestions.
     */
    public static ErrorResult handleCommandValidationError(String command, List<String> errors, Component parent) {
        String title = "Command Validation Error";
        StringBuilder message = new StringBuilder("The command could not be processed:\n\n");
        
        for (String error : errors) {
                            message.append("- ").append(error).append("\n");
        }
        
        List<String> suggestions = generateCommandSuggestions(command);
        
        return showErrorWithSuggestions(parent, title, message.toString(), suggestions, false);
    }
    
    /**
     * Handles timeout errors with cancellation options.
     */
    public static ErrorResult handleTimeoutError(String operation, Component parent) {
        String title = "Operation Timeout";
        String message = "The " + operation + " operation is taking longer than expected.\n\n" +
                        "This may be due to:\n" +
                                    "- High server load\n" +
            "- Network connectivity issues\n" +
            "- Complex command processing\n\n" +
                        "Would you like to continue waiting or cancel the operation?";
        
        int choice = JOptionPane.showOptionDialog(
            parent,
            message,
            title,
            JOptionPane.YES_NO_CANCEL_OPTION,
            JOptionPane.WARNING_MESSAGE,
            null,
            new String[]{"Continue Waiting", "Cancel Operation", "Retry"},
            "Continue Waiting"
        );
        
        switch (choice) {
            case 0: return ErrorResult.continueOperation();
            case 1: return ErrorResult.cancelOperation();
            case 2: return ErrorResult.retryOperation();
            default: return ErrorResult.cancelOperation();
        }
    }
    
    /**
     * Shows an error dialog with suggestions and recovery options.
     */
    private static ErrorResult showErrorWithSuggestions(Component parent, String title, String message, 
                                                       List<String> suggestions, boolean showSettings) {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        
        // Error message
        JTextArea messageArea = new JTextArea(message);
        messageArea.setEditable(false);
        messageArea.setOpaque(false);
        messageArea.setFont(UIManager.getFont("Label.font"));
        panel.add(messageArea, BorderLayout.NORTH);
        
        // Suggestions
        if (!suggestions.isEmpty()) {
            JPanel suggestionPanel = new JPanel(new BorderLayout());
            suggestionPanel.setBorder(BorderFactory.createTitledBorder("Suggestions"));
            
            StringBuilder suggestionText = new StringBuilder();
            for (String suggestion : suggestions) {
                suggestionText.append("- ").append(suggestion).append("\n");
            }
            
            JTextArea suggestionArea = new JTextArea(suggestionText.toString());
            suggestionArea.setEditable(false);
            suggestionArea.setOpaque(false);
            suggestionArea.setFont(UIManager.getFont("Label.font"));
            suggestionPanel.add(suggestionArea, BorderLayout.CENTER);
            
            panel.add(suggestionPanel, BorderLayout.CENTER);
        }
        
        // Buttons
        List<String> buttonOptions = new ArrayList<>();
        buttonOptions.add("OK");
        if (showSettings) {
            buttonOptions.add("Open Settings");
        }
        buttonOptions.add("Retry");
        
        int choice = JOptionPane.showOptionDialog(
            parent,
            panel,
            title,
            JOptionPane.DEFAULT_OPTION,
            JOptionPane.ERROR_MESSAGE,
            null,
            buttonOptions.toArray(new String[0]),
            "OK"
        );
        
        if (showSettings && choice == 1) {
            return ErrorResult.openSettings();
        } else if ((showSettings && choice == 2) || (!showSettings && choice == 1)) {
            return ErrorResult.retryOperation();
        } else {
            return ErrorResult.acknowledgeError();
        }
    }
    
    /**
     * Generates helpful command suggestions based on the failed command.
     */
    private static List<String> generateCommandSuggestions(String command) {
        List<String> suggestions = new ArrayList<>();
        String lowerCommand = command.toLowerCase();
        
        // General suggestions
        suggestions.add("Use simple, clear language");
        suggestions.add("Specify object names or types clearly");
        
        // Specific suggestions based on command content
        if (lowerCommand.contains("move") || lowerCommand.contains("position")) {
            suggestions.add("Try: 'move the [object] [distance] units [direction]'");
            suggestions.add("Example: 'move the Goomba 10 units right'");
            suggestions.add("Use directions: right, left, up, down, forward, backward");
        }
        
        if (lowerCommand.contains("rotate") || lowerCommand.contains("turn")) {
            suggestions.add("Try: 'rotate [object] [degrees] degrees'");
            suggestions.add("Example: 'rotate the platform 45 degrees'");
        }
        
        if (lowerCommand.contains("scale") || lowerCommand.contains("size")) {
            suggestions.add("Try: 'scale [object] by [factor]x'");
            suggestions.add("Example: 'scale the block by 2x'");
        }
        
        if (lowerCommand.contains("color") || lowerCommand.contains("change")) {
            suggestions.add("Try: 'change [object] color to [color]'");
            suggestions.add("Example: 'change the Koopa color to red'");
        }
        
        // Object reference suggestions
        if (!lowerCommand.contains("the ") && !lowerCommand.contains("all ")) {
            suggestions.add("Use 'the' before object names: 'the Goomba'");
            suggestions.add("Use 'all' for multiple objects: 'all coins'");
        }
        
        return suggestions;
    }
    
    /**
     * Result of error handling indicating what action should be taken.
     */
    public static class ErrorResult {
        public enum Action {
            ACKNOWLEDGE, RETRY, CANCEL, CONTINUE, OPEN_SETTINGS
        }
        
        private final Action action;
        private final String message;
        
        private ErrorResult(Action action, String message) {
            this.action = action;
            this.message = message;
        }
        
        public Action getAction() {
            return action;
        }
        
        public String getMessage() {
            return message;
        }
        
        public static ErrorResult acknowledgeError() {
            return new ErrorResult(Action.ACKNOWLEDGE, "Error acknowledged");
        }
        
        public static ErrorResult retryOperation() {
            return new ErrorResult(Action.RETRY, "Retry requested");
        }
        
        public static ErrorResult cancelOperation() {
            return new ErrorResult(Action.CANCEL, "Operation cancelled");
        }
        
        public static ErrorResult continueOperation() {
            return new ErrorResult(Action.CONTINUE, "Continue operation");
        }
        
        public static ErrorResult openSettings() {
            return new ErrorResult(Action.OPEN_SETTINGS, "Open settings requested");
        }
    }
}
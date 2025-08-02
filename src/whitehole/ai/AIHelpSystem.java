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
import javax.swing.tree.DefaultMutableTreeNode;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.List;

/**
 * AI Help System providing documentation, examples, and troubleshooting guidance
 */
public class AIHelpSystem {
    
    public static class CommandExample {
        public final String command;
        public final String description;
        public final String category;
        
        public CommandExample(String command, String description, String category) {
            this.command = command;
            this.description = description;
            this.category = category;
        }
    }
    
    private static final List<CommandExample> COMMAND_EXAMPLES = new ArrayList<>();
    
    static {
        // Movement commands
        COMMAND_EXAMPLES.add(new CommandExample(
            "move the Goomba 10 units right",
            "Moves a specific object by a distance in a direction",
            "Movement"
        ));
        COMMAND_EXAMPLES.add(new CommandExample(
            "move all coins 5 units up",
            "Moves all objects of a type in the same direction",
            "Movement"
        ));
        COMMAND_EXAMPLES.add(new CommandExample(
            "move the platform to position 100, 0, 50",
            "Moves an object to specific coordinates",
            "Movement"
        ));
        
        // Rotation commands
        COMMAND_EXAMPLES.add(new CommandExample(
            "rotate the block 45 degrees",
            "Rotates an object by a specific angle",
            "Rotation"
        ));
        COMMAND_EXAMPLES.add(new CommandExample(
            "rotate all enemies 90 degrees clockwise",
            "Rotates multiple objects with direction specification",
            "Rotation"
        ));
        
        // Scaling commands
        COMMAND_EXAMPLES.add(new CommandExample(
            "scale the platform by 1.5x",
            "Changes the size of an object by a factor",
            "Scaling"
        ));
        COMMAND_EXAMPLES.add(new CommandExample(
            "make the Koopa twice as big",
            "Natural language scaling command",
            "Scaling"
        ));
        
        // Property changes
        COMMAND_EXAMPLES.add(new CommandExample(
            "change the Koopa's color to red",
            "Modifies object properties like color",
            "Properties"
        ));
        COMMAND_EXAMPLES.add(new CommandExample(
            "set the switch to activated",
            "Changes object state or properties",
            "Properties"
        ));
        
        // Object creation
        COMMAND_EXAMPLES.add(new CommandExample(
            "add a coin at position 100, 50, 0",
            "Creates a new object at specific coordinates",
            "Object Creation"
        ));
        COMMAND_EXAMPLES.add(new CommandExample(
            "add 3 coins above the platform",
            "Creates multiple objects with relative positioning",
            "Object Creation"
        ));
        
        // Batch operations
        COMMAND_EXAMPLES.add(new CommandExample(
            "move all objects near the start point 10 units left",
            "Performs operations on multiple objects based on location",
            "Batch Operations"
        ));
        COMMAND_EXAMPLES.add(new CommandExample(
            "rotate all platforms in this zone 180 degrees",
            "Applies transformations to all objects of a type",
            "Batch Operations"
        ));
    }
    
    public static void showHelpDialog(Component parent) {
        JDialog helpDialog = new JDialog((Frame) SwingUtilities.getWindowAncestor(parent), 
                                       "AI Command Help", true);
        helpDialog.setSize(800, 600);
        helpDialog.setLocationRelativeTo(parent);
        
        JTabbedPane tabbedPane = new JTabbedPane();
        
        // Command Examples Tab
        tabbedPane.addTab("Command Examples", createCommandExamplesPanel());
        
        // Syntax Guide Tab
        tabbedPane.addTab("Syntax Guide", createSyntaxGuidePanel());
        
        // Troubleshooting Tab
        tabbedPane.addTab("Troubleshooting", createTroubleshootingPanel());
        
        // Configuration Tab
        tabbedPane.addTab("Configuration", createConfigurationPanel());
        
        helpDialog.add(tabbedPane);
        helpDialog.setVisible(true);
    }
    
    private static JPanel createCommandExamplesPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        
        // Create categorized examples
        DefaultMutableTreeNode root = new DefaultMutableTreeNode("AI Commands");
        
        // Group examples by category
        java.util.Map<String, DefaultMutableTreeNode> categoryNodes = new java.util.HashMap<>();
        
        for (CommandExample example : COMMAND_EXAMPLES) {
            DefaultMutableTreeNode categoryNode = categoryNodes.get(example.category);
            if (categoryNode == null) {
                categoryNode = new DefaultMutableTreeNode(example.category);
                categoryNodes.put(example.category, categoryNode);
                root.add(categoryNode);
            }
            
            DefaultMutableTreeNode exampleNode = new DefaultMutableTreeNode(example);
            categoryNode.add(exampleNode);
        }
        
        JTree tree = new JTree(root);
        tree.setRootVisible(false);
        tree.setShowsRootHandles(true);
        
        // Expand all categories
        for (int i = 0; i < tree.getRowCount(); i++) {
            tree.expandRow(i);
        }
        
        JTextArea detailArea = new JTextArea();
        detailArea.setEditable(false);
        detailArea.setWrapStyleWord(true);
        detailArea.setLineWrap(true);
        detailArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        detailArea.setText("Select a command example from the tree to see details.");
        
        tree.addTreeSelectionListener(e -> {
            DefaultMutableTreeNode node = (DefaultMutableTreeNode) tree.getLastSelectedPathComponent();
            if (node != null && node.getUserObject() instanceof CommandExample) {
                CommandExample example = (CommandExample) node.getUserObject();
                detailArea.setText(String.format(
                    "Command: %s\n\nDescription: %s\n\nCategory: %s\n\n" +
                    "You can copy this command and modify it for your needs. " +
                    "Replace object names and values with your specific requirements.",
                    example.command, example.description, example.category
                ));
            }
        });
        
        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                                            new JScrollPane(tree),
                                            new JScrollPane(detailArea));
        splitPane.setDividerLocation(300);
        
        panel.add(splitPane, BorderLayout.CENTER);
        
        return panel;
    }
    
    private static JPanel createSyntaxGuidePanel() {
        JPanel panel = new JPanel(new BorderLayout());
        
        JTextArea syntaxArea = new JTextArea();
        syntaxArea.setEditable(false);
        syntaxArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        syntaxArea.setText(getSyntaxGuideText());
        
        panel.add(new JScrollPane(syntaxArea), BorderLayout.CENTER);
        
        return panel;
    }
    
    private static JPanel createTroubleshootingPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        
        JTextArea troubleshootingArea = new JTextArea();
        troubleshootingArea.setEditable(false);
        troubleshootingArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        troubleshootingArea.setText(getTroubleshootingText());
        
        panel.add(new JScrollPane(troubleshootingArea), BorderLayout.CENTER);
        
        return panel;
    }
    
    private static JPanel createConfigurationPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        
        JTextArea configArea = new JTextArea();
        configArea.setEditable(false);
        configArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        configArea.setText(getConfigurationText());
        
        panel.add(new JScrollPane(configArea), BorderLayout.CENTER);
        
        return panel;
    }
    
    private static String getSyntaxGuideText() {
        return """
AI Command Syntax Guide
======================

Basic Command Structure:
[ACTION] [TARGET] [PARAMETERS]

Actions:
- move, translate, position
- rotate, turn, spin
- scale, resize, make bigger/smaller
- change, set, modify

Targets:
- Specific objects: "the Goomba", "the red platform"
- Object types: "all coins", "enemies", "platforms"
- Positional references: "objects near the start", "the leftmost block"
- Multiple objects: "all objects in this area"

Parameters:
- Distances: "10 units", "5 meters", "100 pixels"
- Directions: "left", "right", "up", "down", "forward", "backward"
- Angles: "45 degrees", "90 degrees clockwise", "half a turn"
- Scales: "2x", "1.5 times", "twice as big", "half the size"
- Coordinates: "to position 100, 0, 50"
- Properties: "color to red", "state to activated"

Natural Language Support:
The AI understands natural language, so you can use phrases like:
- "Make the platform bigger"
- "Turn the enemy around"
- "Put the coin above the block"
- "Shrink all the obstacles"

Tips:
- Be specific about which objects you want to modify
- Use clear directional terms (left/right, up/down)
- Specify units when possible (units, degrees)
- If multiple objects match, the AI will ask for clarification
""";
    }
    
    private static String getTroubleshootingText() {
        return """
AI Integration Troubleshooting Guide
===================================

Common Issues and Solutions:

1. "AI Provider Unavailable" Error
   Problem: Cannot connect to AI service
   Solutions:
   - Check your internet connection
   - Verify API key is correct (for Gemini)
   - Ensure Ollama server is running (for Ollama)
   - Check firewall settings
   - Try switching AI providers in settings

2. "Object Not Found" Error
   Problem: AI cannot identify the target object
   Solutions:
   - Use more specific object names
   - Try referring to objects by type instead of name
   - Use positional references ("the leftmost coin")
   - Check if the object exists in the current galaxy

3. "Invalid Command" Error
   Problem: AI cannot understand the command
   Solutions:
   - Use simpler, more direct language
   - Follow the syntax guide patterns
   - Break complex commands into smaller steps
   - Check the command examples for reference

4. "Transformation Failed" Error
   Problem: The requested change cannot be applied
   Solutions:
   - Check if the transformation violates object constraints
   - Ensure the target position is within valid boundaries
   - Verify the object supports the requested property change
   - Try smaller incremental changes

5. Slow Response Times
   Problem: AI takes too long to respond
   Solutions:
   - Check network connection speed
   - Try using a local Ollama instance
   - Reduce galaxy complexity if possible
   - Consider using batch operations for multiple changes

6. Unexpected Results
   Problem: AI performs wrong actions
   Solutions:
   - Be more specific in your commands
   - Use object names instead of generic terms
   - Verify the current galaxy context
   - Check the undo history to revert changes

Configuration Issues:

Gemini API Setup:
- Obtain API key from Google AI Studio
- Enter key in Settings > AI Configuration
- Test connection using the "Test" button
- Ensure you have sufficient API quota

Ollama Setup:
- Install Ollama on your system
- Start Ollama server (usually localhost:11434)
- Download required models (llama2, codellama, etc.)
- Configure server URL in settings
- Test connection to verify setup

Getting Help:
- Use the command examples as templates
- Start with simple commands and build complexity
- Check the syntax guide for proper formatting
- Join the Discord community for additional support
""";
    }
    
    private static String getConfigurationText() {
        return """
AI Configuration Guide
=====================

Setting Up AI Providers:

Gemini (Google AI):
1. Go to https://makersuite.google.com/app/apikey
2. Create a new API key
3. Copy the key to Settings > AI Configuration > Gemini API Key
4. Select your preferred model (gemini-1.5-flash recommended)
5. Click "Test Connection" to verify setup

Ollama (Local AI):
1. Download and install Ollama from https://ollama.ai
2. Start Ollama server: ollama serve
3. Download a model: ollama pull llama2
4. Configure server URL in Settings (default: http://localhost:11434)
5. Select your downloaded model
6. Test connection to verify setup

Provider Selection:
- Gemini: Better accuracy, requires internet and API key
- Ollama: Works offline, requires local setup and more resources

Recommended Settings:
- For best results: Use Gemini with gemini-1.5-flash model
- For privacy: Use Ollama with llama2 or codellama model
- For speed: Use smaller models like gemini-1.5-flash or llama2:7b

Security Notes:
- API keys are stored securely in system preferences
- Galaxy data sent to AI is minimal (object names, positions, types)
- No game assets or copyrighted content is transmitted
- Local Ollama processing keeps all data on your machine

Performance Tips:
- Gemini is generally faster for complex commands
- Ollama performance depends on your hardware
- Larger models provide better accuracy but slower responses
- Consider your internet speed when choosing providers

Troubleshooting Configuration:
- Verify API keys don't have extra spaces
- Check firewall settings for Ollama
- Ensure Ollama models are properly downloaded
- Test with simple commands first
- Check the troubleshooting tab for specific error solutions
""";
    }
    
    public static String getQuickHelpText() {
        return """
Quick AI Command Help:

Basic Commands:
- add [object] at [position]
- move [object] [distance] [direction]
- rotate [object] [degrees] degrees
- scale [object] by [factor]x
- change [object] [property] to [value]

Examples:
- add a coin at position 100, 50, 0
- move the Goomba 10 units right
- rotate all coins 45 degrees
- scale the platform by 1.5x
- change the Koopa color to red

Tips:
- Be specific about object names
- Use clear directions (left, right, up, down)
- Try "all [type]" for multiple objects
- Use Ctrl+Z to undo changes

Click the Help button for detailed documentation.
""";
    }
}
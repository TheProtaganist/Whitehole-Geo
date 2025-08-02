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

import whitehole.math.Vec3f;
import org.json.JSONObject;
import org.json.JSONArray;
import org.json.JSONException;
import java.awt.Component;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

/**
 * CommandParser processes natural language commands by sending them to AI providers
 * with galaxy context and parsing the structured responses into CommandResult objects.
 */
public class CommandParser {
    
    private final AIProviderManager providerManager;
    private final GalaxyContextManager contextManager;
    private final OptimizedAIResponseProcessor responseProcessor;
    
    // Patterns for fallback parsing if AI response is malformed
    private static final Pattern MOVE_PATTERN = Pattern.compile(
        "move\\s+(?:the\\s+)?(.+?)\\s+(\\d+(?:\\.\\d+)?)\\s+units?\\s+(?:to\\s+the\\s+)?(right|left|up|down|forward|backward)",
        Pattern.CASE_INSENSITIVE
    );
    
    private static final Pattern ROTATE_PATTERN = Pattern.compile(
        "rotate\\s+(?:all\\s+)?(.+?)\\s+(\\d+(?:\\.\\d+)?)\\s+degrees?",
        Pattern.CASE_INSENSITIVE
    );
    
    private static final Pattern SCALE_PATTERN = Pattern.compile(
        "scale\\s+(?:the\\s+)?(.+?)\\s+by\\s+(\\d+(?:\\.\\d+)?)x?",
        Pattern.CASE_INSENSITIVE
    );
    
    // Add object commands
    // Matches: add 5 coins at x,y,z  OR add coin  OR add 5 coins above/below/near object
    private static final Pattern ADD_PATTERN = Pattern.compile(
        "add\\s+(?:(\\d+)\\s+)?(?:an?\\s+)?([\\w\\s]+?)(?:\\s+(?:at\\s+([\\d.-]+)[,\\s\\|]\\s*([\\d.-]+)[,\\s\\|]\\s*([\\d.-]+)|(above|below|near|behind|in\\s+front\\s+of)\\s+(.+?)))?$",
        Pattern.CASE_INSENSITIVE
    );
    
    private static final Pattern POSITION_PATTERN = Pattern.compile(
        "move\\s+(.+?)\\s+to\\s+position\\s+(\\d+(?:\\.\\d+)?),?\\s*(\\d+(?:\\.\\d+)?),?\\s*(\\d+(?:\\.\\d+)?)",
        Pattern.CASE_INSENSITIVE
    );
    
    public CommandParser(AIProviderManager providerManager, GalaxyContextManager contextManager) {
        this.providerManager = providerManager;
        this.contextManager = contextManager;
        this.responseProcessor = new OptimizedAIResponseProcessor();
    }
    
    /**
     * Processes a natural language command and returns structured results.
     * 
     * @param command The natural language command to process
     * @param context The current galaxy context
     * @return CommandResult containing transformations and feedback
     */
    public CommandResult processCommand(String command, GalaxyContext context) {
        if (command == null || command.trim().isEmpty()) {
            return CommandResult.failure("Command cannot be empty", 
                generateCommandSuggestions(""));
        }
        
        if (context == null) {
            return CommandResult.failure("Galaxy context is required");
        }
        
        // Pre-validate command structure
        List<String> preValidationErrors = preValidateCommand(command, context);
        if (!preValidationErrors.isEmpty()) {
            return CommandResult.failure(preValidationErrors, 
                generateCommandSuggestions(command));
        }
        
        try {
            // Check if AI provider is available
            if (!providerManager.isCurrentProviderAvailable()) {
                // Try to get AI response even if provider seems unavailable
                try {
                    AIResponse aiResponse = providerManager.processCommand(
                        "The user said: '" + command + "'. " +
                        "I'm having trouble with my AI service, but please provide a helpful response " +
                        "explaining what they can do instead or how they could rephrase their request.", 
                        context);
                    if (aiResponse.isSuccess()) {
                        return CommandResult.failure(aiResponse.getFeedback(), 
                            List.of("Note: AI service had issues, but here's what you can try:"));
                    }
                } catch (Exception aiError) {
                    // Fallback to predefined response if AI fails
                }
                
                // Try fallback parsing if AI provider is not available
                return tryFallbackParsing(command, context, 
                    List.of("AI provider is not available: " + providerManager.getCurrentProviderStatus()));
            }
            
            // Create structured prompt for AI processing using optimized serialization
            String structuredPrompt = createOptimizedStructuredPrompt(command, context);
            
            // Send command to AI provider
            AIResponse aiResponse = providerManager.processCommand(structuredPrompt, context);
            
            if (!aiResponse.isSuccess()) {
                // Try fallback parsing if AI failed
                return tryFallbackParsing(command, context, aiResponse.getErrors());
            }
            
            // Parse AI response using optimized processor
            List<ObjectTransformation> transformations = responseProcessor.processResponse(aiResponse.getFeedback(), context);
            
            if (transformations.isEmpty()) {
                // Try fallback parsing if no transformations were extracted
                return tryFallbackParsing(command, context, List.of("No transformations found in AI response"));
            }
            
            // Validate transformations
            List<String> validationErrors = validateTransformations(transformations, context);
            if (!validationErrors.isEmpty()) {
                return CommandResult.failure(validationErrors, 
                    generateCommandSuggestions(command));
            }
            
            return CommandResult.success(transformations, 
                "Successfully processed command: " + command, 
                aiResponse.getFeedback());
            
        } catch (AIProviderException e) {
            // Try fallback parsing if AI provider failed
            return tryFallbackParsing(command, context, List.of("AI provider error: " + e.getMessage()));
        } catch (Exception e) {
            // Try to get AI response even for technical errors
            try {
                AIResponse aiResponse = providerManager.processCommand(
                    "I encountered an error processing this command: '" + command + "'. " +
                    "Please provide a helpful response explaining what the user can do instead.", 
                    context);
                if (aiResponse.isSuccess()) {
                    return CommandResult.failure(aiResponse.getFeedback(), 
                        List.of("Note: There was a technical issue, but here's what you can try:"));
                }
            } catch (Exception aiError) {
                // Fallback to predefined response if AI also fails
            }
            
            String helpfulResponse = generateHelpfulResponse(command, context);
            return CommandResult.failure(helpfulResponse, 
                List.of("Note: There was a technical issue, but I can still help you!"));
        }
    }
    
    /**
     * Pre-validates command structure before sending to AI.
     */
    /**
     * Permissive pre-validation: we only block completely empty commands (handled earlier).
     * If the command contains any common action keywords or matches ADD_PATTERN, we allow it.
     * Otherwise we still allow it – callers can decide what to do with unrelated AI responses.
     */
    private List<String> preValidateCommand(String command, GalaxyContext context) {
        List<String> errors = new ArrayList<>();
        String trimmed = command.trim().toLowerCase();

        // Immediate pass for the dedicated add regex
        if (ADD_PATTERN.matcher(trimmed).find()) {
            return errors;
        }
        // Common action words
        String[] actions = {"add", "move", "rotate", "scale", "turn", "change property", "change", "rename", "set"};
        for (String action : actions) {
            if (trimmed.contains(action)) {
                return errors;
            }
        }
        // No blocking – just return the empty error list
        return errors;
    }
    
    /**
     * Generates helpful command suggestions based on the failed command and context.
     */
    private List<String> generateCommandSuggestions(String command) {
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
        } else if (lowerCommand.contains("rotate") || lowerCommand.contains("turn")) {
            suggestions.add("Try: 'rotate [object] [degrees] degrees'");
            suggestions.add("Example: 'rotate the platform 45 degrees'");
        } else if (lowerCommand.contains("scale") || lowerCommand.contains("size")) {
            suggestions.add("Try: 'scale [object] by [factor]x'");
            suggestions.add("Example: 'scale the block by 2x'");
        } else if (lowerCommand.contains("color") || lowerCommand.contains("change")) {
            suggestions.add("Try: 'change [object] color to [color]'");
            suggestions.add("Example: 'change the Koopa color to red'");
        } else {
            // No specific action detected, provide general examples
            suggestions.add("Try: 'move the [object] [distance] units [direction]'");
            suggestions.add("Try: 'rotate [object] [degrees] degrees'");
            suggestions.add("Try: 'scale [object] by [factor]x'");
        }
        
        // Object reference suggestions
        if (!lowerCommand.contains("the ") && !lowerCommand.contains("all ")) {
            suggestions.add("Use 'the' before object names: 'the Goomba'");
            suggestions.add("Use 'all' for multiple objects: 'all coins'");
        }
        
        return suggestions;
    }
    
    /**
     * Generates a helpful AI response when a command cannot be parsed as transformations.
     */
    private String generateHelpfulResponse(String command, GalaxyContext context) {
        String lowerCommand = command.toLowerCase();
        
        // Check if it's a greeting or general question
        if (lowerCommand.contains("hello") || lowerCommand.contains("hi") || 
            lowerCommand.contains("help") || lowerCommand.contains("what can you do")) {
            return "Hey! I'm your Mario Galaxy modder assistant. I can help you move, rotate, scale, and add objects in your galaxy. " +
                   "Try commands like 'move the Goomba 10 units right' or 'add a coin at position 100, 50, 0' or 'add 5 coins above RedBlueExStepA'. " +
                   "What would you like to do?";
        }
        
        // Check if it's asking about objects
        if (lowerCommand.contains("what") && (lowerCommand.contains("object") || lowerCommand.contains("here"))) {
            String objectSummary = getAvailableObjectSummary(context);
            return "I can see " + objectSummary + " in your current galaxy. " +
                   "You can reference objects by name, type, or use 'all' for multiple objects. " +
                   "Try 'move the [object name] 5 units up' to move something!";
        }
        
        // Check if it's a move command but with wrong syntax
        if (lowerCommand.contains("move") || lowerCommand.contains("position")) {
            return "I understand you want to move something! Try using this format: 'move the [object name] [distance] units [direction]'. " +
                   "For example: 'move the Goomba 10 units right' or 'move all coins 5 units up'. " +
                   "Available directions: right, left, up, down, forward, backward.";
        }
        
        // Check if it's a rotation command
        if (lowerCommand.contains("rotate") || lowerCommand.contains("turn")) {
            return "I can help you rotate objects! Try: 'rotate the [object name] [degrees] degrees'. " +
                   "For example: 'rotate the platform 45 degrees' or 'rotate all blocks 90 degrees'.";
        }
        
        // Check if it's a scaling command
        if (lowerCommand.contains("scale") || lowerCommand.contains("size") || lowerCommand.contains("bigger") || lowerCommand.contains("smaller")) {
            return "I can help you scale objects! Try: 'scale the [object name] by [factor]x'. " +
                   "For example: 'scale the block by 2x' to make it bigger, or 'scale the Goomba by 0.5x' to make it smaller.";
        }
        
        // Check if it's an add command
        if (lowerCommand.contains("add") || lowerCommand.contains("create") || lowerCommand.contains("spawn")) {
            return "I can help you add new objects! Try: 'add a [object type] at position [x], [y], [z]'. " +
                   "For example: 'add a coin at position 100, 50, 0' or 'add 3 coins above RedBlueExStepA'. " +
                   "Available object types: Goomba, Koopa, Coin, Star, Platform, Block, Pipe, etc.";
        }
        
        // Default helpful response
        return "I'm your Mario Galaxy modder assistant! I can help you modify objects in your galaxy. " +
               "Try commands like: 'move the Goomba 10 units right', 'rotate the platform 45 degrees', " +
               "'scale the block by 2x', or 'add a coin at position 100, 50, 0' or 'add 5 coins above RedBlueExStepA'. " +
               "What would you like to do?";
    }
    
    /**
     * Gets a summary of available objects in the context.
     */
    private String getAvailableObjectSummary(GalaxyContext context) {
        if (context.getObjects().isEmpty()) {
            return "No objects in current galaxy";
        }
        
        Map<String, Integer> typeCounts = new HashMap<>();
        for (GalaxyContext.ObjectInfo obj : context.getObjects()) {
            String type = obj.getType();
            typeCounts.put(type, typeCounts.getOrDefault(type, 0) + 1);
        }
        
        StringBuilder summary = new StringBuilder();
        int count = 0;
        for (Map.Entry<String, Integer> entry : typeCounts.entrySet()) {
            if (count > 0) summary.append(", ");
            summary.append(entry.getValue()).append(" ").append(entry.getKey());
            count++;
            if (count >= 5) { // Limit to first 5 types
                summary.append("...");
                break;
            }
        }
        
        return summary.toString();
    }
    
    /**
     * Creates an optimized structured prompt for AI processing that requests JSON output.
     * Uses optimized context serialization for better performance.
     */
    private String createOptimizedStructuredPrompt(String userCommand, GalaxyContext context) {
        StringBuilder prompt = new StringBuilder();
        
        prompt.append("You are an AI assistant for a Super Mario Galaxy level editor called Whitehole Geo. ");
        prompt.append("You must respond with ONLY a valid JSON object containing transformation instructions.\n\n");
        
        // Add object database context
        try {
            ObjectDatabaseContext objContext = ObjectDatabaseContext.getInstance();
            prompt.append("=== OBJECT DATABASE CONTEXT ===\n");
            prompt.append(objContext.getMinimalContext());
            prompt.append("\n");
        } catch (Exception e) {
            // If object database context fails, continue without it
            System.err.println("Failed to load object database context in prompt: " + e.getMessage());
        }
        
        // Use optimized serialization for AI context
        JSONObject contextJson = contextManager.serializeForAI(context, 100); // Limit to 100 most relevant objects
        
        prompt.append("=== CURRENT GALAXY CONTEXT ===\n");
        prompt.append("Galaxy: ").append(contextJson.optString("galaxy", "Unknown")).append("\n");
        prompt.append("Zone: ").append(contextJson.optString("zone", "Unknown")).append("\n");
        
        JSONArray objects = contextJson.optJSONArray("objects");
        if (objects != null) {
            prompt.append("Objects in scene (").append(objects.length()).append(" most relevant):\n");
            
            for (int i = 0; i < Math.min(objects.length(), 50); i++) { // Limit display to 50 objects
                JSONObject obj = objects.getJSONObject(i);
                prompt.append("- ID ").append(obj.getInt("id"))
                      .append(": ").append(obj.getString("name"))
                      .append(" (").append(obj.getString("type")).append(")")
                      .append(" at ").append(obj.getJSONArray("pos").toString());
                
                if (obj.has("tags")) {
                    JSONArray tags = obj.getJSONArray("tags");
                    if (tags.length() > 0) {
                        prompt.append(", tags: ");
                        for (int j = 0; j < tags.length(); j++) {
                            if (j > 0) prompt.append(", ");
                            prompt.append(tags.getString(j));
                        }
                    }
                }
                prompt.append("\n");
            }
            
            if (contextJson.optBoolean("truncated", false)) {
                prompt.append("... (").append(contextJson.getInt("totalCount")).append(" total objects)\n");
            }
        }
        
        prompt.append("\nUSER COMMAND: \"").append(userCommand).append("\"\n\n");
        
        prompt.append("Respond with ONLY this JSON format:\n");
        prompt.append("{\n");
        prompt.append("  \"transformations\": [\n");
        prompt.append("    {\n");
        prompt.append("      \"objectId\": 123,\n");
        prompt.append("      \"type\": \"TRANSLATE|ROTATE|SCALE|SET_POSITION\",\n");
        prompt.append("      \"x\": 10.0,\n");
        prompt.append("      \"y\": 0.0,\n");
        prompt.append("      \"z\": 0.0,\n");
        prompt.append("      \"description\": \"Move object 10 units right\"\n");
        prompt.append("    }\n");
        prompt.append("  ]\n");
        prompt.append("}\n\n");
        
        prompt.append("DIRECTIONS: right=+X, left=-X, up=+Y, down=-Y, forward=+Z, backward=-Z\n");
        prompt.append("TRANSFORMATION TYPES:\n");
        prompt.append("- TRANSLATE: Move object by delta (relative movement)\n");
        prompt.append("- SET_POSITION: Set absolute position\n");
        prompt.append("- ROTATE: Rotate by degrees (relative rotation)\n");
        prompt.append("- SCALE: Scale by factor (multiply current scale)\n\n");
        prompt.append("RESPOND WITH ONLY THE JSON, NO OTHER TEXT.");
        
        return prompt.toString();
    }
    
    /**
     * Creates a structured prompt for AI processing that requests JSON output.
     * Legacy method for compatibility.
     */
    private String createStructuredPrompt(String userCommand, GalaxyContext context) {
        StringBuilder prompt = new StringBuilder();
        
        prompt.append("You are an AI assistant for a Super Mario Galaxy level editor called Whitehole Geo. ");
        prompt.append("You must respond with ONLY a valid JSON object containing transformation instructions.\n\n");
        
        // Add object database context
        try {
            ObjectDatabaseContext objContext = ObjectDatabaseContext.getInstance();
            prompt.append("=== OBJECT DATABASE CONTEXT ===\n");
            prompt.append(objContext.getMinimalContext());
            prompt.append("\n");
        } catch (Exception e) {
            // If object database context fails, continue without it
            System.err.println("Failed to load object database context in prompt: " + e.getMessage());
        }
        
        prompt.append("=== CURRENT GALAXY CONTEXT ===\n");
        prompt.append("Galaxy: ").append(context.getGalaxyName()).append("\n");
        prompt.append("Zone: ").append(context.getCurrentZone()).append("\n");
        prompt.append("Objects in scene:\n");
        
        for (GalaxyContext.ObjectInfo obj : context.getObjects()) {
            prompt.append("- ID ").append(obj.getUniqueId())
                  .append(": ").append(obj.getDisplayName())
                  .append(" (").append(obj.getName()).append(")")
                  .append(" at position ").append(formatVector(obj.getPosition()))
                  .append(", scale ").append(formatVector(obj.getScale()))
                  .append(", tags: ").append(String.join(", ", obj.getTags()))
                  .append("\n");
        }
        
        prompt.append("\nUSER COMMAND: \"").append(userCommand).append("\"\n\n");
        
        prompt.append("Respond with ONLY this JSON format:\n");
        prompt.append("{\n");
        prompt.append("  \"transformations\": [\n");
        prompt.append("    {\n");
        prompt.append("      \"objectId\": 123,\n");
        prompt.append("      \"type\": \"TRANSLATE|ROTATE|SCALE|SET_POSITION\",\n");
        prompt.append("      \"x\": 10.0,\n");
        prompt.append("      \"y\": 0.0,\n");
        prompt.append("      \"z\": 0.0,\n");
        prompt.append("      \"description\": \"Move object 10 units right\"\n");
        prompt.append("    }\n");
        prompt.append("  ]\n");
        prompt.append("}\n\n");
        
        prompt.append("DIRECTIONS: right=+X, left=-X, up=+Y, down=-Y, forward=+Z, backward=-Z\n");
        prompt.append("TRANSFORMATION TYPES:\n");
        prompt.append("- TRANSLATE: Move object by delta (relative movement)\n");
        prompt.append("- SET_POSITION: Set absolute position\n");
        prompt.append("- ROTATE: Rotate by degrees (relative rotation)\n");
        prompt.append("- SCALE: Scale by factor (multiply current scale)\n\n");
        prompt.append("RESPOND WITH ONLY THE JSON, NO OTHER TEXT.");
        
        return prompt.toString();
    }
    
    /**
     * Parses AI response JSON into ObjectTransformation objects.
     */
    private List<ObjectTransformation> parseAIResponse(String aiResponse, GalaxyContext context) {
        List<ObjectTransformation> transformations = new ArrayList<>();
        
        try {
            // Extract JSON from the response
            String jsonStr = extractJSON(aiResponse);
            if (jsonStr == null) {
                return transformations; // Empty list if no JSON found
            }
            
            JSONObject json = new JSONObject(jsonStr);
            
            if (!json.has("transformations")) {
                return transformations; // Empty list if no transformations array
            }
            
            JSONArray transformArray = json.getJSONArray("transformations");
            
            for (int i = 0; i < transformArray.length(); i++) {
                JSONObject transformObj = transformArray.getJSONObject(i);
                
                try {
                    ObjectTransformation transformation = parseTransformationObject(transformObj);
                    if (transformation != null) {
                        transformations.add(transformation);
                    }
                } catch (Exception e) {
                    // Skip malformed transformation objects
                    System.err.println("Skipping malformed transformation: " + e.getMessage());
                }
            }
            
        } catch (JSONException e) {
            // Return empty list if JSON parsing fails
            System.err.println("Failed to parse AI response JSON: " + e.getMessage());
        }
        
        return transformations;
    }
    
    /**
     * Parses a single transformation object from JSON.
     */
    private ObjectTransformation parseTransformationObject(JSONObject transformObj) throws JSONException {
        int objectId = transformObj.getInt("objectId");
        String typeStr = transformObj.getString("type");
        double x = transformObj.getDouble("x");
        double y = transformObj.getDouble("y");
        double z = transformObj.getDouble("z");
        String description = transformObj.optString("description", "");
        
        // Convert to ObjectTransformation
        ObjectTransformation.TransformationType type;
        try {
            type = ObjectTransformation.TransformationType.valueOf(typeStr);
        } catch (IllegalArgumentException e) {
            throw new JSONException("Invalid transformation type: " + typeStr);
        }
        
        return new ObjectTransformation.Builder()
            .setObjectId(objectId)
            .setType(type)
            .setVectorValue((float)x, (float)y, (float)z)
            .setDescription(description)
            .build();
    }
    
    /**
     * Attempts fallback parsing using regex patterns when AI processing fails.
     */
    private CommandResult tryFallbackParsing(String command, GalaxyContext context, List<String> aiErrors) {
        List<ObjectTransformation> transformations = new ArrayList<>();
        List<String> warnings = new ArrayList<>(aiErrors);
        warnings.add("Using fallback command parsing");
        
        // Try add pattern (object creation)
        Matcher addMatcher = ADD_PATTERN.matcher(command);
        if (addMatcher.find()) {
            String qtyStr = addMatcher.group(1);
            int qty = qtyStr != null ? Integer.parseInt(qtyStr) : 1;
            String objType = addMatcher.group(2).trim();

            Vec3f pos = new Vec3f(0, 0, 0);
            
            // Check for absolute coordinates (group 3-5)
            if (addMatcher.group(3) != null) {
                float x = Float.parseFloat(addMatcher.group(3));
                float y = Float.parseFloat(addMatcher.group(4));
                float z = Float.parseFloat(addMatcher.group(5));
                pos = new Vec3f(x, y, z);
            }
            // Check for relative positioning (group 6-7)
            else if (addMatcher.group(6) != null) {
                String relativePos = addMatcher.group(6).toLowerCase();
                String targetObject = addMatcher.group(7);
                
                // Find the target object
                List<GalaxyContext.ObjectInfo> targetObjects = findObjectsByReference(targetObject, context);
                if (!targetObjects.isEmpty()) {
                    GalaxyContext.ObjectInfo target = targetObjects.get(0);
                    Vec3f targetPos = target.getPosition();
                    
                    // Calculate relative position
                    switch (relativePos) {
                        case "above":
                            pos = new Vec3f(targetPos.x, targetPos.y + 100, targetPos.z);
                            break;
                        case "below":
                            pos = new Vec3f(targetPos.x, targetPos.y - 100, targetPos.z);
                            break;
                        case "near":
                            pos = new Vec3f(targetPos.x + 50, targetPos.y, targetPos.z + 50);
                            break;
                        case "behind":
                            pos = new Vec3f(targetPos.x, targetPos.y, targetPos.z - 100);
                            break;
                        case "in front of":
                            pos = new Vec3f(targetPos.x, targetPos.y, targetPos.z + 100);
                            break;
                        default:
                            pos = targetPos; // Default to same position
                            break;
                    }
                }
            }
            
            for (int i = 0; i < qty; i++) {
                transformations.add(ObjectTransformation.addObject(objType, pos, "Add object " + objType));
            }
        }
        
        // Try move pattern
        Matcher moveMatcher = MOVE_PATTERN.matcher(command);
        if (moveMatcher.find()) {
            String objectRef = moveMatcher.group(1);
            float distance = Float.parseFloat(moveMatcher.group(2));
            String direction = moveMatcher.group(3).toLowerCase();
            
            Vec3f delta = getDirectionVector(direction, distance);
            List<GalaxyContext.ObjectInfo> objects = findObjectsByReference(objectRef, context);
            
            for (GalaxyContext.ObjectInfo obj : objects) {
                transformations.add(ObjectTransformation.translate(
                    obj.getUniqueId(), delta, 
                    "Move " + obj.getDisplayName() + " " + distance + " units " + direction));
            }
        }
        
        // Try position pattern
        Matcher positionMatcher = POSITION_PATTERN.matcher(command);
        if (positionMatcher.find()) {
            String objectRef = positionMatcher.group(1);
            float x = Float.parseFloat(positionMatcher.group(2));
            float y = Float.parseFloat(positionMatcher.group(3));
            float z = Float.parseFloat(positionMatcher.group(4));
            
            List<GalaxyContext.ObjectInfo> objects = findObjectsByReference(objectRef, context);
            
            for (GalaxyContext.ObjectInfo obj : objects) {
                transformations.add(new ObjectTransformation.Builder()
                    .setObjectId(obj.getUniqueId())
                    .setType(ObjectTransformation.TransformationType.SET_POSITION)
                    .setVectorValue(x, y, z)
                    .setDescription("Move " + obj.getDisplayName() + " to position (" + x + ", " + y + ", " + z + ")")
                    .build());
            }
        }
        
        // Try rotate pattern
        Matcher rotateMatcher = ROTATE_PATTERN.matcher(command);
        if (rotateMatcher.find()) {
            String objectRef = rotateMatcher.group(1);
            float degrees = Float.parseFloat(rotateMatcher.group(2));
            
            List<GalaxyContext.ObjectInfo> objects = findObjectsByReference(objectRef, context);
            
            for (GalaxyContext.ObjectInfo obj : objects) {
                transformations.add(ObjectTransformation.rotate(
                    obj.getUniqueId(), new Vec3f(0, degrees, 0),
                    "Rotate " + obj.getDisplayName() + " " + degrees + " degrees"));
            }
        } else {
            // Try alternative rotate pattern for "all X" format
            Pattern altRotatePattern = Pattern.compile(
                "rotate\\s+all\\s+(.+?)\\s+(\\d+(?:\\.\\d+)?)\\s+degrees?",
                Pattern.CASE_INSENSITIVE
            );
            Matcher altMatcher = altRotatePattern.matcher(command);
            if (altMatcher.find()) {
                String objectRef = altMatcher.group(1);
                float degrees = Float.parseFloat(altMatcher.group(2));
                
                List<GalaxyContext.ObjectInfo> objects = findObjectsByReference(objectRef, context);
                
                for (GalaxyContext.ObjectInfo obj : objects) {
                    transformations.add(ObjectTransformation.rotate(
                        obj.getUniqueId(), new Vec3f(0, degrees, 0),
                        "Rotate " + obj.getDisplayName() + " " + degrees + " degrees"));
                }
            }
        }
        
        // Try scale pattern
        Matcher scaleMatcher = SCALE_PATTERN.matcher(command);
        if (scaleMatcher.find()) {
            String objectRef = scaleMatcher.group(1);
            float factor = Float.parseFloat(scaleMatcher.group(2));
            
            List<GalaxyContext.ObjectInfo> objects = findObjectsByReference(objectRef, context);
            
            for (GalaxyContext.ObjectInfo obj : objects) {
                transformations.add(ObjectTransformation.scale(
                    obj.getUniqueId(), new Vec3f(factor, factor, factor),
                    "Scale " + obj.getDisplayName() + " by " + factor + "x"));
            }
        }
        
        if (transformations.isEmpty()) {
            // Try to get AI response for commands that couldn't be parsed
            try {
                AIResponse aiResponse = providerManager.processCommand(
                    "The user said: '" + command + "'. " +
                    "I couldn't parse this as a specific command to modify objects. " +
                    "Please provide a helpful response explaining what they can do instead, " +
                    "or suggest how they could rephrase their request to modify objects in the galaxy.", 
                    context);
                if (aiResponse.isSuccess()) {
                    return CommandResult.failure(aiResponse.getFeedback(), warnings);
                }
            } catch (Exception aiError) {
                // Fallback to predefined response if AI fails
            }
            
            String helpfulResponse = generateHelpfulResponse(command, context);
            return CommandResult.failure(helpfulResponse, warnings);
        }
        
        return CommandResult.success(transformations, 
            "Processed command using fallback parsing: " + command, 
            "Fallback parsing found " + transformations.size() + " transformation(s)",
            warnings);
    }
    
    /**
     * Finds objects in the context by reference (name, type, or tag).
     * Returns all potential matches for disambiguation.
     */
    private List<GalaxyContext.ObjectInfo> findObjectsByReference(String reference, GalaxyContext context) {
        List<GalaxyContext.ObjectInfo> matches = new ArrayList<>();
        String ref = reference.toLowerCase().trim();
        
        // Try exact name match first
        for (GalaxyContext.ObjectInfo obj : context.getObjects()) {
            if (obj.getName().toLowerCase().equals(ref) || 
                obj.getDisplayName().toLowerCase().equals(ref)) {
                matches.add(obj);
            }
        }
        
        if (!matches.isEmpty()) {
            return matches;
        }
        
        // Try partial name match
        for (GalaxyContext.ObjectInfo obj : context.getObjects()) {
            if (obj.getName().toLowerCase().contains(ref) || 
                obj.getDisplayName().toLowerCase().contains(ref)) {
                matches.add(obj);
            }
        }
        
        if (!matches.isEmpty()) {
            return matches;
        }
        
        // Try tag match (including plural forms)
        for (GalaxyContext.ObjectInfo obj : context.getObjects()) {
            for (String tag : obj.getTags()) {
                if (tag.toLowerCase().contains(ref) || 
                    (ref.endsWith("s") && tag.toLowerCase().contains(ref.substring(0, ref.length() - 1)))) {
                    matches.add(obj);
                    break;
                }
            }
        }
        
        if (!matches.isEmpty()) {
            return matches;
        }
        
        // Try type match
        for (GalaxyContext.ObjectInfo obj : context.getObjects()) {
            if (obj.getType().toLowerCase().contains(ref)) {
                matches.add(obj);
            }
        }
        
        return matches;
    }
    
    /**
     * Finds objects with disambiguation support for UI interaction.
     * This method should be called from UI context to handle ambiguous references.
     */
    public List<GalaxyContext.ObjectInfo> findObjectsWithDisambiguation(String reference, 
                                                                        GalaxyContext context, 
                                                                        String fullCommand,
                                                                        Component parent) {
        List<GalaxyContext.ObjectInfo> candidates = findObjectsByReference(reference, context);
        
        if (candidates.isEmpty()) {
            return candidates;
        }
        
        // If there's only one match or it's a clear "all" reference, return all matches
        if (candidates.size() == 1 || reference.toLowerCase().trim().startsWith("all ")) {
            return candidates;
        }
        
        // For multiple matches, show disambiguation dialog
        return DisambiguationDialog.disambiguateObjects(parent, candidates, reference, fullCommand);
    }
    
    /**
     * Converts direction string to vector.
     */
    private Vec3f getDirectionVector(String direction, float distance) {
        switch (direction.toLowerCase()) {
            case "right": return new Vec3f(distance, 0, 0);
            case "left": return new Vec3f(-distance, 0, 0);
            case "up": return new Vec3f(0, distance, 0);
            case "down": return new Vec3f(0, -distance, 0);
            case "forward": return new Vec3f(0, 0, distance);
            case "backward": return new Vec3f(0, 0, -distance);
            default: return new Vec3f(0, 0, 0);
        }
    }
    
    /**
     * Validates transformations against context and constraints.
     */
    private List<String> validateTransformations(List<ObjectTransformation> transformations, GalaxyContext context) {
        List<String> errors = new ArrayList<>();
        
        for (ObjectTransformation transformation : transformations) {
            // Check if object exists
            boolean objectExists = false;
            for (GalaxyContext.ObjectInfo obj : context.getObjects()) {
                if (obj.getUniqueId() == transformation.getObjectId()) {
                    objectExists = true;
                    break;
                }
            }
            
            if (!objectExists) {
                errors.add("Object with ID " + transformation.getObjectId() + " not found in context");
            }
            
            // Validate transformation values
            Vec3f vector = transformation.getVectorValue();
            if (vector != null) {
                if (Float.isNaN(vector.x) || Float.isNaN(vector.y) || Float.isNaN(vector.z)) {
                    errors.add("Invalid transformation values (NaN) for object " + transformation.getObjectId());
                }
                
                if (Float.isInfinite(vector.x) || Float.isInfinite(vector.y) || Float.isInfinite(vector.z)) {
                    errors.add("Invalid transformation values (Infinite) for object " + transformation.getObjectId());
                }
            }
        }
        
        return errors;
    }
    
    /**
     * Extracts JSON object from text response.
     */
    private String extractJSON(String text) {
        if (text == null) return null;
        
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        
        if (start != -1 && end != -1 && end > start) {
            return text.substring(start, end + 1);
        }
        
        return null;
    }
    
    /**
     * Formats a Vec3f for display.
     */
    private String formatVector(Vec3f vec) {
        return String.format("(%.1f, %.1f, %.1f)", vec.x, vec.y, vec.z);
    }
    
    /**
     * Shuts down the command parser and cleans up resources.
     * Should be called when the parser is no longer needed.
     */
    public void shutdown() {
        if (responseProcessor != null) {
            responseProcessor.shutdown();
        }
    }
    
    /**
     * Gets performance statistics for monitoring.
     */
    public Map<String, Object> getPerformanceStatistics() {
        Map<String, Object> stats = new HashMap<>();
        
        if (responseProcessor != null) {
            stats.putAll(responseProcessor.getStatistics());
        }
        
        return stats;
    }
}
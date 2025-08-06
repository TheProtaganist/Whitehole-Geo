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

import java.awt.Component;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import javax.swing.SwingUtilities;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import whitehole.math.Vec3f;
import whitehole.smg.object.AbstractObj;
import whitehole.Settings;
import whitehole.ai.ObjectDatabaseContext;
import whitehole.ai.ObjectResolver;

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
    
    // Enhanced patterns for more natural language variations
    private static final Pattern CREATE_PATTERN = Pattern.compile(
        "(?:create|spawn|place)\\s+(?:(\\d+)\\s+)?(?:an?\\s+)?([\\w\\s]+?)(?:\\s+(?:at\\s+([\\d.-]+)[,\\s\\|]\\s*([\\d.-]+)[,\\s\\|]\\s*([\\d.-]+)|(above|below|near|behind|in\\s+front\\s+of|around)\\s+(.+?)))?$",
        Pattern.CASE_INSENSITIVE
    );
    
    private static final Pattern DELETE_PATTERN = Pattern.compile(
        "(?:delete|remove|destroy)\\s+(?:the\\s+)?(.+?)(?:\\s+(?:at|near)\\s+(.+?))?$",
        Pattern.CASE_INSENSITIVE
    );
    
    private static final Pattern COPY_PATTERN = Pattern.compile(
        "(?:copy|duplicate|clone)\\s+(?:the\\s+)?(.+?)(?:\\s+(?:(\\d+)\\s+times?|to\\s+(.+?)))?$",
        Pattern.CASE_INSENSITIVE
    );
    
    private static final Pattern PROPERTY_PATTERN = Pattern.compile(
        "(?:change|set|modify)\\s+(?:the\\s+)?(.+?)\\s+(.+?)\\s+to\\s+(.+?)$",
        Pattern.CASE_INSENSITIVE
    );
    
    // More flexible movement patterns
    private static final Pattern MOVE_TO_PATTERN = Pattern.compile(
        "move\\s+(?:the\\s+)?(.+?)\\s+to\\s+(?:the\\s+)?(.+?)$",
        Pattern.CASE_INSENSITIVE
    );
    
    private static final Pattern TELEPORT_PATTERN = Pattern.compile(
        "(?:teleport|warp)\\s+(?:the\\s+)?(.+?)\\s+to\\s+(?:position\\s+)?([\\d.-]+)[,\\s\\|]\\s*([\\d.-]+)[,\\s\\|]\\s*([\\d.-]+)$",
        Pattern.CASE_INSENSITIVE
    );
    
    private static final Pattern LINK_ID_PATTERN = Pattern.compile(
        "(\\w+)\\s+with\\s+link\\s+id\\s+(\\d+)", 
        Pattern.CASE_INSENSITIVE);
    
    public CommandParser(AIProviderManager providerManager, GalaxyContextManager contextManager) {
        this.providerManager = providerManager;
        this.contextManager = contextManager;
        this.responseProcessor = new OptimizedAIResponseProcessor();
    }
    
    /**
     * Processes a user command using AI and returns the result.
     */
    public CommandResult processCommand(String command, GalaxyContext context) {
        System.out.println("DEBUG: Processing command: " + command);
        
        // Check if we're using a smaller model
        boolean isSmallModel = isSmallModel();
        System.out.println("DEBUG: Using smaller model optimizations: " + isSmallModel);
        
        // Pre-validate the command
        List<String> validationErrors = preValidateCommand(command, context);
        if (!validationErrors.isEmpty()) {
            return CommandResult.failure("Command validation failed: " + String.join(", ", validationErrors));
        }
        
        try {
            // Create optimized prompt for the AI
            String prompt = createOptimizedStructuredPrompt(command, context);
            System.out.println("DEBUG: Created prompt for AI (length: " + prompt.length() + " chars)");
            
            // Get AI response
            AIResponse response = providerManager.getCurrentProvider().processCommand(prompt, context);
            System.out.println("DEBUG: Received AI response (length: " + response.getFeedback().length() + " chars)");
            
            if (response == null || response.getFeedback() == null || response.getFeedback().trim().isEmpty()) {
                System.out.println("DEBUG: AI returned null or empty response");
                return CommandResult.failure("AI returned no response");
            }
            
            // Parse AI response
            List<ObjectTransformation> transformations = parseAIResponse(response.getFeedback(), context);
            System.out.println("DEBUG: Parsed " + transformations.size() + " transformations from AI response");
            
            if (transformations.isEmpty()) {
                System.out.println("DEBUG: No valid transformations found in AI response");
                return CommandResult.failure("No valid transformations found in AI response");
            }
            
            // Parse transformations successfully - execution will be handled by caller
            System.out.println("DEBUG: Parsed " + transformations.size() + " transformations successfully");
            for (ObjectTransformation transformation : transformations) {
                System.out.println("DEBUG: Parsed transformation: " + transformation);
            }
            
            return CommandResult.success(transformations, "Command parsed successfully - " + transformations.size() + " transformations ready for execution", response.getFeedback());
            
        } catch (Exception e) {
            System.err.println("Error processing command: " + e.getMessage());
            e.printStackTrace();
            return CommandResult.failure("Error processing command: " + e.getMessage());
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
            return "I can help you add new objects! Try these formats:\n" +
                   "• 'add [number] [object type] at position [x], [y], [z]'\n" +
                   "• 'add [number] [object type] above/below/near [existing object]'\n" +
                   "• 'create 5 coins around RedBlueExStepA'\n" +
                   "• 'spawn a Goomba at position 100, 50, 0'\n" +
                   "Available object types: Goomba, Koopa, Coin, Star, Platform, Block, Pipe, etc.";
        }
        
        // Check if it's a delete command
        if (lowerCommand.contains("delete") || lowerCommand.contains("remove") || lowerCommand.contains("destroy")) {
            return "I can help you remove objects! Try: 'delete the [object name]' or 'remove all [object type]'. " +
                   "For example: 'delete the Goomba' or 'remove all coins'.";
        }
        
        // Check if it's a copy command
        if (lowerCommand.contains("copy") || lowerCommand.contains("duplicate") || lowerCommand.contains("clone")) {
            return "I can help you copy objects! Try: 'copy the [object name] [number] times' or 'duplicate [object] to [location]'. " +
                   "For example: 'copy the platform 3 times' or 'clone the Goomba above the block'.";
        }
        
        // Check if it's a teleport command
        if (lowerCommand.contains("teleport") || lowerCommand.contains("warp")) {
            return "I can help you teleport objects! Try: 'teleport [object] to position [x], [y], [z]'. " +
                   "For example: 'teleport the Goomba to position 200, 100, 50' or 'warp all coins to 0, 0, 0'.";
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
     * Detects if the current AI provider is a smaller model (like Ollama).
     */
    private boolean isSmallModel() {
        // For now, treat all models as regular models to ensure performance
        // We can add back optimizations later once we confirm everything works
        return false;
    }
    
    /**
     * Creates a comprehensive prompt for AI processing with proper object matching.
     * Includes enhanced context for smaller models like Ollama.
     */
    private String createOptimizedStructuredPrompt(String userCommand, GalaxyContext context) {
        StringBuilder prompt = new StringBuilder();
        
        // CRITICAL: Prevent markdown and explanatory responses
        prompt.append("CRITICAL: You MUST respond with ONLY valid JSON. NO explanations, NO questions, NO markdown, NO formatting.\n\n");
        prompt.append("You are an AI assistant for a Super Mario Galaxy level editor.\n");
        prompt.append("Your ONLY job is to convert user commands into JSON transformations.\n");
        prompt.append("DO NOT use any markdown formatting like **bold** or ```json``` or any other formatting.\n\n");
        
        // Use comprehensive context for better object matching
        JSONObject contextJson = contextManager.serializeForAI(context, 75); // Increased context for better performance
        
        System.out.println("DEBUG: Using comprehensive context (75 objects) for better performance");
        
        prompt.append("Objects in scene:\n");
        JSONArray objects = contextJson.optJSONArray("objects");
        if (objects != null) {
            for (int i = 0; i < Math.min(objects.length(), 75); i++) {
                JSONObject obj = objects.getJSONObject(i);
                prompt.append("- ID ").append(obj.getInt("id"))
                      .append(": ").append(obj.getString("name"))
                      .append(" at ").append(obj.getJSONArray("pos").toString()).append("\n");
            }
        }
        
        prompt.append("\nCommand: \"").append(userCommand).append("\"\n\n");
        
        // Add object database context for better understanding
        try {
            ObjectDatabaseContext objContext = ObjectDatabaseContext.getInstance();
            prompt.append("=== OBJECT DATABASE CONTEXT ===\n");
            prompt.append(objContext.getMinimalContext());
            prompt.append("\n");
            
            // Add specific object functionality information
            prompt.append("=== OBJECT FUNCTIONALITY GUIDE ===\n");
            prompt.append("COLLECTIBLES: Coin (basic collectible), PurpleCoin (special collectible), StarBit (currency), 1Up (extra life)\n");
            prompt.append("ENEMIES: Goomba (basic enemy), Koopa (shell enemy), KoopaJr (mini boss), HammerBros (ranged enemy)\n");
            prompt.append("PLATFORMS: Platform (static), MovingPlatform (moves along path), RotatingPlatform (spins), BreakablePlatform (can be destroyed)\n");
            prompt.append("INTERACTIVE: QuestionBlock (contains items), Switch (activates mechanisms), Door (entrance/exit), Pipe (transport)\n");
            prompt.append("DECORATIVE: Tree, Flower, Rock, Signboard (visual elements)\n");
            prompt.append("HAZARDS: Lava (damage), Spike (damage), FireBar (rotating hazard), Thwomp (crushing hazard)\n");
            prompt.append("POWER-UPS: PowerUpInvincible (temporary invincibility), PowerUpFire (fire ability), PowerUpIce (ice ability)\n");
            prompt.append("VEHICLES: StarShip (player transport), KoopaJrShip (enemy ship), Airship (large transport)\n");
            prompt.append("NPCS: LuigiNPC (Luigi character), Kinopio (Toad character), Rosetta (Rosalina character)\n");
            prompt.append("BOSSES: BossKameck (magic boss), BossKoopa (Koopa boss), BossBowser (final boss)\n");
            prompt.append("CAMERAS: Camera (viewpoint control), CameraRail (camera path), CameraArea (camera zone)\n");
            prompt.append("SOUND: SoundObj (audio), SoundArea (sound zone), BgmChangeArea (music change)\n");
            prompt.append("EFFECTS: ParticleGenerator (visual effects), Light (illumination), Fog (atmosphere)\n");
            prompt.append("\n");
        } catch (Exception e) {
            // If object database context fails, continue without it
            System.err.println("Failed to load object database context in prompt: " + e.getMessage());
        }
        
        // Analyze command for object detection
        String[] words = userCommand.toLowerCase().split("\\s+");
        List<String> detectedObjects = new ArrayList<>();
        
        // Build object name set for matching
        Set<String> allObjectNames = new HashSet<>();
        for (GalaxyContext.ObjectInfo obj : context.getObjects()) {
            allObjectNames.add(obj.getName().toLowerCase());
            allObjectNames.add(obj.getDisplayName().toLowerCase());
        }
        
        // Detect objects mentioned in command
        for (String word : words) {
            for (String objName : allObjectNames) {
                if (objName.equals(word) || objName.contains(word) || word.contains(objName)) {
                    detectedObjects.add(word);
                    break;
                }
            }
        }
        
        // Show object matches with priority
        if (!detectedObjects.isEmpty()) {
            prompt.append("Object matches for command:\n");
            for (String detectedObj : detectedObjects) {
                prompt.append("For '" + detectedObj + "':\n");
                
                // Exact matches (highest priority)
                for (GalaxyContext.ObjectInfo obj : context.getObjects()) {
                    String objName = obj.getName().toLowerCase();
                    String objDisplay = obj.getDisplayName().toLowerCase();
                    if (objName.equals(detectedObj) || objDisplay.equals(detectedObj)) {
                        prompt.append("  EXACT MATCH: ID " + obj.getUniqueId() + " - " + obj.getName() + "\n");
                    }
                }
                
                // Partial matches (lower priority)
                for (GalaxyContext.ObjectInfo obj : context.getObjects()) {
                    String objName = obj.getName().toLowerCase();
                    String objDisplay = obj.getDisplayName().toLowerCase();
                    if ((objName.contains(detectedObj) || objDisplay.contains(detectedObj)) && 
                        !objName.equals(detectedObj) && !objDisplay.equals(detectedObj)) {
                        prompt.append("  PARTIAL MATCH: ID " + obj.getUniqueId() + " - " + obj.getName() + "\n");
                    }
                }
                prompt.append("\n");
            }
        }
        
        // Check for character objects and add to prompt
        String characterObjects = findCharacterObjects(context);
        if (!characterObjects.isEmpty()) {
            prompt.append("CHARACTER OBJECTS IN SCENE:\n");
            prompt.append(characterObjects);
            prompt.append("\n");
            prompt.append("WARNING: If the user requests a character object, ONLY use the objects listed above.\n");
            prompt.append("Do NOT select other objects even if they seem similar.\n");
            prompt.append("\n");
        }
        
        // Check if this is an ADD command
        boolean isAddCommand = userCommand.toLowerCase().contains("add ") || 
                             userCommand.toLowerCase().contains("create ") || 
                             userCommand.toLowerCase().contains("spawn ");
        
        if (isAddCommand) {
            prompt.append("*** ADD COMMAND DETECTED ***\n");
            prompt.append("- Use ADD transformation type\n");
            prompt.append("- Set objectId to 0 (creates new objects)\n");
            prompt.append("- Space objects 20-200 units apart (not too far)\n");
            prompt.append("- For multiple objects, create a tight cluster or small pattern\n");
            prompt.append("- Example: 5 coins should be close together, not scattered\n\n");
        }
        
        // Add spatial relationship guidance
        prompt.append("*** SPATIAL RELATIONSHIP GUIDANCE ***\n");
        prompt.append("- 'near': Position within 20-200 units of reference object\n");
        prompt.append("- 'above': Position 20-200 units higher than reference object\n");
        prompt.append("- 'below': Position 20-200 units lower than reference object\n");
        prompt.append("- 'behind': Position 20-200 units behind reference object (negative Z)\n");
        prompt.append("- 'in front of': Position 20-200 units in front of reference object (positive Z)\n");
        prompt.append("- 'to the left': Position 20-200 units to the left of reference object (negative X)\n");
        prompt.append("- 'to the right': Position 20-200 units to the right of reference object (positive X)\n");
        prompt.append("\n");
        
        // Add specific spatial calculation instructions for smaller models
        prompt.append("*** SPATIAL CALCULATION FOR OLLAMA MODELS ***\n");
        prompt.append("When calculating positions relative to reference objects:\n");
        prompt.append("1. Find the reference object's current position (x, y, z) from the objects list above\n");
        prompt.append("2. For 'near': Add/subtract 20-200 units to each coordinate\n");
        prompt.append("3. For 'above': Add 20-200 to Y coordinate\n");
        prompt.append("4. For 'below': Subtract 20-200 from Y coordinate\n");
        prompt.append("5. For 'behind': Subtract 20-200 from Z coordinate\n");
        prompt.append("6. For 'in front of': Add 20-200 to Z coordinate\n");
        prompt.append("7. For 'left': Subtract 20-200 from X coordinate\n");
        prompt.append("8. For 'right': Add 20-200 to X coordinate\n");
        prompt.append("9. Use the reference object's ACTUAL coordinates, not generic values\n");
        prompt.append("10. If you cannot find reference object coordinates, use objectId 0\n");
        prompt.append("11. For 'near' commands, use SET_POSITION with calculated coordinates\n");
        prompt.append("12. For 'move' commands, use TRANSLATE with delta values\n");
        prompt.append("13. DO NOT add explanatory text in parentheses\n");
        prompt.append("14. DO NOT assume generic positions like [0, 200, 200]\n");
        prompt.append("\n");
        
        // Very explicit rules
        prompt.append("CRITICAL RULES:\n");
        prompt.append("1. Respond with ONLY valid JSON - no other text\n");
        prompt.append("2. Do NOT ask questions or provide explanations\n");
        prompt.append("3. Do NOT use markdown formatting (no **bold**, no ```json```, no formatting)\n");
        prompt.append("4. Do NOT use code blocks or backticks\n");
        prompt.append("5. If the command mentions 'add' or 'create', use ADD transformation with objectId 0\n");
        prompt.append("6. If the command mentions 'move', use TRANSLATE or SET_POSITION\n");
        prompt.append("7. If the command mentions 'scale', use SCALE transformation\n");
        prompt.append("8. If the command mentions 'rotate', use ROTATE transformation\n");
        prompt.append("9. For ADD commands, set objectType to the object name (e.g., 'Coin', 'Goomba', 'Platform')\n");
        prompt.append("10. Use reasonable coordinates (0-10000 range)\n");
        prompt.append("11. If you cannot find the exact object, use objectId 0\n");
        prompt.append("12. NO markdown, NO formatting, NO explanations - ONLY raw JSON\n");
        prompt.append("13. DO NOT add explanatory text in parentheses like \"(Assumes...)\"\n");
        prompt.append("14. DO NOT assume generic positions - use actual object coordinates\n\n");
        
        prompt.append("REQUIRED JSON FORMAT:\n");
        prompt.append("{\"transformations\":[{\"objectId\":[NUMBER],\"type\":\"[TYPE]\",\"x\":[NUMBER],\"y\":[NUMBER],\"z\":[NUMBER],\"description\":\"[DESC]\",\"objectType\":\"[OBJECT_TYPE]\"}]}\n\n");
        
        prompt.append("EXAMPLES:\n");
        prompt.append("- 'add coin' -> {\"transformations\":[{\"objectId\":0,\"type\":\"ADD\",\"x\":0,\"y\":200,\"z\":200,\"description\":\"Add coin\",\"objectType\":\"Coin\"}]}\n");
        prompt.append("- 'move coin right 10' -> {\"transformations\":[{\"objectId\":1,\"type\":\"TRANSLATE\",\"x\":10,\"y\":0,\"z\":0,\"description\":\"Move coin right 10 units\"}]}\n");
        prompt.append("- 'scale goomba 2x' -> {\"transformations\":[{\"objectId\":2,\"type\":\"SCALE\",\"x\":2,\"y\":2,\"z\":2,\"description\":\"Scale goomba 2x\"}]}\n");
        prompt.append("- 'move coin near mario' -> {\"transformations\":[{\"objectId\":1,\"type\":\"SET_POSITION\",\"x\":100,\"y\":300,\"z\":200,\"description\":\"Move coin near mario\"}]}\n");
        prompt.append("- 'move ticocoin near ghost luigi' -> {\"transformations\":[{\"objectId\":99,\"type\":\"SET_POSITION\",\"x\":-4863,\"y\":150,\"z\":9089,\"description\":\"Move TicoCoin near Ghost Luigi\"}]}\n\n");
        
        prompt.append("NOW GENERATE THE JSON FOR THE COMMAND: \"").append(userCommand).append("\"\n");
        prompt.append("RESPOND WITH ONLY THE JSON, NO OTHER TEXT, NO MARKDOWN, NO FORMATTING:\n");
        
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
     * Parses AI response and converts it to transformations.
     */
    public List<ObjectTransformation> parseAIResponse(String aiResponse, GalaxyContext context) {
        System.out.println("DEBUG: Parsing AI response...");
        System.out.println("DEBUG: Raw AI response: " + aiResponse);
        
        if (aiResponse == null || aiResponse.trim().isEmpty()) {
            System.out.println("DEBUG: AI response is null or empty");
            return new ArrayList<>();
        }
        
        // Extract JSON from response
        String jsonString = extractJSON(aiResponse);
        System.out.println("DEBUG: Extracted JSON: " + jsonString);
        
        if (jsonString == null) {
            System.out.println("DEBUG: Failed to extract JSON from AI response");
            return new ArrayList<>();
        }
        
        try {
            JSONObject json = new JSONObject(jsonString);
            JSONArray transformations = json.getJSONArray("transformations");
            
            System.out.println("DEBUG: Found " + transformations.length() + " transformations in AI response");
            
            List<ObjectTransformation> result = new ArrayList<>();
            
            for (int i = 0; i < transformations.length(); i++) {
                JSONObject transformObj = transformations.getJSONObject(i);
                System.out.println("DEBUG: Processing transformation " + (i+1) + ": " + transformObj.toString());
                
                ObjectTransformation transformation = parseTransformationObject(transformObj);
                if (transformation != null) {
                    System.out.println("DEBUG: Successfully parsed transformation: " + transformation);
                    result.add(transformation);
                } else {
                    System.out.println("DEBUG: Failed to parse transformation " + (i+1));
                }
            }
            
            System.out.println("DEBUG: Final result: " + result.size() + " valid transformations");
            return result;
            
        } catch (JSONException e) {
            System.out.println("DEBUG: JSON parsing error: " + e.getMessage());
            return new ArrayList<>();
        }
    }
    
    /**
     * Validates that character objects are correctly selected for character commands.
     */
    private List<ObjectTransformation> validateCharacterObjectSelection(List<ObjectTransformation> transformations, GalaxyContext context) {
        if (transformations.isEmpty()) {
            return transformations;
        }
        
        // Check if any transformation involves character objects
        boolean hasCharacterObjects = false;
        for (ObjectTransformation transform : transformations) {
            GalaxyContext.ObjectInfo obj = findObjectById(transform.getObjectId(), context);
            if (obj != null && isCharacterObject(obj)) {
                hasCharacterObjects = true;
                break;
            }
        }
        
        if (!hasCharacterObjects) {
            return transformations; // No character objects involved, no validation needed
        }
        
        // For character objects, ensure they are valid
        List<ObjectTransformation> validated = new ArrayList<>();
        for (ObjectTransformation transform : transformations) {
            GalaxyContext.ObjectInfo obj = findObjectById(transform.getObjectId(), context);
            if (obj != null && isCharacterObject(obj)) {
                // Character object is valid
                validated.add(transform);
            } else if (obj != null && !isCharacterObject(obj)) {
                // Non-character object selected - this might be wrong
                System.out.println("DEBUG: WARNING - Non-character object selected: " + obj.getName() + " (ID: " + obj.getUniqueId() + ")");
                // Still add it but log the warning
                validated.add(transform);
            } else {
                // Object not found
                System.out.println("DEBUG: Object not found for ID: " + transform.getObjectId());
            }
        }
        
        return validated;
    }
    
    /**
     * Finds an object by ID in the context.
     */
    private GalaxyContext.ObjectInfo findObjectById(int objectId, GalaxyContext context) {
        return context.getObjects().stream()
            .filter(obj -> obj.getUniqueId() == objectId)
            .findFirst()
            .orElse(null);
    }
    
    /**
     * Checks if an object is a character object.
     */
    private boolean isCharacterObject(GalaxyContext.ObjectInfo obj) {
        String name = obj.getName().toLowerCase();
        String displayName = obj.getDisplayName().toLowerCase();
        
        String[] characterNames = {"luigi", "mario", "peach", "toad", "rosetta", "kinopio"};
        for (String charName : characterNames) {
            if (name.contains(charName) || displayName.contains(charName)) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * Parses transformation object from JSON.
     */
    private ObjectTransformation parseTransformationObject(JSONObject transformObj) throws JSONException {
        int objectId = transformObj.getInt("objectId");
        String type = transformObj.getString("type");
        float x = (float) transformObj.getDouble("x");
        float y = (float) transformObj.getDouble("y");
        float z = (float) transformObj.getDouble("z");
        String description = transformObj.getString("description");
        String objectType = transformObj.optString("objectType", null); // Optional field for ADD transformations
        
        System.out.println("DEBUG: Parsing transformation - objectId: " + objectId + 
                          ", type: " + type + ", description: " + description);
        
        // Log what object this ID refers to for debugging
        if (objectId > 0) {
            System.out.println("DEBUG: Object ID " + objectId + " selected for transformation");
        } else if (objectId == 0) {
            // Check if this is an ADD transformation (objectId 0 is correct for ADD)
            if (type != null && type.equalsIgnoreCase("ADD")) {
                System.out.println("DEBUG: Object ID 0 selected (correct for ADD transformation - creates new object)");
            } else {
                System.out.println("DEBUG: Object ID 0 selected (indicates failure/not found)");
            }
        }
        
        Vec3f vector = new Vec3f(x, y, z);
        
        switch (type.toUpperCase()) {
            case "TRANSLATE":
                return new ObjectTransformation.Builder()
                    .setObjectId(objectId)
                    .setType(ObjectTransformation.TransformationType.TRANSLATE)
                    .setVectorValue(vector)
                    .setDescription(description)
                    .build();
            case "ROTATE":
                return new ObjectTransformation.Builder()
                    .setObjectId(objectId)
                    .setType(ObjectTransformation.TransformationType.ROTATE)
                    .setVectorValue(vector)
                    .setDescription(description)
                    .build();
            case "SCALE":
                return new ObjectTransformation.Builder()
                    .setObjectId(objectId)
                    .setType(ObjectTransformation.TransformationType.SCALE)
                    .setVectorValue(vector)
                    .setDescription(description)
                    .build();
            case "SET_POSITION":
                return new ObjectTransformation.Builder()
                    .setObjectId(objectId)
                    .setType(ObjectTransformation.TransformationType.SET_POSITION)
                    .setVectorValue(vector)
                    .setDescription(description)
                    .build();
            case "ADD":
                return new ObjectTransformation.Builder()
                    .setObjectId(objectId)
                    .setType(ObjectTransformation.TransformationType.ADD)
                    .setVectorValue(vector)
                    .setDescription(description)
                    .setAddObjectType(objectType != null ? objectType : "Coin") // Default to Coin if not specified
                    .build();
            default:
                System.out.println("DEBUG: Unknown transformation type: " + type);
                return null;
        }
    }
    
    /**
     * Attempts fallback parsing using regex patterns when AI processing fails.
     */
    private CommandResult tryFallbackParsing(String command, GalaxyContext context, List<String> aiErrors) {
        List<ObjectTransformation> transformations = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        
        // Only add AI errors as warnings if they're meaningful
        for (String error : aiErrors) {
            if (!error.contains("AI response parsing failed") && !error.contains("No transformations found")) {
                warnings.add(error);
            }
        }
        
        // Try add pattern (object creation)
        Matcher addMatcher = ADD_PATTERN.matcher(command);
        if (addMatcher.find()) {
            String qtyStr = addMatcher.group(1);
            int qty = qtyStr != null ? Integer.parseInt(qtyStr) : 1;
            String objType = addMatcher.group(2).trim();
            
            // Clean up object type by removing any positioning keywords that might have been captured
            objType = cleanObjectType(objType);

            Vec3f pos = getSmartDefaultPosition(context);
            
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
            
            // Create objects with distributed positions to avoid overlap
            List<Vec3f> distributedPositions = distributeObjectPositions(pos, qty, objType);
            for (int i = 0; i < qty; i++) {
                Vec3f objPos = distributedPositions.get(i);
                transformations.add(ObjectTransformation.addObject(objType, objPos, "Add object " + objType + " at " + formatVector(objPos)));
            }
        }
        
        // Try Link ID pattern (handle "coin with link id 0" type references)
        Matcher linkIdMatcher = LINK_ID_PATTERN.matcher(command);
        if (linkIdMatcher.find()) {
            String objectType = linkIdMatcher.group(1);
            int linkId = Integer.parseInt(linkIdMatcher.group(2));
            
            // Find the specific object by Link ID
            GalaxyContext.ObjectInfo targetObject = findObjectById(linkId, context);
            if (targetObject != null) {
                // Now handle the rest of the command based on what comes after
                String remainingCommand = command.substring(linkIdMatcher.end()).trim();
                
                // Try to parse the remaining command for transformations
                if (remainingCommand.contains("move") || remainingCommand.contains("to")) {
                    // Handle move/position commands
                    Pattern moveToPattern = Pattern.compile(
                        "(?:move\\s+)?to\\s*\\(\\s*([+-]?\\d+(?:\\.\\d+)?)\\s*,\\s*([+-]?\\d+(?:\\.\\d+)?)\\s*,\\s*([+-]?\\d+(?:\\.\\d+)?)\\s*\\)",
                        Pattern.CASE_INSENSITIVE
                    );
                    Matcher moveToMatcher = moveToPattern.matcher(remainingCommand);
                    if (moveToMatcher.find()) {
                        float x = Float.parseFloat(moveToMatcher.group(1));
                        float y = Float.parseFloat(moveToMatcher.group(2));
                        float z = Float.parseFloat(moveToMatcher.group(3));
                        
                        transformations.add(new ObjectTransformation.Builder()
                            .setObjectId(targetObject.getUniqueId())
                            .setType(ObjectTransformation.TransformationType.SET_POSITION)
                            .setVectorValue(x, y, z)
                            .setDescription("Move " + targetObject.getDisplayName() + " (ID: " + linkId + ") to position (" + x + ", " + y + ", " + z + ")")
                            .build());
                    }
                } else if (remainingCommand.contains("rotate")) {
                    // Handle rotate commands
                    Pattern rotatePattern = Pattern.compile(
                        "rotate\\s+(?:by\\s+)?(\\d+(?:\\.\\d+)?)\\s+degrees?",
                        Pattern.CASE_INSENSITIVE
                    );
                    Matcher rotateMatcher = rotatePattern.matcher(remainingCommand);
                    if (rotateMatcher.find()) {
                        float degrees = Float.parseFloat(rotateMatcher.group(1));
                        
                        transformations.add(ObjectTransformation.rotate(
                            targetObject.getUniqueId(), new Vec3f(0, degrees, 0),
                            "Rotate " + targetObject.getDisplayName() + " (ID: " + linkId + ") " + degrees + " degrees"));
                    }
                } else if (remainingCommand.contains("scale")) {
                    // Handle scale commands
                    Pattern scalePattern = Pattern.compile(
                        "scale\\s+(?:by\\s+)?(\\d+(?:\\.\\d+)?)x?",
                        Pattern.CASE_INSENSITIVE
                    );
                    Matcher scaleMatcher = scalePattern.matcher(remainingCommand);
                    if (scaleMatcher.find()) {
                        float scale = Float.parseFloat(scaleMatcher.group(1));
                        
                        transformations.add(ObjectTransformation.scale(
                            targetObject.getUniqueId(), new Vec3f(scale, scale, scale),
                            "Scale " + targetObject.getDisplayName() + " (ID: " + linkId + ") by " + scale + "x"));
                    }
                }
            } else {
                warnings.add("No object found with Link ID: " + linkId);
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
        
        // Try create pattern (alternative to add)
        Matcher createMatcher = CREATE_PATTERN.matcher(command);
        if (createMatcher.find()) {
            String qtyStr = createMatcher.group(1);
            int qty = qtyStr != null ? Integer.parseInt(qtyStr) : 1;
            String objType = createMatcher.group(2).trim();
            objType = cleanObjectType(objType);

            Vec3f pos = getSmartDefaultPosition(context);
            
            // Check for absolute coordinates (group 3-5)
            if (createMatcher.group(3) != null) {
                float x = Float.parseFloat(createMatcher.group(3));
                float y = Float.parseFloat(createMatcher.group(4));
                float z = Float.parseFloat(createMatcher.group(5));
                pos = new Vec3f(x, y, z);
            }
            // Check for relative positioning (group 6-7)
            else if (createMatcher.group(6) != null) {
                String relativePos = createMatcher.group(6).toLowerCase();
                String targetObject = createMatcher.group(7);
                
                List<GalaxyContext.ObjectInfo> targetObjects = findObjectsByReference(targetObject, context);
                if (!targetObjects.isEmpty()) {
                    GalaxyContext.ObjectInfo target = targetObjects.get(0);
                    Vec3f targetPos = target.getPosition();
                    
                    switch (relativePos) {
                        case "above":
                            pos = new Vec3f(targetPos.x, targetPos.y + 100, targetPos.z);
                            break;
                        case "below":
                            pos = new Vec3f(targetPos.x, targetPos.y - 100, targetPos.z);
                            break;
                        case "near":
                        case "around":
                            pos = new Vec3f(targetPos.x + 50, targetPos.y, targetPos.z + 50);
                            break;
                        case "behind":
                            pos = new Vec3f(targetPos.x, targetPos.y, targetPos.z - 100);
                            break;
                        case "in front of":
                            pos = new Vec3f(targetPos.x, targetPos.y, targetPos.z + 100);
                            break;
                        default:
                            pos = targetPos;
                            break;
                    }
                }
            }
            
            List<Vec3f> distributedPositions = distributeObjectPositions(pos, qty, objType);
            for (int i = 0; i < qty; i++) {
                Vec3f objPos = distributedPositions.get(i);
                transformations.add(ObjectTransformation.addObject(objType, objPos, "Create object " + objType + " at " + formatVector(objPos)));
            }
        }
        
        // Try move to pattern (move object to another object)
        Matcher moveToMatcher = MOVE_TO_PATTERN.matcher(command);
        if (moveToMatcher.find() && transformations.isEmpty()) {
            String sourceRef = moveToMatcher.group(1);
            String targetRef = moveToMatcher.group(2);
            
            List<GalaxyContext.ObjectInfo> sourceObjects = findObjectsByReference(sourceRef, context);
            List<GalaxyContext.ObjectInfo> targetObjects = findObjectsByReference(targetRef, context);
            
            if (!sourceObjects.isEmpty() && !targetObjects.isEmpty()) {
                GalaxyContext.ObjectInfo target = targetObjects.get(0);
                Vec3f targetPos = target.getPosition();
                
                for (GalaxyContext.ObjectInfo source : sourceObjects) {
                    transformations.add(new ObjectTransformation.Builder()
                        .setObjectId(source.getUniqueId())
                        .setType(ObjectTransformation.TransformationType.SET_POSITION)
                        .setVectorValue(targetPos)
                        .setDescription("Move " + source.getDisplayName() + " to " + target.getDisplayName())
                        .build());
                }
            }
        }
        
        // Try teleport pattern
        Matcher teleportMatcher = TELEPORT_PATTERN.matcher(command);
        if (teleportMatcher.find() && transformations.isEmpty()) {
            String objectRef = teleportMatcher.group(1);
            float x = Float.parseFloat(teleportMatcher.group(2));
            float y = Float.parseFloat(teleportMatcher.group(3));
            float z = Float.parseFloat(teleportMatcher.group(4));
            
            List<GalaxyContext.ObjectInfo> objects = findObjectsByReference(objectRef, context);
            
            for (GalaxyContext.ObjectInfo obj : objects) {
                transformations.add(new ObjectTransformation.Builder()
                    .setObjectId(obj.getUniqueId())
                    .setType(ObjectTransformation.TransformationType.SET_POSITION)
                    .setVectorValue(x, y, z)
                    .setDescription("Teleport " + obj.getDisplayName() + " to position (" + x + ", " + y + ", " + z + ")")
                    .build());
            }
        }
        
        // Try property change pattern
        Matcher propertyMatcher = PROPERTY_PATTERN.matcher(command);
        if (propertyMatcher.find() && transformations.isEmpty()) {
            String objectRef = propertyMatcher.group(1);
            String property = propertyMatcher.group(2);
            String value = propertyMatcher.group(3);
            
            List<GalaxyContext.ObjectInfo> objects = findObjectsByReference(objectRef, context);
            
            for (GalaxyContext.ObjectInfo obj : objects) {
                Map<String, Object> properties = new HashMap<>();
                properties.put(property, value);
                transformations.add(ObjectTransformation.changeProperty(
                    obj.getUniqueId(), properties,
                    "Change " + obj.getDisplayName() + " " + property + " to " + value));
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
            "Successfully processed command: " + command, 
            "Applied " + transformations.size() + " transformation(s)",
            warnings);
    }
    
    /**
     * Finds objects in the context by reference (name, type, or tag).
     * Returns all potential matches for disambiguation.
     */
    private List<GalaxyContext.ObjectInfo> findObjectsByReference(String reference, GalaxyContext context) {
        // Use the improved ObjectResolver instead of the simple matching logic
        ObjectResolver resolver = new ObjectResolver(context);
        ObjectResolver.ObjectResolutionResult result = resolver.resolveObjects(reference);
        
        if (result.isSuccess()) {
            return result.getObjects();
        } else {
            // If resolution fails, return empty list
            System.out.println("DEBUG: Object resolution failed: " + result.getErrorMessage());
            return new ArrayList<>();
        }
    }
    
    /**
     * Finds objects with disambiguation support for UI interaction.
     * This method should be called from UI context to handle ambiguous references.
     */
    public List<GalaxyContext.ObjectInfo> findObjectsWithDisambiguation(String reference, 
                                                                        GalaxyContext context, 
                                                                        String fullCommand,
                                                                        Component parent) {
        // Use the improved ObjectResolver
        ObjectResolver resolver = new ObjectResolver(context);
        ObjectResolver.ObjectResolutionResult result = resolver.resolveObjects(reference);
        
        if (!result.isSuccess()) {
            return new ArrayList<>();
        }
        
        List<GalaxyContext.ObjectInfo> candidates = result.getObjects();
        
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
     * Extracts JSON object from text response, removing markdown formatting.
     */
    private String extractJSON(String text) {
        if (text == null) return null;
        
        System.out.println("DEBUG: Extracting JSON from text (length: " + text.length() + ")");
        
        // Remove markdown code blocks and formatting
        String cleaned = text.replaceAll("```json\\s*", "").replaceAll("```\\s*", "");
        cleaned = cleaned.replaceAll("\\*\\*.*?\\*\\*", ""); // Remove bold text
        cleaned = cleaned.replaceAll("\\*.*?\\*", ""); // Remove italic text
        cleaned = cleaned.replaceAll("`.*?`", ""); // Remove inline code
        
        // Remove common explanatory text patterns from Ollama
        cleaned = cleaned.replaceAll("Based on your instructions, here's how I would respond.*?:", "");
        cleaned = cleaned.replaceAll("Based on the user command.*?:", "");
        cleaned = cleaned.replaceAll("Here's the JSON response.*?:", "");
        cleaned = cleaned.replaceAll("In this case.*?:", "");
        cleaned = cleaned.replaceAll("I've assumed.*?:", "");
        cleaned = cleaned.replaceAll("If there were multiple.*?:", "");
        cleaned = cleaned.replaceAll("Based on your command.*?:", "");
        cleaned = cleaned.replaceAll("You'd like to add.*?:", "");
        cleaned = cleaned.replaceAll("Please specify.*?:", "");
        cleaned = cleaned.replaceAll("Once you've decided.*?:", "");
        cleaned = cleaned.replaceAll("I'll provide.*?:", "");
        cleaned = cleaned.replaceAll("Here are some options.*?:", "");
        
        // Remove numbered lists and bullet points
        cleaned = cleaned.replaceAll("\\d+\\.\\s*\\*\\*.*?\\*\\*.*?\\n", "");
        cleaned = cleaned.replaceAll("-\\s*\\*\\*.*?\\*\\*.*?\\n", "");
        
        // Remove explanatory text in parentheses
        cleaned = cleaned.replaceAll("\\([^)]*[Aa]ssumes[^)]*\\)", "");
        cleaned = cleaned.replaceAll("\\([^)]*[Gg]eneric[^)]*\\)", "");
        cleaned = cleaned.replaceAll("\\([^)]*[Pp]osition[^)]*\\)", "");
        cleaned = cleaned.replaceAll("\\([^)]*[Cc]oordinate[^)]*\\)", "");
        
        // Find JSON object boundaries
        int start = cleaned.indexOf('{');
        int end = cleaned.lastIndexOf('}');
        
        if (start != -1 && end != -1 && end > start) {
            String json = cleaned.substring(start, end + 1);
            
            // Additional cleanup for smaller models that might include extra text
            // Remove any text before the first {
            if (json.contains("Based on") || json.contains("here is") || json.contains("JSON response")) {
                int jsonStart = json.indexOf('{');
                if (jsonStart != -1) {
                    json = json.substring(jsonStart);
                }
            }
            
            // Remove any text after the last }
            int jsonEnd = json.lastIndexOf('}');
            if (jsonEnd != -1) {
                json = json.substring(0, jsonEnd + 1);
            }
            
            System.out.println("DEBUG: Successfully extracted JSON (length: " + json.length() + ")");
            return json;
        }
        
        System.out.println("DEBUG: Failed to extract JSON - no valid JSON found");
        return null;
    }
    
    /**
     * Formats a Vec3f for display.
     */
    private String formatVector(Vec3f vec) {
        return String.format("(%.1f, %.1f, %.1f)", vec.x, vec.y, vec.z);
    }
    
    /**
     * Cleans up object type by removing positioning keywords and extra text.
     * This fixes issues where the regex captures too much text as the object type.
     */
    public String cleanObjectType(String objType) {
        if (objType == null || objType.isEmpty()) {
            return objType;
        }
        
        // First, handle the specific case where the regex captured too much
        // This happens when the pattern captures "coins 5 unit" instead of just "coins"
        String normalized = objType.trim();
        String lowerNormalized = normalized.toLowerCase();
        
        // Remove common positioning words that might have been captured
        normalized = normalized.replaceAll("\\b(above|below|near|behind|around|in front of)\\b", "")
            .replaceAll("\\s+", " ")
            .trim();
        lowerNormalized = normalized.toLowerCase();
        
        // Check if the object type contains numbers or "unit" - this indicates regex capture error
        if (lowerNormalized.matches(".*\\d+.*") || lowerNormalized.contains("unit")) {
            // Extract just the object name part (before any numbers or "unit")
            String[] parts = lowerNormalized.split("\\s+");
            for (int i = 0; i < parts.length; i++) {
                String part = parts[i];
                // If this part is a number or "unit", stop here and use the previous parts
                if (part.matches("\\d+") || part.equals("unit") || part.equals("units")) {
                    if (i > 0) {
                        // Reconstruct the object name from the parts before the number/unit
                        StringBuilder objName = new StringBuilder();
                        for (int j = 0; j < i; j++) {
                            if (j > 0) objName.append(" ");
                            objName.append(parts[j]);
                        }
                        normalized = objName.toString();
                        lowerNormalized = normalized.toLowerCase();
                        break;
                    }
                }
            }
        }
        
        // Remove common positioning keywords and everything after them
        String[] positioningKeywords = {
            "near", "above", "below", "behind", "in front of", "at position", "at"
        };
        
        for (String keyword : positioningKeywords) {
            int index = lowerNormalized.indexOf(keyword);
            if (index > 0) {
                // Return the part before the positioning keyword
                return objType.substring(0, index).trim();
            }
        }
        
        // Handle common plural to singular conversions
        if (lowerNormalized.endsWith("s")) {
            switch (lowerNormalized) {
                case "coins":
                    return "Coin";
                case "stars":
                    return "Star";
                case "goombas":
                    return "Goomba";
                case "koopas":
                    return "Koopa";
                case "platforms":
                    return "Platform";
                case "blocks":
                    return "Block";
                case "pipes":
                    return "Pipe";
                case "switches":
                    return "Switch";
                case "doors":
                    return "Door";
                case "springs":
                    return "Spring";
                case "checkpoints":
                    return "Checkpoint";
                case "flags":
                    return "Flag";
                case "goals":
                    return "Goal";
                case "bosses":
                    return "Boss";
                case "items":
                    return "Item";
                case "powerups":
                    return "PowerUp";
                case "collectibles":
                    return "Collectible";
                case "purplecoins":
                    return "PurpleCoin";
                case "starbits":
                    return "StarBit";
                case "1ups":
                case "oneups":
                    return "1Up";
                case "enemies":
                    return "Enemy";
                case "npcs":
                    return "NPC";
                case "vehicles":
                    return "Vehicle";
                case "hazards":
                    return "Hazard";
                case "effects":
                    return "Effect";
                case "cameras":
                    return "Camera";
                case "sounds":
                    return "Sound";
                default:
                    // For other plurals, just remove the 's'
                    if (lowerNormalized.length() > 1) {
                        return normalized.substring(0, normalized.length() - 1);
                    }
            }
        }
        
        // Handle common variations (multi-word names)
        switch (lowerNormalized) {
            case "question block":
            case "questionblock":
                return "QuestionBlock";
            case "moving platform":
            case "movingplatform":
                return "MovingPlatform";
            case "rotating platform":
            case "rotatingplatform":
                return "RotatingPlatform";
            case "breakable platform":
            case "breakableplatform":
                return "BreakablePlatform";
            case "power up":
            case "powerup":
                return "PowerUp";
            case "star bit":
            case "starbit":
                return "StarBit";
            case "purple coin":
            case "purplecoin":
                return "PurpleCoin";
            case "hammer bro":
            case "hammerbro":
                return "HammerBros";
            case "koopa jr":
            case "koopajr":
                return "KoopaJr";
            case "boss koopa":
            case "bosskoopa":
                return "BossKoopa";
            case "boss bowser":
            case "bossbowser":
                return "BossBowser";
            case "boss kameck":
            case "bosskameck":
                return "BossKameck";
            case "luigi npc":
            case "luiginpc":
                return "LuigiNPC";
            case "particle generator":
            case "particlegenerator":
                return "ParticleGenerator";
            case "sound obj":
            case "soundobj":
                return "SoundObj";
            case "sound area":
            case "soundarea":
                return "SoundArea";
            case "bgm change area":
            case "bgmchangearea":
                return "BgmChangeArea";
            case "camera rail":
            case "camerarail":
                return "CameraRail";
            case "camera area":
            case "cameraarea":
                return "CameraArea";
        }
        
        return normalized;
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
    
    /**
     * Distributes multiple objects in a pattern to avoid overlap.
     * Creates a grid or circular pattern based on the number of objects.
     */
    private List<Vec3f> distributeObjectPositions(Vec3f basePosition, int quantity, String objectType) {
        List<Vec3f> positions = new ArrayList<>();
        
        if (quantity == 1) {
            positions.add(new Vec3f(basePosition));
            return positions;
        }
        
        // Determine spacing based on object type
        float spacing = getObjectSpacing(objectType);
        
        if (quantity <= 4) {
            // For small quantities, arrange in a line
            return distributeInLine(basePosition, quantity, spacing);
        } else if (quantity <= 9) {
            // For medium quantities, arrange in a grid
            return distributeInGrid(basePosition, quantity, spacing);
        } else {
            // For large quantities, arrange in a circle
            return distributeInCircle(basePosition, quantity, spacing);
        }
    }
    
    /**
     * Distributes objects in a line pattern.
     */
    private List<Vec3f> distributeInLine(Vec3f basePosition, int quantity, float spacing) {
        List<Vec3f> positions = new ArrayList<>();
        
        // Center the line around the base position
        float startOffset = -(quantity - 1) * spacing / 2.0f;
        
        for (int i = 0; i < quantity; i++) {
            float offset = startOffset + i * spacing;
            // Distribute along X-axis by default
            Vec3f pos = new Vec3f(basePosition.x + offset, basePosition.y, basePosition.z);
            positions.add(pos);
        }
        
        return positions;
    }
    
    /**
     * Distributes objects in a grid pattern.
     */
    private List<Vec3f> distributeInGrid(Vec3f basePosition, int quantity, float spacing) {
        List<Vec3f> positions = new ArrayList<>();
        
        // Calculate grid dimensions
        int cols = (int) Math.ceil(Math.sqrt(quantity));
        int rows = (int) Math.ceil((double) quantity / cols);
        
        // Center the grid around the base position
        float startX = -(cols - 1) * spacing / 2.0f;
        float startZ = -(rows - 1) * spacing / 2.0f;
        
        int objectIndex = 0;
        for (int row = 0; row < rows && objectIndex < quantity; row++) {
            for (int col = 0; col < cols && objectIndex < quantity; col++) {
                float x = basePosition.x + startX + col * spacing;
                float z = basePosition.z + startZ + row * spacing;
                Vec3f pos = new Vec3f(x, basePosition.y, z);
                positions.add(pos);
                objectIndex++;
            }
        }
        
        return positions;
    }
    
    /**
     * Distributes objects in a circular pattern.
     */
    private List<Vec3f> distributeInCircle(Vec3f basePosition, int quantity, float spacing) {
        List<Vec3f> positions = new ArrayList<>();
        
        // Calculate radius based on spacing and quantity
        float radius = (quantity * spacing) / (2.0f * (float) Math.PI);
        
        for (int i = 0; i < quantity; i++) {
            double angle = 2.0 * Math.PI * i / quantity;
            float x = basePosition.x + radius * (float) Math.cos(angle);
            float z = basePosition.z + radius * (float) Math.sin(angle);
            Vec3f pos = new Vec3f(x, basePosition.y, z);
            positions.add(pos);
        }
        
        return positions;
    }
    
    /**
     * Gets appropriate spacing for different object types.
     */
    private float getObjectSpacing(String objectType) {
        String lowerType = objectType.toLowerCase();
        
        // Smaller objects need less spacing
        if (lowerType.contains("coin") || lowerType.contains("star") || lowerType.contains("bit")) {
            return 50.0f;
        }
        // Medium objects
        else if (lowerType.contains("goomba") || lowerType.contains("koopa") || lowerType.contains("block")) {
            return 100.0f;
        }
        // Large objects
        else if (lowerType.contains("platform") || lowerType.contains("pipe") || lowerType.contains("ship")) {
            return 200.0f;
        }
        // Default spacing
        else {
            return 75.0f;
        }
    }
    /**
     * Gets a smart default position for new objects.
     * Tries to find Mario and position objects above and in front of him.
     * Falls back to origin if Mario is not found.
     */
    private Vec3f getSmartDefaultPosition(GalaxyContext context) {
        // Try to find Mario (common names for Mario objects)
        String[] marioNames = {"Mario", "mario", "MarioActor", "Player", "player", "PlayerActor"};
        
        for (String marioName : marioNames) {
            List<GalaxyContext.ObjectInfo> marioObjects = findObjectsByReference(marioName, context);
            if (!marioObjects.isEmpty()) {
                GalaxyContext.ObjectInfo mario = marioObjects.get(0);
                Vec3f marioPos = mario.getPosition();
                
                // Position objects 200 units above Mario and 200 units in front (positive Z)
                return new Vec3f(marioPos.x, marioPos.y + 200, marioPos.z + 200);
            }
        }
        
        // Try to find spawn objects (which might be Mario spawn points)
        List<GalaxyContext.ObjectInfo> spawnObjects = findObjectsByReference("spawn", context);
        if (!spawnObjects.isEmpty()) {
            GalaxyContext.ObjectInfo spawn = spawnObjects.get(0);
            Vec3f spawnPos = spawn.getPosition();
            System.out.println("DEBUG: Using spawn object for positioning: " + spawn.getName() + " at " + spawnPos);
            return new Vec3f(spawnPos.x, spawnPos.y + 200, spawnPos.z + 200);
        }
        
        // Try to find any object with "Start" in the name (start points)
        List<GalaxyContext.ObjectInfo> startObjects = findObjectsByReference("start", context);
        if (!startObjects.isEmpty()) {
            GalaxyContext.ObjectInfo start = startObjects.get(0);
            Vec3f startPos = start.getPosition();
            System.out.println("DEBUG: Using start object for positioning: " + start.getName() + " at " + startPos);
            return new Vec3f(startPos.x, startPos.y + 200, startPos.z + 200);
        }
        
        // Try to find any object with "Spawn" in the name
        List<GalaxyContext.ObjectInfo> spawnNameObjects = findObjectsByReference("Spawn", context);
        if (!spawnNameObjects.isEmpty()) {
            GalaxyContext.ObjectInfo spawn = spawnNameObjects.get(0);
            Vec3f spawnPos = spawn.getPosition();
            System.out.println("DEBUG: Using Spawn object for positioning: " + spawn.getName() + " at " + spawnPos);
            return new Vec3f(spawnPos.x, spawnPos.y + 200, spawnPos.z + 200);
        }
        
        // If no reference objects found, use a reasonable default position
        // This is better than (0,0,0) which might be inside geometry
        System.out.println("DEBUG: No reference objects found, using default position");
        return new Vec3f(0, 200, 200);
    }

    /**
     * Finds character objects (Luigi, Mario, Peach, etc.) in the context.
     * Returns a formatted string listing them.
     */
    private String findCharacterObjects(GalaxyContext context) {
        StringBuilder characterList = new StringBuilder();
        List<GalaxyContext.ObjectInfo> characterObjects = new ArrayList<>();
        
        // Add common character names to the list
        String[] characterNames = {"Luigi", "Mario", "Peach", "Toad", "Rosetta", "Kinopio", "LuigiNPC", "MarioActor", "Player", "PlayerActor"};
        
        for (String name : characterNames) {
            List<GalaxyContext.ObjectInfo> foundObjects = findObjectsByReference(name, context);
            if (!foundObjects.isEmpty()) {
                characterObjects.addAll(foundObjects);
            }
        }
        
        if (characterObjects.isEmpty()) {
            return ""; // No character objects found
        }
        
        // Sort by name for consistent output
        characterObjects.sort(Comparator.comparing(GalaxyContext.ObjectInfo::getDisplayName));
        
        for (GalaxyContext.ObjectInfo obj : characterObjects) {
            characterList.append("- ").append(obj.getDisplayName()).append(" (").append(obj.getName()).append(")\n");
        }
        
        return characterList.toString();
    }
}

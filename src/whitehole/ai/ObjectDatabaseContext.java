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

import whitehole.db.ObjectDB;
import whitehole.db.ObjectDB.ObjectInfo;
import whitehole.db.ObjectDB.ClassInfo;
import whitehole.db.ObjectDB.CategoryInfo;
import whitehole.db.ObjectDB.PropertyInfo;
import org.json.JSONObject;
import org.json.JSONArray;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Manages object database context for AI providers.
 * Provides comprehensive information about available game objects,
 * their properties, and categories to help AI understand what objects
 * can be created and manipulated.
 */
public class ObjectDatabaseContext {
    
    private static ObjectDatabaseContext instance;
    private boolean initialized = false;
    
    private ObjectDatabaseContext() {}
    
    public static ObjectDatabaseContext getInstance() {
        if (instance == null) {
            instance = new ObjectDatabaseContext();
        }
        return instance;
    }
    
    /**
     * Initializes the object database context.
     * Should be called after ObjectDB.init() has been called.
     */
    public void initialize() {
        if (initialized) return;
        
        // Ensure ObjectDB is initialized
        try {
            // This will load the database if not already loaded
            ObjectDB.getObjectInfos();
            initialized = true;
        } catch (Exception e) {
            System.err.println("Failed to initialize ObjectDatabaseContext: " + e.getMessage());
        }
    }
    
    /**
     * Gets a comprehensive summary of the object database for AI context.
     * This includes categories, popular objects, and general information.
     */
    public String getDatabaseSummary() {
        if (!initialized) {
            initialize();
        }
        
        StringBuilder sb = new StringBuilder();
        sb.append("=== SUPER MARIO GALAXY OBJECT DATABASE ===\n\n");
        
        // Add categories
        List<CategoryInfo> categories = ObjectDB.getCategories();
        sb.append("Available Object Categories (").append(categories.size()).append("):\n");
        for (CategoryInfo category : categories) {
            sb.append("- ").append(category.toString()).append(": ").append(category.getDescription()).append("\n");
        }
        sb.append("\n");
        
        // Add popular objects by category
        Map<String, ObjectInfo> allObjects = ObjectDB.getObjectInfos();
        Map<String, List<ObjectInfo>> objectsByCategory = allObjects.values().stream()
            .filter(obj -> obj.isValid() && !obj.isUnused() && !obj.isLeftover())
            .collect(Collectors.groupingBy(ObjectInfo::category));
        
        sb.append("Popular Objects by Category:\n");
        for (Map.Entry<String, List<ObjectInfo>> entry : objectsByCategory.entrySet()) {
            String category = entry.getKey();
            List<ObjectInfo> objects = entry.getValue();
            
            if (objects.size() > 0) {
                sb.append("\n").append(category.toUpperCase()).append(" (").append(objects.size()).append(" objects):\n");
                
                // Show top 10 most relevant objects per category
                List<ObjectInfo> topObjects = objects.stream()
                    .sorted((a, b) -> {
                        // Sort by relevance: higher progress first, then by name
                        int progressCompare = Integer.compare(b.progress(), a.progress());
                        if (progressCompare != 0) return progressCompare;
                        return a.toString().compareToIgnoreCase(b.toString());
                    })
                    .limit(10)
                    .collect(Collectors.toList());
                
                for (ObjectInfo obj : topObjects) {
                    sb.append("  - ").append(obj.toString())
                      .append(" (").append(obj.internalName()).append(")")
                      .append(" - ").append(obj.description())
                      .append("\n");
                }
            }
        }
        
        return sb.toString();
    }
    
    /**
     * Gets detailed information about a specific object for AI context.
     */
    public String getObjectDetails(String objectName) {
        if (!initialized) {
            initialize();
        }
        
        ObjectInfo obj = ObjectDB.getObjectInfo(objectName);
        if (obj == null || !obj.isValid()) {
            return "Object '" + objectName + "' not found in database.";
        }
        
        StringBuilder sb = new StringBuilder();
        sb.append("=== OBJECT DETAILS: ").append(obj.toString()).append(" ===\n\n");
        
        // Basic information
        sb.append("Internal Name: ").append(obj.internalName()).append("\n");
        sb.append("Category: ").append(obj.category()).append("\n");
        sb.append("Description: ").append(obj.description()).append("\n");
        sb.append("Area Shape: ").append(obj.areaShape()).append("\n");
        sb.append("Games: ").append(obj.games()).append("\n");
        sb.append("Progress: ").append(obj.progress()).append("\n");
        sb.append("Unused: ").append(obj.isUnused()).append("\n");
        sb.append("Leftover: ").append(obj.isLeftover()).append("\n\n");
        
        // Class information for both games
        for (int game = 1; game <= 2; game++) {
            String gameName = (game == 1) ? "SMG1" : "SMG2";
            String className = obj.className(game);
            ClassInfo classInfo = obj.classInfo(game);
            
            sb.append(gameName).append(" Class: ").append(className).append("\n");
            if (classInfo != null && !classInfo.description().isEmpty()) {
                sb.append(gameName).append(" Description: ").append(classInfo.description()).append("\n");
            }
            
            // Properties
            if (classInfo != null && classInfo.properties() != null) {
                sb.append(gameName).append(" Properties:\n");
                for (Map.Entry<String, PropertyInfo> propEntry : classInfo.properties().entrySet()) {
                    PropertyInfo prop = propEntry.getValue();
                    sb.append("  - ").append(prop.simpleName())
                      .append(": ").append(prop.description())
                      .append(" [Needed: ").append(prop.needed()).append("]")
                      .append("\n");
                    
                    // Show possible values if available
                    List<String> values = prop.values();
                    if (values != null && !values.isEmpty()) {
                        sb.append("    Values: ").append(String.join(", ", values)).append("\n");
                    }
                }
            }
            sb.append("\n");
        }
        
        return sb.toString();
    }
    
    /**
     * Gets a list of objects matching a search term for AI context.
     */
    public String searchObjects(String searchTerm) {
        if (!initialized) {
            initialize();
        }
        
        if (searchTerm == null || searchTerm.trim().isEmpty()) {
            return "Please provide a search term.";
        }
        
        String term = searchTerm.toLowerCase();
        Map<String, ObjectInfo> allObjects = ObjectDB.getObjectInfos();
        
        List<ObjectInfo> matches = allObjects.values().stream()
            .filter(obj -> obj.isValid() && !obj.isUnused() && !obj.isLeftover())
            .filter(obj -> 
                obj.toString().toLowerCase().contains(term) ||
                obj.internalName().toLowerCase().contains(term) ||
                obj.description().toLowerCase().contains(term) ||
                obj.category().toLowerCase().contains(term)
            )
            .sorted((a, b) -> {
                // Sort by relevance: exact matches first, then by progress
                boolean aExact = a.toString().toLowerCase().equals(term) || 
                                a.internalName().toLowerCase().equals(term);
                boolean bExact = b.toString().toLowerCase().equals(term) || 
                                b.internalName().toLowerCase().equals(term);
                
                if (aExact && !bExact) return -1;
                if (!aExact && bExact) return 1;
                
                return Integer.compare(b.progress(), a.progress());
            })
            .limit(20) // Limit results
            .collect(Collectors.toList());
        
        if (matches.isEmpty()) {
            return "No objects found matching '" + searchTerm + "'.";
        }
        
        StringBuilder sb = new StringBuilder();
        sb.append("=== SEARCH RESULTS FOR '").append(searchTerm).append("' ===\n\n");
        sb.append("Found ").append(matches.size()).append(" objects:\n\n");
        
        for (ObjectInfo obj : matches) {
            sb.append("- ").append(obj.toString())
              .append(" (").append(obj.internalName()).append(")")
              .append(" - ").append(obj.category())
              .append(" - ").append(obj.description())
              .append("\n");
        }
        
        return sb.toString();
    }
    
    /**
     * Gets a comprehensive context string for AI providers.
     * This includes database summary and commonly used objects.
     */
    public String getFullContext() {
        if (!initialized) {
            initialize();
        }
        
        StringBuilder sb = new StringBuilder();
        sb.append(getDatabaseSummary());
        sb.append("\n=== COMMONLY USED OBJECTS ===\n\n");
        
        // Add some commonly used objects
        String[] commonObjects = {
            "Coin", "PurpleCoin", "Star", "StarBit", "1Up", "Mushroom", "FireFlower", "IceFlower",
            "Goomba", "Koopa", "QuestionBlock", "BrickBlock", "WoodBlock", "Switch", "Door", "Pipe",
            "Spring", "Platform", "MovingPlatform", "Checkpoint", "Flag", "Goal", "Enemy", "Boss"
        };
        
        for (String objName : commonObjects) {
            ObjectInfo obj = ObjectDB.getObjectInfo(objName);
            if (obj != null && obj.isValid()) {
                sb.append("- ").append(obj.toString())
                  .append(" (").append(obj.internalName()).append(")")
                  .append(" - ").append(obj.category())
                  .append(": ").append(obj.description())
                  .append("\n");
            }
        }
        
        sb.append("\n=== USAGE INSTRUCTIONS ===\n");
        sb.append("When creating objects, use the internal name (e.g., 'Coin' not 'coin').\n");
        sb.append("Objects are categorized by type (enemies, collectibles, blocks, etc.).\n");
        sb.append("Each object has specific properties that can be modified.\n");
        sb.append("Use the search function to find specific objects or browse by category.\n");
        
        return sb.toString();
    }
    
    /**
     * Gets a minimal context string for quick AI reference.
     */
    public String getMinimalContext() {
        if (!initialized) {
            initialize();
        }
        
        StringBuilder sb = new StringBuilder();
        sb.append("=== SUPER MARIO GALAXY OBJECT DATABASE ===\n\n");
        
        // Add categories
        List<CategoryInfo> categories = ObjectDB.getCategories();
        sb.append("Available Object Categories (").append(categories.size()).append("):\n");
        for (CategoryInfo category : categories) {
            sb.append("- ").append(category.toString()).append(": ").append(category.getDescription()).append("\n");
        }
        sb.append("\n");
        
        // Add popular objects by category with descriptions
        Map<String, ObjectInfo> allObjects = ObjectDB.getObjectInfos();
        Map<String, List<ObjectInfo>> objectsByCategory = allObjects.values().stream()
            .filter(obj -> obj.isValid() && !obj.isUnused() && !obj.isLeftover())
            .collect(Collectors.groupingBy(ObjectInfo::category));
        
        sb.append("Popular Objects by Category:\n");
        for (Map.Entry<String, List<ObjectInfo>> entry : objectsByCategory.entrySet()) {
            String category = entry.getKey();
            List<ObjectInfo> objects = entry.getValue();
            
            if (objects.size() > 0) {
                sb.append("\n").append(category.toUpperCase()).append(" (").append(objects.size()).append(" objects):\n");
                
                // Show top 15 most relevant objects per category
                List<ObjectInfo> topObjects = objects.stream()
                    .sorted((a, b) -> {
                        // Sort by relevance: higher progress first, then by name
                        int progressCompare = Integer.compare(b.progress(), a.progress());
                        if (progressCompare != 0) return progressCompare;
                        return a.toString().compareToIgnoreCase(b.toString());
                    })
                    .limit(15)
                    .collect(Collectors.toList());
                
                for (ObjectInfo obj : topObjects) {
                    sb.append("  - ").append(obj.toString())
                      .append(" (").append(obj.internalName()).append(")")
                      .append(" - ").append(obj.description())
                      .append("\n");
                }
            }
        }
        
        sb.append("\n=== OBJECT USAGE GUIDELINES ===\n");
        sb.append("- Use exact object names as shown above (e.g., 'Coin', 'Goomba', 'QuestionBlock')\n");
        sb.append("- Common collectibles: Coin, PurpleCoin, StarBit, 1Up\n");
        sb.append("- Common enemies: Goomba, Koopa, KoopaJr, HammerBros\n");
        sb.append("- Common platforms: Platform, MovingPlatform, RotatingPlatform\n");
        sb.append("- Common interactive: QuestionBlock, Switch, Door, Pipe\n");
        sb.append("- Common decorative: Tree, Flower, Rock, Signboard\n");
        sb.append("- Common hazards: Lava, Spike, FireBar, Thwomp\n");
        sb.append("- Common power-ups: PowerUpInvincible, PowerUpFire, PowerUpIce\n");
        sb.append("- Common vehicles: StarShip, KoopaJrShip, Airship\n");
        sb.append("- Common NPCs: LuigiNPC, Kinopio, Rosetta\n");
        sb.append("- Common bosses: BossKameck, BossKoopa, BossBowser\n");
        
        return sb.toString();
    }
    
    /**
     * Checks if an object exists in the database.
     */
    public boolean objectExists(String objectName) {
        if (!initialized) {
            initialize();
        }
        
        ObjectInfo obj = ObjectDB.getObjectInfo(objectName);
        return obj != null && obj.isValid();
    }
    
    /**
     * Gets the internal name for an object (handles common name variations).
     */
    public String getInternalName(String objectName) {
        if (!initialized) {
            initialize();
        }
        
        // Handle plural forms by converting to singular
        String normalizedName = normalizeObjectName(objectName);
        
        // Try direct lookup first
        ObjectInfo obj = ObjectDB.getObjectInfo(normalizedName);
        if (obj != null && obj.isValid()) {
            return obj.internalName();
        }
        
        // Try case-insensitive search
        Map<String, ObjectInfo> allObjects = ObjectDB.getObjectInfos();
        String lowerName = normalizedName.toLowerCase();
        
        for (ObjectInfo candidate : allObjects.values()) {
            if (candidate.isValid() && 
                (candidate.toString().toLowerCase().equals(lowerName) ||
                 candidate.internalName().toLowerCase().equals(lowerName))) {
                return candidate.internalName();
            }
        }
        
        return null; // Not found
    }
    
    /**
     * Gets object name suggestions for similar names.
     * Useful for helping users find the correct object name.
     */
    public List<String> getObjectNameSuggestions(String partialName) {
        if (!initialized) {
            initialize();
        }
        
        List<String> suggestions = new ArrayList<>();
        if (partialName == null || partialName.trim().isEmpty()) {
            return suggestions;
        }
        
        String searchTerm = partialName.toLowerCase().trim();
        Map<String, ObjectInfo> allObjects = ObjectDB.getObjectInfos();
        
        // Find objects that contain the search term
        for (ObjectInfo obj : allObjects.values()) {
            if (obj.isValid() && !obj.isUnused() && !obj.isLeftover()) {
                String objName = obj.toString().toLowerCase();
                String internalName = obj.internalName().toLowerCase();
                
                if (objName.contains(searchTerm) || internalName.contains(searchTerm)) {
                    suggestions.add(obj.toString());
                }
            }
        }
        
        // Sort by relevance (exact matches first, then alphabetical)
        suggestions.sort((a, b) -> {
            boolean aExact = a.toLowerCase().startsWith(searchTerm);
            boolean bExact = b.toLowerCase().startsWith(searchTerm);
            
            if (aExact && !bExact) return -1;
            if (!aExact && bExact) return 1;
            
            return a.compareToIgnoreCase(b);
        });
        
        // Limit to top 10 suggestions
        return suggestions.stream().limit(10).collect(Collectors.toList());
    }
    
    /**
     * Normalizes object names by converting plurals to singular forms.
     */
    private String normalizeObjectName(String objectName) {
        if (objectName == null || objectName.isEmpty()) {
            return objectName;
        }
        
        String lowerName = objectName.toLowerCase();
        
        // Common plural to singular mappings
        if (lowerName.endsWith("s")) {
            // Remove trailing 's' for common cases
            String singular = objectName.substring(0, objectName.length() - 1);
            
            // Special cases that need different handling
            switch (lowerName) {
                case "coins":
                    return "Coin";
                case "stars":
                    return "Star";
                case "goombas":
                    return "Goomba";
                case "koopas":
                    return "Koopa";
                case "enemies":
                    return "Enemy";
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
                case "mushrooms":
                    return "Mushroom";
                case "fireflowers":
                    return "FireFlower";
                case "iceflowers":
                    return "IceFlower";
                case "questionblocks":
                    return "QuestionBlock";
                case "brickblocks":
                    return "BrickBlock";
                case "woodblocks":
                    return "WoodBlock";
                case "movingplatforms":
                    return "MovingPlatform";
                case "areas":
                    return "Area";
                case "cameras":
                    return "Camera";
                case "paths":
                    return "Path";
                case "pathpoints":
                    return "PathPoint";
                default:
                    // For other cases, just remove the 's'
                    return singular;
            }
        }
        
        return objectName;
    }
} 
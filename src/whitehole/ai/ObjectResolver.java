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
import java.util.*;
import java.util.stream.Collectors;

/**
 * ObjectResolver matches AI-identified objects with actual galaxy objects using
 * fuzzy matching algorithms for names, types, and spatial relationships.
 * Handles disambiguation when multiple objects match the same criteria.
 */
public class ObjectResolver {
    
    // Matching thresholds
    private static final double NAME_SIMILARITY_THRESHOLD = 0.8; // Increased from 0.6 to be more strict
    private static final double PARTIAL_NAME_THRESHOLD = 0.6; // Increased from 0.4 to be more strict
    private static final float SPATIAL_NEAR_DISTANCE = 100.0f;
    private static final float SPATIAL_CLOSE_DISTANCE = 50.0f;
    
    // Scoring weights
    private static final double EXACT_NAME_SCORE = 1.0;
    private static final double PARTIAL_NAME_SCORE = 0.8;
    private static final double DISPLAY_NAME_SCORE = 0.7;
    private static final double TYPE_MATCH_SCORE = 0.6;
    private static final double TAG_MATCH_SCORE = 0.5;
    private static final double SPATIAL_BONUS = 0.3;
    private static final double FUZZY_NAME_SCORE = 0.4; // New lower score for fuzzy matches
    
    private final GalaxyContext context;
    
    public ObjectResolver(GalaxyContext context) {
        this.context = context;
    }
    
    /**
     * Resolves object references from natural language descriptions.
     * Returns a list of matching objects with confidence scores.
     */
    public ObjectResolutionResult resolveObjects(String reference) {
        if (reference == null || reference.trim().isEmpty()) {
            return ObjectResolutionResult.failure("Empty reference provided");
        }
        
        String cleanedReference = reference.toLowerCase().trim();
        System.out.println("DEBUG: Resolving object reference: '" + reference + "' (cleaned: '" + cleanedReference + "')");
        
        // Debug: Show what we're looking for BEFORE condition checks
        System.out.println("DEBUG: Looking for reference: '" + reference + "' (cleaned: '" + cleanedReference + "')");
        System.out.println("DEBUG: Is player/playeractor/spawn 0? " + 
                          (cleanedReference.equals("player") || cleanedReference.equals("playeractor") || 
                           cleanedReference.equals("spawn 0") || cleanedReference.equals("spawn0")));
        
        List<ObjectMatch> allMatches = new ArrayList<>();
        
        // 0. Try Link ID matches first (highest priority - exact ID match)
        List<ObjectMatch> linkIdMatches = findLinkIdMatches(reference);
        allMatches.addAll(linkIdMatches);
        
        // 1. Try Player/PlayerActor references (high priority - should resolve to Spawn 0)
        if (cleanedReference.equals("player") || cleanedReference.equals("playeractor") || 
            cleanedReference.equals("spawn 0") || cleanedReference.equals("spawn0")) {
            System.out.println("DEBUG: Player/PlayerActor/Spawn 0 reference detected, calling findPlayerReferences");
            List<ObjectMatch> playerMatches = findPlayerReferences(cleanedReference);
            allMatches.addAll(playerMatches);
        }
        
        // 2. Try exact name matches (high priority)
        List<ObjectMatch> exactMatches = findExactNameMatches(cleanedReference);
        allMatches.addAll(exactMatches);
        
        // 3. Try character-specific matching (high priority for character names)
        if (cleanedReference.contains("luigi") || cleanedReference.contains("mario") || 
            cleanedReference.contains("peach") || cleanedReference.contains("toad") ||
            cleanedReference.contains("rosetta") || cleanedReference.contains("kinopio") ||
            cleanedReference.contains("player") || cleanedReference.contains("playeractor")) {
            List<ObjectMatch> characterMatches = findCharacterMatches(cleanedReference);
            allMatches.addAll(characterMatches);
        }
        
        // 4. Try partial name matches (medium priority)
        List<ObjectMatch> partialMatches = findPartialNameMatches(cleanedReference);
        allMatches.addAll(partialMatches);
        
        // 5. Try display name matches
        List<ObjectMatch> displayMatches = findDisplayNameMatches(cleanedReference);
        allMatches.addAll(displayMatches);
        
        // 6. Try type-based matches (lowest priority)
        List<ObjectMatch> typeMatches = findTypeMatches(cleanedReference);
        allMatches.addAll(typeMatches);
        
        // 7. Try tag-based matches
        List<ObjectMatch> tagMatches = findTagMatches(cleanedReference);
        allMatches.addAll(tagMatches);
        
        // 8. Try spatial matches (very low priority)
        List<ObjectMatch> spatialMatches = findSpatialMatches(cleanedReference);
        allMatches.addAll(spatialMatches);
        
        // Remove duplicates and sort by confidence
        List<ObjectMatch> uniqueMatches = removeDuplicates(allMatches);
        uniqueMatches.sort((a, b) -> Double.compare(b.confidence, a.confidence));
        
        // Debug output
        System.out.println("DEBUG: Found " + uniqueMatches.size() + " unique matches:");
        for (int i = 0; i < uniqueMatches.size(); i++) {
            ObjectMatch match = uniqueMatches.get(i);
            System.out.println("   " + (i+1) + ". ID " + match.object.getUniqueId() + " '" + match.object.getName() + 
                             "' (" + match.object.getDisplayName() + ") - confidence: " + match.confidence + 
                             " - reason: " + match.reason);
        }
        
        // If no matches found, provide helpful suggestions
        if (uniqueMatches.isEmpty()) {
            System.out.println("DEBUG: No objects found matching: " + reference);
            List<String> suggestions = suggestSimilarObjectNames(reference);
            StringBuilder errorMsg = new StringBuilder("No objects found matching: " + reference);
            if (!suggestions.isEmpty()) {
                errorMsg.append("\nSuggestions: ").append(String.join(", ", suggestions));
            }
            return ObjectResolutionResult.failure(errorMsg.toString());
        }
        
        // Return the best matches
        List<GalaxyContext.ObjectInfo> resultObjects = uniqueMatches.stream()
            .map(match -> match.object)
            .collect(Collectors.toList());
        
        return ObjectResolutionResult.success(uniqueMatches, false);
    }
    
    /**
     * Resolves multiple object references, handling "all" keywords and lists.
     */
    public ObjectResolutionResult resolveMultipleObjects(String objectReference) {
        if (objectReference == null || objectReference.trim().isEmpty()) {
            return ObjectResolutionResult.failure("Empty object reference");
        }
        
        String cleanRef = objectReference.trim().toLowerCase();
        
        // Handle "all" keyword
        if (cleanRef.startsWith("all ")) {
            String typeRef = cleanRef.substring(4);
            
            // Handle spatial constraints like "all enemies near start"
            if (typeRef.contains(" near ") || typeRef.contains(" close ") || typeRef.contains(" far ")) {
                String[] parts = typeRef.split(" (near|close|far) ");
                if (parts.length == 2) {
                    String objectType = parts[0];
                    String spatialRef = parts[1];
                    
                    // First get all objects of the specified type
                    ObjectResolutionResult typeResult = resolveAllObjectsOfType(objectType);
                    if (!typeResult.isSuccess()) {
                        return typeResult;
                    }
                    
                    // Then filter by spatial constraint (simplified implementation)
                    // For now, just return the type results as this is a complex feature
                    return typeResult;
                }
            }
            
            return resolveAllObjectsOfType(typeRef);
        }
        
        // Handle comma-separated lists
        if (cleanRef.contains(",")) {
            return resolveObjectList(cleanRef);
        }
        
        // Handle plural forms
        if (cleanRef.endsWith("s") && !cleanRef.endsWith("ss")) {
            String singular = cleanRef.substring(0, cleanRef.length() - 1);
            
            // Handle common irregular plurals
            if (cleanRef.endsWith("ies")) {
                singular = cleanRef.substring(0, cleanRef.length() - 3) + "y";
            }
            
            ObjectResolutionResult pluralResult = resolveAllObjectsOfType(singular);
            if (pluralResult.isSuccess() && !pluralResult.getMatches().isEmpty()) {
                return pluralResult;
            }
            
            // Also try the original plural form in case it matches directly
            pluralResult = resolveAllObjectsOfType(cleanRef);
            if (pluralResult.isSuccess() && !pluralResult.getMatches().isEmpty()) {
                return pluralResult;
            }
        }
        
        // Fall back to single object resolution
        return resolveObjects(objectReference);
    }
    
    /**
     * Finds objects near a specified position or relative to another object.
     */
    public ObjectResolutionResult resolveSpatialReference(String spatialRef, Vec3f referencePosition) {
        if (spatialRef == null || referencePosition == null) {
            return ObjectResolutionResult.failure("Invalid spatial reference");
        }
        
        String cleanRef = spatialRef.trim().toLowerCase();
        List<ObjectMatch> matches = new ArrayList<>();
        
        if (cleanRef.contains("near") || cleanRef.contains("close")) {
            // Find objects near the reference position
            for (GalaxyContext.ObjectInfo obj : context.getObjects()) {
                float distance = calculateDistance(obj.getPosition(), referencePosition);
                if (distance <= SPATIAL_NEAR_DISTANCE) {
                    double confidence = 1.0 - (distance / SPATIAL_NEAR_DISTANCE);
                    matches.add(new ObjectMatch(obj, confidence, "Near reference position"));
                }
            }
        } else if (cleanRef.contains("far") || cleanRef.contains("distant")) {
            // Find objects far from the reference position
            for (GalaxyContext.ObjectInfo obj : context.getObjects()) {
                float distance = calculateDistance(obj.getPosition(), referencePosition);
                if (distance > SPATIAL_NEAR_DISTANCE) {
                    double confidence = Math.min(1.0, distance / (SPATIAL_NEAR_DISTANCE * 2));
                    matches.add(new ObjectMatch(obj, confidence, "Far from reference position"));
                }
            }
        }
        
        if (matches.isEmpty()) {
            return ObjectResolutionResult.failure("No objects found with spatial reference: " + spatialRef);
        }
        
        matches.sort((a, b) -> Double.compare(b.getConfidence(), a.getConfidence()));
        return ObjectResolutionResult.success(matches, false);
    }
    
    // Private helper methods for different matching strategies
    
    private List<ObjectMatch> findLinkIdMatches(String reference) {
        List<ObjectMatch> matches = new ArrayList<>();
        
        // Check if the reference is a number (Link ID)
        try {
            int linkId = Integer.parseInt(reference.trim());
            
            // Find object with this Link ID
            for (GalaxyContext.ObjectInfo obj : context.getObjects()) {
                if (obj.getUniqueId() == linkId) {
                    System.out.println("DEBUG: Link ID match found - Reference: '" + reference + 
                                     "' matches Object ID: " + obj.getUniqueId() + 
                                     " '" + obj.getName() + "' (Display: '" + obj.getDisplayName() + "')");
                    matches.add(new ObjectMatch(obj, 1.0, "Link ID match: " + linkId));
                    return matches; // Only one object can have this ID, so return immediately
                }
            }
            
            // If we get here, no object with this Link ID was found
            System.out.println("DEBUG: No object found with Link ID: " + linkId);
            
        } catch (NumberFormatException e) {
            // Reference is not a number, so no Link ID match
            // This is expected for text references, so don't log anything
        }
        
        return matches;
    }
    
    private List<ObjectMatch> findExactNameMatches(String reference) {
        List<ObjectMatch> matches = new ArrayList<>();
        for (GalaxyContext.ObjectInfo obj : context.getObjects()) {
            String objName = obj.getName().toLowerCase();
            String objDisplayName = obj.getDisplayName().toLowerCase();
            
            if (objName.equals(reference) || objDisplayName.equals(reference)) {
                System.out.println("DEBUG: Exact name match found - Reference: '" + reference + 
                                 "' matches Object: '" + obj.getName() + "' (Display: '" + obj.getDisplayName() + "')");
                matches.add(new ObjectMatch(obj, EXACT_NAME_SCORE, "Exact name match"));
            }
        }
        return matches;
    }
    
    private List<ObjectMatch> findPartialNameMatches(String reference) {
        List<ObjectMatch> matches = new ArrayList<>();
        
        // Special handling for character names
        String[] characterNames = {"luigi", "mario", "peach", "bowser", "toad", "yoshi", "rosalina"};
        boolean isCharacterReference = false;
        for (String charName : characterNames) {
            if (reference.contains(charName)) {
                isCharacterReference = true;
                break;
            }
        }
        
        for (GalaxyContext.ObjectInfo obj : context.getObjects()) {
            String objName = obj.getName().toLowerCase();
            String objDisplayName = obj.getDisplayName().toLowerCase();
            
            // Check if reference is contained in object name or vice versa
            if (objName.contains(reference) || reference.contains(objName) || 
                objDisplayName.contains(reference) || reference.contains(objDisplayName)) {
                double similarity = calculateStringSimilarity(reference, objName);
                if (similarity >= PARTIAL_NAME_THRESHOLD) {
                    // Boost confidence for character matches
                    double confidence = PARTIAL_NAME_SCORE * similarity;
                    if (isCharacterReference) {
                        for (String charName : characterNames) {
                            if (objName.contains(charName) || objDisplayName.contains(charName)) {
                                confidence = Math.max(confidence, 0.9); // High confidence for character matches
                                System.out.println("DEBUG: Character match boosted - Reference: '" + reference + 
                                                 "' matches character object: '" + obj.getName() + "'");
                                break;
                            }
                        }
                    }
                    matches.add(new ObjectMatch(obj, confidence, "Partial name match"));
                }
            }
            
            // Check Levenshtein distance for fuzzy matching
            double similarity = calculateStringSimilarity(reference, objName);
            if (similarity >= NAME_SIMILARITY_THRESHOLD) {
                // Additional check: ensure there's some meaningful overlap
                boolean hasCommonChars = false;
                for (int i = 0; i < Math.min(reference.length(), objName.length()); i++) {
                    if (reference.charAt(i) == objName.charAt(i)) {
                        hasCommonChars = true;
                        break;
                    }
                }
                
                // Check for common substrings of at least 3 characters
                boolean hasCommonSubstring = false;
                for (int len = 3; len <= Math.min(reference.length(), objName.length()); len++) {
                    for (int i = 0; i <= reference.length() - len; i++) {
                        String substring = reference.substring(i, i + len);
                        if (objName.contains(substring)) {
                            hasCommonSubstring = true;
                            break;
                        }
                    }
                    if (hasCommonSubstring) break;
                }
                
                if (hasCommonChars || hasCommonSubstring) {
                    System.out.println("DEBUG: Fuzzy match found - Reference: '" + reference + "' vs Object: '" + objName + 
                                     "' - Similarity: " + String.format("%.3f", similarity) + 
                                     " - Threshold: " + NAME_SIMILARITY_THRESHOLD +
                                     " - Common chars: " + hasCommonChars + " - Common substring: " + hasCommonSubstring);
                    matches.add(new ObjectMatch(obj, FUZZY_NAME_SCORE * similarity, "Fuzzy name match"));
                } else {
                    System.out.println("DEBUG: Rejected fuzzy match - Reference: '" + reference + "' vs Object: '" + objName + 
                                     "' - Similarity: " + String.format("%.3f", similarity) + 
                                     " - No meaningful overlap");
                }
            }
        }
        return matches;
    }
    
    private List<ObjectMatch> findCharacterMatches(String reference) {
        List<ObjectMatch> matches = new ArrayList<>();
        
        // Character name mappings
        String[] characterNames = {"luigi", "mario", "peach", "bowser", "toad", "yoshi", "rosalina", "player", "playeractor"};
        String targetCharacter = null;
        
        // Find which character is being referenced
        for (String charName : characterNames) {
            if (reference.contains(charName)) {
                targetCharacter = charName;
                break;
            }
        }
        
        if (targetCharacter == null) {
            return matches;
        }
        
        // Look for objects with the character name
        for (GalaxyContext.ObjectInfo obj : context.getObjects()) {
            String objName = obj.getName().toLowerCase();
            String objDisplayName = obj.getDisplayName().toLowerCase();
            String objType = obj.getType().toLowerCase();
            
            // Check for character name in object name/display
            if (objName.contains(targetCharacter) || objDisplayName.contains(targetCharacter)) {
                System.out.println("DEBUG: Character object found - '" + targetCharacter + "' in object: '" + 
                                 obj.getName() + "' (Display: '" + obj.getDisplayName() + "')");
                matches.add(new ObjectMatch(obj, 0.95, "Character match: " + targetCharacter));
            }
            
            // Special handling for Mario - check spawn objects
            if (targetCharacter.equals("mario")) {
                if (objType.contains("spawn") || objName.contains("spawn") || objDisplayName.contains("spawn")) {
                    System.out.println("DEBUG: Mario spawn object found - '" + obj.getName() + "' (Display: '" + obj.getDisplayName() + "')");
                    matches.add(new ObjectMatch(obj, 0.95, "Mario spawn object"));
                }
            }
            
            // Special handling for Player/PlayerActor - check spawn objects
            if (targetCharacter.equals("player") || targetCharacter.equals("playeractor")) {
                if (objType.contains("spawn") || objName.contains("spawn") || objDisplayName.contains("spawn")) {
                    System.out.println("DEBUG: Player spawn object found - '" + obj.getName() + "' (Display: '" + obj.getDisplayName() + "')");
                    matches.add(new ObjectMatch(obj, 0.95, "Player spawn object"));
                }
            }
        }
        
        return matches;
    }
    
    /**
     * Handles Player and PlayerActor references by finding spawn objects (Spawn 0).
     * These references should resolve to Mario's spawn point.
     */
    private List<ObjectMatch> findPlayerReferences(String reference) {
        List<ObjectMatch> matches = new ArrayList<>();
        
        System.out.println("DEBUG: Looking for Player/PlayerActor reference: '" + reference + "'");
        
        // Look for spawn objects (which represent the player spawn point)
        for (GalaxyContext.ObjectInfo obj : context.getObjects()) {
            String objName = obj.getName().toLowerCase();
            String objDisplayName = obj.getDisplayName().toLowerCase();
            String objType = obj.getType().toLowerCase();
            
            // Check for spawn objects (Spawn 0, etc.)
            if (objType.contains("spawn") || objName.contains("spawn") || objDisplayName.contains("spawn")) {
                System.out.println("DEBUG: Player spawn object found - '" + obj.getName() + 
                                 "' (Display: '" + obj.getDisplayName() + "') ID: " + obj.getUniqueId());
                matches.add(new ObjectMatch(obj, 0.95, "Player spawn object"));
            }
            
            // Also check for start objects as fallback
            if (objType.contains("start") || objName.contains("start") || objDisplayName.contains("start")) {
                System.out.println("DEBUG: Player start object found - '" + obj.getName() + 
                                 "' (Display: '" + obj.getDisplayName() + "') ID: " + obj.getUniqueId());
                matches.add(new ObjectMatch(obj, 0.9, "Player start object"));
            }
        }
        
        // If no spawn/start objects found, look for Mario objects (since Mario is the player)
        if (matches.isEmpty()) {
            for (GalaxyContext.ObjectInfo obj : context.getObjects()) {
                String objName = obj.getName().toLowerCase();
                String objDisplayName = obj.getDisplayName().toLowerCase();
                
                if (objName.contains("mario") || objDisplayName.contains("mario")) {
                    System.out.println("DEBUG: Player Mario object found - '" + obj.getName() + 
                                     "' (Display: '" + obj.getDisplayName() + "') ID: " + obj.getUniqueId());
                    matches.add(new ObjectMatch(obj, 0.9, "Player Mario object"));
                }
            }
        }
        
        // If still no matches, look for objects with ID 0 (common for player spawn)
        if (matches.isEmpty()) {
            for (GalaxyContext.ObjectInfo obj : context.getObjects()) {
                if (obj.getUniqueId() == 0) {
                    System.out.println("DEBUG: Player object with ID 0 found - '" + obj.getName() + 
                                     "' (Display: '" + obj.getDisplayName() + "')");
                    matches.add(new ObjectMatch(obj, 0.85, "Player object (ID 0)"));
                }
            }
        }
        
        if (matches.isEmpty()) {
            System.out.println("DEBUG: No Player/PlayerActor objects found");
        } else {
            System.out.println("DEBUG: Found " + matches.size() + " Player/PlayerActor objects");
        }
        
        return matches;
    }
    
    private List<ObjectMatch> findDisplayNameMatches(String reference) {
        List<ObjectMatch> matches = new ArrayList<>();
        for (GalaxyContext.ObjectInfo obj : context.getObjects()) {
            String displayName = obj.getDisplayName().toLowerCase();
            if (displayName.contains(reference) || reference.contains(displayName)) {
                double similarity = calculateStringSimilarity(reference, displayName);
                matches.add(new ObjectMatch(obj, DISPLAY_NAME_SCORE * similarity, "Display name match"));
            }
        }
        return matches;
    }
    
    private List<ObjectMatch> findTypeMatches(String reference) {
        List<ObjectMatch> matches = new ArrayList<>();
        for (GalaxyContext.ObjectInfo obj : context.getObjects()) {
            String objType = obj.getType().toLowerCase();
            if (objType.equals(reference) || objType.contains(reference) || reference.contains(objType)) {
                double similarity = calculateStringSimilarity(reference, objType);
                matches.add(new ObjectMatch(obj, TYPE_MATCH_SCORE * similarity, "Type match"));
            }
        }
        return matches;
    }
    
    private List<ObjectMatch> findTagMatches(String reference) {
        List<ObjectMatch> matches = new ArrayList<>();
        for (GalaxyContext.ObjectInfo obj : context.getObjects()) {
            for (String tag : obj.getTags()) {
                String tagLower = tag.toLowerCase();
                if (tagLower.equals(reference) || tagLower.contains(reference) || reference.contains(tagLower)) {
                    double similarity = calculateStringSimilarity(reference, tagLower);
                    matches.add(new ObjectMatch(obj, TAG_MATCH_SCORE * similarity, "Tag match: " + tag));
                }
            }
        }
        return matches;
    }
    
    private List<ObjectMatch> findSpatialMatches(String reference) {
        List<ObjectMatch> matches = new ArrayList<>();
        
        // Handle spatial keywords
        if (reference.contains("start") || reference.contains("beginning")) {
            // Find objects near origin or start points
            Vec3f origin = new Vec3f(0, 0, 0);
            for (GalaxyContext.ObjectInfo obj : context.getObjects()) {
                if (obj.getType().equals("start") || obj.getTags().contains("player")) {
                    matches.add(new ObjectMatch(obj, 0.9, "Start point match"));
                } else {
                    float distance = calculateDistance(obj.getPosition(), origin);
                    if (distance <= SPATIAL_CLOSE_DISTANCE) {
                        double confidence = 0.5 * (1.0 - distance / SPATIAL_CLOSE_DISTANCE);
                        matches.add(new ObjectMatch(obj, confidence, "Near start"));
                    }
                }
            }
        }
        
        return matches;
    }
    
    private ObjectResolutionResult resolveAllObjectsOfType(String typeReference) {
        List<ObjectMatch> matches = new ArrayList<>();
        
        // Handle plural forms by trying both singular and plural
        String singularForm = typeReference;
        if (typeReference.endsWith("s") && !typeReference.endsWith("ss")) {
            singularForm = typeReference.substring(0, typeReference.length() - 1);
        }
        
        for (GalaxyContext.ObjectInfo obj : context.getObjects()) {
            boolean matched = false;
            
            // Check type match (both forms)
            String objType = obj.getType().toLowerCase();
            if (objType.contains(typeReference) || objType.contains(singularForm)) {
                matches.add(new ObjectMatch(obj, TYPE_MATCH_SCORE, "Type match"));
                matched = true;
            }
            
            // Check tag match (both forms)
            if (!matched) {
                for (String tag : obj.getTags()) {
                    String tagLower = tag.toLowerCase();
                    if (tagLower.contains(typeReference) || tagLower.contains(singularForm) ||
                        typeReference.contains(tagLower) || singularForm.contains(tagLower)) {
                        matches.add(new ObjectMatch(obj, TAG_MATCH_SCORE, "Tag match"));
                        matched = true;
                        break;
                    }
                }
            }
            
            // Check name match for common plurals (both forms)
            if (!matched) {
                String objName = obj.getName().toLowerCase();
                if (objName.contains(typeReference) || objName.contains(singularForm)) {
                    matches.add(new ObjectMatch(obj, PARTIAL_NAME_SCORE, "Name contains type"));
                }
            }
        }
        
        if (matches.isEmpty()) {
            return ObjectResolutionResult.failure("No objects found of type: " + typeReference);
        }
        
        return ObjectResolutionResult.success(matches, false);
    }
    
    private ObjectResolutionResult resolveObjectList(String listReference) {
        String[] parts = listReference.split(",");
        List<ObjectMatch> allMatches = new ArrayList<>();
        
        for (String part : parts) {
            ObjectResolutionResult result = resolveObjects(part.trim());
            if (result.isSuccess()) {
                allMatches.addAll(result.getMatches());
            }
        }
        
        if (allMatches.isEmpty()) {
            return ObjectResolutionResult.failure("No objects found in list: " + listReference);
        }
        
        return ObjectResolutionResult.success(allMatches, false);
    }
    
    /**
     * Finds objects with similar names that might be what the user actually wants.
     */
    private List<String> findSimilarObjectNames(String reference) {
        List<String> similarNames = new ArrayList<>();
        String cleanRef = reference.toLowerCase().trim();
        
        // Get all object names in the scene
        Set<String> allNames = new HashSet<>();
        for (GalaxyContext.ObjectInfo obj : context.getObjects()) {
            allNames.add(obj.getName());
            allNames.add(obj.getDisplayName());
        }
        
        // Find names with high similarity
        for (String name : allNames) {
            if (name == null || name.isEmpty()) continue;
            
            double similarity = calculateStringSimilarity(cleanRef, name.toLowerCase());
            if (similarity >= 0.6) { // High similarity threshold
                similarNames.add(name);
            }
        }
        
        // Sort by similarity
        similarNames.sort((a, b) -> {
            double simA = calculateStringSimilarity(cleanRef, a.toLowerCase());
            double simB = calculateStringSimilarity(cleanRef, b.toLowerCase());
            return Double.compare(simB, simA);
        });
        
        return similarNames.stream().limit(3).collect(Collectors.toList());
    }
    
    /**
     * Suggests how to add a Luigi object when none exists.
     */
    private String getLuigiObjectSuggestion() {
        return "To add a Luigi object, try one of these commands:\n" +
               "- 'Add LuigiNPC' (Luigi character)\n" +
               "- 'Add LuigiNPC at 0, 150, 0' (Luigi at specific position)\n" +
               "- 'Create LuigiNPC near start' (Luigi near start point)\n" +
               "Then you can use 'Scale LuigiNPC by 10x and move to (0, 150, 9000)'";
    }
    
    /**
     * Checks if there are any Luigi-related objects in the scene.
     */
    private boolean hasLuigiObjects() {
        for (GalaxyContext.ObjectInfo obj : context.getObjects()) {
            String objName = obj.getName().toLowerCase();
            String objDisplayName = obj.getDisplayName().toLowerCase();
            
            if (objName.contains("luigi") || objDisplayName.contains("luigi")) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * Suggests similar object names when no exact matches are found.
     */
    private List<String> suggestSimilarObjectNames(String reference) {
        List<String> suggestions = new ArrayList<>();
        
        // Get all object names in the scene
        Set<String> allNames = new HashSet<>();
        for (GalaxyContext.ObjectInfo obj : context.getObjects()) {
            allNames.add(obj.getName());
            allNames.add(obj.getDisplayName());
        }
        
        // Find names with high similarity
        for (String name : allNames) {
            if (name == null || name.isEmpty()) continue;
            
            double similarity = calculateStringSimilarity(reference, name.toLowerCase());
            if (similarity >= 0.5) { // Lower threshold for suggestions
                suggestions.add(name);
            }
        }
        
        // Sort by similarity and limit to top 5
        suggestions.sort((a, b) -> {
            double simA = calculateStringSimilarity(reference, a.toLowerCase());
            double simB = calculateStringSimilarity(reference, b.toLowerCase());
            return Double.compare(simB, simA);
        });
        
        return suggestions.stream().limit(5).collect(Collectors.toList());
    }
    
    // Utility methods
    
    private double calculateStringSimilarity(String s1, String s2) {
        if (s1.equals(s2)) return 1.0;
        if (s1.isEmpty() || s2.isEmpty()) return 0.0;
        
        // Use Levenshtein distance for similarity calculation
        int maxLen = Math.max(s1.length(), s2.length());
        int distance = levenshteinDistance(s1, s2);
        return 1.0 - (double) distance / maxLen;
    }
    
    private int levenshteinDistance(String s1, String s2) {
        int[][] dp = new int[s1.length() + 1][s2.length() + 1];
        
        for (int i = 0; i <= s1.length(); i++) {
            dp[i][0] = i;
        }
        for (int j = 0; j <= s2.length(); j++) {
            dp[0][j] = j;
        }
        
        for (int i = 1; i <= s1.length(); i++) {
            for (int j = 1; j <= s2.length(); j++) {
                int cost = s1.charAt(i - 1) == s2.charAt(j - 1) ? 0 : 1;
                dp[i][j] = Math.min(Math.min(
                    dp[i - 1][j] + 1,      // deletion
                    dp[i][j - 1] + 1),     // insertion
                    dp[i - 1][j - 1] + cost // substitution
                );
            }
        }
        
        return dp[s1.length()][s2.length()];
    }
    
    private float calculateDistance(Vec3f pos1, Vec3f pos2) {
        Vec3f diff = new Vec3f(pos1);
        diff.subtract(pos2);
        return diff.length();
    }
    
    /**
     * Removes duplicate object matches, keeping the highest confidence match for each object.
     */
    private List<ObjectMatch> removeDuplicates(List<ObjectMatch> matches) {
        Map<Integer, ObjectMatch> uniqueMatches = new HashMap<>();
        for (ObjectMatch match : matches) {
            int objectId = match.object.getUniqueId();
            if (!uniqueMatches.containsKey(objectId) || 
                match.confidence > uniqueMatches.get(objectId).confidence) {
                uniqueMatches.put(objectId, match);
            }
        }
        return new ArrayList<>(uniqueMatches.values());
    }
    
    /**
     * Lists all objects in the scene for debugging purposes.
     */
    public void listAllObjects() {
        System.out.println("DEBUG: All objects in scene:");
        for (GalaxyContext.ObjectInfo obj : context.getObjects()) {
            System.out.println("DEBUG:   ID " + obj.getUniqueId() + 
                             " - Name: '" + obj.getName() + 
                             "' - Display: '" + obj.getDisplayName() + 
                             "' - Type: '" + obj.getType() + "'");
        }
        System.out.println("DEBUG: Total objects: " + context.getObjects().size());
    }
    
    /**
     * Lists objects containing a specific substring for debugging.
     */
    public void listObjectsContaining(String substring) {
        String searchTerm = substring.toLowerCase();
        System.out.println("DEBUG: Objects containing '" + substring + "':");
        int count = 0;
        for (GalaxyContext.ObjectInfo obj : context.getObjects()) {
            String objName = obj.getName().toLowerCase();
            String objDisplayName = obj.getDisplayName().toLowerCase();
            
            if (objName.contains(searchTerm) || objDisplayName.contains(searchTerm)) {
                System.out.println("DEBUG:   ID " + obj.getUniqueId() + 
                                 " - Name: '" + obj.getName() + 
                                 "' - Display: '" + obj.getDisplayName() + 
                                 "' - Type: '" + obj.getType() + "'");
                count++;
            }
        }
        System.out.println("DEBUG: Found " + count + " objects containing '" + substring + "'");
    }
    
    /**
     * Comprehensive debug method to show all objects and their similarity to a reference.
     */
    public void debugObjectMatching(String reference) {
        String cleanRef = reference.toLowerCase().trim();
        System.out.println("DEBUG: === COMPREHENSIVE OBJECT MATCHING DEBUG ===");
        System.out.println("DEBUG: Reference: '" + reference + "' (cleaned: '" + cleanRef + "')");
        System.out.println("DEBUG: Total objects in scene: " + context.getObjects().size());
        System.out.println("DEBUG: ");
        
        List<ObjectMatch> allMatches = new ArrayList<>();
        
        for (GalaxyContext.ObjectInfo obj : context.getObjects()) {
            String objName = obj.getName().toLowerCase();
            String objDisplayName = obj.getDisplayName().toLowerCase();
            
            // Calculate similarities
            double nameSimilarity = calculateStringSimilarity(cleanRef, objName);
            double displaySimilarity = calculateStringSimilarity(cleanRef, objDisplayName);
            double maxSimilarity = Math.max(nameSimilarity, displaySimilarity);
            
            // Check for character matches
            boolean isCharacterMatch = false;
            String[] characterNames = {"luigi", "mario", "peach", "bowser", "toad", "yoshi", "rosalina"};
            for (String charName : characterNames) {
                if (cleanRef.contains(charName) && (objName.contains(charName) || objDisplayName.contains(charName))) {
                    isCharacterMatch = true;
                    break;
                }
            }
            
            // Check for exact matches
            boolean isExactMatch = objName.equals(cleanRef) || objDisplayName.equals(cleanRef);
            
            // Check for partial matches
            boolean isPartialMatch = objName.contains(cleanRef) || cleanRef.contains(objName) || 
                                   objDisplayName.contains(cleanRef) || cleanRef.contains(objDisplayName);
            
            // Only show objects with some relevance
            if (maxSimilarity >= 0.3 || isCharacterMatch || isExactMatch || isPartialMatch) {
                System.out.println("DEBUG: Object ID " + obj.getUniqueId() + 
                                 " - Name: '" + obj.getName() + 
                                 "' - Display: '" + obj.getDisplayName() + 
                                 "' - Type: '" + obj.getType() + "'");
                System.out.println("DEBUG:   Name similarity: " + String.format("%.3f", nameSimilarity) + 
                                 " - Display similarity: " + String.format("%.3f", displaySimilarity));
                System.out.println("DEBUG:   Exact match: " + isExactMatch + 
                                 " - Partial match: " + isPartialMatch + 
                                 " - Character match: " + isCharacterMatch);
                System.out.println("DEBUG: ");
                
                // Add to matches list for analysis
                if (isExactMatch) {
                    allMatches.add(new ObjectMatch(obj, EXACT_NAME_SCORE, "Exact match"));
                } else if (isCharacterMatch) {
                    allMatches.add(new ObjectMatch(obj, 0.95, "Character match"));
                } else if (isPartialMatch && maxSimilarity >= PARTIAL_NAME_THRESHOLD) {
                    allMatches.add(new ObjectMatch(obj, PARTIAL_NAME_SCORE * maxSimilarity, "Partial match"));
                } else if (maxSimilarity >= NAME_SIMILARITY_THRESHOLD) {
                    allMatches.add(new ObjectMatch(obj, FUZZY_NAME_SCORE * maxSimilarity, "Fuzzy match"));
                }
            }
        }
        
        // Sort and show top matches
        allMatches.sort((a, b) -> Double.compare(b.getConfidence(), a.getConfidence()));
        System.out.println("DEBUG: === TOP MATCHES ===");
        for (int i = 0; i < Math.min(allMatches.size(), 10); i++) {
            ObjectMatch match = allMatches.get(i);
            System.out.println("DEBUG: " + (i+1) + ". ID " + match.getObject().getUniqueId() + 
                             " '" + match.getObject().getName() + "' - confidence: " + 
                             String.format("%.3f", match.getConfidence()) + " - reason: " + match.getReason());
        }
        System.out.println("DEBUG: === END DEBUG ===");
    }
    
    /**
     * Represents a matched object with confidence score and reasoning.
     */
    public static class ObjectMatch {
        private final GalaxyContext.ObjectInfo object;
        private final double confidence;
        private final String reason;
        
        public ObjectMatch(GalaxyContext.ObjectInfo object, double confidence, String reason) {
            this.object = object;
            this.confidence = Math.max(0.0, Math.min(1.0, confidence)); // Clamp to [0,1]
            this.reason = reason;
        }
        
        public GalaxyContext.ObjectInfo getObject() {
            return object;
        }
        
        public double getConfidence() {
            return confidence;
        }
        
        public String getReason() {
            return reason;
        }
        
        @Override
        public String toString() {
            return String.format("ObjectMatch{id=%d, name='%s', confidence=%.2f, reason='%s'}", 
                object.getUniqueId(), object.getName(), confidence, reason);
        }
    }
    
    /**
     * Result of object resolution containing matches and disambiguation info.
     */
    public static class ObjectResolutionResult {
        private final boolean success;
        private final List<ObjectMatch> matches;
        private final boolean needsDisambiguation;
        private final String errorMessage;
        
        private ObjectResolutionResult(boolean success, List<ObjectMatch> matches, 
                                     boolean needsDisambiguation, String errorMessage) {
            this.success = success;
            this.matches = matches != null ? new ArrayList<>(matches) : new ArrayList<>();
            this.needsDisambiguation = needsDisambiguation;
            this.errorMessage = errorMessage;
        }
        
        public boolean isSuccess() {
            return success;
        }
        
        public List<ObjectMatch> getMatches() {
            return new ArrayList<>(matches);
        }
        
        public boolean needsDisambiguation() {
            return needsDisambiguation;
        }
        
        public String getErrorMessage() {
            return errorMessage;
        }
        
        public List<GalaxyContext.ObjectInfo> getObjects() {
            return matches.stream()
                .map(ObjectMatch::getObject)
                .collect(Collectors.toList());
        }
        
        public ObjectMatch getBestMatch() {
            return matches.isEmpty() ? null : matches.get(0);
        }
        
        public static ObjectResolutionResult success(List<ObjectMatch> matches, boolean needsDisambiguation) {
            return new ObjectResolutionResult(true, matches, needsDisambiguation, null);
        }
        
        public static ObjectResolutionResult failure(String errorMessage) {
            return new ObjectResolutionResult(false, null, false, errorMessage);
        }
        
        @Override
        public String toString() {
            if (!success) {
                return "ObjectResolutionResult{success=false, error='" + errorMessage + "'}";
            }
            return String.format("ObjectResolutionResult{success=true, matches=%d, needsDisambiguation=%s}", 
                matches.size(), needsDisambiguation);
        }
    }
}
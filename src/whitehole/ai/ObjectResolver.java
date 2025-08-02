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
    private static final double NAME_SIMILARITY_THRESHOLD = 0.6;
    private static final double PARTIAL_NAME_THRESHOLD = 0.4;
    private static final float SPATIAL_NEAR_DISTANCE = 100.0f;
    private static final float SPATIAL_CLOSE_DISTANCE = 50.0f;
    
    // Scoring weights
    private static final double EXACT_NAME_SCORE = 1.0;
    private static final double PARTIAL_NAME_SCORE = 0.8;
    private static final double DISPLAY_NAME_SCORE = 0.7;
    private static final double TYPE_MATCH_SCORE = 0.6;
    private static final double TAG_MATCH_SCORE = 0.5;
    private static final double SPATIAL_BONUS = 0.3;
    
    private final GalaxyContext context;
    
    public ObjectResolver(GalaxyContext context) {
        this.context = context;
    }
    
    /**
     * Resolves object references from natural language descriptions.
     * Returns a list of matching objects with confidence scores.
     */
    public ObjectResolutionResult resolveObjects(String objectReference) {
        if (objectReference == null || objectReference.trim().isEmpty()) {
            return ObjectResolutionResult.failure("Empty object reference");
        }
        
        String cleanRef = objectReference.trim().toLowerCase();
        List<ObjectMatch> matches = new ArrayList<>();
        
        // Try different matching strategies
        matches.addAll(findExactNameMatches(cleanRef));
        matches.addAll(findPartialNameMatches(cleanRef));
        matches.addAll(findDisplayNameMatches(cleanRef));
        matches.addAll(findTypeMatches(cleanRef));
        matches.addAll(findTagMatches(cleanRef));
        matches.addAll(findSpatialMatches(cleanRef));
        
        // Remove duplicates and sort by confidence
        Map<Integer, ObjectMatch> uniqueMatches = new HashMap<>();
        for (ObjectMatch match : matches) {
            int objectId = match.getObject().getUniqueId();
            if (!uniqueMatches.containsKey(objectId) || 
                match.getConfidence() > uniqueMatches.get(objectId).getConfidence()) {
                uniqueMatches.put(objectId, match);
            }
        }
        
        List<ObjectMatch> sortedMatches = uniqueMatches.values().stream()
            .sorted((a, b) -> Double.compare(b.getConfidence(), a.getConfidence()))
            .collect(Collectors.toList());
        
        if (sortedMatches.isEmpty()) {
            return ObjectResolutionResult.failure("No objects found matching: " + objectReference);
        }
        
        // Check if disambiguation is needed
        boolean needsDisambiguation = sortedMatches.size() > 1 && 
            Math.abs(sortedMatches.get(0).getConfidence() - sortedMatches.get(1).getConfidence()) < 0.1;
        
        return ObjectResolutionResult.success(sortedMatches, needsDisambiguation);
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
    
    private List<ObjectMatch> findExactNameMatches(String reference) {
        List<ObjectMatch> matches = new ArrayList<>();
        for (GalaxyContext.ObjectInfo obj : context.getObjects()) {
            if (obj.getName().toLowerCase().equals(reference)) {
                matches.add(new ObjectMatch(obj, EXACT_NAME_SCORE, "Exact name match"));
            }
        }
        return matches;
    }
    
    private List<ObjectMatch> findPartialNameMatches(String reference) {
        List<ObjectMatch> matches = new ArrayList<>();
        for (GalaxyContext.ObjectInfo obj : context.getObjects()) {
            String objName = obj.getName().toLowerCase();
            
            // Check if reference is contained in object name or vice versa
            if (objName.contains(reference) || reference.contains(objName)) {
                double similarity = calculateStringSimilarity(reference, objName);
                if (similarity >= PARTIAL_NAME_THRESHOLD) {
                    matches.add(new ObjectMatch(obj, PARTIAL_NAME_SCORE * similarity, "Partial name match"));
                }
            }
            
            // Check Levenshtein distance for fuzzy matching
            double similarity = calculateStringSimilarity(reference, objName);
            if (similarity >= NAME_SIMILARITY_THRESHOLD) {
                matches.add(new ObjectMatch(obj, PARTIAL_NAME_SCORE * similarity, "Fuzzy name match"));
            }
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
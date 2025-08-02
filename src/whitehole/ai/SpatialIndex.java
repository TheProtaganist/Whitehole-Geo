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
import java.util.concurrent.*;
import java.util.function.Consumer;

/**
 * Spatial indexing system for efficient position-based object queries
 * and relationship detection. Uses a simple grid-based approach for
 * fast proximity searches.
 */
public class SpatialIndex {
    private static final float GRID_SIZE = 1000.0f; // Grid cell size in world units
    
    private final Map<GridCell, List<GalaxyContext.ObjectInfo>> grid;
    private final List<GalaxyContext.ObjectInfo> allObjects;
    
    public SpatialIndex() {
        this.grid = new HashMap<>();
        this.allObjects = new ArrayList<>();
    }
    
    /**
     * Adds an object to the spatial index.
     * @param object The object to add
     */
    public void addObject(GalaxyContext.ObjectInfo object) {
        allObjects.add(object);
        
        GridCell cell = getGridCell(object.getPosition());
        grid.computeIfAbsent(cell, k -> new ArrayList<>()).add(object);
    }
    
    /**
     * Removes an object from the spatial index.
     * @param object The object to remove
     */
    public void removeObject(GalaxyContext.ObjectInfo object) {
        allObjects.remove(object);
        
        GridCell cell = getGridCell(object.getPosition());
        List<GalaxyContext.ObjectInfo> cellObjects = grid.get(cell);
        if (cellObjects != null) {
            cellObjects.remove(object);
            if (cellObjects.isEmpty()) {
                grid.remove(cell);
            }
        }
    }
    
    /**
     * Clears all objects from the spatial index.
     */
    public void clear() {
        grid.clear();
        allObjects.clear();
    }
    
    /**
     * Finds all objects within a specified distance of a position.
     * @param position The center position
     * @param maxDistance Maximum distance to search
     * @return List of objects within the specified distance
     */
    public List<GalaxyContext.ObjectInfo> findObjectsNear(Vec3f position, float maxDistance) {
        List<GalaxyContext.ObjectInfo> result = new ArrayList<>();
        
        // Calculate grid cells to check based on search radius
        int gridRadius = (int) Math.ceil(maxDistance / GRID_SIZE);
        GridCell centerCell = getGridCell(position);
        
        // Dynamic grid radius: start at 500, extend to 700 only if needed
        int baseRadius = Math.min(gridRadius, 500);
        int maxRadius = Math.min(gridRadius, 700);
        
        // Dynamic search with time-based early termination
        float maxDistanceSquared = maxDistance * maxDistance;
        long searchStartTime = System.currentTimeMillis();
        final long MAX_SEARCH_TIME_MS = 2000; // Maximum 2 seconds per search
        
        // Phase 1: Search within base radius (500 cells)
        int currentRadius = baseRadius;
        int baseRadiusSquared = baseRadius * baseRadius;
        
        for (int layer = 0; layer <= currentRadius; layer++) {
            // Time check every 10 layers to avoid excessive overhead
            if (layer % 10 == 0 && System.currentTimeMillis() - searchStartTime > MAX_SEARCH_TIME_MS) {
                break;
            }
            
            boolean foundInLayer = false;
            
            if (layer == 0) {
                // Check center cell
                GridCell cell = new GridCell(centerCell.x, centerCell.y, centerCell.z);
                List<GalaxyContext.ObjectInfo> cellObjects = grid.get(cell);
                if (cellObjects != null && !cellObjects.isEmpty()) {
                    for (GalaxyContext.ObjectInfo obj : cellObjects) {
                        float distanceSquared = calculateDistanceSquared(position, obj.getPosition());
                        if (distanceSquared <= maxDistanceSquared) {
                            result.add(obj);
                            foundInLayer = true;
                        }
                    }
                }
            } else {
                // Check all cells in current layer
                for (int dx = -layer; dx <= layer; dx++) {
                    for (int dy = -layer; dy <= layer; dy++) {
                        for (int dz = -layer; dz <= layer; dz++) {
                            // Only check cells on the boundary of current layer
                            if (Math.abs(dx) != layer && Math.abs(dy) != layer && Math.abs(dz) != layer) {
                                continue;
                            }
                            
                            // Skip cells that are definitely too far (sphere vs cube optimization)
                            if (dx*dx + dy*dy + dz*dz > baseRadiusSquared) {
                                continue;
                            }
                            
                            GridCell cell = new GridCell(
                                centerCell.x + dx,
                                centerCell.y + dy,
                                centerCell.z + dz
                            );
                            
                            List<GalaxyContext.ObjectInfo> cellObjects = grid.get(cell);
                            if (cellObjects != null && !cellObjects.isEmpty()) {
                                for (GalaxyContext.ObjectInfo obj : cellObjects) {
                                    float distanceSquared = calculateDistanceSquared(position, obj.getPosition());
                                    if (distanceSquared <= maxDistanceSquared) {
                                        result.add(obj);
                                        foundInLayer = true;
                                    }
                                }
                            }
                        }
                    }
                }
            }
            
            // Early termination if we have good results and no recent finds
            if (result.size() > 100 && layer > baseRadius/2 && !foundInLayer) {
                break;
            }
        }
        
        // Phase 2: Extend to 700 cells only if we have few results and time remaining
        if (result.size() < 50 && maxRadius > baseRadius && 
            System.currentTimeMillis() - searchStartTime < MAX_SEARCH_TIME_MS / 2) {
            
            int maxRadiusSquared = maxRadius * maxRadius;
            
            for (int layer = baseRadius + 1; layer <= maxRadius; layer++) {
                // Strict time check for extended search
                if (System.currentTimeMillis() - searchStartTime > MAX_SEARCH_TIME_MS) {
                    break;
                }
                
                boolean foundInLayer = false;
                
                // Check all cells in current layer
                for (int dx = -layer; dx <= layer; dx++) {
                    for (int dy = -layer; dy <= layer; dy++) {
                        for (int dz = -layer; dz <= layer; dz++) {
                            // Only check cells on the boundary of current layer
                            if (Math.abs(dx) != layer && Math.abs(dy) != layer && Math.abs(dz) != layer) {
                                continue;
                            }
                            
                            // Skip cells that are definitely too far
                            if (dx*dx + dy*dy + dz*dz > maxRadiusSquared) {
                                continue;
                            }
                            
                            GridCell cell = new GridCell(
                                centerCell.x + dx,
                                centerCell.y + dy,
                                centerCell.z + dz
                            );
                            
                            List<GalaxyContext.ObjectInfo> cellObjects = grid.get(cell);
                            if (cellObjects != null && !cellObjects.isEmpty()) {
                                for (GalaxyContext.ObjectInfo obj : cellObjects) {
                                    float distanceSquared = calculateDistanceSquared(position, obj.getPosition());
                                    if (distanceSquared <= maxDistanceSquared) {
                                        result.add(obj);
                                        foundInLayer = true;
                                    }
                                }
                            }
                        }
                    }
                }
                
                // Stop extended search if we found enough or no recent finds
                if (result.size() > 200 || (!foundInLayer && layer > baseRadius + 50)) {
                    break;
                }
            }
        }
        
        return result;
    }
    
    /**
     * Finds all objects within a bounding box.
     * @param min Minimum corner of the bounding box
     * @param max Maximum corner of the bounding box
     * @return List of objects within the bounding box
     */
    public List<GalaxyContext.ObjectInfo> findObjectsInBounds(Vec3f min, Vec3f max) {
        List<GalaxyContext.ObjectInfo> result = new ArrayList<>();
        
        GridCell minCell = getGridCell(min);
        GridCell maxCell = getGridCell(max);
        
        // Check all grid cells within the bounding box
        for (int x = minCell.x; x <= maxCell.x; x++) {
            for (int y = minCell.y; y <= maxCell.y; y++) {
                for (int z = minCell.z; z <= maxCell.z; z++) {
                    GridCell cell = new GridCell(x, y, z);
                    
                    List<GalaxyContext.ObjectInfo> cellObjects = grid.get(cell);
                    if (cellObjects != null) {
                        for (GalaxyContext.ObjectInfo obj : cellObjects) {
                            Vec3f pos = obj.getPosition();
                            if (pos.x >= min.x && pos.x <= max.x &&
                                pos.y >= min.y && pos.y <= max.y &&
                                pos.z >= min.z && pos.z <= max.z) {
                                result.add(obj);
                            }
                        }
                    }
                }
            }
        }
        
        return result;
    }
    
    /**
     * Finds the closest object to a given position.
     * @param position The reference position
     * @param maxDistance Maximum distance to search (optional, use Float.MAX_VALUE for unlimited)
     * @return The closest object, or null if none found within maxDistance
     */
    public GalaxyContext.ObjectInfo findClosestObject(Vec3f position, float maxDistance) {
        GalaxyContext.ObjectInfo closest = null;
        float closestDistanceSquared = Float.MAX_VALUE;
        float maxDistanceSquared = maxDistance * maxDistance;
        
        List<GalaxyContext.ObjectInfo> candidates = findObjectsNear(position, maxDistance);
        
        for (GalaxyContext.ObjectInfo obj : candidates) {
            float distanceSquared = calculateDistanceSquared(position, obj.getPosition());
            if (distanceSquared < closestDistanceSquared && distanceSquared <= maxDistanceSquared) {
                closest = obj;
                closestDistanceSquared = distanceSquared;
            }
        }
        
        return closest;
    }
    
    /**
     * Gets all objects in the spatial index.
     * @return List of all indexed objects
     */
    public List<GalaxyContext.ObjectInfo> getAllObjects() {
        return new ArrayList<>(allObjects);
    }
    
    /**
     * Gets the number of objects in the spatial index.
     * @return Number of indexed objects
     */
    public int getObjectCount() {
        return allObjects.size();
    }
    
    /**
     * Gets statistics about the spatial index for debugging.
     * @return Map containing index statistics
     */
    public Map<String, Object> getStatistics() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalObjects", allObjects.size());
        stats.put("gridCells", grid.size());
        stats.put("gridSize", GRID_SIZE);
        
        // Calculate average objects per cell
        if (!grid.isEmpty()) {
            int totalObjectsInCells = grid.values().stream()
                .mapToInt(List::size)
                .sum();
            stats.put("averageObjectsPerCell", (double) totalObjectsInCells / grid.size());
        } else {
            stats.put("averageObjectsPerCell", 0.0);
        }
        
        return stats;
    }
    
    // Async methods to prevent UI freezing
    
    /**
     * Asynchronously finds all objects within a specified distance of a position.
     * @param position The center position
     * @param maxDistance Maximum distance to search
     * @param callback Callback to receive the results
     * @return CompletableFuture that completes when the search is done
     */
    public CompletableFuture<List<GalaxyContext.ObjectInfo>> findObjectsNearAsync(
            Vec3f position, float maxDistance, Consumer<List<GalaxyContext.ObjectInfo>> callback) {
        return CompletableFuture.supplyAsync(() -> {
            List<GalaxyContext.ObjectInfo> result = findObjectsNear(position, maxDistance);
            if (callback != null) {
                callback.accept(result);
            }
            return result;
        });
    }
    
    /**
     * Asynchronously finds all objects within a bounding box.
     * @param min Minimum corner of the bounding box
     * @param max Maximum corner of the bounding box
     * @param callback Callback to receive the results
     * @return CompletableFuture that completes when the search is done
     */
    public CompletableFuture<List<GalaxyContext.ObjectInfo>> findObjectsInBoundsAsync(
            Vec3f min, Vec3f max, Consumer<List<GalaxyContext.ObjectInfo>> callback) {
        return CompletableFuture.supplyAsync(() -> {
            List<GalaxyContext.ObjectInfo> result = findObjectsInBounds(min, max);
            if (callback != null) {
                callback.accept(result);
            }
            return result;
        });
    }
    
    /**
     * Asynchronously finds the closest object to a given position.
     * @param position The reference position
     * @param maxDistance Maximum distance to search
     * @param callback Callback to receive the result
     * @return CompletableFuture that completes when the search is done
     */
    public CompletableFuture<GalaxyContext.ObjectInfo> findClosestObjectAsync(
            Vec3f position, float maxDistance, Consumer<GalaxyContext.ObjectInfo> callback) {
        return CompletableFuture.supplyAsync(() -> {
            GalaxyContext.ObjectInfo result = findClosestObject(position, maxDistance);
            if (callback != null) {
                callback.accept(result);
            }
            return result;
        });
    }
    
    /**
     * Cancellable async search with progress reporting.
     * @param position The center position
     * @param maxDistance Maximum distance to search
     * @param progressCallback Callback for progress updates (0.0 to 1.0)
     * @param resultCallback Callback for final results
     * @return CompletableFuture that can be cancelled
     */
    public CompletableFuture<List<GalaxyContext.ObjectInfo>> findObjectsNearWithProgress(
            Vec3f position, float maxDistance, 
            Consumer<Double> progressCallback,
            Consumer<List<GalaxyContext.ObjectInfo>> resultCallback) {
        
        return CompletableFuture.supplyAsync(() -> {
            List<GalaxyContext.ObjectInfo> result = new ArrayList<>();
            
            // Calculate grid cells to check based on search radius
            int gridRadius = (int) Math.ceil(maxDistance / GRID_SIZE);
            GridCell centerCell = getGridCell(position);
            
            // Dynamic grid radius: start at 500, extend to 700 only if needed
            int baseRadius = Math.min(gridRadius, 500);
            int maxRadius = Math.min(gridRadius, 700);
            
            int totalCells = (2 * gridRadius + 1) * (2 * gridRadius + 1) * (2 * gridRadius + 1);
            int processedCells = 0;
            
            // Check all grid cells within the search radius
            for (int dx = -gridRadius; dx <= gridRadius; dx++) {
                for (int dy = -gridRadius; dy <= gridRadius; dy++) {
                    for (int dz = -gridRadius; dz <= gridRadius; dz++) {
                        // Check for cancellation
                        if (Thread.currentThread().isInterrupted()) {
                            return result; // Return partial results if cancelled
                        }
                        
                        // Skip cells that are definitely too far (sphere vs cube optimization)
                        if (dx*dx + dy*dy + dz*dz > gridRadius*gridRadius) {
                            processedCells++;
                            continue;
                        }
                        
                        GridCell cell = new GridCell(
                            centerCell.x + dx,
                            centerCell.y + dy,
                            centerCell.z + dz
                        );
                        
                        List<GalaxyContext.ObjectInfo> cellObjects = grid.get(cell);
                        if (cellObjects != null) {
                            float maxDistanceSquared = maxDistance * maxDistance;
                            for (GalaxyContext.ObjectInfo obj : cellObjects) {
                                float distanceSquared = calculateDistanceSquared(position, obj.getPosition());
                                if (distanceSquared <= maxDistanceSquared) {
                                    result.add(obj);
                                }
                            }
                        }
                        
                        processedCells++;
                        
                        // Report progress every 100 cells
                        if (processedCells % 100 == 0 && progressCallback != null) {
                            double progress = (double) processedCells / totalCells;
                            progressCallback.accept(progress);
                        }
                    }
                }
            }
            
            // Final progress update
            if (progressCallback != null) {
                progressCallback.accept(1.0);
            }
            
            if (resultCallback != null) {
                resultCallback.accept(result);
            }
            
            return result;
        });
    }
    
    // Private helper methods
    

    
    private GridCell getGridCell(Vec3f position) {
        int x = (int) Math.floor(position.x / GRID_SIZE);
        int y = (int) Math.floor(position.y / GRID_SIZE);
        int z = (int) Math.floor(position.z / GRID_SIZE);
        return new GridCell(x, y, z);
    }
    
    private float calculateDistance(Vec3f pos1, Vec3f pos2) {
        float dx = pos1.x - pos2.x;
        float dy = pos1.y - pos2.y;
        float dz = pos1.z - pos2.z;
        return (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
    }
    
    private float calculateDistanceSquared(Vec3f pos1, Vec3f pos2) {
        float dx = pos1.x - pos2.x;
        float dy = pos1.y - pos2.y;
        float dz = pos1.z - pos2.z;
        return dx * dx + dy * dy + dz * dz;
    }
    
    /**
     * Represents a cell in the spatial grid.
     */
    private static class GridCell {
        final int x, y, z;
        
        GridCell(int x, int y, int z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }
        
        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (obj == null || getClass() != obj.getClass()) return false;
            GridCell gridCell = (GridCell) obj;
            return x == gridCell.x && y == gridCell.y && z == gridCell.z;
        }
        
        @Override
        public int hashCode() {
            return Objects.hash(x, y, z);
        }
        
        @Override
        public String toString() {
            return String.format("GridCell(%d, %d, %d)", x, y, z);
        }
    }
}
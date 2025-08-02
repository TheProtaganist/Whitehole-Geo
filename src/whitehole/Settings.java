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
package whitehole;

import java.awt.Color;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.prefs.Preferences;

public final class Settings {
    private Settings() {}
    
    private static final Preferences PREFERENCES = Preferences.userRoot();
    
    
    // ==== GENERAL ====
    public static String getLastGameDir() { return PREFERENCES.get("whitehole_lastGameDir", null); }
    public static void setLastGameDir(String val) { PREFERENCES.put("whitehole_lastGameDir", val); }
    
    public static String getBaseGameDir() { return PREFERENCES.get("whitehole_baseGameDir", null); }
    public static void setBaseGameDir(String val) { PREFERENCES.put("whitehole_baseGameDir", val); }
    
    public static String[] getRecentBcsvs() {  return PREFERENCES.get("whitehole_recentBcsvs", "").split(", ");}
    public static void setRecentBcsvs(List<String> val) { PREFERENCES.put("whitehole_recentBcsvs", String.join(", ", val)); }
    
    public static final String[] DEFAULT_BCSV_2 = {"/StageData/RedBlueExGalaxy/RedBlueExGalaxyScenario.arc", "/RedBlueExGalaxyScenario/ScenarioData.bcsv"};
    public static final String[] DEFAULT_BCSV_1 = {"/StageData/CocoonExGalaxy/CocoonExGalaxyScenario.arc", "/CocoonExGalaxyScenario/ScenarioData.bcsv"};      
    public static String[] getLastBcsv() {
        String[] recentBcsvs = getRecentBcsvs();
        if (recentBcsvs[0].isBlank())
            return Whitehole.getCurrentGameType() == 2 ? DEFAULT_BCSV_2: DEFAULT_BCSV_1;
        return getRecentBcsvs()[0].split("\n");
    }
    public static void setLastBcsv(String archivePath, String filePath) {
        ArrayList<String> recentBcsvs = new ArrayList<>();
        Collections.addAll(recentBcsvs, getRecentBcsvs());
        String mostRecentBcsv = archivePath + "\n" + filePath;
        if (recentBcsvs.contains(mostRecentBcsv))
            recentBcsvs.removeIf(s -> s.equals(mostRecentBcsv));
        if (recentBcsvs.size() >= 5)
            recentBcsvs = new ArrayList<>(recentBcsvs.subList(0, 4));
        recentBcsvs.add(0, mostRecentBcsv);
        setRecentBcsvs(recentBcsvs);
    }
    public static String getLastBcsvArchive() { return getLastBcsv()[0]; }
    public static String getLastBcsvFile() { return getLastBcsv()[1]; }
    
    public static boolean getSJISNotSupported() { return PREFERENCES.getBoolean("whitehole_sjisNotSupported", false); }
    public static void setSJISNotSupported(boolean val) { PREFERENCES.putBoolean("whitehole_sjisNotSupported", val); }
    
    public static boolean getUseDarkMode() { return PREFERENCES.getBoolean("whitehole_useDarkMode", false); }
    public static void setUseDarkMode(boolean val) { PREFERENCES.putBoolean("whitehole_useDarkMode", val); }
    
    public static boolean getUseCyanTheme() { return PREFERENCES.getBoolean("whitehole_useCyanTheme", true); }
    public static void setUseCyanTheme(boolean val) { PREFERENCES.putBoolean("whitehole_useCyanTheme", val); }
    
    public static boolean getOpenGalaxyEditorMaximized() { return PREFERENCES.getBoolean("whitehole_openGalaxyEditorMaximized", false); }
    public static void setOpenGalaxyEditorMaximized(boolean val) { PREFERENCES.putBoolean("whitehole_openGalaxyEditorMaximized", val); }
    
    
    // ==== DEBUGGING ====
    public static boolean getDebugFakeColor() { return PREFERENCES.getBoolean("whitehole_debugFakeColor", false); }
    public static void setDebugFakeColor(boolean val) { PREFERENCES.putBoolean("whitehole_debugFakeColor", val); }
    
    public static boolean getDebugFastDrag() { return PREFERENCES.getBoolean("whitehole_debugFastDrag", false); }
    public static void setDebugFastDrag(boolean val) { PREFERENCES.putBoolean("whitehole_debugFastDrag", val); }
    
    public static boolean getDebugAdditionalLogs() { return PREFERENCES.getBoolean("whitehole_debugAdditionalLogs", false); }
    public static void setDebugAdditionalLogs(boolean val) { PREFERENCES.putBoolean("whitehole_debugAdditionalLogs", val); }
    
    
    // ==== RENDERING TOGGLES ====
    public static boolean getShowAxis() { return PREFERENCES.getBoolean("whitehole_showAxis", true); }
    public static void setShowAxis(boolean val) { PREFERENCES.putBoolean("whitehole_showAxis", val); }
    
    public static boolean getShowAreas() { return PREFERENCES.getBoolean("whitehole_showAreas", true); }
    public static void setShowAreas(boolean val) { PREFERENCES.putBoolean("whitehole_showAreas", val); }
    
    public static boolean getShowCameras() { return PREFERENCES.getBoolean("whitehole_showCameras", true); }
    public static void setShowCameras(boolean val) { PREFERENCES.putBoolean("whitehole_showCameras", val); }
    
    public static boolean getShowGravity() { return PREFERENCES.getBoolean("whitehole_showGravity", true); }
    public static void setShowGravity(boolean val) { PREFERENCES.putBoolean("whitehole_showGravity", val); }
    
    public static boolean getShowPaths() { return PREFERENCES.getBoolean("whitehole_showPaths", true); }
    public static void setShowPaths(boolean val) { PREFERENCES.putBoolean("whitehole_showPaths", val); }
    
    
    public static boolean getUseBetterQuality() { return PREFERENCES.getBoolean("whitehole_useBetterQuality", true); }
    public static void setUseBetterQuality(boolean val) { PREFERENCES.putBoolean("whitehole_useBetterQuality", val); }
    
    public static boolean getUseLowPolyModels() { return PREFERENCES.getBoolean("whitehole_useLowPolyModels", false); }
    public static void setUseLowPolyModels(boolean val) { PREFERENCES.putBoolean("whitehole_useLowPolyModels", val); }
    
    public static boolean getUseCollisionModels() { return PREFERENCES.getBoolean("whitehole_useCollisionModels", false); }
    public static void setUseCollisionModels(boolean val) { PREFERENCES.putBoolean("whitehole_useCollisionModels", val); }
    
    
    // ==== EDITOR COLORS ====
    public static Color getColor(String preferencesString, Color fallbackColor) { 
        int red = PREFERENCES.getInt(preferencesString + "Red", -1);
        int green = PREFERENCES.getInt(preferencesString + "Green", -1);
        int blue = PREFERENCES.getInt(preferencesString + "Blue", -1);
        int alpha = PREFERENCES.getInt(preferencesString + "Alpha", -1);
        if (red == -1 || green == -1 || blue == -1 || alpha == -1) {
            return fallbackColor;
        }
        return new Color(red, green, blue, alpha);
    }
    public static void setColor(String preferencesString, Color newColor) {
        PREFERENCES.putInt(preferencesString + "Red", newColor.getRed());
        PREFERENCES.putInt(preferencesString + "Green", newColor.getGreen());
        PREFERENCES.putInt(preferencesString + "Blue", newColor.getBlue());
        PREFERENCES.putInt(preferencesString + "Alpha", newColor.getAlpha());
    }
    
    
    public static final Color DEFAULT_NORMAL_AREA_PRIMARY_COLOR = new Color(75, 255, 255);
    public static Color getNormalAreaPrimaryColor() { return getColor("whitehole_normalAreaPrimaryColor", DEFAULT_NORMAL_AREA_PRIMARY_COLOR); }
    public static void setNormalAreaPrimaryColor(Color val) { setColor("whitehole_normalAreaPrimaryColor", val); }
    
    public static final Color DEFAULT_NORMAL_AREA_SECONDARY_COLOR = new Color(255, 75, 75);
    public static Color getNormalAreaSecondaryColor() { return getColor("whitehole_normalAreaSecondaryColor", DEFAULT_NORMAL_AREA_SECONDARY_COLOR); }
    public static void setNormalAreaSecondaryColor(Color val) { setColor("whitehole_normalAreaSecondaryColor", val); }
    
    public static final Color DEFAULT_CAMERA_AREA_PRIMARY_COLOR = new Color(204, 0, 0);
    public static Color getCameraAreaPrimaryColor() { return getColor("whitehole_cameraAreaPrimaryColor", DEFAULT_CAMERA_AREA_PRIMARY_COLOR); }
    public static void setCameraAreaPrimaryColor(Color val) { setColor("whitehole_cameraAreaPrimaryColor", val); }
    
    public static final Color DEFAULT_CAMERA_AREA_SECONDARY_COLOR = new Color(0, 204, 204);
    public static Color getCameraAreaSecondaryColor() { return getColor("whitehole_cameraAreaSecondaryColor", DEFAULT_CAMERA_AREA_SECONDARY_COLOR); }
    public static void setCameraAreaSecondaryColor(Color val) { setColor("whitehole_cameraAreaSecondaryColor", val); }
    
    public static final Color DEFAULT_GRAVITY_AREA_PRIMARY_COLOR = new Color(0, 204, 0);
    public static Color getGravityAreaPrimaryColor() { return getColor("whitehole_gravityAreaPrimaryColor", DEFAULT_GRAVITY_AREA_PRIMARY_COLOR); }
    public static void setGravityAreaPrimaryColor(Color val) { setColor("whitehole_gravityAreaPrimaryColor", val); }
    
    public static final Color DEFAULT_GRAVITY_AREA_SECONDARY_COLOR = new Color(204, 0, 204);
    public static Color getGravityAreaSecondaryColor() { return getColor("whitehole_gravityAreaSecondaryColor", DEFAULT_GRAVITY_AREA_SECONDARY_COLOR); }
    public static void setGravityAreaSecondaryColor(Color val) { setColor("whitehole_gravityAreaSecondaryColor", val); }
    
    public static final Color DEFAULT_GRAVITY_AREA_ZERO_PRIMARY_COLOR = new Color(0, 204, 153);
    public static Color getGravityAreaZeroPrimaryColor() { return getColor("whitehole_gravityAreaZeroPrimaryColor", DEFAULT_GRAVITY_AREA_ZERO_PRIMARY_COLOR); }
    public static void setGravityAreaZeroPrimaryColor(Color val) { setColor("whitehole_gravityAreaZeroPrimaryColor", val); }
    
    public static final Color DEFAULT_GRAVITY_AREA_ZERO_SECONDARY_COLOR = new Color(204, 0, 51);
    public static Color getGravityAreaZeroSecondaryColor() { return getColor("whitehole_gravityAreaZerpSecondaryColor", DEFAULT_GRAVITY_AREA_ZERO_SECONDARY_COLOR); }
    public static void setGravityAreaZeroSecondaryColor(Color val) { setColor("whitehole_gravityAreaZerpSecondaryColor", val); }
    
    public static final Color DEFAULT_OBJECT_HIGHLIGHT_COLOR = new Color(255, 255, 191, 76);
    public static Color getObjectHighlightColor() { return getColor("whitehole_objectHighlightColor", DEFAULT_OBJECT_HIGHLIGHT_COLOR); }
    public static void setObjectHighlightColor(Color val) { setColor("whitehole_objectHighlightColor", val); }
    
    // Cyan theme background colors
    public static final float[] DEFAULT_CYAN_BACKGROUND_COLOR = {0.94f, 0.97f, 1.0f, 1.0f}; // Light blue (AliceBlue)
    public static final float[] DEFAULT_BLUE_BACKGROUND_COLOR = {0.118f, 0.118f, 0.784f, 1.0f}; // Original blue
    
    public static float[] getBackgroundColor() {
        if (getUseCyanTheme()) {
            return DEFAULT_CYAN_BACKGROUND_COLOR;
        } else {
            return DEFAULT_BLUE_BACKGROUND_COLOR;
        }
    }
    
    
    // ==== EDITOR CONTROLS ====
    public static boolean getUseReverseRot() { return PREFERENCES.getBoolean("whitehole_useReverseRot", false); }
    public static void setUseReverseRot(boolean val) { PREFERENCES.putBoolean("whitehole_useReverseRot", val); }
    
    public static boolean getUseWASD() { return PREFERENCES.getBoolean("whitehole_useWASD", false); }
    public static void setUseWASD(boolean val) { PREFERENCES.putBoolean("whitehole_useWASD", val); }
    
    // ==== CAMERA CONTROLS ====
    public static boolean getUseRightClickCamera() { return PREFERENCES.getBoolean("whitehole_useRightClickCamera", false); }
    public static void setUseRightClickCamera(boolean val) { PREFERENCES.putBoolean("whitehole_useRightClickCamera", val); }
    
    public static int getKeyPosition() { return PREFERENCES.getInt("whitehole_keyPosition", KeyEvent.VK_G); }
    public static void setKeyPosition(int val) { PREFERENCES.putInt("whitehole_keyPosition", val); }
    
    public static int getKeyRotation() { return PREFERENCES.getInt("whitehole_keyRotation", KeyEvent.VK_R); }
    public static void setKeyRotation(int val) { PREFERENCES.putInt("whitehole_keyRotation", val); }
    
    public static int getKeyScale() { return PREFERENCES.getInt("whitehole_keyScale", KeyEvent.VK_S); }
    public static void setKeyScale(int val) { PREFERENCES.putInt("whitehole_keyScale", val); }
    
    
    // ==== AI PROVIDER SETTINGS ====
    public enum AIProviderType {
        GEMINI,
        OLLAMA
    }
    
    public static AIProviderType getActiveAIProvider() {
        String provider = PREFERENCES.get("whitehole_activeAIProvider", "GEMINI");
        try {
            return AIProviderType.valueOf(provider);
        } catch (IllegalArgumentException e) {
            return AIProviderType.GEMINI;
        }
    }
    public static void setActiveAIProvider(AIProviderType val) { 
        PREFERENCES.put("whitehole_activeAIProvider", val.name()); 
    }
    
    // Gemini settings
    public static String getGeminiApiKey() { 
        String key = PREFERENCES.get("whitehole_geminiApiKey", "");
        // If the key looks like a placeholder or demo key, return empty
        if (key.contains("your-api-key") || key.contains("demo") || key.contains("test") || 
            key.contains("example") || key.contains("placeholder") || key.contains("sample")) {
            return "";
        }
        return key; 
    }
    public static void setGeminiApiKey(String val) { PREFERENCES.put("whitehole_geminiApiKey", val); }
    
    /**
     * Forces the Gemini API key to be completely cleared.
     * Use this to reset the API key to blank.
     */
    public static void forceClearGeminiApiKey() {
        PREFERENCES.remove("whitehole_geminiApiKey");
    }
    
    public static String getGeminiEndpoint() { 
        return PREFERENCES.get("whitehole_geminiEndpoint", 
            "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent"); 
    }
    public static void setGeminiEndpoint(String val) { PREFERENCES.put("whitehole_geminiEndpoint", val); }
    
    public static String getSelectedGeminiModel() { 
        return PREFERENCES.get("whitehole_selectedGeminiModel", "gemini-1.5-flash-latest"); 
    }
    public static void setSelectedGeminiModel(String val) { PREFERENCES.put("whitehole_selectedGeminiModel", val); }
    
    // Ollama settings
    public static String getOllamaServerUrl() { 
        return PREFERENCES.get("whitehole_ollamaServerUrl", "http://localhost:11434"); 
    }
    public static void setOllamaServerUrl(String val) { PREFERENCES.put("whitehole_ollamaServerUrl", val); }
    
    public static String getOllamaModel() { 
        return PREFERENCES.get("whitehole_ollamaModel", "llama2"); 
    }
    public static void setOllamaModel(String val) { PREFERENCES.put("whitehole_ollamaModel", val); }
    
    public static String getSelectedOllamaModel() { 
        return PREFERENCES.get("whitehole_selectedOllamaModel", "llama2"); 
    }
    public static void setSelectedOllamaModel(String val) { PREFERENCES.put("whitehole_selectedOllamaModel", val); }
    
    /**
     * Clears any invalid AI provider settings that might cause issues.
     * Call this if there are configuration problems.
     */
    public static void clearInvalidAISettings() {
        // Clear any test endpoints
        String currentEndpoint = getGeminiEndpoint();
        if (currentEndpoint.contains("test-endpoint.com") || currentEndpoint.isEmpty()) {
            setGeminiEndpoint("https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent");
        }
        
        // Clear any test API keys or non-empty keys (force blank by default)
        String currentKey = getGeminiApiKey();
        if (currentKey.equals("test-key") || currentKey.equals("default-key") || 
            currentKey.contains("test") || currentKey.contains("example") ||
            currentKey.contains("demo") || currentKey.contains("sample") ||
            currentKey.contains("placeholder") || currentKey.contains("your-api-key") ||
            currentKey.contains("AIza") || currentKey.length() > 0) {
            setGeminiApiKey("");
            // Force the preference to be cleared
            PREFERENCES.remove("whitehole_geminiApiKey");
        }
        
        // Clear any invalid Ollama URLs
        String currentOllamaUrl = getOllamaServerUrl();
        if (currentOllamaUrl.contains("test-endpoint.com") || currentOllamaUrl.isEmpty()) {
            setOllamaServerUrl("http://localhost:11434");
        }
    }
}

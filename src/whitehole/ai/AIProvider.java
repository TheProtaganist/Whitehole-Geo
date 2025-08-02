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

import java.util.Map;

/**
 * Interface for AI providers that can process natural language commands
 * and return structured responses for object manipulation.
 */
public interface AIProvider {
    
    /**
     * Processes a natural language command with galaxy context and returns
     * a structured response containing object transformations.
     * 
     * @param command The natural language command to process
     * @param context The current galaxy context containing object information
     * @return AIResponse containing transformations and feedback
     * @throws AIProviderException if processing fails
     */
    AIResponse processCommand(String command, GalaxyContext context) throws AIProviderException;
    
    /**
     * Checks if the AI provider is available and properly configured.
     * 
     * @return true if the provider can process commands, false otherwise
     */
    boolean isAvailable();
    
    /**
     * Configures the AI provider with the given settings.
     * 
     * @param config Configuration parameters specific to this provider
     * @throws AIProviderException if configuration is invalid
     */
    void configure(Map<String, String> config) throws AIProviderException;
    
    /**
     * Gets the display name of this AI provider.
     * 
     * @return Human-readable name of the provider
     */
    String getProviderName();
    
    /**
     * Gets the current configuration status of this provider.
     * 
     * @return Configuration status message
     */
    String getConfigurationStatus();
}
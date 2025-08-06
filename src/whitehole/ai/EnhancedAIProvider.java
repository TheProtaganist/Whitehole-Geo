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

/**
 * Enhanced AI provider interface that extends the base AIProvider with additional
 * capabilities like streaming, model selection, and connection testing.
 */
public interface EnhancedAIProvider extends AIProvider {
    
    /**
     * Checks if this provider supports streaming responses.
     * 
     * @return true if streaming is supported, false otherwise
     */
    boolean supportsStreaming();
    
    /**
     * Processes a command with streaming response support.
     * 
     * @param command The natural language command to process
     * @param context The current galaxy context containing object information
     * @param callback Callback for receiving streaming response chunks
     * @return AIResponse containing final transformations and feedback
     * @throws AIProviderException if processing fails
     */
    AIResponse processStreamingCommand(String command, GalaxyContext context, StreamingCallback callback) throws AIProviderException;
    
    /**
     * Gets information about available models for this provider.
     * 
     * @return Array of ModelInfo objects describing available models
     * @throws AIProviderException if model information cannot be retrieved
     */
    ModelInfo[] getAvailableModels() throws AIProviderException;
    
    /**
     * Tests the connection to the AI provider service.
     * 
     * @return true if connection is successful, false otherwise
     */
    boolean testConnection();
    
    /**
     * Callback interface for streaming responses.
     */
    interface StreamingCallback {
        /**
         * Called when a chunk of the response is received.
         * 
         * @param chunk The response chunk
         */
        void onChunk(String chunk);
        
        /**
         * Called when streaming is complete.
         */
        void onComplete();
        
        /**
         * Called when an error occurs during streaming.
         * 
         * @param error The error that occurred
         */
        void onError(Exception error);
    }
    
    /**
     * Information about an AI model.
     */
    class ModelInfo {
        private final String id;
        private final String name;
        private final String description;
        private final boolean supportsVision;
        private final int maxTokens;
        private final double inputCostPer1kTokens;
        private final double outputCostPer1kTokens;
        
        public ModelInfo(String id, String name, String description, boolean supportsVision, 
                        int maxTokens, double inputCostPer1kTokens, double outputCostPer1kTokens) {
            this.id = id;
            this.name = name;
            this.description = description;
            this.supportsVision = supportsVision;
            this.maxTokens = maxTokens;
            this.inputCostPer1kTokens = inputCostPer1kTokens;
            this.outputCostPer1kTokens = outputCostPer1kTokens;
        }
        
        public String getId() { return id; }
        public String getName() { return name; }
        public String getDescription() { return description; }
        public boolean supportsVision() { return supportsVision; }
        public int getMaxTokens() { return maxTokens; }
        public double getInputCostPer1kTokens() { return inputCostPer1kTokens; }
        public double getOutputCostPer1kTokens() { return outputCostPer1kTokens; }
        
        @Override
        public String toString() {
            return name + " (" + id + ")";
        }
    }
}
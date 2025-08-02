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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * CommandResult represents the structured result of processing a natural language
 * command through the AI system. It contains transformations to apply, user feedback,
 * and any errors or warnings that occurred during processing.
 */
public class CommandResult {
    private final boolean success;
    private final List<ObjectTransformation> transformations;
    private final String userFeedback;
    private final String aiResponse;
    private final List<String> errors;
    private final List<String> warnings;
    private final long processingTimeMs;
    
    private CommandResult(Builder builder) {
        this.success = builder.success;
        this.transformations = Collections.unmodifiableList(new ArrayList<>(builder.transformations));
        this.userFeedback = builder.userFeedback;
        this.aiResponse = builder.aiResponse;
        this.errors = Collections.unmodifiableList(new ArrayList<>(builder.errors));
        this.warnings = Collections.unmodifiableList(new ArrayList<>(builder.warnings));
        this.processingTimeMs = builder.processingTimeMs;
    }
    
    /**
     * Returns true if the command was processed successfully.
     */
    public boolean isSuccess() {
        return success;
    }
    
    /**
     * Returns the list of object transformations to apply.
     */
    public List<ObjectTransformation> getTransformations() {
        return transformations;
    }
    
    /**
     * Returns user-friendly feedback about what was processed.
     */
    public String getUserFeedback() {
        return userFeedback;
    }
    
    /**
     * Returns the raw AI response for debugging purposes.
     */
    public String getAiResponse() {
        return aiResponse;
    }
    
    /**
     * Returns any error messages from processing.
     */
    public List<String> getErrors() {
        return errors;
    }
    
    /**
     * Returns any warning messages from processing.
     */
    public List<String> getWarnings() {
        return warnings;
    }
    
    /**
     * Returns the time taken to process the command in milliseconds.
     */
    public long getProcessingTimeMs() {
        return processingTimeMs;
    }
    
    /**
     * Returns true if there are any errors.
     */
    public boolean hasErrors() {
        return !errors.isEmpty();
    }
    
    /**
     * Returns true if there are any warnings.
     */
    public boolean hasWarnings() {
        return !warnings.isEmpty();
    }
    
    /**
     * Returns the number of transformations that will be applied.
     */
    public int getTransformationCount() {
        return transformations.size();
    }
    
    /**
     * Returns a summary of the command result for logging.
     */
    public String getSummary() {
        StringBuilder summary = new StringBuilder();
        summary.append("CommandResult{");
        summary.append("success=").append(success);
        summary.append(", transformations=").append(transformations.size());
        summary.append(", errors=").append(errors.size());
        summary.append(", warnings=").append(warnings.size());
        summary.append(", processingTime=").append(processingTimeMs).append("ms");
        summary.append("}");
        return summary.toString();
    }
    
    /**
     * Builder class for creating CommandResult instances.
     */
    public static class Builder {
        private boolean success = false;
        private List<ObjectTransformation> transformations = new ArrayList<>();
        private String userFeedback = "";
        private String aiResponse = "";
        private List<String> errors = new ArrayList<>();
        private List<String> warnings = new ArrayList<>();
        private long processingTimeMs = 0;
        
        public Builder setSuccess(boolean success) {
            this.success = success;
            return this;
        }
        
        public Builder addTransformation(ObjectTransformation transformation) {
            this.transformations.add(transformation);
            return this;
        }
        
        public Builder setTransformations(List<ObjectTransformation> transformations) {
            this.transformations = new ArrayList<>(transformations);
            return this;
        }
        
        public Builder setUserFeedback(String userFeedback) {
            this.userFeedback = userFeedback != null ? userFeedback : "";
            return this;
        }
        
        public Builder setAiResponse(String aiResponse) {
            this.aiResponse = aiResponse != null ? aiResponse : "";
            return this;
        }
        
        public Builder addError(String error) {
            this.errors.add(error);
            return this;
        }
        
        public Builder setErrors(List<String> errors) {
            this.errors = new ArrayList<>(errors);
            return this;
        }
        
        public Builder addWarning(String warning) {
            this.warnings.add(warning);
            return this;
        }
        
        public Builder setWarnings(List<String> warnings) {
            this.warnings = new ArrayList<>(warnings);
            return this;
        }
        
        public Builder setProcessingTimeMs(long processingTimeMs) {
            this.processingTimeMs = processingTimeMs;
            return this;
        }
        
        public CommandResult build() {
            return new CommandResult(this);
        }
    }
    
    /**
     * Creates a successful command result.
     */
    public static CommandResult success(List<ObjectTransformation> transformations, String userFeedback) {
        return new Builder()
                .setSuccess(true)
                .setTransformations(transformations)
                .setUserFeedback(userFeedback)
                .build();
    }
    
    /**
     * Creates a successful command result with AI response.
     */
    public static CommandResult success(List<ObjectTransformation> transformations, String userFeedback, String aiResponse) {
        return new Builder()
                .setSuccess(true)
                .setTransformations(transformations)
                .setUserFeedback(userFeedback)
                .setAiResponse(aiResponse)
                .build();
    }
    
    /**
     * Creates a successful command result with warnings.
     */
    public static CommandResult success(List<ObjectTransformation> transformations, String userFeedback, 
                                      String aiResponse, List<String> warnings) {
        return new Builder()
                .setSuccess(true)
                .setTransformations(transformations)
                .setUserFeedback(userFeedback)
                .setAiResponse(aiResponse)
                .setWarnings(warnings)
                .build();
    }
    
    /**
     * Creates a failed command result with error messages.
     */
    public static CommandResult failure(List<String> errors) {
        return new Builder()
                .setSuccess(false)
                .setErrors(errors)
                .build();
    }
    
    /**
     * Creates a failed command result with a single error message.
     */
    public static CommandResult failure(String error) {
        return new Builder()
                .setSuccess(false)
                .addError(error)
                .build();
    }
    
    /**
     * Creates a failed command result with error and warnings.
     */
    public static CommandResult failure(String error, List<String> warnings) {
        return new Builder()
                .setSuccess(false)
                .addError(error)
                .setWarnings(warnings)
                .build();
    }
    
    /**
     * Creates a failed command result with multiple errors and warnings.
     */
    public static CommandResult failure(List<String> errors, List<String> warnings) {
        return new Builder()
                .setSuccess(false)
                .setErrors(errors)
                .setWarnings(warnings)
                .build();
    }
}
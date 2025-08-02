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
 * Represents a structured response from an AI provider containing
 * object transformations and user feedback.
 */
public class AIResponse {
    private final boolean success;
    private final List<ObjectTransformation> transformations;
    private final String feedback;
    private final List<String> errors;
    private final List<String> warnings;
    
    private AIResponse(Builder builder) {
        this.success = builder.success;
        this.transformations = Collections.unmodifiableList(new ArrayList<>(builder.transformations));
        this.feedback = builder.feedback;
        this.errors = Collections.unmodifiableList(new ArrayList<>(builder.errors));
        this.warnings = Collections.unmodifiableList(new ArrayList<>(builder.warnings));
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
    public String getFeedback() {
        return feedback;
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
     * Builder class for creating AIResponse instances.
     */
    public static class Builder {
        private boolean success = false;
        private List<ObjectTransformation> transformations = new ArrayList<>();
        private String feedback = "";
        private List<String> errors = new ArrayList<>();
        private List<String> warnings = new ArrayList<>();
        
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
        
        public Builder setFeedback(String feedback) {
            this.feedback = feedback != null ? feedback : "";
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
        
        public AIResponse build() {
            return new AIResponse(this);
        }
    }
    
    /**
     * Creates a successful response with transformations and feedback.
     */
    public static AIResponse success(List<ObjectTransformation> transformations, String feedback) {
        return new Builder()
                .setSuccess(true)
                .setTransformations(transformations)
                .setFeedback(feedback)
                .build();
    }
    
    /**
     * Creates a failed response with error messages.
     */
    public static AIResponse failure(List<String> errors) {
        return new Builder()
                .setSuccess(false)
                .setErrors(errors)
                .build();
    }
    
    /**
     * Creates a failed response with a single error message.
     */
    public static AIResponse failure(String error) {
        return new Builder()
                .setSuccess(false)
                .addError(error)
                .build();
    }
}
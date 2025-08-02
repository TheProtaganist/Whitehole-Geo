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
 * Exception thrown by AI providers when processing fails.
 */
public class AIProviderException extends Exception {
    
    public enum ErrorType {
        CONFIGURATION_ERROR,
        NETWORK_ERROR,
        AUTHENTICATION_ERROR,
        RATE_LIMIT_ERROR,
        INVALID_RESPONSE,
        SERVICE_UNAVAILABLE,
        TIMEOUT_ERROR,
        UNKNOWN_ERROR
    }
    
    private final ErrorType errorType;
    
    public AIProviderException(String message, ErrorType errorType) {
        super(message);
        this.errorType = errorType;
    }
    
    public AIProviderException(String message, ErrorType errorType, Throwable cause) {
        super(message, cause);
        this.errorType = errorType;
    }
    
    public ErrorType getErrorType() {
        return errorType;
    }
    
    /**
     * Returns a user-friendly error message based on the error type.
     */
    public String getUserFriendlyMessage() {
        switch (errorType) {
            case CONFIGURATION_ERROR:
                return "AI provider is not properly configured. Please check your settings.";
            case NETWORK_ERROR:
                return "Network connection failed. Please check your internet connection.";
            case AUTHENTICATION_ERROR:
                return "Authentication failed. Please check your API key or credentials.";
            case RATE_LIMIT_ERROR:
                return "Rate limit exceeded. Please wait before trying again.";
            case INVALID_RESPONSE:
                return "AI provider returned an invalid response. Please try again.";
            case SERVICE_UNAVAILABLE:
                return "AI service is currently unavailable. Please try again later.";
            case TIMEOUT_ERROR:
                return "Request timed out. Please try again.";
            default:
                return "An unknown error occurred: " + getMessage();
        }
    }
}
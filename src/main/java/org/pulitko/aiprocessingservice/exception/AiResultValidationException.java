package org.pulitko.aiprocessingservice.exception;

public class AiResultValidationException extends RuntimeException {
    public AiResultValidationException(String schemaKey, String message, Throwable cause) {
        super("Validation failed for schema " + schemaKey + ": " + message, cause);
    }

    public AiResultValidationException(String schemaKey, String message) {
        super("Validation failed for schema " + schemaKey + ": " + message);
    }
}

package org.pulitko.aiprocessingservice.exception;

public class AiResultValidationException extends BaseBusinessException {
    private final String ref;
    private final static String key = "AI_RESULT_VALIDATION_FAILED";
    private static final int ERROR_CODE = 422;

    public AiResultValidationException(String ref, String message) {
        super("Validation failed for schema " + message, ERROR_CODE);
        this.ref = ref;
    }

    public AiResultValidationException(String ref, String message, Throwable cause) {
        super("Validation failed for schema: " + message, ERROR_CODE, cause);
        this.ref = ref;
    }

    @Override
    public String getKey() {
        return key;
    }
}

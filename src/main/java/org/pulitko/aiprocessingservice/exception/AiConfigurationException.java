package org.pulitko.aiprocessingservice.exception;


public class AiConfigurationException extends BaseBusinessException {
    private final String ref;
    private static final String key = "ERR_AI_CONFIG_INVALID_SCHEMA";
    private static final int ERROR_CODE = 5002;
    public AiConfigurationException(String ref, String message) {
        super(message, ERROR_CODE);
        this.ref = ref;
    }
    public AiConfigurationException(String ref, String message, Throwable cause) {
        super("Validation failed for schema: " + message, ERROR_CODE, cause);
        this.ref = ref;
    }



    @Override
    public String getKey() {
        return key;
    }
}
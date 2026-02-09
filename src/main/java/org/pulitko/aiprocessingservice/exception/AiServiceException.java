package org.pulitko.aiprocessingservice.exception;

public class AiServiceException extends BaseBusinessException {
    private static final String key = "ERR_AI_EMPTY";
    private static final int ERROR_CODE = 5001;
    public AiServiceException(String message) {
        super(message, ERROR_CODE);
    }

    @Override
    public String getKey() {
        return key;
    }
}
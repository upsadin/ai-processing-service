package org.pulitko.aiprocessingservice.exception;

public class AiRetryableException extends RuntimeException {
    public AiRetryableException(String message, Throwable cause) {
        super(message, cause);
    }
}
package org.pulitko.aiprocessingservice.exception;

public class PromptNotFoundException extends RuntimeException {
    public PromptNotFoundException(String message) {
        super(message);
    }
}

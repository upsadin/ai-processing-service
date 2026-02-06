package org.pulitko.aiprocessingservice.exception;

import jakarta.validation.ConstraintViolation;
import org.pulitko.aiprocessingservice.model.IncomingMessage;

import java.util.Set;

public class IncomingMessageValidationException extends BaseBusinessException {

    private static final String key = "INCOMING_MESSAGE_VALIDATION_FAILED";
    private static final int ERROR_CODE = 422;

    private final Set<ConstraintViolation<IncomingMessage>> violations;

    public IncomingMessageValidationException(Set<ConstraintViolation<IncomingMessage>> violations) {
        super("Incoming message validation failed",
                ERROR_CODE);
        this.violations = violations;
    }

    public Set<ConstraintViolation<IncomingMessage>> getViolations() {
        return violations;
    }

    @Override
    public String getKey() {
        return null;
    }
}

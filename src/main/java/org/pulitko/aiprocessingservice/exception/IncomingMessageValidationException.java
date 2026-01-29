package org.pulitko.aiprocessingservice.exception;

import jakarta.validation.ConstraintViolation;
import org.pulitko.aiprocessingservice.model.IncomingMessage;

import java.util.Set;

public class IncomingMessageValidationException extends RuntimeException {

    private final Set<ConstraintViolation<IncomingMessage>> violations;

    public IncomingMessageValidationException(Set<ConstraintViolation<IncomingMessage>> violations) {
        super("Incoming message validation failed");
        this.violations = violations;
    }

    public Set<ConstraintViolation<IncomingMessage>> getViolations() {
        return violations;
    }
}

package org.pulitko.aiprocessingservice.usecases.validation;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.pulitko.aiprocessingservice.exception.IncomingMessageValidationException;
import org.pulitko.aiprocessingservice.model.IncomingMessage;
import org.springframework.stereotype.Component;

import java.util.Set;

@Slf4j
@Component
@RequiredArgsConstructor
public class IncomingMessageValidator {
    private final Validator validator;

    public void validate(IncomingMessage message) {
        Set<ConstraintViolation<IncomingMessage>> violations = validator.validate(message);

        if (!violations.isEmpty()) {
            log.warn("Validation failed: {}", violations);
            throw new IncomingMessageValidationException(violations);
        }
    }


}

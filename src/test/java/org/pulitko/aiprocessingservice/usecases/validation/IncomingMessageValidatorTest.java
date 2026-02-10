package org.pulitko.aiprocessingservice.usecases.validation;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.pulitko.aiprocessingservice.exception.IncomingMessageValidationException;
import org.pulitko.aiprocessingservice.dto.IncomingMessage;
import org.pulitko.aiprocessingservice.util.TestData;

import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;
import static org.junit.jupiter.api.Assertions.*;

class IncomingMessageValidatorTest {

    private static Validator validator;
    private IncomingMessageValidator messageValidator;

    @BeforeAll
    static void init() {
        validator = Validation.buildDefaultValidatorFactory().getValidator();
    }

    @BeforeEach
    void setUp() {
        messageValidator = new IncomingMessageValidator(validator);
    }

    @Test
    public void shouldValidate() {
        IncomingMessage message = TestData.INCOMING_MESSAGE;
        assertDoesNotThrow(() -> messageValidator.validate(message));
    }

    @Test
    public void shouldThrowWhenRefNull() {
        IncomingMessage message = TestData.INCOMING_MESSAGE_WITH_NULL_REF;
        IncomingMessageValidationException exception =
                assertThrows(IncomingMessageValidationException.class, () -> messageValidator.validate(message));
        assertThat(exception.getViolations()).isNotEmpty();
    }

    @Test
    public void shouldThrowWhenTypeNull() {
        IncomingMessage message = TestData.INCOMING_MESSAGE_WITH_NULL_TYPE;
        IncomingMessageValidationException exception =
                assertThrows(IncomingMessageValidationException.class, () -> messageValidator.validate(message));
        assertThat(exception.getViolations()).isNotEmpty();
    }

    @Test
    public void shouldThrowWhenPayloadNull() {
        IncomingMessage message = TestData.INCOMING_MESSAGE_WITH_NULL_PAYLOAD;
        IncomingMessageValidationException exception =
                assertThrows(IncomingMessageValidationException.class, () -> messageValidator.validate(message));
        assertThat(exception.getViolations()).isNotEmpty();
    }

}
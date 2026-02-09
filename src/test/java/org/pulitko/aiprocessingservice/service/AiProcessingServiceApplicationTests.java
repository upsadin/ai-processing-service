package org.pulitko.aiprocessingservice.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import jakarta.validation.ConstraintViolation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.pulitko.aiprocessingservice.ai.AiClient;
import org.pulitko.aiprocessingservice.exception.AiResultValidationException;
import org.pulitko.aiprocessingservice.exception.IncomingMessageValidationException;
import org.pulitko.aiprocessingservice.exception.PromptNotFoundException;
import org.pulitko.aiprocessingservice.kafka.KafkaOutgoingPublisher;
import org.pulitko.aiprocessingservice.model.ProcessedResult;
import org.pulitko.aiprocessingservice.model.IncomingMessage;
import org.pulitko.aiprocessingservice.model.Prompt;
import org.pulitko.aiprocessingservice.usecases.validation.AiResultValidator;
import org.pulitko.aiprocessingservice.usecases.validation.IncomingMessageValidator;
import org.pulitko.aiprocessingservice.util.TestData;

import java.util.Set;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;
import static org.pulitko.aiprocessingservice.util.TestData.*;

@ExtendWith(MockitoExtension.class)
class AiProcessingServiceApplicationTests {
    @Mock
    private AiClient aiClient;
    @Mock
    private PromptService promptService;
    @Mock
    private AiResultValidator aiResultValidator;
    @Mock
    private IncomingMessageValidator incomingMessageValidator;;
    @Mock
    private KafkaOutgoingPublisher kafkaOutgoingPublisher;

    @InjectMocks
    private AiProcessingService service;

    @Spy
    private final ObjectMapper objectMapper = JsonMapper.builder()
            .findAndAddModules()
            .build();

    @Test
    void shouldProcessMessageAndReturnResult() {
        IncomingMessage msg = INCOMING_MESSAGE;
        Prompt prompt = TestData.PROMPT;

        when(promptService.getByRef(msg.ref())).thenReturn(prompt);
        when(aiClient.analyze(any(), any())).thenReturn(SUCCESS_AI_RESULT);
        when(aiResultValidator.cleanAndValidate(anyString(),anyString(),anyString())).thenReturn(SUCCESS_AI_RESULT);

        ProcessedResult actualResult = service.process(msg);

        assertThat(actualResult).isNotNull();
        assertThat(actualResult.fullName()).isEqualTo("Иван Иванов");
        assertThat(actualResult.matches()).isTrue();

    }

    @Test
    void shouldThrowExceptionWhenValidationFails() {
        IncomingMessage msg = INCOMING_MESSAGE;
        Prompt prompt = TestData.PROMPT;
        when(promptService.getByRef(msg.ref())).thenReturn(prompt);
        when(aiClient.analyze(any(),any())).thenReturn(SUCCESS_AI_RESULT);
        doThrow(new AiResultValidationException(REF_JAVACANDIDATE, "Confidence out of range"))
                .when(aiResultValidator).cleanAndValidate(anyString(), anyString(), anyString());

        assertThrows(AiResultValidationException.class, () -> {
            service.process(msg);
        });
    }

    @Test
    void shouldDontValidateIncomingMessage() {
        IncomingMessage msg = INCOMING_MESSAGE;
        Prompt prompt = TestData.PROMPT;

        ConstraintViolation<IncomingMessage> violation = mock(ConstraintViolation.class);
        Set<ConstraintViolation<IncomingMessage>> violations = Set.of(violation);

        doThrow(new IncomingMessageValidationException(violations)).
                when(incomingMessageValidator).validate(msg);

        assertThrows(IncomingMessageValidationException.class, () -> {
            service.process(msg);
        });
        verify(promptService, never()).getByRef(any());
        verify(aiClient, never()).analyze(any(), any());
        verify(aiResultValidator, never()).cleanAndValidate(any(),any(),any());
        verify(kafkaOutgoingPublisher, never()).send(any(),any());
    }

    @Test
    void shouldDontFindPrompt() {
        IncomingMessage msg = INCOMING_MESSAGE;
        Prompt prompt = TestData.PROMPT;

        doNothing().when(incomingMessageValidator).validate(msg);
        doThrow(new PromptNotFoundException("Prompt not found for ref= " + INCOMING_MESSAGE.ref())).
                when(promptService).getByRef(msg.ref());

        assertThrows(PromptNotFoundException.class, () -> {
            service.process(msg);
        });
        verify(aiClient, never()).analyze(any(),any());
        verify(aiResultValidator, never()).cleanAndValidate(any(),any(),any());
        verify(kafkaOutgoingPublisher, never()).send(any(),any());
    }


}

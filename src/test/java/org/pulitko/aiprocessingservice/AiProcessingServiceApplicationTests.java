package org.pulitko.aiprocessingservice;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import jakarta.validation.ConstraintViolation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.pulitko.aiprocessingservice.ai.AiClient;
import org.pulitko.aiprocessingservice.exception.AiResultValidationException;
import org.pulitko.aiprocessingservice.exception.IncomingMessageValidationException;
import org.pulitko.aiprocessingservice.exception.PromptNotFoundException;
import org.pulitko.aiprocessingservice.kafka.KafkaOutgoingPublisher;
import org.pulitko.aiprocessingservice.model.AiResult;
import org.pulitko.aiprocessingservice.model.IncomingMessage;
import org.pulitko.aiprocessingservice.prompt.Prompt;
import org.pulitko.aiprocessingservice.service.AiProcessingService;
import org.pulitko.aiprocessingservice.service.PromptService;
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
    void shouldProcessMessageAndPublish() {
        IncomingMessage msg = INCOMING_MESSAGE;
        Prompt prompt = TestData.PROMPT;

        when(promptService.getByRef(msg.ref())).thenReturn(prompt);
        when(aiClient.analyze(any())).thenReturn(SUCCESS_AI_RESULT);

        service.process(msg, SOURCE_ID_JAVACANDIDATE);

        verify(incomingMessageValidator).validate(msg);
        verify(aiResultValidator).validate(eq(SUCCESS_AI_RESULT), anyString(), anyString());


        ArgumentCaptor<AiResult> captor = ArgumentCaptor.forClass(AiResult.class);
        verify(kafkaOutgoingPublisher).send(captor.capture(), eq(SOURCE_ID_JAVACANDIDATE));

        AiResult resultDto = captor.getValue();
        assertThat(resultDto.matches()).isTrue();
        assertThat(resultDto.fullName()).isEqualTo("Иван Иванов");
    }

    @Test
    void shouldProcessMessageAndDontPublish() {
        IncomingMessage msg = INCOMING_MESSAGE;
        Prompt prompt = TestData.PROMPT;

        doNothing().when(incomingMessageValidator).validate(msg);
        when(promptService.getByRef(msg.ref())).thenReturn(prompt);
        when(aiClient.analyze(prompt.template())).thenReturn(SUCCESS_AI_RESULT);
        doThrow(new AiResultValidationException(prompt.schemaJson(), "Confidence out of range")).
                when(aiResultValidator).validate(SUCCESS_AI_RESULT, prompt.schemaJson(), prompt.ref());

        assertThrows(AiResultValidationException.class, () -> {
            service.process(msg, SOURCE_ID_JAVACANDIDATE);
        });
        verify(kafkaOutgoingPublisher, never()).send(any(),any());
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
            service.process(msg, SOURCE_ID_JAVACANDIDATE);
        });
        verify(promptService, never()).getByRef(any());
        verify(aiClient, never()).analyze(any());
        verify(aiResultValidator, never()).validate(any(),any(),any());
        verify(kafkaOutgoingPublisher, never()).send(any(),any());
    }

    @Test
    void shouldDontFindPrompt() {
        IncomingMessage msg = INCOMING_MESSAGE;
        Prompt prompt = TestData.PROMPT;

        doNothing().when(incomingMessageValidator).validate(msg);
        doThrow(new PromptNotFoundException("Prompt not found for ref=" + INCOMING_MESSAGE.ref())).
                when(promptService).getByRef(msg.ref());

        assertThrows(PromptNotFoundException.class, () -> {
            service.process(msg, SOURCE_ID_JAVACANDIDATE);
        });
        verify(aiClient, never()).analyze(any());
        verify(aiResultValidator, never()).validate(any(),any(),any());
        verify(kafkaOutgoingPublisher, never()).send(any(),any());
    }


}

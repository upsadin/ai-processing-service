package org.pulitko.aiprocessingservice.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.ValidationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.pulitko.aiprocessingservice.ai.AiClient;
import org.pulitko.aiprocessingservice.exception.AiResultValidationException;
import org.pulitko.aiprocessingservice.dto.ProcessedResult;
import org.pulitko.aiprocessingservice.dto.IncomingMessage;
import org.pulitko.aiprocessingservice.model.Prompt;
import org.pulitko.aiprocessingservice.usecases.validation.AiResultValidator;
import org.pulitko.aiprocessingservice.usecases.validation.IncomingMessageValidator;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class AiProcessingService {
    private final AiClient aiClient;
    private final IncomingMessageValidator incomingMessageValidator;
    private final AiResultValidator aiResultValidator;
    private final PromptService promptService;
    private final ObjectMapper objectMapper;

    public ProcessedResult process(IncomingMessage message) {
        if (message == null) {
            log.debug("Incoming message is null");
        }
        String ref = message.ref();
        incomingMessageValidator.validate(message);
        Prompt prompt = promptService.getByRef(ref);
        ProcessedResult processedResult = null;
        try {
            String aiResultAsString = aiClient.analyze(prompt.template(), message.payload());
            aiResultAsString = aiResultValidator.cleanAndValidate(aiResultAsString, prompt.schemaJson(), ref);
            processedResult = objectMapper.readValue(aiResultAsString, ProcessedResult.class);
        } catch (ValidationException | JsonProcessingException e) {
            throw new AiResultValidationException(ref, "Malformed JSON from AI", e);
        }
        return processedResult;
    }
}

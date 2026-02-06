package org.pulitko.aiprocessingservice.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.pulitko.aiprocessingservice.ai.AiClient;
import org.pulitko.aiprocessingservice.exception.AiResultValidationException;
import org.pulitko.aiprocessingservice.model.ProcessedResult;
import org.pulitko.aiprocessingservice.model.IncomingMessage;
import org.pulitko.aiprocessingservice.model.Prompt;
import org.pulitko.aiprocessingservice.usecases.validation.AiResultValidator;
import org.pulitko.aiprocessingservice.usecases.validation.IncomingMessageValidator;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class AiProcessingService {

    private final AiClient aiClient;
    private final IncomingMessageValidator incomingMessageValidator;
    private final AiResultValidator aiResultValidator;
    private final PromptService promptService;
    private final PromptGenerator promptGenerator;
    private final ObjectMapper objectMapper;

//    @Async("aiExecutor")
    public ProcessedResult process(IncomingMessage message) {
        if (message == null) {
            log.debug("Incoming message is null");
        }
        String ref = message.ref();
        incomingMessageValidator.validate(message);
        Prompt prompt = promptService.getByRef(ref);
        String promptForAnalyze = promptGenerator.generate(prompt.template(), message.payload());
        String aiResultAsString = aiClient.analyze(promptForAnalyze);
        aiResultValidator.validate(aiResultAsString, prompt.schemaJson(), ref);
        ProcessedResult processedResult = null;
        try {
            processedResult = objectMapper.readValue(aiResultAsString, ProcessedResult.class);
        } catch (JsonProcessingException e) {
            throw new AiResultValidationException(ref, "Malformed JSON from AI", e);
        }
        return processedResult;
    }
}

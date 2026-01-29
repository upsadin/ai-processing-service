package org.pulitko.aiprocessingservice.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.pulitko.aiprocessingservice.ai.AiClient;
import org.pulitko.aiprocessingservice.kafka.KafkaOutgoingPublisher;
import org.pulitko.aiprocessingservice.model.AiResult;
import org.pulitko.aiprocessingservice.model.IncomingMessage;
import org.pulitko.aiprocessingservice.prompt.Prompt;
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
    private final KafkaOutgoingPublisher kafkaOutgoingPublisher;
    private final ObjectMapper objectMapper;

    @Async("aiExecutor")
    public void process(IncomingMessage message, String sourceId) {
        if (message == null) {
            log.debug("Incoming message is null");
        }
        incomingMessageValidator.validate(message);
        Prompt prompt = promptService.getByRef(message.ref());
        String aiResultAsString = aiClient.analyze(prompt.template());
        aiResultValidator.validate(aiResultAsString, prompt.schemaJson(), prompt.ref());
        AiResult aiResult = null;
        try {
            aiResult = objectMapper.readValue(aiResultAsString, AiResult.class);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }

        kafkaOutgoingPublisher.send(aiResult, sourceId);
    }
}

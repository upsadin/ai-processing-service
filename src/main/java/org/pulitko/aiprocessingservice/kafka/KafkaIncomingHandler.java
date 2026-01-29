package org.pulitko.aiprocessingservice.kafka;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.pulitko.aiprocessingservice.exception.IncomingMessageValidationException;
import org.pulitko.aiprocessingservice.model.IncomingMessage;
import org.pulitko.aiprocessingservice.service.AiProcessingService;
import org.springframework.kafka.annotation.KafkaHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

import java.util.concurrent.RejectedExecutionException;

@KafkaListener(topics = "${spring.kafka.topics.incoming}",
        groupId = "${spring.kafka.groups-id.consumer}")
@Slf4j
@Component
@RequiredArgsConstructor
public class KafkaIncomingHandler {
    private final AiProcessingService aiProcessingService;
    private final DlqPublisher dlqPublisher;


    @KafkaHandler
    public void handle(@Payload IncomingMessage message, @Header("x-sourceId") String sourceId) {
        log.info("Received event id:{}, ref {}", sourceId, message.ref());
        try {
            aiProcessingService.process(message, sourceId);
        } catch (
                IncomingMessageValidationException e) {
            log.warn("Validation failed, sourceId={}", sourceId, e);
            throw e;
        } catch (RejectedExecutionException e) {
            dlqPublisher.publish(message, sourceId, e.getMessage());
        }
    }
}

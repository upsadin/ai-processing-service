package org.pulitko.aiprocessingservice.kafka;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.pulitko.aiprocessingservice.exception.BaseBusinessException;
import org.pulitko.aiprocessingservice.dto.ProcessedResult;
import org.pulitko.aiprocessingservice.dto.IncomingMessage;
import org.pulitko.aiprocessingservice.service.AiProcessingService;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.RejectedExecutionException;


@Slf4j
@Component
@RequiredArgsConstructor
public class KafkaIncomingHandler {
    private final AiProcessingService aiProcessingService;
    private final KafkaOutgoingPublisher kafkaOutgoingPublisher;
    private final DlqPublisher dlqPublisher;

    @KafkaListener(
            topics = "${spring.kafka.topics.incoming}",
            groupId = "${spring.kafka.groups-id.consumer}",
            containerFactory = "kafkaListenerContainerFactory")
    public void handle(@Payload IncomingMessage message,
                       @Header("x-sourceId") String sourceId) {

        if (message == null || message.payload().isBlank()) {
            log.warn("Payload is null or empty, check for errors in headers.");
            return;
        }
        log.info("Received event id:{}, ref: {}", sourceId, message.ref());

        try {
            ProcessedResult processedResult = aiProcessingService.process(message);
            kafkaOutgoingPublisher.send(processedResult, sourceId);
        } catch (BaseBusinessException e) {
            if (e.getSourceId() == null) {
                e.withSourceId(sourceId);
            }
            log.warn("Business logic error, sourceId={}", sourceId, e);
            dlqPublisher.publish(message, sourceId, e.getMessage());
        } catch (RejectedExecutionException e) {
            log.error("The system is overloaded and cannot process it now: {}", sourceId);
            throw e;
        } catch (Exception e) {
            log.error("CRITICAL: Unexpected error during AI processing for sourceId={}", sourceId, e);
            throw e;
        }
    }
}

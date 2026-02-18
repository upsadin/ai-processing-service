package org.pulitko.aiprocessingservice.kafka;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.pulitko.aiprocessingservice.exception.AiResultValidationException;
import org.pulitko.aiprocessingservice.dto.IncomingMessage;
import org.pulitko.aiprocessingservice.util.AbstractDbIT;
import org.springframework.beans.factory.annotation.Value;
import java.time.Duration;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.pulitko.aiprocessingservice.util.TestData.*;


class DlqTest extends AbstractDbIT {


    @Value("${spring.kafka.topics.processing-dlq}")
    private String businessDlq;

    @Value("${spring.kafka.topics.incoming}")
    private String incoming;

    @BeforeEach
    void resetSpy() {
        Mockito.reset(aiProcessingService);
        deserializationErrorRepository.deleteAll();
    }

    @Test
    void shouldSaveDeserializationErrorToDb() {

        kafkaTemplate.executeInTransaction(t -> t.send(incoming, "invalid-json"));

        Awaitility.await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            var errors = deserializationErrorRepository.findAll();
            boolean foundMyError = errors.stream()
                    .anyMatch(e -> e.payload().contains("invalid-json"));
            assertThat(foundMyError).isTrue();
        });
    }
    @Test
    void shouldGoToBusinessDlq() {
        IncomingMessage msg = INCOMING_MESSAGE_WITH_WRONG_REF;

        ProducerRecord<String, Object> record = new ProducerRecord<>(incoming, msg);
        record.headers().add("x-sourceId", SOURCE_ID_JAVACANDIDATE.getBytes());
        kafkaTemplate.executeInTransaction(t -> t.send(record));
        await()
                .atMost(Duration.ofSeconds(10))
                .untilAsserted(() -> {
                    ArgumentCaptor<IncomingMessage> messageCaptor = ArgumentCaptor.forClass(IncomingMessage.class);
                    ArgumentCaptor<String> sourceIdCaptor = ArgumentCaptor.forClass(String.class);
                    ArgumentCaptor<String> reasonCaptor = ArgumentCaptor.forClass(String.class);
                    verify(dlqListener, atLeastOnce()).handleBusinessError(messageCaptor.capture(),
                            sourceIdCaptor.capture(), reasonCaptor.capture());
                    IncomingMessage capturedMessage = messageCaptor.getValue();
                    String capturedSourceId = sourceIdCaptor.getValue();

                    assertEquals(INVALID_REF_JAVACANDIDATE, capturedMessage.ref());
                    assertEquals(SOURCE_ID_JAVACANDIDATE, capturedSourceId);
                });
    }

    @Test
    void shouldPropagateToSystemErrorHandlerOnUnexpectedException() {
        IncomingMessage msg = INCOMING_MESSAGE;
        doThrow(new RuntimeException("System error"))
                .when(aiProcessingService).process(any());
        ProducerRecord<String, Object> producerRecord = new ProducerRecord<>(incoming, msg);
        producerRecord.headers().add("x-sourceId", SOURCE_ID_JAVACANDIDATE.getBytes());

        kafkaTemplate.executeInTransaction(t -> t.send(producerRecord));

        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            verify(aiProcessingService, atLeastOnce()).process(any());
            verify(dlqPublisher, never()).publish(any(), any(), any());
        });
    }

    @Test
    void shouldRetryOnRejectedExecutionException() {
        IncomingMessage msg = INCOMING_MESSAGE;

        doThrow(new RejectedExecutionException("Overloaded"))
                .when(aiProcessingService).process(any());

        ProducerRecord<String, Object> producerRecord = new ProducerRecord<>(incoming, msg);
        producerRecord.headers().add("x-sourceId", SOURCE_ID_JAVACANDIDATE.getBytes());

        kafkaTemplate.executeInTransaction(t -> t.send(producerRecord));

        await().atMost(15, TimeUnit.SECONDS).untilAsserted(() -> {
            verify(aiProcessingService, times(4)).process(any());
        });
    }

    @Test
    void shouldHandleBusinessExceptionViaDlqPublisher() {
        IncomingMessage msg = INCOMING_MESSAGE;

        doThrow(new AiResultValidationException(REF_JAVACANDIDATE, "Not valid"))
                .when(aiProcessingService).process(any());

        ProducerRecord<String, Object> producerRecord = new ProducerRecord<>(incoming, msg);
        producerRecord.headers().add("x-sourceId", SOURCE_ID_JAVACANDIDATE.getBytes());

        kafkaTemplate.executeInTransaction(t -> t.send(producerRecord));

        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            verify(dlqPublisher, times(1)).publish(any(), any(), contains("Validation failed"));
        });
    }
}
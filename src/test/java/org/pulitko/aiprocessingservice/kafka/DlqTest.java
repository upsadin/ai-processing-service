package org.pulitko.aiprocessingservice.kafka;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import org.pulitko.aiprocessingservice.exception.AiResultValidationException;
import org.pulitko.aiprocessingservice.exception.PromptNotFoundException;
import org.pulitko.aiprocessingservice.model.IncomingMessage;
import org.pulitko.aiprocessingservice.repository.DeserializationErrorRepository;
import org.pulitko.aiprocessingservice.repository.PromptRepository;
import org.pulitko.aiprocessingservice.service.AiProcessingService;
import org.pulitko.aiprocessingservice.service.DeserializationErrorService;
import org.pulitko.aiprocessingservice.service.PromptServiceDb;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.ActiveProfiles;

import java.time.Duration;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.pulitko.aiprocessingservice.util.TestData.*;

@EnableKafka
@SpringBootTest
@EnableAutoConfiguration
@EmbeddedKafka(partitions = 1, topics = {
        "${spring.kafka.topics.dlq}",
        "${spring.kafka.topics.processing-dlq}",
        "${spring.kafka.topics.incoming}"})
@ActiveProfiles({"test", "no-db"})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DlqTest {
    @Autowired private KafkaTemplate<String, Object> kafkaTemplate;
    @Autowired private EmbeddedKafkaBroker embeddedKafka;
    @Autowired private KafkaIncomingHandler kafkaIncomingHandler;
    @SpyBean private DeserializationErrorService deserializationErrorService;
    @SpyBean private DlqListener dlqListener;
    @SpyBean private DlqPublisher dlqPublisher;

    @MockBean private DeserializationErrorRepository deserializationErrorRepository;
    @MockBean private PromptRepository promptRepository;
    @MockBean private PromptServiceDb promptServiceDb;
    @MockBean private AiProcessingService aiProcessingService;

    @Value("${spring.kafka.topics.processing-dlq}")
    private String businessDlq;

    @Value("${spring.kafka.topics.incoming}")
    private String incoming;
    @BeforeEach
    void setUp() {
        reset(aiProcessingService, dlqPublisher, deserializationErrorRepository);
    }
    @Test
    void shouldSaveDeserializationErrorToDb() {
        kafkaTemplate.send(incoming, "this_is_not_json");
        kafkaTemplate.flush();

        await()
                .atMost(Duration.ofSeconds(10))
                .untilAsserted(() -> {
                    verify(deserializationErrorRepository, atLeastOnce()).save(any());
                });
    }
    @Test
    void shouldGoToBusinessDlq() {
        IncomingMessage msg = INCOMING_MESSAGE;
        doThrow(new PromptNotFoundException("Prompt Not Found"))
                .when(aiProcessingService).process(any());

        ProducerRecord<String, Object> record = new ProducerRecord<>(incoming, msg);
        record.headers().add("x-sourceId", SOURCE_ID_JAVACANDIDATE.getBytes());
        kafkaTemplate.send(record);
        kafkaTemplate.flush();
        await()
                .atMost(Duration.ofSeconds(10))
                .untilAsserted(() -> {
                    ArgumentCaptor<IncomingMessage> messageCaptor = ArgumentCaptor.forClass(IncomingMessage.class);
                    ArgumentCaptor<String> sourceIdCaptor = ArgumentCaptor.forClass(String.class);
                    verify(dlqListener, atLeastOnce()).handleBusinessError(messageCaptor.capture(),
                            sourceIdCaptor.capture());IncomingMessage capturedMessage = messageCaptor.getValue();
                    String capturedSourceId = sourceIdCaptor.getValue();

                    assertEquals(REF_JAVACANDIDATE, capturedMessage.ref());
                    assertEquals(SOURCE_ID_JAVACANDIDATE, capturedSourceId);
                });
    }

    @Test
    void shouldPropagateToSystemErrorHandlerOnUnexpectedException() {
        IncomingMessage msg = INCOMING_MESSAGE;
        doThrow(new RuntimeException("System error"))
                .when(aiProcessingService).process(any());

        kafkaTemplate.send(incoming, msg);

        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            verify(aiProcessingService, atLeastOnce()).process(any());
            verify(dlqPublisher, never()).publish(any(), any(), any());
//            verifyNoInteractions(repository);
        });
    }

    @Test
    void shouldRetryOnRejectedExecutionException() {
        IncomingMessage msg = INCOMING_MESSAGE;

        doThrow(new RejectedExecutionException("Overloaded"))
                .when(aiProcessingService).process(any());

        kafkaTemplate.send(incoming, msg);

        await().atMost(15, TimeUnit.SECONDS).untilAsserted(() -> {
            verify(aiProcessingService, times(4)).process(any());
        });
    }

    @Test
    void shouldHandleBusinessExceptionViaDlqPublisher() {
        IncomingMessage msg = INCOMING_MESSAGE;

        doThrow(new AiResultValidationException(REF_JAVACANDIDATE, "Not valid"))
                .when(aiProcessingService).process(any());

        kafkaTemplate.send(incoming, msg);

        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            verify(dlqPublisher, times(1)).publish(any(), any(), contains("Validation failed"));
        });
    }

}
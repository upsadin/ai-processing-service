package org.pulitko.aiprocessingservice.kafka;

import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.pulitko.aiprocessingservice.dto.IncomingMessage;
import org.pulitko.aiprocessingservice.model.Prompt;
import org.pulitko.aiprocessingservice.usecases.validation.AiResultValidator;
import org.pulitko.aiprocessingservice.usecases.validation.IncomingMessageValidator;
import org.pulitko.aiprocessingservice.util.AbstractDbIT;
import org.pulitko.aiprocessingservice.util.TestData;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.pulitko.aiprocessingservice.util.TestData.SOURCE_ID_JAVACANDIDATE;

@Slf4j
class KafkaFlowTest extends AbstractDbIT {
    @MockBean
    private IncomingMessageValidator incomingMessageValidator;

    @MockBean
    private AiResultValidator aiResultValidator;

    @Value("${spring.kafka.topics.outgoing}")
    private String outTopic;

    @Value("${spring.kafka.topics.incoming}")
    private String inTopic;

    private Consumer<String, String> testConsumer;

    @BeforeEach
    void setUp() {
        Mockito.reset(promptService, aiClient, incomingMessageValidator, aiResultValidator);
    }

    @Test
    void testKafkaFlow() {
        IncomingMessage msg = TestData.INCOMING_MESSAGE;
        String sourceId = SOURCE_ID_JAVACANDIDATE;

        ProducerRecord<String, Object> record = new ProducerRecord<>(inTopic, msg);
        record.headers().add("x-sourceId", sourceId.getBytes(StandardCharsets.UTF_8));

        Prompt prompt = TestData.PROMPT;
        String aiResult = TestData.SUCCESS_AI_RESULT;

        doNothing().when(incomingMessageValidator).validate(any());
        when(promptService.getByRef(msg.ref())).thenReturn(prompt);
        when(aiClient.analyze(any(), any(), anyString(),anyString())).thenReturn(aiResult);
        when(aiResultValidator.validate(any(), any(), any())).thenReturn(aiResult);

        kafkaTemplate.executeInTransaction(t -> t.send(record));


        await().atMost(15, TimeUnit.SECONDS).untilAsserted(() -> {
            verify(aiProcessingService, atLeastOnce()).process(argThat(m -> m.ref().equals(msg.ref())));
            verify(kafkaOutgoingPublisher, atLeastOnce()).send(any());
        });
    }


}
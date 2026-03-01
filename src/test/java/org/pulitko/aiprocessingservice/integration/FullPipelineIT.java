package org.pulitko.aiprocessingservice.integration;


import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.*;
import org.pulitko.aiprocessingservice.dto.IncomingMessage;
import org.pulitko.aiprocessingservice.util.AbstractDbIT;
import org.pulitko.aiprocessingservice.util.TestData;
import org.skyscreamer.jsonassert.JSONAssert;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.test.utils.KafkaTestUtils;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.pulitko.aiprocessingservice.util.TestData.INCOMING_MESSAGE;
import static org.pulitko.aiprocessingservice.util.TestData.SOURCE_ID_JAVACANDIDATE;


@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class FullPipelineIT extends AbstractDbIT {

    @Value("${spring.kafka.topics.incoming}")
    private String incomingTopic;

    @Value("${spring.kafka.topics.outgoing}")
    private String outgoingTopic;

    private Consumer<String, String> testConsumer;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUpConsumer() {
        Map<String, Object> props = KafkaTestUtils.consumerProps(
                kafka.getBootstrapServers(), "test-group-pipeline", "true");
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

        testConsumer = new DefaultKafkaConsumerFactory<>(
                props,
                new StringDeserializer(),
                new StringDeserializer()).createConsumer();

        testConsumer.subscribe(Collections.singleton(outgoingTopic));
    }

    @AfterEach
    void closeConsumer() {
        if (testConsumer != null) {
            testConsumer.unsubscribe();
            testConsumer.close(Duration.ofSeconds(5));
        }
    }

    @Test
    void shouldLoadPromptFromDbAndPublishResult() throws Exception {
        IncomingMessage incomingMessage = INCOMING_MESSAGE;
        String aiResponse = TestData.SUCCESS_AI_RESULT;
        String sourceId = SOURCE_ID_JAVACANDIDATE;

        when(promptService.getActivePrompt(anyString())).thenReturn(TestData.PROMPT);
        when(promptMapper.toAiDto(any())).thenReturn(TestData.PROMPT);
        when(aiClient.analyze(anyString(), anyString(), anyString(), anyString())).thenReturn(aiResponse);

        ProducerRecord<String, Object> record = new ProducerRecord<>(incomingTopic, incomingMessage);
        record.headers().add("x-sourceId", sourceId.getBytes(StandardCharsets.UTF_8));

        kafkaTemplate.executeInTransaction(t -> {
            t.send(record);
            return null;
        });

        AtomicReference<ConsumerRecord<String, String>> receivedRef = new AtomicReference<>();

        Awaitility.await()
                .atMost(Duration.ofSeconds(15))
                .pollInterval(Duration.ofMillis(500))
                .until(() -> {
                    ConsumerRecords<String, String> records = testConsumer.poll(Duration.ofMillis(200));
                    if (!records.isEmpty()) {
                        receivedRef.set(records.iterator().next());
                        return true;
                    }
                    return false;
                });

        ConsumerRecord<String, String> received = receivedRef.get();
        assertThat(received).isNotNull();
        assertThat(received.key()).isEqualTo(sourceId);
        JsonNode rootNode = objectMapper.readTree(received.value());
        assertThat(rootNode.get("sourceId").asText()).isEqualTo(sourceId);
        assertThat(rootNode.get("ref").asText()).isEqualTo(incomingMessage.ref());
        JsonNode actualAiResult = rootNode.get("aiResult");
        JSONAssert.assertEquals(aiResponse, actualAiResult.toString(), true);
    }
}
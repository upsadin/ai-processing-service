package org.pulitko.aiprocessingservice.integration;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.awaitility.Awaitility;
import org.json.JSONException;
import org.junit.jupiter.api.*;
import org.pulitko.aiprocessingservice.ai.AiClient;
import org.pulitko.aiprocessingservice.dto.IncomingMessage;
import org.pulitko.aiprocessingservice.util.AbstractDbIT;
import org.pulitko.aiprocessingservice.util.TestData;
import org.skyscreamer.jsonassert.JSONAssert;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.context.ActiveProfiles;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.pulitko.aiprocessingservice.util.TestData.INCOMING_MESSAGE;
import static org.pulitko.aiprocessingservice.util.TestData.SOURCE_ID_JAVACANDIDATE;

@SpringBootTest
@ActiveProfiles("test")
@EmbeddedKafka(
        partitions = 1,
        topics = {
                "${spring.kafka.topics.outgoing}",
                "${spring.kafka.topics.incoming}"
        }
)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class FullPipelineIT extends AbstractDbIT {

    @Value("${spring.kafka.topics.incoming}")
    private String incomingTopic;

    @Value("${spring.kafka.topics.outgoing}")
    private String outgoingTopic;

    @Autowired
    private EmbeddedKafkaBroker embeddedKafka;

    @Autowired
    private KafkaTemplate<String, Object> kafkaTemplate;

    @MockBean
    private AiClient aiClient;

    private Consumer<String, String> testConsumer;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        Map<String, Object> props = KafkaTestUtils.consumerProps(
                "test-group-" + java.util.UUID.randomUUID(), "true", embeddedKafka);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        testConsumer = new DefaultKafkaConsumerFactory<>(
                props,
                new StringDeserializer(),
                new StringDeserializer()).createConsumer();
        embeddedKafka.consumeFromAnEmbeddedTopic(testConsumer, outgoingTopic);
    }

    @AfterEach
    void closeConsumer() {
        if (testConsumer != null) {
            testConsumer.close();
        }
    }

    @Test
    void shouldLoadPromptFromDbAndPublishResult() throws JSONException, JsonProcessingException, InterruptedException {
        String sourceId = SOURCE_ID_JAVACANDIDATE;
        IncomingMessage incomingMessage = INCOMING_MESSAGE;
        String aiResponse = TestData.SUCCESS_AI_RESULT;
        testConsumer.subscribe(Collections.singleton(outgoingTopic));
        when(aiClient.analyze(anyString(),anyString(),anyString(),anyString())).thenReturn(aiResponse);

        ProducerRecord<String, Object> record = new ProducerRecord<>(incomingTopic, incomingMessage);
        record.headers().add("x-sourceId",sourceId.getBytes(StandardCharsets.UTF_8));


        kafkaTemplate.send(record);
        kafkaTemplate.flush();
        AtomicReference<ConsumerRecord<String, String>> receivedRef = new AtomicReference<>();

        Awaitility.await()
                .atMost(Duration.ofSeconds(15))
                .until(() -> {
                    ConsumerRecords<String, String> records = KafkaTestUtils.getRecords(testConsumer, Duration.ofSeconds(1));
                    if (!records.isEmpty()) {
                        receivedRef.set(records.iterator().next());
                        return true;
                    }
                    return false;
                });

        ConsumerRecord<String, String> received = receivedRef.get();
        assertThat(received).isNotNull();

        JSONAssert.assertEquals(aiResponse, received.value(), true);
        assertThat(received.key()).isEqualTo(sourceId);

        Header sourceHeader = received.headers().lastHeader("x-sourceId");
        assertThat(sourceHeader).isNotNull();
        assertThat(new String(sourceHeader.value(), StandardCharsets.UTF_8)).isEqualTo(sourceId);
    }
}
package org.pulitko.aiprocessingservice.util;

import org.pulitko.aiprocessingservice.ai.client.OpenAiClient;
import org.pulitko.aiprocessingservice.kafka.DlqListener;
import org.pulitko.aiprocessingservice.kafka.DlqPublisher;
import org.pulitko.aiprocessingservice.kafka.KafkaIncomingHandler;
import org.pulitko.aiprocessingservice.kafka.KafkaOutgoingPublisher;
import org.pulitko.aiprocessingservice.repository.DeserializationErrorRepository;
import org.pulitko.aiprocessingservice.repository.PromptRepository;
import org.pulitko.aiprocessingservice.service.AiProcessingService;
import org.pulitko.aiprocessingservice.service.PromptServiceDb;
import org.pulitko.aiprocessingservice.usecases.mapper.PromptMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.cache.CacheManager;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.UUID;

@Testcontainers
@SpringBootTest
@ActiveProfiles("test")
@Import(AbstractDbIT.TestCacheConfig.class)
public abstract class AbstractDbIT {
    @Autowired protected KafkaTemplate<String, Object> kafkaTemplate;
    @Autowired protected KafkaIncomingHandler kafkaIncomingHandler;
    @Autowired protected DeserializationErrorRepository deserializationErrorRepository;
    @SpyBean protected KafkaOutgoingPublisher kafkaOutgoingPublisher;
    @MockBean protected PromptServiceDb promptService;
    @SpyBean protected PromptRepository promptRepository;
    @SpyBean protected AiProcessingService aiProcessingService;
    @SpyBean protected DlqPublisher dlqPublisher;
    @SpyBean protected DlqListener dlqListener;
    @MockBean protected OpenAiClient aiClient;
    @MockBean protected CacheManager cacheManager;
    @MockBean protected PromptMapper promptMapper;

    @Container
    static final PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:16");

    @Container
    protected static final KafkaContainer kafka =
            new KafkaContainer(
                    DockerImageName.parse("apache/kafka:3.7.0"))
                            .withEnv("KAFKA_TRANSACTION_STATE_LOG_REPLICATION_FACTOR", "1")
                            .withEnv("KAFKA_TRANSACTION_STATE_LOG_MIN_ISR", "1")
                            .withEnv("KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR", "1");


    static {
        postgres.start();
        kafka.start();
    }

    @DynamicPropertySource
    static void dbProps(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);

        registry.add("spring.kafka.bootstrap-servers",
                kafka::getBootstrapServers);

        registry.add("spring.kafka.producer.transaction-id-prefix",
                () -> "tx-" + UUID.randomUUID() + "-");

        registry.add("spring.kafka.producer.properties.transaction.timeout.ms",
                () -> "30000");
    }

    @TestConfiguration
    public static class TestCacheConfig {
        @Bean
        @Primary
        public CacheManager cacheManager() {
            return new ConcurrentMapCacheManager("prompts");
        }
    }
}
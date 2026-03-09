package org.pulitko.aiprocessingservice.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.pulitko.aiprocessingservice.dto.IncomingMessage;
import org.pulitko.aiprocessingservice.exception.BaseBusinessException;
import org.pulitko.aiprocessingservice.service.DeserializationErrorService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.boot.ssl.SslBundles;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.*;
import org.springframework.kafka.listener.*;
import org.springframework.kafka.support.serializer.*;
import org.springframework.kafka.transaction.KafkaTransactionManager;
import org.springframework.util.backoff.FixedBackOff;

import java.io.ByteArrayInputStream;
import java.io.ObjectInputStream;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.Map;

@Slf4j
@Configuration
@RequiredArgsConstructor
@EnableKafka
public class KafkaConfig {

    @Value("${spring.kafka.topics.outgoing}")
    private String publisherTopic;

    @Value("${spring.kafka.topics.incoming}")
    private String incomingTopic;

    @Value("${spring.kafka.topics.processing-dlq}")
    private String businessDlqTopic;

    private final KafkaProperties kafkaProperties;
    private final SslBundles sslBundles;

    private final DeserializationErrorService errorService;

    private String buildJaasConfig() {
        String username = System.getenv("KAFKACLUSTER_USERNAME");
        String password = System.getenv("KAFKACLUSTER_PASSWORD");
        if (username != null && password != null) {
            String jaasTemplate = "org.apache.kafka.common.security.scram.ScramLoginModule required username=\"%s\" password=\"%s\";";
            return String.format(jaasTemplate, username.trim(), password.trim());
        }
        return null;
    }

    @Bean
    public ConsumerFactory<String, IncomingMessage> consumerFactory() {
        Map<String, Object> config = kafkaProperties.buildConsumerProperties(null);
        String jaasConfig = buildJaasConfig();
        if (jaasConfig != null) {
            config.put("sasl.jaas.config", jaasConfig);
        }
        log.info("ConsumerFactory config: bootstrap.servers={}, security.protocol={}, sasl.mechanism={}, sasl.jaas.config={}",
                config.get("bootstrap.servers"),
                config.get("security.protocol"),
                config.get("sasl.mechanism"),
                jaasConfig != null ? "[SET]" : "[NOT SET]");
        JsonDeserializer<IncomingMessage> jsonDeserializer = new JsonDeserializer<>(IncomingMessage.class);
        jsonDeserializer.addTrustedPackages("org.pulitko.aiprocessingservice.dto", "org.pulitko.aiprocessingservice.model");
        jsonDeserializer.setRemoveTypeHeaders(false);
        ErrorHandlingDeserializer<IncomingMessage> errorHandlingDeserializer =
                new ErrorHandlingDeserializer<>(jsonDeserializer);

        return new DefaultKafkaConsumerFactory<>(
                config,
                new StringDeserializer(),
                errorHandlingDeserializer
        );
    }

    private ConsumerRecordRecoverer createRecoverer(DeserializationErrorService errorService) {
        return (record, ex) -> {
            String rawPayload = null;

            if (ex instanceof org.springframework.kafka.listener.ListenerExecutionFailedException) {
                Throwable cause = ex.getCause();
                if (cause instanceof org.springframework.kafka.support.serializer.DeserializationException dex) {
                    if (dex.getData() != null) {
                        rawPayload = new String(dex.getData(), StandardCharsets.UTF_8);
                    }
                }
            }

            if (rawPayload == null) {
                for (org.apache.kafka.common.header.Header header : record.headers()) {
                    if (header.key().contains("DeserializationException")) {
                        byte[] data = header.value();
                        if (data == null) continue;

                        if (data.length > 2 && data[0] == (byte) 0xAC && data[1] == (byte) 0xED) {
                            try (ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(data))) {
                                Object obj = ois.readObject();
                                if (obj instanceof org.springframework.kafka.support.serializer.DeserializationException dex) {
                                    rawPayload = new String(dex.getData(), StandardCharsets.UTF_8);
                                } else if (obj instanceof byte[] bytes) {
                                    rawPayload = new String(bytes, StandardCharsets.UTF_8);
                                }
                            } catch (Exception e) {
                                rawPayload = "Hex Dump: " + HexFormat.of().formatHex(data);
                            }
                        } else {
                            rawPayload = new String(data, StandardCharsets.UTF_8);
                        }
                    }
                }
            }

            if (rawPayload == null && record.value() != null) {
                rawPayload = record.value().toString();
            }

            String finalData = (rawPayload != null) ? rawPayload : "Could not extract data";

            log.error("Recovery: Saving to DB: {}", finalData);
            errorService.saveError(finalData, ex.getMessage(), record.topic());
        };
    }

    private void addExceptionClassifications(DefaultAfterRollbackProcessor<?, ?> processor) {
        processor.addNotRetryableExceptions(
                org.springframework.kafka.support.serializer.DeserializationException.class,
                org.springframework.messaging.converter.MessageConversionException.class,
                BaseBusinessException.class,
                org.springframework.messaging.handler.invocation.MethodArgumentResolutionException.class
        );
        processor.addRetryableExceptions(
                org.springframework.dao.DataAccessException.class,
                java.net.ConnectException.class,
                org.springframework.web.client.ResourceAccessException.class,
                java.util.concurrent.RejectedExecutionException.class,
                org.springframework.kafka.KafkaException.class,
                java.util.concurrent.TimeoutException.class
        );
    }

    private void addExceptionClassifications(DefaultErrorHandler errorHandler) {
        errorHandler.addNotRetryableExceptions(
                org.springframework.kafka.support.serializer.DeserializationException.class,
                org.springframework.messaging.converter.MessageConversionException.class,
                BaseBusinessException.class,
                org.springframework.messaging.handler.invocation.MethodArgumentResolutionException.class
        );
        errorHandler.addRetryableExceptions(
                org.springframework.dao.DataAccessException.class,
                java.net.ConnectException.class,
                org.springframework.web.client.ResourceAccessException.class,
                java.util.concurrent.RejectedExecutionException.class,
                org.springframework.kafka.KafkaException.class,
                java.util.concurrent.TimeoutException.class
        );
    }

    @Bean
    ConcurrentKafkaListenerContainerFactory<String, IncomingMessage> kafkaListenerContainerFactory(
            ConsumerFactory<String, IncomingMessage> consumerFactory,
            ProducerFactory<String, Object> producerFactory) {
        log.info("Creating KafkaListenerContainerFactory bean");
        ConcurrentKafkaListenerContainerFactory<String, IncomingMessage> factory = new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);

        var backOff = new FixedBackOff(2000L, 3);
        var recoverer = createRecoverer(errorService);

        if (producerFactory.transactionCapable()) {
            log.info("Transactions enabled — using KafkaTransactionManager + AfterRollbackProcessor");
            factory.getContainerProperties().setKafkaAwareTransactionManager(
                    new KafkaTransactionManager<>(producerFactory));
            var processor = new DefaultAfterRollbackProcessor<>(recoverer, backOff);
            addExceptionClassifications(processor);
            factory.setAfterRollbackProcessor(processor);
        } else {
            log.info("Transactions disabled — using DefaultErrorHandler");
            var errorHandler = new DefaultErrorHandler(recoverer, backOff);
            addExceptionClassifications(errorHandler);
            factory.setCommonErrorHandler(errorHandler);
        }

        factory.setAutoStartup(true);
        return factory;
    }

    private Map<String, Object> baseProducerConfig() {
        Map<String, Object> conf = kafkaProperties.buildProducerProperties(null);
        conf.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        conf.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        return conf;
    }

    @Bean
    public ProducerFactory<String, Object> producerFactory() {
        Map<String, Object> conf = baseProducerConfig();
        String username = System.getenv("KAFKACLUSTER_USERNAME").trim();
        String password = System.getenv("KAFKACLUSTER_PASSWORD").trim();
                String jaasTemplate = "org.apache.kafka.common.security.scram.ScramLoginModule required username=\"%s\" password=\"%s\";";
        String jaasConfig = String.format(jaasTemplate, username, password);
        log.info("Kafka username length: {}", username.length());
        log.info("Kafka password length: {}", password.length());

        if (username != null && password != null) {
            conf.put("sasl.jaas.config", jaasConfig);
        }
        DefaultKafkaProducerFactory<String, Object> factory =
                new DefaultKafkaProducerFactory<>(conf);
        String txPrefix = kafkaProperties.getProducer().getTransactionIdPrefix();
        if (txPrefix != null && !txPrefix.isBlank()) {
            factory.setTransactionIdPrefix(txPrefix);
        }

        return factory;
    }

    @Bean
    public KafkaTemplate<String, Object> kafkaTemplate(ProducerFactory<String, Object> producerFactory) {
        return new KafkaTemplate<>(producerFactory);
    }

    @Bean
    public KafkaTransactionManager<String, Object> kafkaTransactionManager(
            ProducerFactory<String, Object> producerFactory) {
        if (!producerFactory.transactionCapable()) {
            log.info("ProducerFactory is not transactional — skipping KafkaTransactionManager bean");
            return null;
        }
        return new KafkaTransactionManager<>(producerFactory);
    }

    @Bean
    public NewTopic createTopic() {
        return TopicBuilder.name(publisherTopic)
                .partitions(3)
                .replicas(1)
                .build();
    }
    @Bean
    public NewTopic deadLetterTopic() {
        return TopicBuilder.name(businessDlqTopic)
                .partitions(1)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic rawInputTopic() {
        return TopicBuilder.name(incomingTopic)
                .partitions(3)
                .replicas(1)
                .build();
    }

}

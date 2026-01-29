package org.pulitko.aiprocessingservice.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.pulitko.aiprocessingservice.model.IncomingMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.*;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.springframework.util.backoff.FixedBackOff;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Configuration
@RequiredArgsConstructor
@EnableKafka
public class KafkaConfig {
    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Value("${spring.kafka.groups-id.consumer}")
    private String groupId;

    @Value("${spring.kafka.topics.dlq}")
    private String dlqTopic;

    @Value("${spring.kafka.topics.outgoing}")
    private String publisherTopic;

    @Bean
    public ConsumerFactory<String, IncomingMessage> consumerFactory() {
        Map<String, Object> config = new HashMap<>();
        config.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        config.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        config.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        config.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ErrorHandlingDeserializer.class);
        config.put(ErrorHandlingDeserializer.VALUE_DESERIALIZER_CLASS, JsonDeserializer.class);
        JsonDeserializer<IncomingMessage> jsonDeserializer = new JsonDeserializer<>(IncomingMessage.class);
        jsonDeserializer.addTrustedPackages("org.pulitko.aiprocessingservice.model");
        jsonDeserializer.setRemoveTypeHeaders(false);
        ErrorHandlingDeserializer<IncomingMessage> errorHandlingDeserializer =
                new ErrorHandlingDeserializer<>(jsonDeserializer);

        return new DefaultKafkaConsumerFactory<>(
                config,
                new StringDeserializer(),
                errorHandlingDeserializer
        );
    }

    @Bean
    ConcurrentKafkaListenerContainerFactory<String, IncomingMessage> kafkaListenerContainerFactory(
            ConsumerFactory<String, IncomingMessage> consumerFactory,
            CommonErrorHandler errorHandler) {
        log.info("Creating KafkaListenerContainerFactory bean");
        ConcurrentKafkaListenerContainerFactory<String, IncomingMessage> factory = new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        factory.setCommonErrorHandler(errorHandler);
        factory.setAutoStartup(true);
        return factory;
    }

    @Bean
    public CommonErrorHandler errorHandler(KafkaTemplate<String, Object> kafkaTemplate) {
        var recoverer = new DeadLetterPublishingRecoverer(kafkaTemplate,
                (record, ex) -> new TopicPartition(dlqTopic, -1));
        var backOff = new FixedBackOff(0L, 0);
        var errorHandler = new DefaultErrorHandler(recoverer, backOff);
        return errorHandler;
    }

    @Bean
    public ProducerFactory<String, Object> producerFactory() {
        Map<String, Object> conf = new HashMap<>();
        conf.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        conf.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        conf.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        conf.put(ProducerConfig.RETRIES_CONFIG, 10);
        conf.put(ProducerConfig.RETRY_BACKOFF_MS_CONFIG, 1000);
        conf.put(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, 60000);
        conf.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        return new DefaultKafkaProducerFactory<>(conf);
    }

    @Bean
    public KafkaTemplate<String, Object> kafkaTemplate(ProducerFactory<String, Object> producerFactory) {
        return new KafkaTemplate<>(producerFactory);
    }

    @Bean
    NewTopic createTopic() {
        return TopicBuilder.name(publisherTopic)
                .partitions(3)
                .replicas(1)
                .build();
    }
}

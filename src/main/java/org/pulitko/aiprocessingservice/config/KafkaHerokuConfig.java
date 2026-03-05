package org.pulitko.aiprocessingservice.config;

import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.serializer.JsonSerializer;

import java.util.Map;

@Configuration
@Profile("heroku")
public class KafkaHerokuConfig {

    private final KafkaProperties kafkaProperties;

    public KafkaHerokuConfig(KafkaProperties kafkaProperties) {
        this.kafkaProperties = kafkaProperties;
    }

    @Bean
    public ProducerFactory<String, Object> producerFactory() {

        Map<String, Object> conf = kafkaProperties.buildProducerProperties(null);

        conf.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        conf.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);

        conf.put("security.protocol", "SASL_SSL");
        conf.put("sasl.mechanism", "SCRAM-SHA-512");

        conf.put(
                "sasl.jaas.config",
                "org.apache.kafka.common.security.scram.ScramLoginModule required " +
                        "username=\"" + System.getenv("KAFKACLUSTER_USERNAME") + "\" " +
                        "password=\"" + System.getenv("KAFKACLUSTER_PASSWORD") + "\";"
        );

        return new DefaultKafkaProducerFactory<>(conf);
    }
}

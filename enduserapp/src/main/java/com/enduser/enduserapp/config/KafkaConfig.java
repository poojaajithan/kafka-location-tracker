package com.enduser.enduserapp.config;

import org.apache.kafka.common.TopicPartition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

@Configuration
public class KafkaConfig {

    private static final Logger logger = LoggerFactory.getLogger(KafkaConfig.class);

    @Bean
    public DefaultErrorHandler errorHandler(KafkaTemplate<Object, Object> template) {
        // Custom destination resolver routes failed messages to <topic>.DLT on the same partition
        // as the original message. This preserves partition affinity so DLT consumers receive
        // failures in the same order they occurred, and allows tracing which partition produced the failure.
        // The default resolver always sends to partition 0, which breaks ordering when the DLT has multiple partitions.
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(template,
                (record, exception) -> new TopicPartition(record.topic() + ".DLT", record.partition()));
        return new DefaultErrorHandler(recoverer, new FixedBackOff(1000L, 3));
    }

    @KafkaListener(topics = AppConstants.LOCATION_UPDATE_TOPIC, groupId = AppConstants.CONSUMER_GROUP_ID)
    public void updatedLocation(String value) {
        if (value == null) {
            throw new IllegalArgumentException("Received null location update — routing to DLT");
        }
        try {
            double location = Double.parseDouble(value.trim());
            if (location < 0) {
                throw new IllegalArgumentException("Received negative location value: " + value + " — routing to DLT");
            }
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Received non-numeric location value: " + value + " — routing to DLT");
        }
        logger.info("Received location update from Kafka topic {}: {}", AppConstants.LOCATION_UPDATE_TOPIC, value);
    }

    @KafkaListener(topics = AppConstants.LOCATION_UPDATE_TOPIC_DLT, groupId = AppConstants.CONSUMER_GROUP_ID)
    public void handleDlt(String value) {
        logger.error("Dead-lettered message from {}: {}", AppConstants.LOCATION_UPDATE_TOPIC_DLT, value);
    }
}

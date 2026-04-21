package com.enduser.enduserapp.config;

import com.enduser.enduserapp.models.LocationUpdateRequest;
import org.apache.kafka.common.TopicPartition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.util.backoff.FixedBackOff;

@Configuration
public class KafkaConfig {

    private static final Logger logger = LoggerFactory.getLogger(KafkaConfig.class);

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, LocationUpdateRequest> kafkaListenerContainerFactory(
            ConsumerFactory<String, LocationUpdateRequest> consumerFactory,
            DefaultErrorHandler errorHandler) {
        ConcurrentKafkaListenerContainerFactory<String, LocationUpdateRequest> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        factory.getContainerProperties().setObservationEnabled(true);
        factory.setCommonErrorHandler(errorHandler);
        return factory;
    }

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
    public void updatedLocation(@Header(KafkaHeaders.RECEIVED_KEY) String driverKey, LocationUpdateRequest request) {
        if (request.latitude() < 0 || request.longitude() < 0) {
            throw new IllegalArgumentException("Received negative coordinates [driverId=" + driverKey + "]: lat="
                    + request.latitude() + ", lon=" + request.longitude() + " — routing to DLT");
        }
        logger.info("Received location update [driverId={}]: lat={}, lon={}", driverKey, request.latitude(), request.longitude());
    }

    @KafkaListener(topics = AppConstants.LOCATION_UPDATE_TOPIC_DLT, groupId = AppConstants.CONSUMER_GROUP_ID)
    public void handleDlt(LocationUpdateRequest value) {
        logger.error("Dead-lettered message from {}: {}", AppConstants.LOCATION_UPDATE_TOPIC_DLT, value);
    }
}

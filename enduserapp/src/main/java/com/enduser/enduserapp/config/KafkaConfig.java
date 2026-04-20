package com.enduser.enduserapp.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.KafkaListener;

@Configuration
public class KafkaConfig {

    private static final Logger logger = LoggerFactory.getLogger(KafkaConfig.class);
    
    @KafkaListener(topics = AppConstants.LOCATION_UPDATE_TOPIC, groupId = AppConstants.CONSUMER_GROUP_ID)
    public void updatedLocation(String value){
        logger.info("Received location update from Kafka topic {}: {}", AppConstants.LOCATION_UPDATE_TOPIC, value);
    }
}

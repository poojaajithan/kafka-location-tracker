package com.deliverydriver.deliverydriverapp.services;

import com.deliverydriver.deliverydriverapp.config.AppConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
public class KafkaService {

    private static final Logger logger = LoggerFactory.getLogger(KafkaService.class);

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    public boolean updateLocation(String location){
        this.kafkaTemplate.send(AppConstants.LOCATION_UPDATE_TOPIC, location);
        logger.info("Location sent to Kafka topic {}: {}", AppConstants.LOCATION_UPDATE_TOPIC, location);
        return true;
    }

}

package com.deliverydriver.deliverydriverapp.services;

import com.deliverydriver.deliverydriverapp.config.AppConstants;
import com.deliverydriver.deliverydriverapp.models.LocationUpdateRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class KafkaService {

    private static final Logger logger = LoggerFactory.getLogger(KafkaService.class);

    @Autowired
    private KafkaTemplate<String, LocationUpdateRequest> kafkaTemplate;

    /**
     * Sends the location update inside a Kafka producer transaction.
     * If the send fails (e.g. broker unavailable), the transaction aborts and the
     * broker discards the partially-written message — consumers with isolation.level=
     * read_committed will never see it. Without a transaction, a failed send could
     * leave a partial message visible to consumers.
     */
    @Transactional
    public boolean updateLocation(LocationUpdateRequest request) {
        this.kafkaTemplate.send(AppConstants.LOCATION_UPDATE_TOPIC, request.driverId(), request);
        logger.info("Location sent to Kafka topic {} [driverId={}]: lat={}, lon={}",
                AppConstants.LOCATION_UPDATE_TOPIC, request.driverId(), request.latitude(), request.longitude());
        return true;
    }

}

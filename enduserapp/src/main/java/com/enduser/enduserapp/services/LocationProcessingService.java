package com.enduser.enduserapp.services;

import com.enduser.enduserapp.config.AppConstants;
import com.enduser.enduserapp.models.LocationUpdate;
import com.enduser.enduserapp.models.LocationUpdateRequest;
import com.enduser.enduserapp.repositories.LocationUpdateRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class LocationProcessingService {

    private static final Logger logger = LoggerFactory.getLogger(LocationProcessingService.class);

    private final LocationUpdateRepository repository;
    private final KafkaTemplate<String, LocationUpdateRequest> kafkaTemplate;

    public LocationProcessingService(LocationUpdateRepository repository,
                                     KafkaTemplate<String, LocationUpdateRequest> kafkaTemplate) {
        this.repository = repository;
        this.kafkaTemplate = kafkaTemplate;
    }

    /**
     * Consumes a location update, persists it to the database, and publishes a
     * "location-processed" event — all within a single atomic transaction.
     *
     * The ChainedKafkaTransactionManager (set on the container factory) coordinates
     * three resources in one unit of work:
     *   1. Kafka consumer offset commit   — marks the inbound message as processed
     *   2. JPA / H2 database write        — persists the LocationUpdate row
     *   3. Kafka producer send            — publishes to location-processed-topic
     *
     * If the application crashes after step 2 but before step 3, none of the three
     * commit. On restart, Kafka re-delivers the same message because the offset was
     * never committed, and the transaction runs again — preventing the duplicate-payment
     * scenario described in the scenario above.
     */
    @Transactional
    @KafkaListener(topics = AppConstants.LOCATION_UPDATE_TOPIC, groupId = AppConstants.CONSUMER_GROUP_ID)
    public void updatedLocation(@Header(KafkaHeaders.RECEIVED_KEY) String driverKey,
                                LocationUpdateRequest request) {
        if (request.latitude() < 0 || request.longitude() < 0) {
            throw new IllegalArgumentException(
                    "Received negative coordinates [driverId=" + driverKey + "]: lat="
                            + request.latitude() + ", lon=" + request.longitude()
                            + " — routing to DLT");
        }

        // Step 1: persist to database inside the open transaction
        LocationUpdate entity = new LocationUpdate(
                request.driverId(), request.latitude(), request.longitude(), Instant.now());
        repository.save(entity);
        logger.info("Saved location to DB [driverId={}]: lat={}, lon={}",
                driverKey, request.latitude(), request.longitude());

        // Step 2: publish the downstream event inside the SAME transaction.
        // This send is buffered until the transaction commits. If the DB save above
        // throws, the transaction rolls back and this message is never published.
        kafkaTemplate.send(AppConstants.LOCATION_PROCESSED_TOPIC, driverKey, request);
        logger.info("Published to {} [driverId={}]", AppConstants.LOCATION_PROCESSED_TOPIC, driverKey);
    }

    @KafkaListener(topics = AppConstants.LOCATION_UPDATE_TOPIC_DLT, groupId = AppConstants.CONSUMER_GROUP_ID)
    public void handleDlt(LocationUpdateRequest value) {
        logger.error("Dead-lettered message from {}: {}", AppConstants.LOCATION_UPDATE_TOPIC_DLT, value);
    }
}

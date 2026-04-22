package com.enduser.enduserapp.config;

import com.enduser.enduserapp.models.LocationUpdateRequest;
import jakarta.persistence.EntityManagerFactory;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.TopicPartition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.transaction.KafkaTransactionManager;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.util.backoff.FixedBackOff;

@Configuration
public class KafkaConfig {

    /**
     * Kafka transaction manager — placed on the listener container so that:
     *   1. A Kafka producer transaction is opened before each listener invocation.
     *   2. The consumer offset commit is sent inside that same transaction.
     *   3. Any kafkaTemplate.send() calls in the listener are buffered in that transaction.
     * All three commit (or all roll back) atomically on the Kafka broker side.
     */
    @Bean
    public KafkaTransactionManager<String, LocationUpdateRequest> kafkaTransactionManager(
            ProducerFactory<String, LocationUpdateRequest> producerFactory) {
        return new KafkaTransactionManager<>(producerFactory);
    }

    /**
     * JPA transaction manager — named 'transactionManager' (the default name Spring Data JPA
     * repositories look for) and @Primary so plain @Transactional targets the DB.
     * When the listener is annotated with @Transactional, Spring opens a JPA transaction
     * that is registered as a transaction synchronization on the current thread.
     * Spring commits the JPA (DB) transaction first, then the container commits the
     * Kafka transaction (send + offset). If the DB commit fails, the Kafka transaction
     * rolls back — no message is published and the offset is not advanced.
     */
    @Bean
    @Primary
    public JpaTransactionManager transactionManager(EntityManagerFactory emf) {
        return new JpaTransactionManager(emf);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, LocationUpdateRequest> kafkaListenerContainerFactory(
            ConsumerFactory<String, LocationUpdateRequest> consumerFactory,
            DefaultErrorHandler errorHandler,
            KafkaTransactionManager<String, LocationUpdateRequest> kafkaTransactionManager) {
        ConcurrentKafkaListenerContainerFactory<String, LocationUpdateRequest> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        factory.getContainerProperties().setObservationEnabled(true);
        factory.setCommonErrorHandler(errorHandler);
        // The KafkaTransactionManager on the container is what makes the consumer offset
        // commit transactional — offsets only advance when the Kafka TX (which also
        // wraps the producer send) commits successfully.
        factory.getContainerProperties().setTransactionManager(kafkaTransactionManager);
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

    @Bean
    public NewTopic locationProcessedTopic() {
        return TopicBuilder.name(AppConstants.LOCATION_PROCESSED_TOPIC)
                .partitions(3)
                .replicas(1)
                .build();
    }
}

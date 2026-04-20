# Kafka Location Tracker

A real-time location tracking system built with Spring Boot microservices communicating via Apache Kafka.

## Architecture

```
Delivery Driver App  →  Kafka Topic (location-update-topic)  →  End User App
     (port 8080)                                                  (port 8081)
```

- **deliverydriverapp** — Kafka producer. Exposes a REST API to receive location updates from drivers and publishes them to a Kafka topic.
- **enduserapp** — Kafka consumer. Listens to the Kafka topic and processes incoming location updates in real time.

## Tech Stack

- Java 17
- Spring Boot 3.2.5
- Spring Kafka 3.6.2
- Apache Kafka (running locally)
- Maven (multi-module project)

## Prerequisites

- Java 17+
- Maven 3.6+
- Apache Kafka running on `localhost:9092`

### Start Kafka (if not already running)

```bash
# Start Zookeeper
bin/zookeeper-server-start.sh config/zookeeper.properties

# Start Kafka broker
bin/kafka-server-start.sh config/server.properties
```

## Project Structure

```
location-tracker/          ← Parent Maven project
├── deliverydriverapp/     ← Producer microservice (port 8080)
│   └── src/main/java/com/deliverydriver/deliverydriverapp/
│       ├── config/
│       │   ├── AppConstants.java
│       │   └── KafkaConfig.java
│       ├── controllers/
│       │   └── LocationController.java
│       └── services/
│           └── KafkaService.java
└── enduserapp/            ← Consumer microservice (port 8081)
    └── src/main/java/com/enduser/enduserapp/
        └── config/
            ├── AppConstants.java
            └── KafkaConfig.java
```

## Running the Applications

### 1. Start deliverydriverapp (Producer)

```bash
cd deliverydriverapp
./mvnw spring-boot:run
```

Runs on `http://localhost:8080`

### 2. Start enduserapp (Consumer)

```bash
cd enduserapp
mvn spring-boot:run
```

Runs on `http://localhost:8081`

## API Usage

### Send a location update

```
POST http://localhost:8080/location/update
Content-Type: text/plain

12.9716,77.5946
```

**Response:** `Location updated successfully`

The location will be published to the `location-update-topic` Kafka topic and consumed by `enduserapp`, which logs it:

```
INFO  Received location update from Kafka topic location-update-topic: 12.9716,77.5946
```

## Kafka Configuration

| Property | Value |
|---|---|
| Bootstrap servers | `localhost:9092` |
| Topic | `location-update-topic` |
| Partitions | 1 |
| Replicas | 1 |
| Consumer group | `location-group` |

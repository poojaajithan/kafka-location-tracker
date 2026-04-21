# Kafka Location Tracker

A real-time location tracking system built with Spring Boot microservices communicating via Apache Kafka, with distributed tracing via Micrometer + Zipkin.

## Architecture

```
Delivery Driver App  →  Kafka Topic (location-update-topic)  →  End User App
     (port 8080)                                                  (port 8081)
          ↓                                                            ↓
                         Zipkin (port 9411)
```

- **deliverydriverapp** — Kafka producer. Exposes a REST API to receive JSON location updates from drivers, publishes them to a Kafka topic, and emits trace spans.
- **enduserapp** — Kafka consumer. Listens to the Kafka topic, processes incoming location updates, routes failed messages to a Dead Letter Topic (DLT), and emits trace spans.
- **Zipkin** — Collects and visualises distributed traces across both services.

## Tech Stack

- Java 17
- Spring Boot 3.2.5
- Spring Kafka 3.1.4
- Apache Kafka (running locally)
- Micrometer Tracing (Brave bridge) + Zipkin
- Maven (multi-module project)

## Prerequisites

- Java 17+
- Maven 3.6+
- Apache Kafka running on `localhost:9092`
- Docker (for Zipkin)

### Start Kafka (if not already running)

```bash
# Start Zookeeper
bin/zookeeper-server-start.sh config/zookeeper.properties

# Start Kafka broker
bin/kafka-server-start.sh config/server.properties
```

### Start Zipkin

```bash
docker run -d -p 9411:9411 --name zipkin openzipkin/zipkin
```

Zipkin UI: `http://localhost:9411`

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
│       ├── models/
│       │   └── LocationUpdateRequest.java
│       └── services/
│           └── KafkaService.java
└── enduserapp/            ← Consumer microservice (port 8081)
    └── src/main/java/com/enduser/enduserapp/
        ├── config/
        │   ├── AppConstants.java
        │   └── KafkaConfig.java
        └── models/
            └── LocationUpdateRequest.java
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
Content-Type: application/json

{"driverId": "driver-1", "latitude": 12.9716, "longitude": 77.5946}
```

**Response:** `Location updated successfully`

The message is serialised as JSON, published to the `location-update-topic` Kafka topic, and consumed by `enduserapp`, which logs it:

```
INFO  [enduserapp,<traceId>,<spanId>] Received location update [driverId=driver-1]: lat=12.9716, lon=77.5946
```

### Send an invalid update (triggers DLT)

```
POST http://localhost:8080/location/update
Content-Type: application/json

{"driverId": "driver-1", "latitude": -5.0, "longitude": 77.5946}
```

Negative coordinates cause the consumer to throw, retrying 3 times before routing the message to `location-update-topic.DLT`.

## Distributed Tracing

Every request produces a single `traceId` that flows from HTTP → Kafka producer → Kafka consumer across both services. Logs include the traceId and spanId:

```
[deliverydriverapp, 69e7c80e6d603a0fb5532627add0f981, b5532627add0f981]  ← HTTP + produce
[enduserapp,        69e7c80e6d603a0fb5532627add0f981, 027e1bc965b90bd8]  ← consume
```

View the full trace in Zipkin at `http://localhost:9411`:
- Span 1: `deliverydriverapp` — `POST /location/update`
- Span 2: `deliverydriverapp` — `location-update-topic send`
- Span 3: `enduserapp` — `location-update-topic receive`

## Kafka Configuration

| Property | Value |
|---|---|
| Bootstrap servers | `localhost:9092` |
| Topic | `location-update-topic` |
| Partitions | 3 |
| Replicas | 1 |
| Consumer group | `location-group` |
| Dead Letter Topic | `location-update-topic.DLT` |
| Message format | JSON (`JsonSerializer` / `JsonDeserializer`) |
| DLT retry policy | 3 retries, 1 s fixed backoff |

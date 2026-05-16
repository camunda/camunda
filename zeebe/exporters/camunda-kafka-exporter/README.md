# Camunda Kafka Exporter

The Camunda Kafka Exporter forwards Zeebe records to Kafka topics.

It is based on the design of the community `zeebe-kafka-exporter`, adapted for this monorepo.

## Delivery and ordering

- Delivery semantics are at-least-once.
- Failed flushes are retried from an in-memory bounded queue.
- Partition routing preserves Zeebe partition affinity using:

  `kafkaPartition = zeebePartitionId % topicPartitionCount`

## Configuration

```yaml
zeebe:
  broker:
    exporters:
      camunda-kafka:
        className: io.camunda.exporter.kafka.CamundaKafkaExporter
        args:
          maxBatchSize: 100
          maxQueueSize: 10000
          overflowPolicy: "drop_oldest"
          flushIntervalMs: 1000
          producer:
            servers: "kafka-1:9092,kafka-2:9092"
            clientId: "zeebe"
            requestTimeoutMs: 5000
            maxBlockingTimeoutMs: 1000
            closeTimeoutMs: 5000
            config: |
              linger.ms=5
              batch.size=32768
          records:
            defaults: { type: "event", topic: "zeebe" }
            job: { topic: "zeebe-job" }
            incident: { topic: "zeebe-incident" }
            variable: { topic: "zeebe-variable" }
```

## Key settings

- `maxBatchSize`: Number of records buffered before forced flush.
- `maxQueueSize`: Maximum buffered records waiting for flush retries.
- `overflowPolicy`: Queue overflow behavior. Supported values: `drop_oldest`, `drop_newest`, `block`.
- `flushIntervalMs`: Periodic flush interval.
- `producer.servers`: Kafka bootstrap servers (required).
- `records`: Per-value-type topic/type overrides.

`records.<valueType>.type` accepts a comma-separated list: `command`, `event`, `rejection`.

Record keys use `partitionId-position`, for example:

```text
2-42
```

Record payloads are wrapped with a schema version:

```json
{"schemaVersion":1,"record":{"foo":"bar"}}
```

Kafka headers include: `valueType`, `intent`, `partitionId`, `recordType`, `tenantId`, `brokerVersion`.

Definition and identity records are exported only from partition 1.

You can run multiple independent Kafka exports by defining multiple exporter entries under `zeebe.broker.exporters` with different names and args.

## Migration from community exporter

- Topic routing by value type is preserved via `records.<valueType>.topic`.
- Record type filtering remains configurable via `records.<valueType>.type`.
- Message key format changed from JSON to `partitionId-position`.
- Payload now uses a schema envelope (`schemaVersion` + `record`).
- Metadata is available as Kafka headers, reducing consumer-side payload parsing.

## Build

```bash
./mvnw install -pl zeebe/exporters/camunda-kafka-exporter -am -Dquickly -T1C
```

## Test

```bash
./mvnw verify -pl zeebe/exporters/camunda-kafka-exporter -Dquickly -T1C
```

## Local self-managed Kafka integration example

One-command smoke test (starts Kafka, publishes a sample event, verifies consumption):

```bash
make kafka-exporter-smoke
```

All-in-one stack (Camunda + Kafka) for easier end-user testing:

```bash
make kafka-exporter-camunda-stack-up
```

This uses [docker-compose.sm-camunda-kafka.yml](zeebe/exporters/camunda-kafka-exporter/examples/docker-compose.sm-camunda-kafka.yml) and expects an image containing this exporter. During development in this repo you can build one with:

```bash
docker build -f camunda.Dockerfile --build-arg BASE=public -t camunda/camunda:SNAPSHOT .
```

Start a local Kafka broker:

```bash
docker compose -f zeebe/exporters/camunda-kafka-exporter/examples/docker-compose.sm-kafka.yml up -d
```

Point exporter configuration to the local broker:

```yaml
zeebe:
  broker:
    exporters:
      camunda-kafka:
        className: io.camunda.exporter.kafka.CamundaKafkaExporter
        args:
          producer:
            servers: "localhost:9092"
          records:
            defaults: { type: "event", topic: "zeebe" }
```

Trigger a few engine events (for example: deploy a process, start a process instance, complete a task).

Verify that topic data can be consumed again from Kafka:

```bash
bash zeebe/exporters/camunda-kafka-exporter/examples/verify-consumption.sh zeebe 10
```

Run the smoke helper directly (without make):

```bash
bash zeebe/exporters/camunda-kafka-exporter/examples/smoke-test.sh zeebe 1
```

Direct consumer command (without helper script):

```bash
docker compose -f zeebe/exporters/camunda-kafka-exporter/examples/docker-compose.sm-kafka.yml exec -T kafka \
  kafka-console-consumer \
  --bootstrap-server kafka:29092 \
  --topic zeebe \
  --from-beginning \
  --max-messages 10 \
  --timeout-ms 15000 \
  --property print.headers=true \
  --property print.key=true \
  --property print.value=true
```

Stop local Kafka:

```bash
docker compose -f zeebe/exporters/camunda-kafka-exporter/examples/docker-compose.sm-kafka.yml down -v
```

Or use the make target:

```bash
make kafka-exporter-smoke-down
```

Stop all-in-one Camunda + Kafka stack:

```bash
make kafka-exporter-camunda-stack-down
```

## Enterprise authentication examples

Two additional stacks demonstrate authentication patterns typical in large financial institutions.
Both require OpenSSL and a JDK `keytool` to generate demo certificates first:

```bash
bash zeebe/exporters/camunda-kafka-exporter/examples/gen-certs.sh
```

### SASL/SCRAM-SHA-512 over TLS (SASL\_SSL)

Password-based authentication with mandatory TLS encryption. Credentials are stored as hashed
SCRAM tokens in ZooKeeper — never in plaintext. Common where Kerberos infrastructure is not
available but strong authentication is required.

Broker port: `9093`. Camunda is pre-configured via `camunda-sasl-scram.yml`.

End-to-end demo (starts the stack, deploys a process, verifies Kafka records via SCRAM auth):

```bash
bash zeebe/exporters/camunda-kafka-exporter/examples/run-kafka-exporter-demo-sasl-scram.sh
```

Manual stack control:

```bash
docker compose -f zeebe/exporters/camunda-kafka-exporter/examples/docker-compose.sm-kafka-sasl-scram.yml up -d
docker compose -f zeebe/exporters/camunda-kafka-exporter/examples/docker-compose.sm-kafka-sasl-scram.yml down -v
```

### Mutual TLS (mTLS)

Service-to-service authentication entirely via X.509 certificates — no username or password.
The broker enforces `ssl.client.auth=required`; clients must present a certificate signed by
the shared CA. Satisfies NIST 800-204, PCI-DSS, and ISO 27001 requirements.

Broker port: `9094`. Camunda is pre-configured via `camunda-mtls.yml`.

End-to-end demo (starts the stack, deploys a process, verifies Kafka records using the Zeebe
client certificate CN=zeebe):

```bash
bash zeebe/exporters/camunda-kafka-exporter/examples/run-kafka-exporter-demo-mtls.sh
```

Manual stack control:

```bash
docker compose -f zeebe/exporters/camunda-kafka-exporter/examples/docker-compose.sm-kafka-mtls.yml up -d
docker compose -f zeebe/exporters/camunda-kafka-exporter/examples/docker-compose.sm-kafka-mtls.yml down -v
```


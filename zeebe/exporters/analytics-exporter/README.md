# Camunda Analytics Exporter

Zeebe exporter that ships product analytics events to a Camunda analytics endpoint via
OTLP/HTTP. Designed for Self-Managed and SaaS deployments.

## Design Principles

### Fire-and-forget

The exporter **never impacts broker throughput**. Every record's position is updated eagerly
and unconditionally — before any analytics processing happens. If the analytics pipeline is
slow, saturated, or completely down, the broker keeps running at full speed.

### Analytics-grade delivery

Data loss is accepted. Events may be dropped under any of these conditions:

- The OTel batch queue is full (back-pressure from a slow or unreachable endpoint)
- The broker crashes or restarts (in-memory queue is lost)
- The OTLP endpoint returns errors (no persistent retry, no disk buffering)

This is a deliberate trade-off: analytics data is approximate, not exact. Consumer-side
de-duplication is expected (using `camunda.cluster.id` + `camunda.partition.id` +
`camunda.log.position` as composite key).

### Zero backpressure

The OTel SDK's `BatchLogRecordProcessor` runs on a background thread with a bounded queue.
When the queue fills, new records are silently dropped — the `export()` method on the Zeebe
actor thread never blocks, never waits, never retries synchronously.

### Record filter

The exporter sets a `RecordFilter` that accepts only event records for registered handler
types, originating from the local partition. See `AnalyticsRecordFilter` Javadoc for the
filtering layers and the rationale behind partition-based deduplication.

## What it exports

Events follow the [OTel Events semantic convention](https://opentelemetry.io/docs/specs/semconv/general/events/) —
identified by the `event.name` attribute. See handler classes in `handler/` and
`AnalyticsAttributes` for the specific events and their attributes.

### OTel resource attributes

|       Attribute        |  Type  |                 Description                  |
|------------------------|--------|----------------------------------------------|
| `camunda.cluster.id`   | string | Cluster identifier (from broker Context API) |
| `camunda.partition.id` | long   | Partition ID                                 |
| `service.name`         | string | Always `camunda-zeebe`                       |

## Configuration

```yaml
zeebe:
  broker:
    exporters:
      analytics:
        className: io.camunda.exporter.analytics.AnalyticsExporter
        args:
          endpoint: "https://analytics.cloud.camunda.io"
          pushInterval: "PT5S"
          maxQueueSize: 2048
          maxBatchSize: 512
```

|    Property    |               Default                |                      Description                      |
|----------------|--------------------------------------|-------------------------------------------------------|
| `endpoint`     | `https://analytics.cloud.camunda.io` | OTLP/HTTP base URL. The SDK appends `/v1/logs`.       |
| `pushInterval` | `PT5S`                               | Batch push interval (ISO 8601 duration).              |
| `maxQueueSize` | `2048`                               | Maximum log records queued in-memory before dropping. |
| `maxBatchSize` | `512`                                | Maximum records per export batch.                     |

## Architecture

See source Javadocs for component details. Key files:

- **`AnalyticsExporter`** — Zeebe exporter lifecycle and handler wiring.
- **`HandlerRegistry`** — Routes records by (ValueType, Intent) to handlers.
- **`AnalyticsRecordFilter`** — Broker-level filtering (type, value, intent, partition).
- **`OtelSdkManager`** — OTel SDK lifecycle (Resource, LoggerProvider, BatchProcessor).
- **`handler/`** — Individual event handlers (one per analytics event type).

## Building

```bash
# Build with dependencies
./mvnw install -pl zeebe/exporters/analytics-exporter -am -Dquickly -T1C

# Run unit tests
./mvnw verify -pl zeebe/exporters/analytics-exporter -DskipTests=false -DskipITs -Dquickly

# Run all tests (unit + integration, requires Docker)
./mvnw verify -pl zeebe/exporters/analytics-exporter -DskipTests=false -Dquickly
```

## Local Development

Start a local OTel Collector with debug output:

```bash
docker run --rm -p 4318:4318 \
  -v $(pwd)/src/test/resources/otel-collector-config.yaml:/etc/otelcol-contrib/config.yaml \
  otel/opentelemetry-collector-contrib:0.119.0
```

Then configure the exporter with `endpoint: "http://localhost:4318"`.

## Testing

### Unit tests (`AnalyticsExporterTest`)

Tests handler routing, position tracking, attribute mapping, config validation, and error
resilience. Uses `InMemoryLogRecordExporter` via an `OtelSdkManager` subclass that swaps
the OTLP transport — same Resource and SDK construction as production.

### SDK pipeline tests (`OtelSdkManagerTest`)

Tests OTel pipeline contract: non-blocking when queue is full, unreachable endpoint handling,
event delivery, flush-on-shutdown, failure recovery, and post-shutdown safety.

### Integration tests (`AnalyticsExporterOtelIT`)

End-to-end tests using a real OTel Collector in Docker (Testcontainers). No mocking, no
overrides — uses the default `AnalyticsExporter` constructor with the production
`BatchLogRecordProcessor` and real OTLP/HTTP transport. Tests event delivery with attribute
verification and batching behavior.

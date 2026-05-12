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

### No record filter

The exporter does **not** set a `RecordFilter`. It accepts all records from the broker to
track the full log position for compaction. Only event records with registered handlers
produce OTel log records; everything else is a fast no-op (EnumMap lookup returning null).

## What it exports

|                Event                | OTel Signal |
|-------------------------------------|-------------|
| `PROCESS_INSTANCE_CREATION` (event) | Log Record  |

Events follow the [OTel Events semantic convention](https://opentelemetry.io/docs/specs/semconv/general/events/) —
identified by the `event.name` attribute. Body is not set (per spec: "Events SHOULD NOT use
body except to represent a string display message").

### Log record attributes

|              Attribute              |  Type  |                   Description                    |
|-------------------------------------|--------|--------------------------------------------------|
| `event.name`                        | string | Event type identifier (OTel semantic convention) |
| `camunda.bpmn_process_id`           | string | BPMN process ID                                  |
| `camunda.process_version`           | long   | Process definition version                       |
| `camunda.process_definition_key`    | long   | Process definition key                           |
| `camunda.process_instance_key`      | long   | Process instance key                             |
| `camunda.root_process_instance_key` | long   | Root process instance key (for call activities)  |
| `camunda.tenant_id`                 | string | Tenant ID                                        |
| `camunda.log.position`              | long   | Log stream position (de-duplication key)         |

Attribute names follow [OTel naming conventions](https://opentelemetry.io/docs/specs/semconv/general/naming/) —
dot-delimited namespaces, snake_case for multi-word components.

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

```
Zeebe actor thread                          Background thread
─────────────────                          ─────────────────
export(record)
  ├── updatePosition (always, unconditionally)
  ├── EnumMap lookup → handler or null
  └── handler → OtelSdkManager.logEvent(eventName, logPosition, builder)
       └── BatchLogRecordProcessor.onEmit()   ──→  queue  ──→  OTLP/HTTP POST
           (non-blocking, drops if full)                       to analytics endpoint
```

Key components:

- **`AnalyticsExporter`** — Zeebe exporter. Owns handler registry and record routing.
  Handlers build OTel log records directly via the `logEvent()` lambda API.
- **`OtelSdkManager`** — Manages the OTel SDK lifecycle (Resource, LoggerProvider, SDK).
  Provides `logEvent(eventName, logPosition, builder)` — sets `event.name`, severity,
  and log position automatically; the builder lambda adds event-specific attributes.
- **`AnalyticsAttributes`** — Shared OTel attribute key constants following naming conventions.

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

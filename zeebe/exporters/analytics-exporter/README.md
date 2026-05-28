# Camunda Analytics Exporter

Zeebe exporter that ships product analytics events to a Camunda analytics endpoint over
OTLP/HTTP. It is opt-in, default-off, and intended for Camunda 8 Self-Managed deployments.

The exporter is **analytics-grade**, not billing- or audit-grade: it is designed so it
cannot impact broker throughput, and it accepts data loss under failure. It runs only on
the partition leader, so no extra high-availability setup is required.

> **Data handling.** The exporter sends process metadata only. It does **not** export
> process variables, payloads, message bodies, or any other potentially sensitive data. For
> the company-wide policy on what telemetry Camunda collects and how privacy is protected,
> see [data collection](https://docs.camunda.io/docs/reference/data-collection/data-collection/).

## Enable the exporter

To enable the exporter, add an `analytics` block to your broker configuration. To disable
it, remove the block. On Camunda 8.10 and later, the exporter ships with Zeebe and no
`jarPath` is required. On 8.9 and earlier, you must install the standalone JAR (see
[Install on older clusters](#install-on-older-clusters-standalone-jar)) and reference it
via `jarPath`.

### Prerequisites

The exporter requires a Camunda license key and a cluster ID.

- **Camunda 8.10 and later:** both values are resolved automatically from the broker
  context. No additional setup is needed.
- **Camunda 8.9 and earlier:** the broker does not expose the license key or cluster ID
  through the context API. Provide them via environment variables on every broker:
  - `CAMUNDA_LICENSE_KEY` — the Camunda license key.
  - `ZEEBE_BROKER_CLUSTER_CLUSTERID` — the cluster identifier.

  Without these variables, the exporter fails to start on 8.9 and earlier.

### YAML configuration

Two configuration styles are supported.

**Unified configuration (Camunda 8.9 and later, recommended):**

```yaml
camunda:
  data:
    exporters:
      analytics:
        class-name: io.camunda.exporter.analytics.AnalyticsExporter
        # jar-path is required on 8.9 only; omit on 8.10+
        jar-path: /usr/local/zeebe/exporters/camunda-analytics-exporter.jar
        args:
          endpoint: https://analytics.cloud.camunda.io
          push-interval: PT5S
          max-queue-size: 2048
          max-batch-size: 512
```

**Legacy configuration (Camunda 8.8 and earlier):**

```yaml
zeebe:
  broker:
    exporters:
      analytics:
        className: io.camunda.exporter.analytics.AnalyticsExporter
        jarPath: /usr/local/zeebe/exporters/camunda-analytics-exporter.jar
        args:
          endpoint: https://analytics.cloud.camunda.io
          pushInterval: PT5S
          maxQueueSize: 2048
          maxBatchSize: 512
```

### Environment variables

The same settings can be provided via environment variables.

**Unified (8.9+):** `CAMUNDA_DATA_EXPORTERS_ANALYTICS_*`

```sh
CAMUNDA_DATA_EXPORTERS_ANALYTICS_CLASSNAME=io.camunda.exporter.analytics.AnalyticsExporter
# JARPATH is required on 8.9 only; omit on 8.10+
CAMUNDA_DATA_EXPORTERS_ANALYTICS_JARPATH=/usr/local/zeebe/exporters/camunda-analytics-exporter.jar
CAMUNDA_DATA_EXPORTERS_ANALYTICS_ARGS_ENDPOINT=https://analytics.cloud.camunda.io
CAMUNDA_DATA_EXPORTERS_ANALYTICS_ARGS_PUSHINTERVAL=PT5S
CAMUNDA_DATA_EXPORTERS_ANALYTICS_ARGS_MAXQUEUESIZE=2048
CAMUNDA_DATA_EXPORTERS_ANALYTICS_ARGS_MAXBATCHSIZE=512
```

**Legacy (8.8 and earlier):** `ZEEBE_BROKER_EXPORTERS_ANALYTICS_*`

```sh
ZEEBE_BROKER_EXPORTERS_ANALYTICS_CLASSNAME=io.camunda.exporter.analytics.AnalyticsExporter
ZEEBE_BROKER_EXPORTERS_ANALYTICS_JARPATH=/usr/local/zeebe/exporters/camunda-analytics-exporter.jar
ZEEBE_BROKER_EXPORTERS_ANALYTICS_ARGS_ENDPOINT=https://analytics.cloud.camunda.io
ZEEBE_BROKER_EXPORTERS_ANALYTICS_ARGS_PUSHINTERVAL=PT5S
ZEEBE_BROKER_EXPORTERS_ANALYTICS_ARGS_MAXQUEUESIZE=2048
ZEEBE_BROKER_EXPORTERS_ANALYTICS_ARGS_MAXBATCHSIZE=512
```

### Verify the exporter is running

On broker startup, look for the following log line — it confirms the exporter loaded with
the expected endpoint, cluster ID, and partition ID:

```
Analytics exporter configured: endpoint=https://analytics.cloud.camunda.io, clusterId=<cluster-id>, partitionId=<partition-id>
```

## Configuration reference

All options live under `args`. Defaults are tuned for typical Self-Managed deployments and
rarely need to be changed.

| Option           | Type     | Description                                                                                                            | Default                              |
|------------------|----------|------------------------------------------------------------------------------------------------------------------------|--------------------------------------|
| `endpoint`       | string   | OTLP/HTTP base URL for the analytics endpoint. The OTel SDK appends `/v1/logs` automatically.                          | `https://analytics.cloud.camunda.io` |
| `push-interval`  | duration | Maximum time between batch pushes, as an [ISO 8601 duration](https://en.wikipedia.org/wiki/ISO_8601#Durations).        | `PT5S`                               |
| `max-queue-size` | int      | Maximum number of log records buffered in memory before new records are dropped.                                       | `2048`                               |
| `max-batch-size` | int      | Maximum number of records sent in a single OTLP request. Must be less than or equal to `max-queue-size`.               | `512`                                |

## What data is exported

Each supported record is emitted as an [OpenTelemetry log record](https://opentelemetry.io/docs/specs/semconv/general/events/),
identified by the `event.name` attribute following the OTel Events semantic convention.

### Event types

| Source record                | Event name                 |
|------------------------------|----------------------------|
| `PROCESS_INSTANCE_CREATION`  | `process_instance_created` |

### Log record attributes

Per-event attributes attached to each log record:

| Attribute                          | Type   | Description                                                |
|------------------------------------|--------|------------------------------------------------------------|
| `event.name`                       | string | Event type identifier.                                     |
| `camunda.bpmn_process_id`          | string | BPMN process ID.                                           |
| `camunda.process_version`          | long   | Process definition version.                                |
| `camunda.process_definition_key`   | long   | Process definition key.                                    |
| `camunda.process_instance_key`     | long   | Process instance key.                                      |
| `camunda.tenant_id`                | string | Tenant ID, or the default tenant when not multi-tenant.    |
| `camunda.log.position`             | long   | Log stream position. Used as a deduplication key.          |

### Resource attributes

Cluster-wide attributes attached to every log record:

| Attribute              | Type   | Description             |
|------------------------|--------|-------------------------|
| `camunda.cluster.id`   | string | Cluster identifier.     |
| `camunda.partition.id` | long   | Partition ID.           |
| `service.name`         | string | Always `camunda-zeebe`. |

The exporter does **not** include process variables, payloads, message contents, job
variables, or any other end-user data. Only the process metadata listed above is exported.

## Failure behavior

The exporter is fire-and-forget: under any failure mode, broker throughput is unaffected
and analytics records may be dropped silently. Specifically, events can be lost when:

- **The in-memory queue is full.** When `max-queue-size` is reached — typically because
  the endpoint is slow or unreachable — new records are dropped on the broker thread
  without retry.
- **The broker crashes or restarts.** The in-memory queue is not persisted, so any records
  buffered at the time of the crash are lost.
- **The OTLP endpoint returns an error.** The exporter does not retry persistently and
  does not buffer to disk; the affected batch is dropped.

Because each event carries `camunda.cluster.id`, `camunda.partition.id`, and
`camunda.log.position`, downstream consumers can deduplicate events using the combination
of these attributes as a composite key.

## Known limitations

- **Analytics-grade only.** No exactly-once delivery, no reconciliation, no client-side
  gap filling. Use the exporter for product analytics and trends, not for billing, audit,
  or any workflow that requires complete data.
- **No PII.** Only process metadata is exported. Process variables, message payloads, and
  other potentially sensitive fields are never sent.
- **Fixed event set.** The exporter emits a small, hardcoded set of event types. There is
  no runtime configuration to add, remove, or filter event types.
- **Camunda endpoint only.** The exporter is intended for use with the Camunda analytics
  endpoint. While the `endpoint` option accepts any OTLP/HTTP URL, redirecting to a
  custom backend is not a supported deployment pattern.

## Install on older clusters (standalone JAR)

For clusters that do not ship the exporter as part of their distribution, a standalone
JAR will be provided. Drop the JAR onto the broker classpath (for example, into
`/usr/local/zeebe/exporters/`), point the exporter configuration at it with `jarPath`,
and provide the `CAMUNDA_LICENSE_KEY` and `ZEEBE_BROKER_CLUSTER_CLUSTERID` environment
variables described in [Prerequisites](#prerequisites).

## How it works

The exporter consumes records from the Zeebe log stream, filters for a small set of event
types, converts each matching record into an OpenTelemetry log record, and pushes it to
the configured OTLP/HTTP endpoint. Three design choices keep the broker fast and
predictable:

- **Fire-and-forget, non-blocking pipeline.** Every record's position is acknowledged
  eagerly and unconditionally, before any analytics processing happens. A background
  thread batches and pushes records through the OTel SDK's `BatchLogRecordProcessor`; when
  the in-memory queue fills, new records are silently dropped instead of back-pressuring
  the broker.
- **Partition-aware filtering.** Each event is emitted by exactly one partition — the one
  that originally produced it — so events are not duplicated across a multi-partition
  cluster. See `AnalyticsRecordFilter` for the filtering layers and the rationale behind
  partition-based deduplication.
- **Leader-only execution.** The exporter runs on the partition leader only, so no extra
  high-availability setup or cross-replica coordination is needed.

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

## Local development

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

Tests OTel pipeline contract: non-blocking when queue is full, unreachable endpoint
handling, event delivery, flush-on-shutdown, failure recovery, and post-shutdown safety.

### Integration tests (`AnalyticsExporterOtelIT`)

End-to-end tests using a real OTel Collector in Docker (Testcontainers). No mocking, no
overrides — uses the default `AnalyticsExporter` constructor with the production
`BatchLogRecordProcessor` and real OTLP/HTTP transport. Tests event delivery with
attribute verification and batching behavior.

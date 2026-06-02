# Camunda Analytics Exporter

Zeebe exporter that ships product analytics events to a Camunda analytics endpoint over
OTLP/HTTP. It is opt-in, default-off, and intended for Camunda 8 Self-Managed deployments.

The exporter is **analytics-grade**, not billing- or audit-grade: it is designed so it
cannot impact broker throughput, and it accepts data loss under failure. It runs only on
the partition leader, so no extra high-availability setup is required.

> **Data handling.** The exporter sends process metadata only. It does **not** export
> process variables, payloads, message bodies, or any other potentially sensitive data.

## Enable the exporter

The analytics exporter is disabled by default. To enable it, add an `analytics` exporter
declaration to your broker configuration; to disable it again, remove the declaration.
The configuration can be provided via YAML or as environment variables — both styles are
shown below.

The exact shape of the configuration depends on your Camunda version:

- **8.10 and later:** the exporter ships with Zeebe; you only need to declare it.
- **8.9 and earlier:** the exporter is not shipped, so you must install the
  [standalone JAR](#install-on-older-clusters-standalone-jar) and reference it via
  `jarPath`.

### Prerequisites

The exporter requires a Camunda license key and a cluster ID.

**License key.** The exporter authenticates to the Camunda analytics endpoint using your
Camunda 8 Self-Managed license key. The raw key is never sent over the network — it is
hashed into a fingerprint (sent as the `x-camunda-fingerprint` header) and used as the
HMAC secret for signing each batch of events. This is the same license key you already
use to run Camunda 8 Self-Managed; if you do not have it, contact your Camunda account
team or open a support ticket.

**Cluster ID.** The cluster ID identifies which cluster a given event came from. It is
attached to every event as the `camunda.cluster.id` resource attribute and is part of the
deduplication key used by the analytics backend (`camunda.cluster.id` +
`camunda.partition.id` + `camunda.log.position`). The value should be stable per cluster —
changing it makes existing events look like they come from a different cluster.

How the exporter obtains these values depends on your Camunda version:

- **Camunda 8.10 and later:** [cluster id](https://docs.camunda.io/docs/next/self-managed/components/orchestration-cluster/core-settings/configuration/properties/#cluster)
  and [license key](https://docs.camunda.io/docs/next/self-managed/components/orchestration-cluster/core-settings/configuration/properties/#licensing)
  values are resolved automatically from the broker context. No additional setup is needed.
- **Camunda 8.9 and earlier:** the broker does not expose the license key or cluster ID
  through the context API. Provide them via environment variables on every broker:
  - `CAMUNDA_LICENSE_KEY` — the Camunda license key.
  - `ZEEBE_BROKER_CLUSTER_CLUSTERID` — the cluster identifier. This is the broker's
    standard cluster-ID setting (`zeebe.broker.cluster.clusterId`); if it is already
    configured on the broker, the analytics exporter picks it up automatically.

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

|        Option        |   Type   |                                                                                              Description                                                                                              |               Default                |
|----------------------|----------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|--------------------------------------|
| `endpoint`           | string   | OTLP/HTTP base URL for the analytics endpoint. The OTel SDK appends `/v1/logs` automatically.                                                                                                         | `https://analytics.cloud.camunda.io` |
| `push-interval`      | duration | Maximum time between batch pushes, as an [ISO 8601 duration](https://en.wikipedia.org/wiki/ISO_8601#Durations).                                                                                       | `PT5M`                               |
| `heartbeat-interval` | duration | Interval between periodic heartbeat events carrying static cluster metadata. The first heartbeat is emitted synchronously on leader open, so a new leader sends one immediately on leadership change. | `PT10M`                              |
| `max-queue-size`     | int      | Maximum number of log records buffered in memory before new records are dropped.                                                                                                                      | `2048`                               |
| `max-batch-size`     | int      | Maximum number of records sent in a single OTLP request. Must be less than or equal to `max-queue-size`.                                                                                              | `512`                                |

## What data is exported

Each supported record is emitted as an [OpenTelemetry log record](https://opentelemetry.io/docs/specs/semconv/general/events/),
identified by the `event.name` attribute following the OTel Events semantic convention.

The exporter does **not** include process variables, payloads, message contents, job
variables, or any other end-user data.

### Event types

|        Source record        |       Intent        |          Event name          |                                                Notes                                                 |
|-----------------------------|---------------------|------------------------------|------------------------------------------------------------------------------------------------------|
| `PROCESS_INSTANCE_CREATION` | `CREATED`           | `process_instance_created`   | Emitted for every new process instance.                                                              |
| `PROCESS_INSTANCE`          | `ELEMENT_ACTIVATED` | `adhoc_subprocess_activated` | Emitted only when the activated element is an ad-hoc sub-process.                                    |
| `USAGE_METRIC`              | `EXPORTED`          | `usage_metric_exported`      | Emitted once per usage metric export interval. Internal reset events are skipped.                    |
| —                           | —                   | `heartbeat`                  | Emitted periodically by the partition leader (see `heartbeat-interval`); not tied to the log stream. |

### Common log record attributes

These attributes are set on every log record:

|         Attribute         |  Type  |                                               Description                                                |
|---------------------------|--------|----------------------------------------------------------------------------------------------------------|
| `event.name`              | string | Event type identifier (one of the names in the table above).                                             |
| `camunda.log.position`    | long   | Log stream position. Used as a deduplication key.                                                        |
| `camunda.sequence_number` | long   | Monotonic per-partition counter incremented for each emitted event. Used for ordering and gap detection. |

### Heartbeat attributes

The `heartbeat` event carries static cluster metadata instead of the common log/sequence
attributes (heartbeats are not tied to the log stream):

|         Attribute          |  Type  |                               Description                                |
|----------------------------|--------|--------------------------------------------------------------------------|
| `event.name`               | string | Always `heartbeat`.                                                      |
| `camunda.broker.version`   | string | Broker version (matches `io.camunda.zeebe.util.VersionUtil#getVersion`). |
| `camunda.exporter.version` | string | Analytics exporter version.                                              |
| `camunda.schema.version`   | string | Schema URL identifying the analytics payload shape.                      |

### Resource attributes

Cluster-wide attributes attached to every log record:

|       Attribute        |  Type  |       Description       |
|------------------------|--------|-------------------------|
| `camunda.cluster.id`   | string | Cluster identifier.     |
| `camunda.partition.id` | long   | Partition ID.           |
| `service.name`         | string | Always `camunda-zeebe`. |

## Failure behavior

The exporter is fire-and-forget: under any failure mode, broker throughput is unaffected
and analytics records may be dropped silently. Specifically, events can be lost when:

- **The in-memory queue is full.** When `max-queue-size` is reached — typically because
  the endpoint is slow or unreachable — new records are dropped on the broker thread
  without retry.
- **The broker crashes or restarts.** The in-memory queue is not persisted, so any records
  buffered at the time of the crash are lost.
- **The OTLP endpoint returns an error.** The exporter does not retry persistently and
  does not buffer to disk; the affected events are dropped.

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

## Install on older clusters (standalone JAR)

For clusters that do not ship the exporter as part of their distribution, a standalone
JAR will be provided. Drop the JAR onto the broker classpath (for example, into
`/usr/local/zeebe/exporters/`), point the exporter configuration at it with `jarPath`,
and provide the `CAMUNDA_LICENSE_KEY` and `ZEEBE_BROKER_CLUSTER_CLUSTERID` environment
variables described in [Prerequisites](#prerequisites).

For the general procedure for adding custom exporters to a broker, see
[installing Zeebe exporters](https://docs.camunda.io/docs/next/self-managed/components/orchestration-cluster/zeebe/exporters/install-zeebe-exporters/).

## How it works

The exporter consumes records from the Zeebe log stream, filters for a small set of event
types, converts each matching record into an OpenTelemetry log record, and pushes it to
the configured OTLP/HTTP endpoint. Three design choices keep the broker fast and
predictable:

- **Fire-and-forget, non-blocking pipeline.** A background thread batches and pushes
  records through the OTel SDK's `BatchLogRecordProcessor`; when the in-memory queue
  fills, new records are silently dropped instead of back-pressuring the broker. After
  invoking the handler, the broker unconditionally acknowledges the record's position —
  handler exceptions are caught and swallowed — so neither a failing handler nor a
  saturated queue can stall the broker.
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

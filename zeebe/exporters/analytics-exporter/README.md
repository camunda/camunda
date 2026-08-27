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
        args:
          endpoint: https://analytics.cloud.camunda.io
          push-interval: PT5M
          max-queue-size: 2048
          max-batch-size: 512
          sampling-rate: 1.0
          categories:
            - contractual
            - optional
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
          pushInterval: PT5M
          maxQueueSize: 2048
          maxBatchSize: 512
          samplingRate: 1.0
          categories:
            - contractual
            - optional
```

### Environment variables

The same settings can be provided via environment variables.

**Unified (8.9+):** `CAMUNDA_DATA_EXPORTERS_ANALYTICS_*`

```sh
CAMUNDA_DATA_EXPORTERS_ANALYTICS_CLASSNAME=io.camunda.exporter.analytics.AnalyticsExporter
CAMUNDA_DATA_EXPORTERS_ANALYTICS_ARGS_ENDPOINT=https://analytics.cloud.camunda.io
CAMUNDA_DATA_EXPORTERS_ANALYTICS_ARGS_PUSHINTERVAL=PT5M
CAMUNDA_DATA_EXPORTERS_ANALYTICS_ARGS_MAXQUEUESIZE=2048
CAMUNDA_DATA_EXPORTERS_ANALYTICS_ARGS_MAXBATCHSIZE=512
CAMUNDA_DATA_EXPORTERS_ANALYTICS_ARGS_CATEGORIES_0=contractual
CAMUNDA_DATA_EXPORTERS_ANALYTICS_ARGS_CATEGORIES_1=optional
```

**Legacy (8.8 and earlier):** `ZEEBE_BROKER_EXPORTERS_ANALYTICS_*`

```sh
ZEEBE_BROKER_EXPORTERS_ANALYTICS_CLASSNAME=io.camunda.exporter.analytics.AnalyticsExporter
ZEEBE_BROKER_EXPORTERS_ANALYTICS_ARGS_ENDPOINT=https://analytics.cloud.camunda.io
ZEEBE_BROKER_EXPORTERS_ANALYTICS_ARGS_PUSHINTERVAL=PT5M
ZEEBE_BROKER_EXPORTERS_ANALYTICS_ARGS_MAXQUEUESIZE=2048
ZEEBE_BROKER_EXPORTERS_ANALYTICS_ARGS_MAXBATCHSIZE=512
ZEEBE_BROKER_EXPORTERS_ANALYTICS_ARGS_CATEGORIES_0=contractual
ZEEBE_BROKER_EXPORTERS_ANALYTICS_ARGS_CATEGORIES_1=optional
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

|        Option        |   Type   |                                                                                                  Description                                                                                                  |               Default                |
|----------------------|----------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|--------------------------------------|
| `endpoint`           | string   | OTLP/HTTP base URL for the analytics endpoint. The OTel SDK appends `/v1/logs` automatically.                                                                                                                 | `https://analytics.cloud.camunda.io` |
| `push-interval`      | duration | Maximum time between batch pushes, as an [ISO 8601 duration](https://en.wikipedia.org/wiki/ISO_8601#Durations).                                                                                               | `PT5M`                               |
| `heartbeat-interval` | duration | Interval between periodic heartbeat events carrying static cluster metadata.                                                                                                                                  | `PT10M`                              |
| `max-queue-size`     | int      | Maximum number of log records buffered in memory before new records are dropped.                                                                                                                              | `2048`                               |
| `max-batch-size`     | int      | Maximum number of records sent in a single OTLP request. Must be less than or equal to `max-queue-size`.                                                                                                      | `512`                                |
| `sampling-rate`      | double   | Default sampling rate for log events, between 0.0 (none) and 1.0 (all). Handlers may declare a lower rate; the effective rate is always the minimum of the two.                                               | `1.0`                                |
| `categories`         | list     | List of analytics event categories to export. Valid values: `contractual` (commercial/licence metrics), `optional` (non-commercial product usage metrics). When omitted or empty, all categories are enabled. | `[contractual, optional]`            |

## What data is exported

Each supported record is emitted as an [OpenTelemetry log record](https://opentelemetry.io/docs/specs/semconv/general/events/),
identified by the `event.name` attribute following the OTel Events semantic convention.

The exporter does **not** include process variables, payloads, message contents, job
variables, or any other end-user data.

### Event types

|   Source record    |       Intent        |              Event name               |                                               Notes                                                |
|--------------------|---------------------|---------------------------------------|----------------------------------------------------------------------------------------------------|
| `PROCESS_INSTANCE` | `ELEMENT_ACTIVATED` | `camunda.process.instance.activated`  | Emitted when a root process element is activated, so it covers every start type.                   |
| `USER_TASK`        | `CREATED`           | `user_task_created`                   | Emitted for every new user task.                                                                   |
| `USER_TASK`        | `ASSIGNED`          | `camunda.user_task.assigned`          | Emitted for every user task assignment with a non-empty assignee.                                  |
| `TENANT`           | `CREATED`           | `camunda.tenant.created`              | Emitted for every new tenant.                                                                      |
| `TENANT`           | `DELETED`           | `camunda.tenant.deleted`              | Emitted for every deleted tenant.                                                                  |
| `INCIDENT`         | `CREATED`           | `camunda.process.incident.created`    | Emitted for every raised incident.                                                                 |
| `INCIDENT`         | `RESOLVED`          | `camunda.process.incident.resolved`   | Emitted for every resolved incident.                                                               |
| `PROCESS`          | `CREATED`           | `camunda.process.definition.created`  | Emitted once per process definition, so a deployment of several processes produces one event each. |
| `PROCESS`          | `DELETED`           | `camunda.process.definition.deleted`  | Emitted once per deleted process definition.                                                       |
| `DECISION`         | `CREATED`           | `camunda.decision.definition.created` | Emitted once per decision in a deployed decision requirements graph.                               |
| `DECISION`         | `DELETED`           | `camunda.decision.definition.deleted` | Emitted once per deleted decision definition.                                                      |
| `FORM`             | `CREATED`           | `camunda.form.definition.created`     | Emitted once per deployed form.                                                                    |
| `FORM`             | `DELETED`           | `camunda.form.definition.deleted`     | Emitted once per deleted form definition.                                                          |
| `AGENT_INSTANCE`   | `CREATED`           | `camunda.agent.instance.created`      | Emitted for every created agent instance.                                                          |
| `AGENT_INSTANCE`   | `COMPLETED`         | `camunda.agent.instance.completed`    | Emitted for every completed agent instance.                                                        |
| —                  | —                   | `heartbeat`                           | Emitted periodically by the partition leader (see `heartbeat-interval`).                           |

`user_task_created` and `heartbeat` predate the analytics data contract and still carry flat
snake_case names. Every other signal uses the canonical dotted contract name.

### Common log record attributes

These attributes are set on every log record:

|            Attribute            |  Type  |                                               Description                                                |
|---------------------------------|--------|----------------------------------------------------------------------------------------------------------|
| `event.name`                    | string | Event type identifier (one of the names in the table above).                                             |
| `camunda.log.position`          | long   | Log stream position. Used as a deduplication key.                                                        |
| `camunda.event.sequence_number` | long   | Monotonic per-partition counter incremented for each emitted event. Used for ordering and gap detection. |

### Per-event attributes

Beyond the common attributes above, each event type carries its own additional fields:

**`camunda.process.instance.activated`**

|              Attribute              |  Type  |                  Description                   |
|-------------------------------------|--------|------------------------------------------------|
| `camunda.process.id`                | string | BPMN process ID.                               |
| `camunda.process.version`           | long   | Deployed process version.                      |
| `camunda.process.definition_key`    | long   | Process definition key.                        |
| `camunda.process.instance_key`      | long   | Process instance key.                          |
| `camunda.process.root_instance_key` | long   | Root process instance key (for sub-processes). |
| `camunda.tenant.id`                 | string | Tenant ID.                                     |

The event is taken from the activation of the root process element, which is the single point every
process instance passes through however it was started: the client API, or a message, timer, signal
or conditional start event. Process instances started by a call activity are excluded, so the event
counts root instances only.

**`user_task_created`**

|            Attribute             |  Type  |            Description            |
|----------------------------------|--------|-----------------------------------|
| `camunda.process.id`             | string | BPMN process ID.                  |
| `camunda.process.definition_key` | long   | Process definition key.           |
| `camunda.process.instance_key`   | long   | Process instance key.             |
| `camunda.element.id`             | string | BPMN element ID of the user task. |
| `camunda.tenant.id`              | string | Tenant ID.                        |

Note: unlike `camunda.process.instance.activated`, this event does not carry
`camunda.process.version`.

**`camunda.user_task.assigned`**

|           Attribute            |  Type  |      Description      |
|--------------------------------|--------|-----------------------|
| `camunda.user_task.key`        | long   | User task key.        |
| `camunda.process.instance_key` | long   | Process instance key. |
| `camunda.tenant.id`            | string | Tenant ID.            |

No assignee-derived data (raw or hashed) is exported; the event only signals that an assignment
happened, giving a count of assignment events per cluster/process definition. Assignments with an
empty assignee produce no event, matching the engine's assignment guard.

**`camunda.tenant.created`**

|      Attribute      |  Type  | Description |
|---------------------|--------|-------------|
| `camunda.tenant.id` | string | Tenant ID.  |

The tenant name, description, and associated entity are deliberately not exported.

**`camunda.tenant.deleted`**

|      Attribute      |  Type  | Description |
|---------------------|--------|-------------|
| `camunda.tenant.id` | string | Tenant ID.  |

**`camunda.process.incident.created`** and **`camunda.process.incident.resolved`**

|            Attribute             |  Type  |               Description                |
|----------------------------------|--------|------------------------------------------|
| `camunda.incident.key`           | long   | Incident key, taken from the record key. |
| `camunda.process.id`             | string | BPMN process ID.                         |
| `camunda.process.definition_key` | long   | Process definition key.                  |
| `camunda.process.instance_key`   | long   | Process instance key.                    |
| `camunda.tenant.id`              | string | Tenant ID.                               |

Both events carry the same attributes, so time-to-resolution is a join on
`camunda.incident.key`. The incident error message is deliberately not exported: it can
quote expressions and variable values.

**`camunda.process.definition.created`** and **`camunda.process.definition.deleted`**

|            Attribute             |  Type  |       Description       |
|----------------------------------|--------|-------------------------|
| `camunda.process.id`             | string | BPMN process ID.        |
| `camunda.process.version`        | long   | Process version.        |
| `camunda.process.definition_key` | long   | Process definition key. |
| `camunda.tenant.id`              | string | Tenant ID.              |

The BPMN resource, resource name, and version tag are deliberately not exported.

**`camunda.decision.definition.created`** and **`camunda.decision.definition.deleted`**

|         Attribute          |  Type  |        Description        |
|----------------------------|--------|---------------------------|
| `camunda.decision.id`      | string | Decision ID from the DMN. |
| `camunda.decision.key`     | long   | Decision key.             |
| `camunda.decision.version` | long   | Decision version.         |
| `camunda.tenant.id`        | string | Tenant ID.                |

The decision name and version tag are deliberately not exported.

**`camunda.form.definition.created`** and **`camunda.form.definition.deleted`**

|       Attribute        |  Type  |  Description  |
|------------------------|--------|---------------|
| `camunda.form.id`      | string | Form ID.      |
| `camunda.form.key`     | long   | Form key.     |
| `camunda.form.version` | long   | Form version. |
| `camunda.tenant.id`    | string | Tenant ID.    |

The form resource, resource name, and version tag are deliberately not exported.

**`camunda.agent.instance.created`** and **`camunda.agent.instance.completed`**

|              Attribute              |  Type  |                       Description                        |
|-------------------------------------|--------|----------------------------------------------------------|
| `camunda.agent.instance_key`        | long   | Agent instance key.                                      |
| `camunda.agent.definition_key`      | long   | Agent definition key.                                    |
| `camunda.agent.status`              | string | Agent instance status, e.g. `INITIALIZING`, `COMPLETED`. |
| `camunda.process.id`                | string | BPMN process ID.                                         |
| `camunda.process.definition_key`    | long   | Process definition key.                                  |
| `camunda.process.instance_key`      | long   | Process instance key.                                    |
| `camunda.process.root_instance_key` | long   | Root process instance key (for sub-processes).           |
| `camunda.tenant.id`                 | string | Tenant ID.                                               |

Both events carry the same attributes, so agent run duration is a join on
`camunda.agent.instance_key`. The agent definition (model, provider, system prompt), its
tools, its collected metrics such as token counts, its configured limits, the changed
attribute names, and the version tag are all deliberately not exported.

### Pre-aggregated counters

Alongside the events above, the exporter ships delta counters over OTLP metrics. Each counter
is incremented once per source record and carries the dimensions listed below.

|                Counter                |            Source record            |     Dimensions      |
|---------------------------------------|-------------------------------------|---------------------|
| `camunda.decision.instance.evaluated` | `DECISION_EVALUATION` / `EVALUATED` | `camunda.tenant.id` |

`camunda.decision.instance.evaluated` counts evaluation records, not the decisions inside them:
a decision that requires sub-decisions still counts once, and failed evaluations are not counted,
which is the same counting rule as the `EDI` usage metric. It is the authoritative per-tenant EDI
source the exporter ships; the engine's aggregated usage metric is no longer forwarded. The counter
also takes every `EVALUATED` record, whereas only version 2 of that record feeds `EDI` (version 1 is
applied as a no-op), so records written by a broker older than 8.8
would count here but not there — that is outside the supported upgrade sources for 8.10, where the
two agree.

### Heartbeat attributes

The `heartbeat` event carries static cluster metadata instead of the common log/sequence
attributes (heartbeats are not tied to the log stream):

|              Attribute               |  Type  |                               Description                                |
|--------------------------------------|--------|--------------------------------------------------------------------------|
| `event.name`                         | string | Always `heartbeat`.                                                      |
| `camunda.heartbeat.broker_version`   | string | Broker version (matches `io.camunda.zeebe.util.VersionUtil#getVersion`). |
| `camunda.heartbeat.exporter_version` | string | Analytics exporter version.                                              |

The analytics schema URL (`https://camunda.io/schemas/analytics/v1`) is delivered automatically via
the OTel instrumentation scope on every record, not as a per-record attribute.

### Resource attributes

Attached once per exporter instance to every log record, metric point, and heartbeat — not
per-record fields:

|          Attribute           |  Type  |                                                                                                                                                         Description                                                                                                                                                         |
|------------------------------|--------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `camunda.cluster.id`         | string | Cluster identifier.                                                                                                                                                                                                                                                                                                         |
| `camunda.partition.id`       | long   | Partition ID.                                                                                                                                                                                                                                                                                                               |
| `service.name`               | string | Always `camunda-zeebe`.                                                                                                                                                                                                                                                                                                     |
| `camunda.tenant.physical_id` | string | Physical-tenant id of the broker/exporter instance that produced the signal. Static for the lifetime of the exporter instance (one instance runs per physical-tenant per partition) — unlike `camunda.tenant.id` (see per-event attributes below), which is the logical tenant of the specific record and varies per event. |

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
- **Fixed event set per category.** The exporter emits a small, hardcoded set of event
  types. The `categories` option controls which categories of events are exported, but
  individual event types within a category cannot be toggled independently.

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

### Microbenchmarks (`AnalyticsExporterBenchmark`)

JMH benchmarks measuring per-record overhead across the main hot paths. Run manually only — not
wired into CI.

```bash
./mvnw verify -pl zeebe/exporters/analytics-exporter \
    -Dtest=AnalyticsExporterBenchmark -DskipTests=false -Dbenchmark=true
```

To profile, add `-Dbenchmark.profiler=jfr+gc`:

```bash
./mvnw verify -pl zeebe/exporters/analytics-exporter \
    -Dtest=AnalyticsExporterBenchmark -DskipTests=false \
    -Dbenchmark=true -Dbenchmark.profiler=jfr+gc
```

JMH writes the `.jfr` file to `target/jmh-jfr/`. Open it in IntelliJ Ultimate
("Open Profiler Results") or JDK Mission Control.

# Analytics Exporter — Agent Instructions

> Full design context: [APOLLO Engineering Doc](https://docs.google.com/document/d/1QG3snL1Va_PeVuO4gU0YGwVpyEkvbxUtxTPCy2Ouulg)
> Epic: camunda/product-hub#3247 | Phase 1: camunda/camunda#52362

## Hard Invariants

These are non-negotiable. Every change must preserve them.

- **Never impact broker throughput.** The exporter is fire-and-forget. Position is updated
  eagerly BEFORE analytics processing. No blocking, no backpressure, no disk I/O on the
  actor thread. If the endpoint is slow or down, drop data.
- **No silent double-counting.** Server must always detect overlaps via position ranges +
  sequence numbers. Overcounting = overcharging customers. Every push carries enough
  metadata for server-side dedup.
- **No PII.** Process metadata only. Never export process variables, payloads, message
  bodies, BPMN XML content, or any end-user data.
- **Analytics-grade.** Some data loss is accepted. No exactly-once delivery. Loss on crash
  = entire in-memory buffer. This is by design, not a bug.
- **Default off.** Exporter only runs when explicitly declared in broker config.
- **TLS mandatory.** http:// endpoints rejected in config validation (except localhost).

## What NOT To Do

- Don't add retry logic that could stall the actor thread
- Don't write to disk from the export path
- Don't add new fields that could contain customer/user data without legal/GDPR review
- Don't change position acknowledgment to be conditional on analytics delivery
- Don't introduce cross-thread synchronization that blocks the actor thread
- Don't bypass the RecordFilter — it prevents unnecessary deserialization
- Don't add dependencies without checking standalone JAR shading impact (~3MB OTel SDK already)

## Confirmed Design Decisions

| # |                   Decision                    |                                                         Key point                                                         |
|---|-----------------------------------------------|---------------------------------------------------------------------------------------------------------------------------|
| 1 | Analytics-grade data                          | Loss-tolerant. Never block broker for analytics.                                                                          |
| 2 | Solution 2b — in-JVM fire-and-forget          | No sidecar, no disk buffer, no secondary storage dependency.                                                              |
| 3 | OTLP/HTTP wire protocol                       | OTel Logs for events (/v1/logs), OTel Metrics for counters (/v1/metrics). Protobuf preferred.                             |
| 4 | License-derived HMAC auth                     | Zero-config for SM. Fingerprint (SHA256) + HMAC signature per request. License never leaves cluster.                      |
| 5 | Gap detection via sequence numbers (Option C) | Contiguous `camunda.event.sequence_number` per partition. Missing seq = exact loss count. ~10 lines, no custom processor. |
| 6 | Pre-aggregated delta counters                 | Ship OTel Metrics alongside raw events. Companion gauge `camunda.metric.export_window` carries dedup/timing metadata.     |

## Technical Setup

### Pipeline

```
Replicated log
  → AnalyticsRecordFilter (4 layers: record type → value type → intent → partition ownership)
  → HandlerRegistry routes (ValueType, Intent) → specific handler
  → OTel SDK:
      Logs:    BatchLogRecordProcessor (bounded queue) → OtlpHttpLogRecordExporter → /v1/logs
      Metrics: ManualMetricReader (flushed on partition thread) → OtlpHttpMetricExporter → /v1/metrics
```

### Key Components

- **AnalyticsExporter** — Lifecycle, handler wiring, metadata persistence
- **AnalyticsRecordFilter** — Filters before deserialization (implements RecordFilter)
- **HandlerRegistry** — EnumMap<ValueType> → HashMap<Intent, Handler> routing
- **OtelSdkManager** — OTel SDK lifecycle, event emission, metric flush, auth headers
- **ManualMetricReader** — Custom MetricReader; collection triggered from partition thread (no cross-thread races)
- **AnalyticsExporterMetadata** — Persisted state: eventSequenceNumber, metricSequenceNumber

### Current Event Handlers

|         ValueType         |      Intent       |            Handler             |          event.name          |                       Extra filter                        |
|---------------------------|-------------------|--------------------------------|------------------------------|-----------------------------------------------------------|
| PROCESS_INSTANCE_CREATION | CREATED           | ProcessInstanceCreationHandler | `process_instance_created`   | —                                                         |
| PROCESS_INSTANCE          | ELEMENT_ACTIVATED | AdHocSubProcessHandler         | `adhoc_subprocess_activated` | bpmnElementType == AD_HOC_SUB_PROCESS                     |
| USAGE_METRIC              | EXPORTED          | UsageMetricHandler             | `usage_metric_exported`      | skips EventType.NONE resets                               |
| —                         | —                 | (scheduled)                    | `heartbeat`                  | every heartbeatInterval, carries broker/exporter versions |

### Adding a New Event Handler

1. Create handler class implementing `AnalyticsHandler`
2. Register in `AnalyticsExporter.configure()` with `handlerRegistry.register(valueType, intent, handler)`
3. Filter is auto-updated — HandlerRegistry builds the RecordFilter from registered handlers
4. If pre-aggregated metric needed: call `otelSdkManager.incrementMetric()` from handler
5. Run existing tests + add handler-specific test cases

### Attribute Naming

All attributes use **snake_case, dot-separated** following OTel semantic conventions:
- `camunda.process.id` (not `camunda.bpmnProcessId`)
- `camunda.process.definition_key` (not `camunda.processDefinitionKey`)
- `camunda.event.sequence_number`, `camunda.log.position`, `camunda.tenant.id`

Namespace all custom attributes with `camunda.` prefix. Use `event.name` (OTel standard, no prefix).

### Auth Headers (current implementation)

Static (set once): `x-camunda-fingerprint`, `x-camunda-cluster-id`
Dynamic (per request, when signing=true): `x-camunda-timestamp`, `x-camunda-signature`
Signature: HMAC-SHA256(licenseKey, `fingerprint|clusterId|timestamp`)

### Sequencing & Metadata

- `eventSequenceNumber` — incremented per emitted log event, persisted in exporter state
- `metricSequenceNumber` — incremented per metric flush, persisted in exporter state
- Both survive restart. Enable server-side gap detection (missing seq = lost data) and dedup.

### Pre-Aggregated Metrics

- Counter `camunda.process_instance.created` with delta temporality (resets each flush)
- Companion gauge `camunda.metric.export_window` per flush carries:
  - `camunda.metric.sequence_number` — contiguous per flush, gap = lost push
  - `camunda.log.position_start/end` — for replay/overlap detection
  - `camunda.event.time_min/max` — real event timestamps (CRITICAL: ≠ export time after replay)
- **Replay detection:** new push `position_start` ≤ any ingested `position_end` → overlap → discard

### Event Time vs Export Time

OTel metric timestamps = flush time, not event time. After broker restart/replay these diverge
by hours. Pipeline MUST use `event.time_min/max` from export_window gauge for time bucketing.

### Replay Constraint

During broker replay, the exporter re-processes records from its last persisted position.
Sequence numbers resume from persisted state (no reset). Any change to sequencing logic must
guarantee deterministic replay — same records must produce same sequence numbers regardless
of whether they arrive during normal operation or replay.

### Sampling

Hash-based deterministic sampling in `OtelSdkManager.logEvent()`. See `docs/sampling-explainer.md`.

- `HashSampler.shouldSample(position, rate)` — pure function of log position, no state
- `logEvent(name, pos, samplingRate, builder)` overload for handlers that want sub-100% rate
- `logEvent(name, pos, builder)` defaults to MAX (no sampling) — existing handlers unchanged
- OtelSdkManager computes `min(defaultRate, samplingRate)` where defaultRate comes from config
- `camunda.event.sample_rate` attribute emitted only when rate < 1.0
- Sequence numbers only increment for sampled events
- Sampling does NOT apply to pre-aggregated metrics (incrementMetric)

**Adding a sampled handler:** call `logEvent(name, pos, 0.01, builder)` instead of the
3-arg overload. That's it — no config, no registry changes, no interface changes.

### Config Defaults

|     Property      |               Default                |
|-------------------|--------------------------------------|
| endpoint          | `https://analytics.cloud.camunda.io` |
| maxQueueSize      | 2048                                 |
| maxBatchSize      | 512 (must be ≤ maxQueueSize)         |
| pushInterval      | PT5M                                 |
| heartbeatInterval | PT10M                                |
| signing           | true                                 |
| samplingRate      | 1.0 (no sampling)                    |

## Testing

- **Unit tests** (`AnalyticsExporterTest`) — handler routing, attributes, config validation, error resilience
- **SDK tests** (`OtelSdkManagerTest`) — OTel pipeline contract: non-blocking queue, failure recovery
- **Integration tests** (`AnalyticsExporterOtelIT`) — real OTel Collector in Docker, end-to-end
- **Benchmarks** (`AnalyticsExporterBenchmark`, `HashSamplerBenchmark`) — JMH, manual only, not in CI

Always run unit + integration tests before submitting changes to this module.

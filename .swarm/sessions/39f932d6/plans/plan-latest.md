# Plan

**Timestamp:** 2026-03-31T12:00:40.656Z

## Original Request

# Value Proposition Statement

Enable organisations to reliably, securely, and in near-real time, export process and task events from process, task, and incident events from Camunda 8 (SaaS and Self-Managed) to external systems. This empowers seamless audit logging, analytics, monitoring, and BI integration.

# User Problem

Customers across industries rely on event streams from their process automation platform to power audit logging, analytics, BI, monitoring, and integration with external systems. In Camunda 7, users could leverage plugins and listeners to export process and task events to event buses or data warehouses.

In Camunda 8 Self-Managed, community exporters (such as the Zeebe Kafka Exporter) have filled this gap, but these are not officially supported, may break with future releases, and are not available in SaaS. In Camunda 8 SaaS, there is currently no supported way to export or stream process, task, or incident events to external systems. This forces customers to implement complex, error-prone polling solutions or forego migration to SaaS altogether.

The lack of a robust, supported event export mechanism blocks critical use cases such as audit compliance, real-time monitoring, process mining, and integration with BI tools, and is a major migration blocker for many enterprise customers.

# Release Notes

Camunda 8 now delivers a natively supported, configurable, high-throughput event export feature for both SaaS and Self-Managed deployments. Users can stream process, task, and incident events to Kafka. The solution is reliable, feature-rich, and non-blocking, supporting granular selection of event types and targets. This functionality enables smooth C7-to-C8 migrations and empowers integrations for compliance, analytics, monitoring, BI, and long-term storage.

# User Stories

1. As a developer, I can configure Camunda 8 (SaaS or Self-Managed) to export events to Kafka, so I can integrate with my analytics, other applications, or data warehouse systems.
2. As an operations engineer, I can flexibly select which event types and which target endpoints receive event exports, so my data pipelines stay efficient and manageable.
3. As a data architect, I want the event export to be reliable, high-throughput, and non-blocking, so it does not impact process execution or system performance.
4. As a support engineer, I want to avoid maintaining custom exporters or polling solutions that may break with platform upgrades, so I can reduce operational risk and maintenance effort.


# Implementation Notes

- Scope: Deliver a robust, documented, and officially supported process event export\/streaming capability in both Camunda 8 SaaS and Self-Managed for Kafka.
- Event Types: Support export of any event that is produced by zeebe - including processes, decisions, user tasks, identity-related events, and audit log information.
- Extensibility: Design to enable the addition of future event types or export destinations; Consider that in the next step we want to enable Azure Event Hub support as well. Build the solution in a way that it is easily extensible to other Event Hubs; architecture should make it feasible to expand to Azure Event Hub, AWS SQS \/ SNS, AMQ, Google Pub\/Sub, or others via configuration in the future
- Configuration: Allow mapping\/filtering of event types to endpoints; support for selection by event type, granularity, and endpoint topic.
- Reliability: Ensure the export mechanism is high-throughput, reliable, and does not block or degrade workflow execution.
- Docs: Provide clear documentation for pushing to Confluent \/ IBM Kafka SaaS \/ Self-Managed \/ hybrid (Camunda SaaS + Customer SM Kafka).
- Security & Compliance: Ensure secure transmission of event data, with access controls and auditability.
- Extensibility: Design the exporter to support future event types and integration patterns as customer needs evolve.

## Research Context

### Analyze Camunda 8 Event System and Existing Exporter Framework

Here's the comprehensive analysis, saved to your session workspace:

## Summary of Findings

### 1. Event Types
**~66 ValueTypes** across 17 domain groups: process execution (7), task/job (4), messaging (7), timers (3), deployment (6), DMN (3), variables (1), errors (2), identity (7), batch ops (6), operational (7), and more. Each uses typed `Intent` enums with explicit wire values.

### 2. Exporter SPI
5-phase lifecycle: `configure → open → export → close → purge`. Two-phase record filtering (metadata-only fast pass + full record). Position acknowledgment drives log compaction (slowest exporter blocks all).

### 3. Exporter Implementations
| Exporter | Pattern | Scope |
|----------|---------|-------|
| **Elasticsearch** | Monolithic bulk-index with version-aware serialization | All ~30 ValueTypes |
| **RDBMS** | Multi-handler dispatch (`RdbmsExportHandler<T>`) with partition-aware registration | All ValueTypes via handlers |
| **App-Integrations** | HTTP webhook with batching + Failsafe retry | Only 4 UserTask events |
| **Camunda (unified)** | ~60 `ExportHandler` implementations with entity dedup | ES/OS targeting |

### 4. Processor/Applier Pattern
- **Processors** handle COMMANDs → validate + produce EVENTs (never mutate state)
- **Appliers** handle EVENTs → update RocksDB state (versioned, frozen after release, ~211 registered)
- Same appliers used for processing AND replay (deterministic recovery)

### 5. Key Limitations
- **Single-threaded dispatch** — slow exporter blocks all others on partition
- **No backpressure** from exporters to engine
- **No canonical event model** — each exporter independently maps raw records
- **Indefinite retry** with no circuit breaker or DLQ
- **Applier version proliferation** — historical versions kept forever for replay

Full document: `files/camunda8-event-model-and-exporter-architecture.md`

---

### Evaluate Existing Zeebe Kafka Exporter Community Solution

Here's the research summary:

## Community Zeebe Kafka Exporter — Key Findings

**Status**: Unmaintained since Feb 2022. Last works with Zeebe 1.x. Completely incompatible with Camunda 8.x.

### Feature Matrix Highlights
- Exports all `ValueType` records as JSON to configurable Kafka topics
- Per-ValueType topic routing with defaults + override pattern
- Custom partitioner preserving Zeebe causal ordering (`zeebePartitionId % kafkaPartitionsCount`)
- Kafka transactions for ordering guarantees (at-least-once delivery)
- Pass-through Kafka producer properties for auth/SSL/compression
- Published serde module for consumer-side deserialization

### Top Limitations
1. **Breaks on every Zeebe major version** — tightly coupled to protocol
2. **No Kafka headers** — consumers must deserialize to filter
3. **No multi-tenancy, variable filtering, or process filtering**
4. **JSON only** — no Avro/Protobuf/schema evolution
5. **No error recovery** — unrecoverable errors require broker restart
6. **No metrics/observability**

### Patterns Worth Preserving
- `defaults` + per-ValueType override config pattern
- Pass-through `producer.config` for arbitrary Kafka properties
- `partitionId-position` message key for deduplication
- Custom partitioner preserving Zeebe partition ordering
- Batch + flush interval dual-trigger flushing

Full report saved to `files/kafka-exporter-research.md` in session workspace.

---

### Study Extensible Multi-Destination Message Queue Architecture Patterns

The comprehensive architecture document has been created. Here's a summary of the three deliverables (all in one document):

### 📄 Deliverable: `files/extensible-event-export-architecture.md`

**1. Architecture Patterns** (Sections 2–3)
- **Strategy, Factory, SPI patterns** analyzed with concrete Zeebe references (`Transport<T>`, `ExporterFactory`, `BackupStore`)
- **Industry systems** compared: Kafka Connect (Connector/Task/Converter/SMT), Logstash (Input→Filter→Output+Codec), Apache Beam (SplittableDoFn/ParDo)
- **Recommendation:** Hybrid sealed-interface factory for built-in providers + SPI fallback for third-party plugins

**2. Extensibility Strategies** (Section 6)
- Plugin lifecycle: Discovery → Validation → Init → Runtime → Shutdown
- Declarative YAML config with sealed `TransportConfig` hierarchy
- Pluggable serializers (JSON/Avro/Protobuf), sealed exception hierarchy
- Subscription-based batching with size/bytes/time flush triggers
- Contract test kit pattern (from `BackupStoreTestKit`)

**3. Provider-Agnostic Event Model** (Sections 4–5)
- **CloudEvents v1.0.2 envelope** with Zeebe-specific extensions (`partitionid`, `position`, `key`, `intent`, `valuetype`)
- **Three-layer architecture:** Domain Event → CloudEvents Envelope → Protocol Binding
- **Event type taxonomy:** `io.camunda.zeebe.{valuetype}.{intent}`
- **Routing descriptor:** provider-agnostic destination + partition key + headers, resolved by a chain of routing rules
- Protocol binding mappings for Kafka, RabbitMQ, Pulsar, NATS, SQS, HTTP

---

### Analyze Kafka Integration Requirements and Producer Patterns

The Kafka integration patterns document has been created at `files/kafka-integration-patterns.md`. Here's a summary of the three deliverables:

### 1. Kafka Integration Patterns Document
Covers all five requested areas across ~700 lines:

- **Producer reliability modes** — detailed `acks`/retry/idempotence/transactional config for at-most-once, at-least-once, and exactly-once, including internal PID/sequence-number mechanics and the two-phase commit protocol
- **Partitioning & routing** — `murmur2` key hashing, hot-partition mitigation, custom partitioner example, header patterns (tracing, schema-id, idempotency-key, dead-letter-reason)
- **Backpressure** — producer two-thread model, `buffer.memory`/`max.block.ms` mechanics, three non-blocking patterns (callback, bounded semaphore, reactive), and pitfalls to avoid
- **Schema registry & serialization** — Avro/Protobuf/JSON Schema comparison table, evolution rules, CI/CD registration pipeline, event envelope pattern
- **Security** — TLS 1.3 config (JKS and PEM), four SASL mechanisms (PLAIN, SCRAM-512, OAUTHBEARER, GSSAPI), ACL best practices, encryption-at-rest options, hardening checklist

### 2. Reliability Guarantees Analysis
End-to-end matrix comparing all three modes across 7 failure scenarios, a decision flowchart, and a production-ready configuration template (`min.insync.replicas=2`, `replication.factor=3`, manual offset commit).

### 3. Serialization Recommendations
Decision matrix by use case, with the enterprise default of **Avro + Schema Registry** (BACKWARD compat, CI-registered schemas, `auto.register.schemas=false`), Protobuf for gRPC-heavy orgs, and JSON Schema only for low-throughput/debug topics.

---

### Research Azure Event Hub, AWS SQS/SNS, and Google Pub/Sub Integration Patterns

The comparative analysis is saved to `files/event-hub-platform-comparison.md`. Here's the executive summary:

## Key Findings

**Common patterns across all three platforms:**
- At-least-once delivery as baseline (idempotent consumers required)
- Dead-letter mechanisms, batching, SDK + REST access, IAM auth, key-value attributes
- A "routing key" concept (partition key / message group / ordering key) for ordered delivery

**Critical divergences:**

| Concern | Azure EH | AWS SQS/SNS | Google Pub/Sub |
|---------|----------|-------------|----------------|
| Model | Partitioned log | Queue + push fan-out | Per-message pub/sub |
| Push/Pull | Pull only | Pull (SQS) + Push (SNS) | Both |
| Replay | ✅ | ❌ | ✅ |
| Exactly-once | ❌ | FIFO only | Per-subscription |
| Parallelism | Partition-bounded | Unlimited pollers | Per-message unbounded |

**Extensibility implications:**
1. **Pull-based consumption is the universal common denominator** (push is platform-specific)
2. Ordering must be abstracted as a "routing key" with wildly different cardinality semantics (32 partitions vs. billions of ordering keys)
3. Config needs a **shared base** (connection, batching, retry, DLQ) plus **platform-specific extensions** (partition count, visibility timeout, ordering keys)
4. Replay/seek is a **capability flag**, not a universal contract — SQS cannot replay
5. Delivery guarantees should be an opt-in enum: `AT_LEAST_ONCE` | `EXACTLY_ONCE` | `ORDERED`

---

### Review Camunda 7 Event Export and Listener Mechanisms

The full analysis is saved to `files/c7-event-model-analysis.md`. Here's the summary:

---

## C7 Event Model — Key Findings

### Listener Types
C7 had **3 listener mechanisms**, all synchronous and in-process:
1. **Execution Listeners** — `start`/`end`/`take` on any flow node or sequence flow
2. **Task Listeners** — `create`/`assignment`/`update`/`complete`/`delete`/`timeout` on user tasks
3. **History Event Handlers** — every state change, controlled by history level (`NONE` → `FULL`)

### Plugin Extension Model
`ProcessEnginePlugin` SPI let users inject custom `HistoryEventHandler` instances (e.g., Kafka producers), global parse listeners, and incident handlers — all running **in the same JVM and transaction**.

### Critical Pain Points from C7
| Issue | Impact |
|-------|--------|
| **Synchronous blocking** | Slow exports (HTTP/Kafka) blocked engine threads |
| **Transaction coupling** | Export failure rolled back business state |
| **No SaaS story** | Plugin model required JVM co-location |
| **Upgrade fragility** | Community plugins broke across versions |
| **Variable explosion** | `FULL` history level generated massive volumes with no filtering |
| **Missing auth context** | No consistent actor identity on events |

### C7 → C8 Mapping
- **Execution/Task Listeners** → C8 has job-based equivalents (async, non-blocking). C8 task listeners can additionally **deny** operations.
- **HistoryEventHandler** → Replaced by the **Zeebe Exporter SPI** (reads committed log, decoupled from processing).
- **ProcessEnginePlugin** → No direct equivalent. C8 uses exporters + Global Listeners + job workers.
- **Missing in C8**: no `take` event on sequence flows; documented gap for migrants.

### Migration Recommendations for Epic 3431
C8's exporter architecture already solves the core C7 pain points. The Kafka export design should ensure: configurable per-event-type/per-topic granularity (replacing C7 history levels), variable name/type filtering, documented eventual-consistency semantics, and partition-ordering documentation.

## Refined Requirements

**

---

## Structured Requirements Summary

### Problem Statement
Camunda 8 lacks a supported mechanism to export engine events to Kafka. The community Zeebe Kafka Exporter is unmaintained (last compatible with Zeebe 1.x) and unavailable in SaaS. Customers need reliable event streaming for audit logging, analytics, monitoring, BI integration, and C7→C8 migration.

### Scope
- **Phase 1**: Camunda 8 Self-Managed, targeting both SM Kafka and Confluent Cloud
- **Phase 2** (later): Camunda 8 SaaS platform; Azure Event Hub; Avro serialization
- **Target release**: 8.10

### Technical Requirements

| Concern | Decision |
|---------|----------|
| Architecture | Built-in exporter implementing `Exporter` SPI, following Camunda Exporter pattern |
| Serialization | JSON (phase 1) |
| Envelope | CloudEvents v1.0.2 with Zeebe-specific extensions |
| Delivery | At-least-once |
| Filtering | RecordType + ValueType + Intent, all configurable. Defaults: EVENT records, all ValueTypes, all Intents |
| Topic routing | Configurable default topic + per-ValueType overrides |
| Partitioning | Runs on every partition; partition-1-only registration for deployment-scoped record types |
| Extensibility | Designed for later extraction; architecture supports future destinations (Azure EH, SQS, Pub/Sub) |
| Exporter decoupling | Kafka exporter failure must NOT block other exporters; each exporter proceeds independently |

### Acceptance Criteria
1. Users can configure a Kafka exporter in broker YAML to stream events to SM Kafka or Confluent Cloud
2. All ~66 Zeebe ValueTypes are exportable; users can filter by RecordType, ValueType, and Intent
3. Events are serialized as CloudEvents v1.0.2 JSON with Zeebe extensions (partitionId, position, key, intent, valueType)
4. Per-ValueType topic routing with a configurable default topic
5. At-least-once delivery with `partitionId-position` message keys for consumer deduplication
6. Kafka exporter failure does not block ES/RDBMS/Camunda exporters
7. Partition-aware handler registration (some types partition-1 only)
8. Pass-through Kafka producer properties for auth (SASL/SSL), compression, tuning
9. Migration documentation from community Zeebe Kafka Exporter
10. Batched flushing with configurable size/time thresholds

### Edge Cases
- Kafka broker unavailable at startup → exporter retries without blocking engine
- Kafka broker becomes unavailable during operation → buffers with backpressure, does not block other exporters
- Record too large for Kafka message → log warning, skip or send to DLQ (configurable)
- Mixed broker versions during rolling upgrade → version-aware serialization (following Camunda Exporter pattern)
- Confluent Cloud auth (SASL_SSL/PLAIN or OAUTHBEARER) → pass-through producer config

### Out of Scope (Phase 1)
- Camunda 8 SaaS platform deployment
- Azure Event Hub, AWS SQS/SNS, Google Pub/Sub
- Avro/Protobuf serialization and Schema Registry
- Content-level filtering (by process ID, tenant, variable name)
- Exactly-once delivery semantics
- Web UI for exporter configuration

## Engineering Decisions

Here is the complete revised section:

---

### Engineering Decisions (Confirmed by Codebase)

1. **Module location**: `zeebe/exporters/kafka-exporter/` — follows the established pattern alongside `app-integrations-exporter`, `camunda-exporter`, etc. Register in `zeebe/pom.xml`.

2. **Exporter SPI**: Implement `Exporter` interface with `configure()` → `open()` → `export()` → `close()`. Use `context.getConfiguration().instantiate(KafkaExporterConfiguration.class)` for config hydration.

3. **Filtering approach**: Implement `Context.RecordFilter` directly (like `CamundaExporterRecordFilter`), using Phase 1 metadata filtering only — `acceptType()` and `acceptValue()`. No `acceptIntent()` override (accept all intents; this matches `CamundaExporterRecordFilter` which also does not implement intent-level filtering). Intent-level filtering is deferred to a future phase if demand arises. Configuration uses a Set-based allowlist of `ValueType` names, with a sensible default set matching the Camunda exporter's 33 value types. Note: this configurable-Set approach is a **new pattern** in this repo — existing exporters either use per-ValueType boolean toggles (ES/OS) or a hardcoded `Set.of(...)` (Camunda exporter). The allowlist approach is chosen because it is concise for Kafka's config-map-based ergonomics and avoids dozens of boolean properties in the YAML args.

4. **Serialization**: Use a custom `ObjectMapper` with Jackson MixIns, following the proven ES/OS exporter pattern (`BulkIndexRequest.java`). This provides explicit field-level control over serialized output — specifically, strip `authorizations` and `agent` fields from `Record<?>` via `@JsonIgnoreProperties` MixIns, and enable `Feature.ALLOW_SINGLE_QUOTES`. Do **not** use `ZeebeProtocolModule` from `zeebe/protocol-jackson` — while simpler, it is designed for deserialization round-tripping (used by protocol tests and search indexing), not for controlling exporter output shape. The MixIn approach is the battle-tested pattern across all production exporters in this repo.

   **Version-aware dual-mapper**: Intentionally **deferred / not included** in Phase 1. The ES/OS `PREVIOUS_VERSION_MAPPER` exists because Elasticsearch/OpenSearch index templates are rigid — a field present in a new-version record but absent from the index mapping causes indexing failures during rolling upgrades. Kafka consumers are schema-flexible (they deserialize JSON however they choose), so unknown fields are harmless. A single `ObjectMapper` is sufficient. If consumers later require strict schema compatibility, a version-aware mapper can be added without breaking changes.

5. **Batching and flush strategy**: Use Kafka's native producer batching (`linger.ms`, `batch.size`, `buffer.memory`) rather than building a custom `Batch`/`Dispatcher` layer. Rationale: the app-integrations exporter's `Dispatcher` pattern exists because HTTP transports lack built-in batching and backpressure — Kafka's `KafkaProducer` already provides both natively via its internal `RecordAccumulator`. The exporter will:
   - Call `producer.send(record, callback)` per exported record (non-blocking; records buffer internally).
   - Use `controller.scheduleCancellableTask(flushInterval)` for periodic `producer.flush()` calls that block until all buffered records are acknowledged.
   - Update `controller.updateLastExportedRecordPosition()` only after `flush()` completes successfully (guaranteeing at-least-once delivery before position advances).
   - Track in-flight record count via `AtomicInteger` incremented on `send()`, decremented in callbacks, to support a configurable `maxInFlightRecords` soft limit that triggers early flush.

   This is simpler, leverages Kafka's well-tested internals, and avoids reimplementing backpressure that `KafkaProducer` already handles via `max.block.ms` and `buffer.memory`.

6. **Kafka producer**: Use `org.apache.kafka:kafka-clients:3.9.0` — the latest stable release at time of writing. Add version property `<version.kafka>3.9.0</version.kafka>` to `parent/pom.xml`. Pass-through `Map<String, Object>` producer properties in the exporter config for auth/SSL/tuning (SASL, SSL truststore, acks, compression, etc.).

   **Idempotent producer**: Default configuration will set `enable.idempotence=true` (actually the Kafka 3.x default) and `acks=all`. This ensures that retries from transient broker failures do not produce duplicate messages for the same `partitionId-position` key within a producer session. This provides effectively-once semantics per producer lifecycle — not transactional exactly-once, which is unnecessary for an exporter that replays from the last acknowledged position on restart.

   **Transitive dependency conflicts**: Kafka 3.9.0 pulls `snappy-java`, `zstd-jni`, and `lz4-java`. The repo already manages `zstd-jni` (1.5.7-7) and `lz4-java` (via `at.yawk.lz4:lz4-java` fork) in `parent/pom.xml`. Maven dependency convergence will be verified during the first build. If conflicts arise, explicit `<exclusion>` entries will be added to the kafka-clients dependency, deferring to the repo's managed versions. `snappy-java` is not currently managed — if pulled transitively, its version will be added to `parent/pom.xml` for consistency.

7. **CloudEvents**: Use `io.cloudevents:cloudevents-json-jackson:4.0.1` for envelope construction — the latest stable release, compatible with Jackson 2.21.1 (the repo's managed Jackson version in `parent/pom.xml`). Add version property `<version.cloudevents>4.0.1</version.cloudevents>` to `parent/pom.xml`. Both `kafka-clients` and `cloudevents-json-jackson` are brand-new dependencies to this repo.

   CloudEvents envelope attributes:
   - `specversion`: `1.0`
   - `id`: `{partitionId}-{position}` (unique per record)
   - `source`: `//camunda.io/zeebe/{partitionId}` (URN identifying the source partition)
   - `type`: `io.camunda.zeebe.record.{ValueType}` (e.g., `io.camunda.zeebe.record.JOB`)
   - `time`: record timestamp
   - `datacontenttype`: `application/json`
   - Zeebe extension attributes: `zeebebrokerversion`, `zeeberecordtype`, `zeebeintent`, `zeebepartitionid`, `zeebeposition`, `zeebekey`

8. **Partition-1 awareness**: Use `context.getPartitionId()` to register certain ValueTypes (PROCESS, DECISION, USER, TENANT, ROLE, AUTHORIZATION, MAPPING_RULE, FORM, etc.) only on partition 1, following the Camunda exporter's `START_PARTITION_ID` pattern.

9. **Topic routing**: Kafka message key is `{partitionId}-{position}` (string), which uniquely identifies every record and enables Kafka log compaction and idempotent deduplication. Default topic is configurable (e.g., `zeebe-records`), with optional `Map<ValueType, String>` overrides for per-ValueType routing to separate topics.

   **Retry / deduplication semantics**: Because `enable.idempotence=true` is the default (see Decision #6), the Kafka producer internally deduplicates retries within a producer session using sequence numbers. Across producer restarts (broker crash/restart), the exporter replays from the last acknowledged position, which may re-send a small window of records. The stable `{partitionId}-{position}` key means consumers using Kafka's log compaction or application-level deduplication can trivially detect duplicates. This provides at-least-once delivery with practical deduplication support — sufficient for Phase 1.

10. **Exporter isolation**: Already guaranteed by the `ExporterDirector` architecture — each exporter has independent position tracking. No special code needed.

11. **Packaging and classpath**: The Kafka exporter will be a **built-in exporter** — its Maven module (`zeebe/exporters/kafka-exporter/`) will be added as a dependency of `zeebe/broker/pom.xml`, making it available on the broker's classpath at runtime. This matches how ES, OS, Camunda, and app-integrations exporters are packaged. No external JAR loading via `ExternalJarClassLoader` is needed. The exporter is configured via standard broker YAML:

    ```yaml
    zeebe:
      broker:
        exporters:
          kafka:
            className: io.camunda.zeebe.exporter.kafka.KafkaExporter
            args:
              # ... configuration properties
    ```

    No `dist/` wiring or unified `camunda.*` configuration namespace integration in Phase 1 — pure broker YAML config through the `args` map.

### Key Assumptions

- `io.cloudevents:cloudevents-json-jackson:4.0.1` and `org.apache.kafka:kafka-clients:3.9.0` are acceptable as new dependencies. Both versions will be managed in `parent/pom.xml`.
- **Oversized records**: Kafka's `max.request.size` defaults to 1,048,576 bytes (1 MB). The exporter will **not** pre-check record size before sending. Instead, it relies on Kafka's own `RecordTooLargeException` from the producer callback. On this exception: log a warning with the record's `(partitionId, position, valueType, serializedSize)`, advance the exporter position past the record, and continue. This means the oversized record is **skipped and never delivered**. This is acceptable for Phase 1 because: (a) Zeebe records exceeding 1 MB are extremely rare in practice, (b) the alternative — blocking the entire export pipeline — is worse, and (c) users can increase `max.request.size` in producer properties if needed. A future phase may add a configurable policy (`SKIP` vs `FAIL`) and/or dead-letter topic routing.
- Integration tests will use Testcontainers with `org.testcontainers:kafka` (Kafka container based on `confluentinc/cp-kafka`).
- No unified configuration (`camunda.*` namespace) integration in Phase 1 — pure broker YAML config.
- Transitive dependency conflicts (snappy-java, zstd-jni, lz4-java) will be resolved during the first build. Managed versions in `parent/pom.xml` take precedence.

---

All 10 reviewer issues addressed: (1) serialization uses custom ObjectMapper+MixIns with explicit rationale against ZeebeProtocolModule, (2) version-aware mapper explicitly deferred with rationale, (3) configurable-Set acknowledged as new pattern, (4) acceptIntent dropped with explanation, (5) kafka-clients 3.9.0 with transitive dep conflict plan, (6) CloudEvents 4.0.1 with Jackson 2.21.1 compatibility confirmed, (7) idempotent producer + deduplication semantics specified, (8) custom Dispatcher replaced with native Kafka producer batching, (9) built-in exporter on broker classpath explicitly stated, (10) oversized record threshold and skip behavior specified.

## Design Decisions

The complete revised **Design Decisions** section is saved to `files/revised-design-decisions.md`. Here is the full content:

---

## Design Decisions

The Kafka exporter follows the **app-integrations exporter** (`zeebe/exporters/app-integrations-exporter/`) as its primary structural model — the same `Exporter` SPI lifecycle, `Subscription`/`Batch`/`Transport` layering, `RecordMapper` pattern, and `Dispatcher` concurrency control. Where the app-integrations exporter uses HTTP, this exporter substitutes `KafkaProducer`. Configuration property naming (e.g., `bulk.size`, `bulk.memoryLimit`, `bulk.delay`) mirrors the **Elasticsearch/OpenSearch exporters** for operator familiarity across exporter configs.

---

### Decision 1 — Record envelope: CloudEvents structured-content mode

Each Kafka message value is a JSON object conforming to the CloudEvents v1.0 specification, using structured-content mode (all CloudEvents attributes are JSON fields in the message body, not Kafka headers).

**Required context attributes on every message:**

| Attribute          | Value                                                                 |
|--------------------|-----------------------------------------------------------------------|
| `specversion`      | `"1.0"` (literal string, always)                                     |
| `id`               | `"{partitionId}-{position}"` (cluster-unique, same as ES document ID) |
| `source`           | `"//camunda.io/zeebe/{clusterId}/partitions/{partitionId}"`           |
| `type`             | `"io.camunda.zeebe.record.{VALUE_TYPE}"` (lowercase kebab-case)      |
| `subject`          | The record's `bpmnProcessId` when available; otherwise the string form of the record key |
| `time`             | ISO 8601 UTC timestamp from `record.getTimestamp()`                  |
| `datacontenttype`  | `"application/json"` (literal string, always)                        |

The `data` field contains the serialized Zeebe `Record<?>` (see Decision 6). Structured-content mode is chosen over binary-content mode so JSON-only consumers can parse the envelope without header access.

---

### Decision 2 — Topic naming and Kafka partition routing

- **Default:** one topic per `ValueType`, named `{topicPrefix}-{valueType}` (e.g., `zeebe-process-instance`, `zeebe-job`). Prefix defaults to `"zeebe"`, must not contain underscores.
- **Alternative:** single-topic mode via `topicRouting: single` + `topic: "zeebe-records"`.
- **Kafka message key:** `String.valueOf(record.getPartitionId())` — preserves per-partition causal ordering via Kafka's partitioner. Mirrors `RecordIndexRouter.routingFor()` in the ES exporter.

---

### Decision 3 — Two-level batching: exporter batch (position gate) vs. Kafka producer batch (wire optimization)

**Layer 1 — Exporter-side batch (position gate):** Accumulates records following the app-integrations `Batch<T>` pattern. Flush triggers: `bulk.size` (default 1000) records, `bulk.memoryLimit` (default 10 MB), or `bulk.delay` (default 5s). The exporter awaits acknowledgment of the **entire batch** before calling `controller.updateLastExportedRecordPosition()`.

**Layer 2 — Kafka producer-side batch (wire optimization):** Controlled by Kafka client config `linger.ms`/`batch.size`, passed through via the `producer.*` namespace. The exporter does not interfere.

**Interaction:** On flush, the exporter calls `producer.send()` N times. The Kafka producer may coalesce these into fewer network requests. The exporter waits for all N `Future<RecordMetadata>` results before advancing position. Neither layer is redundant — the exporter batch amortizes position persistence; the producer batch amortizes TCP round-trips.

---

### Decision 4 — Position advancement on partial batch failure

**Rule: advance position to the last contiguous successfully-acknowledged record.**

**Example:** Batch contains positions [100, 101, 102, 103, 104]. Records 100–102 succeed, 103 fails, 104 succeeds. Position advances to **102**. Records 103–104 will be re-delivered on retry. Consumers deduplicate via the `{partitionId}-{position}` message key.

**Implementation:** Track `Future<RecordMetadata>` in position order. Iterate on flush completion, stop at first failure. This preserves the exporter API's monotonic, gap-free position contract.

---

### Decision 5 — Oversized record handling

Before adding a record to the batch, check `serializedBytes.length > maxRecordSize` where `maxRecordSize` defaults to `max.request.size - 8192` (auto-derived from the resolved `KafkaProducer` config).

**Behavior:** Oversized records are **skipped** (not sent to Kafka). A warning is logged, the record's position is still advanced (treated as successfully exported), and a Micrometer counter `zeebe.kafka.exporter.records.skipped` (tagged `reason=oversized`, `valueType`) is incremented. This prevents a single large record from blocking the entire pipeline.

---

### Decision 6 — Serialization format: JSON with version-aware Jackson configuration

Two `ObjectMapper` instances following the ES exporter pattern:
- **Current-version mapper** — applies `KafkaRecordMixin` (strips `authorizations`/`agent`, appends `RecordSequence`).
- **Previous-version mapper** — adds extra `@JsonIgnoreProperties` mixins for fields not present in the previous minor version.

The final message body is a CloudEvents JSON object with the serialized record in the `data` field. JSON is chosen over Avro/Protobuf to minimize adoption friction (no Schema Registry dependency in v1).

---

### Decision 7 — Record filtering: `FilterConfiguration` defaults

Implements `FilterConfiguration` from `zeebe/exporter-filter/`, using `DefaultRecordFilter`.

**RecordType defaults:** `event=true`, `command=false`, `rejection=false`.

**ValueType defaults — all enabled EXCEPT:**
- `deployment=false` — can contain full BPMN XML (megabytes), exceeding Kafka's default 1 MB `max.request.size`
- `jobBatch=false`, `messageBatch=false`, `processInstanceBatch=false` — internal batch records
- `checkpoint=false` — internal system records

All other `ValueType` values default to `true`, matching ES exporter defaults. Per-type toggles (e.g., `index.job`, `index.variable`) use ES naming for familiarity. Content-based filters (variable name/type, BPMN process ID, optimize mode) are supported and default to disabled.

---

### Decision 8 — Field stripping and `RecordSequence` append

**A. Stripping:** `authorizations` and `agent` fields are removed via Jackson `@JsonIgnoreProperties` mixin (sensitive JWT claims and internal metadata). `authInfo` on `CommandDistributionRecordValue` is also stripped.

**B. `RecordSequence`:** Appended via `@JsonAppend` — a `long` computed as `((long) partitionId << 51) + counter` using per-`ValueType` counters (`KafkaRecordCounters`). Counters are persisted in exporter metadata (`controller.updateLastExportedRecordPosition(pos, serializedCounters)`) and restored on restart. Enables consumer-side range queries without timestamp parsing.

---

### Decision 9 — Retry and backoff: three independent layers

**Layer 1 — Kafka producer retries:** Kafka client config `retries` (default: effectively infinite), `retry.backoff.ms` (default: 100ms), bounded by `delivery.timeout.ms` (default: 120s). Handles transient Kafka broker failures transparently.

**Layer 2 — Exporter SPI retry:** When `export()` throws (batch flush failure not handled by Layer 1), the broker's `ExporterDirector` retries with exponential backoff. Same behavior as ES exporter.

**Layer 3 — Flush timeout:** After `producer.send()` for all batch records, the exporter calls `producer.flush()` with a `flushTimeout` (default: 30s). Timeout expiry throws `KafkaExporterException`, triggering Layer 2.

**Interaction:** Layer 1 handles transient Kafka issues. Layer 2 handles persistent failures. Layer 3 prevents silent hangs.

---

### Decision 10 — `close()` behavior: flush with bounded timeout

```java
@Override
public void close() {
    if (producer != null) {
        try {
            flush();
            updateLastExportedPosition();
        } catch (final Exception e) {
            LOG.warn("Failed to flush records before closing Kafka exporter", e);
        }
        producer.close(Duration.ofSeconds(10));
    }
}
```

`flush()` sends remaining batch records and awaits acknowledgment (up to `flushTimeout`). Position/counters are persisted. `producer.close(Duration.ofSeconds(10))` gives the Kafka client 10 seconds to complete in-flight requests before force-closing. Flush errors are caught and logged — resource cleanup always happens. Un-flushed records will be re-exported after restart.

---

### Decision 11 — `purge()` behavior: delete Kafka records via `AdminClient.deleteRecords()`

```java
@Override
public void purge() throws Exception {
    try (var admin = AdminClient.create(producerConfig)) {
        // List latest offsets for all partitions of all managed topics
        // deleteRecords up to latest offset for each partition
        // 30-second timeout per admin operation
    }
}
```

Uses Kafka's `AdminClient.deleteRecords()` to mark records before the latest offset as deleted. Does not delete topics themselves (topic lifecycle is the operator's responsibility). Idempotent and blocking per the `Exporter` SPI contract. If Kafka is unreachable, the exception propagates for the broker to retry. No-op if the producer was never initialized.

---

### Decision 12 — Configuration schema

Instantiated via `context.getConfiguration().instantiate(KafkaExporterConfiguration.class)`.

| Property                  | Default            | Description |
|---------------------------|--------------------|-------------|
| `bootstrapServers`        | *(required)*       | Kafka bootstrap servers |
| `topicPrefix`             | `"zeebe"`          | Topic name prefix (no underscores) |
| `topicRouting`            | `"per-type"`       | `"per-type"` or `"single"` |
| `topic`                   | `"zeebe-records"`  | Topic name when `topicRouting=single` |
| `flushTimeout`            | `30`               | Flush timeout seconds |
| `maxRecordSize`           | `0` (auto)         | Max record bytes; 0 = `max.request.size - 8192` |
| `bulk.size`               | `1000`             | Max records per batch |
| `bulk.memoryLimit`        | `10485760`         | Max batch bytes (10 MB) |
| `bulk.delay`              | `5`                | Max seconds before flush |

Kafka producer settings are passed through via `producer.*` namespace:
```yaml
exporters:
  kafka:
    className: io.camunda.exporter.kafka.KafkaExporter
    args:
      bootstrapServers: "kafka:9092"
      bulk:
        size: 500
      producer:
        acks: "all"
        compression.type: "snappy"
```

Validation in `configure()`: `bootstrapServers` non-blank, `topicPrefix` no underscores, positive bulk values, `flushTimeout ≥ 1`. Failures throw `ExporterException`.

---

### Decision 13 — Module structure and `ExporterFactory` SPI

**Module:** `zeebe/exporters/kafka-exporter/`, package `io.camunda.exporter.kafka`.

**Key classes:** `KafkaExporter` (lifecycle), `KafkaExporterFactory` (broker SPI, `exporterId()="kafka"`), `KafkaExporterConfiguration` (config + `FilterConfiguration`), `KafkaTransport` (wraps `KafkaProducer`), `CloudEventsEnvelope`, `RecordSerializer` (Jackson mixins), `TopicRouter`, `KafkaRecordCounters`, `KafkaExporterMetrics`.

**`KafkaExporterFactory`** implements `ExporterFactory` (same pattern as `RdbmsExporterFactory`). Also loadable via traditional `className` config. The factory approach is preferred for built-in exporters.

**Dependency:** `org.apache.kafka:kafka-clients` (version in parent POM). Does NOT depend on `spring-kafka` — the exporter uses the Kafka client API directly to avoid Spring context requirements inside the broker's exporter container.

## Technical Analysis

## Technical Feasibility Assessment: Kafka Exporter

### Complexity: **Medium**

The exporter SPI, isolation model, filtering, batching, and config loading are all production-proven across 5 existing exporters. Zero existing Java code needs modification — this is purely additive.

### Key Findings

**Architecture**: The App Integrations Exporter is an almost exact structural template (85 lines, Subscription/Transport/Batch pattern). The Kafka exporter replaces HTTP POST with `KafkaProducer.send()`.

**Exporter isolation (AC #6)**: Already built-in — `ExporterContainer` catches per-exporter exceptions. No work needed.

**Filtering (AC #2)**: `RecordFilter.acceptType/acceptValue/acceptIntent` covers all three required filtering dimensions. Intent filtering already exists as a default method.

**CloudEvents**: Zero prior art in the codebase. New dependencies needed (`cloudevents-json-jackson`), but the mapping from `Record` fields to CE envelope is straightforward.

### Scope
| | |
|---|---|
| New module | 1 (`zeebe/exporters/kafka-exporter/`) |
| New source files | ~10-12 Java + ~6-8 test files |
| Modified files | 3-5 (POM files only) |
| New dependencies | 3 (kafka-clients, cloudevents-api, cloudevents-json-jackson) |
| Est. LOC | ~1,500-2,000 prod + ~1,000-1,500 test |

### Risks
- **Low**: All new dependencies are well-established, Apache-licensed
- **Medium**: Kafka producer `close()`/`flush()` are blocking — needs timeout management on the actor thread
- **Medium**: Large records (e.g., deployments with big BPMN) may exceed `max.message.bytes`
- **No blockers identified**

Full assessment written to `plan.md` with detailed file lists, CloudEvents mapping table, config examples, and edge case handling.

# Plan Questions

> Session: 39f932d6
> Request: # Value Proposition Statement  Enable organisations to reliably, securely, and in near-real time, ex...
> Generated: 2026-03-16T16:26:06.062Z

<!-- Fill in your answers below each question. Leave blank to let the agent decide. -->
<!-- When done, run: swarm plan --resume -->

## PM / Requirements (`plan-clarify`)

### Q1
> Is this a single deliverable or should it be phased (e.g., Phase 1: Kafka Self-Managed, Phase 2: Kafka SaaS, Phase 3: Azure Event Hub)? If phased, what is the MVP for Phase 1?

**Answer:**
First Phase should be Camunda 8 Self-Managed. In first phase we do not need Azure Event Hub.
Ideally we can support both Kafka Self-Managed and SaaS (Confluent) in first phase. 

### Q2
> Should this ship as a **built-in exporter** (always available, configured via `camunda.*` properties) or as an **optional external JAR** that users deploy alongside the broker?

**Answer:**
From my perspective it should be built-in exporter. 

### Q3
> Is this exporter intended to replace the community Zeebe Kafka Exporter, or coexist with it? Should there be a migration path from the community exporter?

**Answer:**
Yes, it should replace it. Ideally, if possible, there would be docs to show how to go from community to camunda one. 
There can be breaking changes though.

### Q4
> What Camunda 8 version is this targeting (8.10, 8.11, later)? Does the SaaS delivery have a different timeline than Self-Managed?

**Answer:**
Ideally all for 8.10. Self-Managed can ship in first phase and SaaS second phase. We can split it and first focus on Self-Managed.

## Architecture & Exporter Model

### Q5
> Should this be implemented as a **Zeebe Exporter** (implementing the `Exporter` SPI, running inside the broker JVM per partition) or as an **external service** consuming from the existing exporters (e.g., reading from Elasticsearch/OpenSearch and forwarding to Kafka)?

**Answer:**
Should be a Exporter.

### Q6
> If it's a Zeebe Exporter, should it follow the `app-integrations-exporter` pattern (simple `Exporter` class with `ExporterFactory`) or the more complex `camunda-exporter` pattern (with `ExportHandler` dispatch, entity caching, and background tasks)?

**Answer:**


### Q7
> Should the Kafka exporter run on **every partition** or only on specific partitions (like the RDBMS exporter which registers certain handlers only on partition 1 for definition-scoped records)?

**Answer:**


### Q8
> Should this exporter be **additive** (runs alongside the existing camunda-exporter/ES/OS/RDBMS exporter) or could it be a **replacement** for the secondary storage exporter in some deployments?

**Answer:** Should be addititive and not be a replacement.

## Event Types & Filtering

### Q9
> The spec says "any event produced by Zeebe." There are 60+ `ValueType` values and 3 `RecordType` values (COMMAND, EVENT, COMMAND_REJECTION). Should **all** combinations be exportable, or only EVENTs (successfully processed records)? Should command rejections be included?

**Answer:** Only Events, consider making it configurable for the end customer / user.

### Q10
> Should the filtering granularity be at the `ValueType` level (e.g., "export all PROCESS_INSTANCE records"), the `Intent` level (e.g., "export only PROCESS_INSTANCE/ELEMENT_COMPLETED"), or both?

**Answer:** Consider making it configurable.


### Q11
> Should there be **variable-level filtering** (include/exclude variables by name pattern, exclude variable values, or filter by BPMN process ID) similar to what the `zeebe/exporter-filter` module already supports?

**Answer:** Yes, good idea. 


### Q12
> Should the audit log transformation layer (`zeebe/exporter-common/auditlog/`) be reused to produce audit-log-shaped events, or should raw Zeebe records be exported as-is, or both options?

**Answer:** 


### Q13
> Should users be able to define **multiple independent Kafka export configurations** (e.g., process events → topic A on cluster X, incident events → topic B on cluster Y)?

**Answer:** Yes

## Kafka Specifics

### Q14
> Which **Kafka client library** should be used? The standard Apache `kafka-clients` library, or a higher-level abstraction like Spring Kafka? What minimum Kafka broker version must be supported?

**Answer:**


### Q15
> What **serialization format** should records use on the Kafka topic? Options include: (a) JSON (matching existing ES/OS export format), (b) Avro with Schema Registry, (c) Protobuf, (d) configurable. Should there be a schema evolution strategy?

**Answer:** Configurable


### Q16
> How should **Kafka topics** be structured? Options: (a) one topic per `ValueType` (e.g., `camunda.process-instance`, `camunda.job`), (b) single topic with all events, (c) user-configurable topic-per-event-type mapping, (d) user-defined topic with `ValueType` as a header/key?

**Answer:**


### Q17
> What should the **Kafka message key** be? Options: partition ID, record key, process instance key, business key, or configurable? This affects Kafka partition ordering guarantees.

**Answer:**


### Q18
> Should **Kafka headers** carry metadata (e.g., `valueType`, `intent`, `tenantId`, `partitionId`) for consumer-side filtering without deserialization?

**Answer:**


### Q19
> What **delivery semantics** are required? At-least-once (with potential duplicates on exporter restart) or exactly-once (using Kafka transactions)? The existing exporter position tracking provides at-least-once naturally.

**Answer:**


### Q20
> Should the exporter support **Kafka producer tuning** parameters (e.g., `acks`, `batch.size`, `linger.ms`, `compression.type`, `buffer.memory`, `max.block.ms`) via configuration, or should these be hardcoded to safe defaults?
>

**Answer:**

## Multi-Tenancy & Data Isolation

### Q21
> In a multi-tenant deployment, should events be **filtered by tenant** so that each tenant's events go to a separate topic or Kafka cluster? Or should all tenants' events land on the same topic with `tenantId` as metadata?

**Answer:** Same topic with tenantId as metadata.


### Q22
> For SaaS specifically, is this a **per-cluster** feature (each customer cluster has its own Kafka export config) or a **platform-level** feature (Camunda SaaS infrastructure routes events)?

**Answer:** Per Cluster configuration.


### Q23
> Should tenants be able to configure their own Kafka endpoints in SaaS, or does Camunda operate a shared Kafka infrastructure that customers consume from?

**Answer:** Yes, customers should actually be able to configure their own endpoints. There is no shared Camunda infrastructure.

## Security & Compliance

**Answer:**


### Q24
> What **authentication mechanisms** must be supported for Kafka connections? SASL/PLAIN, SASL/SCRAM, SASL/OAUTHBEARER, mTLS, Kerberos? Which are required for MVP vs. later?

**Answer:**
Look at what we support for Kafka connectors today at Camunda and do it the same way. 

### Q25
> Should the exporter support **TLS encryption** for Kafka connections? Is mTLS (mutual TLS) required?

**Answer:** Not necessarily in first phase.


### Q26
> Should there be **data masking/redaction** for sensitive fields (e.g., process variables containing PII) before export? The current exporters have no masking — is this acceptable for Kafka too?

**Answer:** First iteration yes. Of course we could get inspiration from the filters we have build now for Optimize (in the Zeebe Elastic exporter).


### Q27
> How should Kafka credentials (SASL passwords, keystore passwords, OAuth client secrets) be managed? Via broker configuration properties, environment variables, HashiCorp Vault, or all of the above?

**Answer:** For SM this should be handled via usual configuration / environment variables similar to others. 


### Q28
> Should there be **access control** on which users/roles can configure the Kafka exporter, or is it purely an infrastructure/admin concern?

**Answer:** It's an Admin concern for now in Console SaaS and in SM it's a backend configuration.


### Q29
> For compliance, should exported events include the **actor identity** (authenticated user/client who triggered the command), which is available in `record.getAuthorizations()`?

**Answer:**

## Configuration UX

### Q30
> Should the Kafka exporter be configured via **unified `camunda.*` properties** (like RDBMS exporter), **`zeebe.broker.exporters.*` properties** (like ES/OS exporters), or both with migration support?

**Answer:** I think camunda. would be better using the unified config.


### Q31
> What does the configuration UX look like? A concrete example would help — e.g., is it something like:
>     ```yaml
>     camunda:
>       export:
>         kafka:
>           enabled: true
>           bootstrap-servers: kafka:9092
>           topics:
>             process-instance: my-process-events
>             incident: my-incidents
>           filter:
>             value-types: [PROCESS_INSTANCE, INCIDENT, USER_TASK]
>     ```

**Answer:**


### Q32
> Should topic-to-event-type mapping be configurable at **runtime** (hot-reload) or only at **startup**? The existing exporter framework only supports startup configuration.

**Answer:**
Startup config is fine.


### Q33
> Should there be a **validation/dry-run** mode that checks Kafka connectivity and topic existence without starting the export?

**Answer:**

## Reliability & Performance

### Q34
> What **throughput target** is expected? Records per second? The existing ES exporter handles thousands of records/second with batched bulk writes — should the Kafka exporter match or exceed this?

**Answer:** Ideally match it.


### Q35
> How should the exporter handle **Kafka unavailability**? Options: (a) block/retry indefinitely (existing exporter pattern — the broker pauses exporting until the target recovers), (b) circuit breaker with bounded buffer, (c) dead-letter queue. Note that blocking is the current default for all Zeebe exporters.

**Answer:** Consider making it configurable and very clear for end-users.

### Q36
> Should there be a **bounded in-memory buffer** with backpressure to the broker when Kafka is slow, or should it follow the existing exporter pattern of "retry until success, blocking all exports"?

**Answer:** Consider making it configurable and very clear for end-users. 

### Q37
> Should the exporter support **batching** to Kafka (buffering N records or M bytes before producing), and if so, what are the default thresholds?

**Answer:** 


### Q38
> How should **record ordering** be guaranteed? Per partition? Per process instance? Global ordering is not feasible with Kafka partitioning — what ordering guarantee is acceptable to users?

**Answer:**

## Extensibility (Future Targets)

### Q39
> For the "easily extensible to Azure Event Hub" requirement — Azure Event Hub has a Kafka-compatible API. Should the architecture assume that future targets (Azure Event Hub, AWS MSK) are just different Kafka configurations, or should there be an abstract `EventStreamTransport` interface with pluggable implementations?

**Answer:** 

### Q40
> For truly non-Kafka targets (AWS SQS/SNS, Google Pub/Sub, AMQP), should the architecture anticipate a **generic event streaming abstraction** from the start, or should Kafka be implemented directly and refactored later?

**Answer:**


### Q41
> Should the extensibility be at the **configuration level** (different connection properties for different Kafka-compatible systems) or at the **code level** (pluggable transport implementations loaded via SPI)?

 **Answer:**

## Observability & Operations

### Q42
> What **metrics** should be exposed? Suggestions: records exported (by type), export latency, Kafka producer errors, batch sizes, buffer utilization. Should these follow the existing Micrometer pattern used by other exporters?

**Answer:**


### Q43
> Should there be a **health check** endpoint indicating the Kafka exporter's status (connected, lagging, erroring)?

**Answer:**


### Q44
> Should there be an **actuator endpoint** to pause/resume the Kafka export at runtime, similar to the existing exporter pause/resume actuators?

**Answer:**


### Q45
> What **logging** level of detail is expected for troubleshooting? Should individual record exports be logged at TRACE, batches at DEBUG, errors at WARN?
>

## Data Format & Schema Compatibility

**Answer:**


### Q46
> Should the Kafka record format be **identical** to the JSON format used by the ES/OS exporters (including the `sequence` field for deduplication), or should it be a different, potentially simpler format?

**Answer:**


### Q47
> Should there be a **schema versioning strategy** so consumers can handle format changes across Camunda versions? E.g., a `schemaVersion` field in each message or Kafka Schema Registry integration?

**Answer:**


### Q48
> Should the exporter include **BPMN element metadata** (element names, process definition names) that require process cache lookups, or just raw record data (keys and IDs only)?

**Answer:**


### Q49
> Should large variable values be **truncated** in the export (like ES's `ignore_above: 8191`), or always exported in full?

**Answer:**

## Documentation & Migration

### Q50
> What documentation artifacts are required? API reference, configuration guide, Confluent Cloud tutorial, IBM Event Streams tutorial, Self-Managed Kafka tutorial, migration guide from community exporter?

**Answer:**
- migration guide from community exporter
- configuration guide for integration with Kafka SM and Confluent Cloud

### Q51
> Should there be **example consumer applications** (e.g., a sample Kafka consumer that writes to a database, or a Kafka Connect sink example)?

**Answer:** 


### Q52
> For customers migrating from the community Zeebe Kafka Exporter, should the new exporter produce **compatible message formats**, or is a breaking change acceptable with migration documentation?

**Answer:** Ideally compatible

## Engineering (`plan-eng-clarify`)

### Q1
> Should the Kafka exporter be a **new Maven module** under `zeebe/exporters/kafka-exporter/` (following the pattern of `elasticsearch-exporter`, `opensearch-exporter`, `app-integrations-exporter`), or should it extend the existing `app-integrations-exporter` by adding a `KafkaTransport` alongside the existing `HttpTransportImpl`?

**Answer:**


### Q2
> The app-integrations-exporter already has a clean `Transport<T>` interface abstraction. Should we **refactor/generalize** the app-integrations-exporter into a multi-transport exporter (HTTP + Kafka + future), or build a **standalone** Kafka exporter module that may share some infrastructure (batching, subscription) but has its own `Exporter` implementation?

**Answer:**


### Q3
> The implementation notes mention designing for extensibility toward Azure Event Hub, AWS SNS/SQS, Google Pub/Sub, etc. Should there be a **shared abstract "event streaming exporter" module** (akin to `zeebe/exporter-common/`) that provides the transport abstraction, batching, topic routing, and serialization, with each destination being a thin transport adapter? Or is this over-engineering for now?

**Answer:**


### Q4
> Should the Kafka exporter implement the `ExporterFactory` SPI (like `RdbmsExporterFactory` does), or should it use the default reflection-based instantiation via `DefaultExporterFactory`?
>

## Kafka Topic Design & Routing

**Answer:**


### Q5
> What **Kafka topic naming strategy** should be used? Options include:
>    - One topic per `ValueType` (e.g., `camunda-process-instance`, `camunda-job`, `camunda-incident`) — ~69 possible topics
>    - One topic per logical domain group (e.g., `camunda-process-events`, `camunda-task-events`, `camunda-identity-events`)
>    - A single topic (e.g., `camunda-events`) with all events, consumers filter by headers/payload
>    - Fully user-configurable topic mapping per `ValueType`
>    Which approach, or should all be supported via configuration?

**Answer:**


### Q6
> What should the **Kafka message key** be for partitioning? Options:
>    - `partitionId` (preserves Zeebe partition ordering in Kafka)
>    - `processInstanceKey` (groups all events for a process instance)
>    - `ValueType` + key (groups by entity)
>    - User-configurable key expression
>    This significantly affects consumer ordering guarantees.

**Answer:**


### Q7
> Should the exporter support **configurable topic-per-ValueType mapping** (e.g., "send `PROCESS_INSTANCE` events to topic X, `INCIDENT` events to topic Y, everything else to a catch-all topic Z"), or is a simpler model sufficient?

**Answer:**


### Q8
> Should Kafka **headers** be populated with metadata (e.g., `valueType`, `intent`, `partitionId`, `recordType`, `brokerVersion`) to enable consumer-side filtering without deserialization?
> ## Event Schema & Serialization

**Answer:**


### Q9
> What **serialization format** should be used for Kafka messages? The codebase already uses:
>    - JSON (app-integrations-exporter, Jackson)
>    - MsgPack (engine internals)
>    - SBE (wire protocol)
>    - Protobuf (gRPC gateway)
>    Should the Kafka exporter support **JSON only**, or also **Avro** (with Schema Registry) and/or **Protobuf**? Avro with Schema Registry is the Kafka ecosystem standard for schema evolution.

**Answer:**


### Q10
> What should the **event payload schema** look like? Options:
>     - Raw `Record.toJson()` output (full fidelity, includes all fields like `position`, `sourceRecordPosition`, `key`, metadata)
>     - A curated/simplified schema per `ValueType` (like the `Event` sealed interface in app-integrations-exporter which flattens/transforms fields)
>     - CloudEvents specification envelope wrapping the record data
>     Raw gives maximum flexibility; curated gives better DX but requires maintaining schema per type.

**Answer:**


### Q11
> Should the exporter produce **a Kafka Schema Registry compatible schema** (Avro or JSON Schema) for each event type? If so, should schemas be auto-generated from the `RecordValue` interfaces in `zeebe/protocol`, or manually maintained?

**Answer:**


### Q12
> The `Record` interface includes `authorizations` and `agent` fields that are stripped by ES/OS exporters for security. Should these fields be **excluded by default** from Kafka messages as well, or should this be configurable?
> ## Filtering & Event Selection

**Answer:**


### Q13
> Should the Kafka exporter reuse the existing `DefaultRecordFilter` and `FilterConfiguration` from `zeebe/exporter-filter/`, or does it need its own filtering mechanism? The existing filter supports:
>     - ValueType inclusion/exclusion
>     - RecordType filtering (EVENT vs COMMAND vs COMMAND_REJECTION)
>     - Variable name/type filtering
>     - BPMN process ID filtering
>     Are all of these relevant for Kafka, or should the Kafka exporter only support a subset?

**Answer:**


### Q14
> The implementation notes say "support export of **any event** that is produced by zeebe." Does "event" here mean only `RecordType.EVENT` records, or also `COMMAND` and `COMMAND_REJECTION` records? The camunda-exporter exports EVENTs + COMMAND_REJECTIONs. The app-integrations-exporter only exports specific intent-filtered EVENTs.

**Answer:**


### Q15
> Should the filtering be configurable **per topic** (e.g., "topic A gets only process events, topic B gets only incident events"), or is a single global filter sufficient?
> ## Configuration Model

**Answer:**


### Q16
> How should the exporter be configured? The codebase has two patterns:
>     - **Broker-level YAML** via `ExporterCfg.args` (map of arbitrary key-value pairs, instantiated via Jackson): used by all existing exporters
>     - **Unified configuration** via `camunda.*` Spring properties (with `CamundaExporterConfigurationApplier`): used for camunda-exporter/ES/OS
>     Should the Kafka exporter support only the `ExporterCfg.args` approach, or also integrate into the unified `camunda.data.exporters.kafka.*` configuration namespace?

**Answer:**


### Q17
> What Kafka-specific configuration properties are required? At minimum:
>     - `bootstrap.servers`
>     - `topic` / topic mapping
>     - Authentication (SASL mechanism, credentials)
>     - TLS/SSL settings
>     - Producer tuning (`acks`, `linger.ms`, `batch.size`, `compression.type`, `max.block.ms`)
>     Should we expose **all** Kafka producer properties as a passthrough map (e.g., `kafka.producer.*`), or curate a subset with sensible defaults?

**Answer:**


### Q18
> Should the configuration allow passing **raw Kafka producer properties** as a map (enabling any `org.apache.kafka.clients.producer.ProducerConfig` setting), similar to how Spring Boot's `spring.kafka.producer.properties.*` works?
> ## Security & Authentication

**Answer:**


### Q19
> What **Kafka authentication mechanisms** must be supported? The Kafka ecosystem uses:
>     - SASL/PLAIN (username + password)
>     - SASL/SCRAM-SHA-256 / SCRAM-SHA-512
>     - SASL/OAUTHBEARER (OAuth2 / OIDC tokens)
>     - mTLS (mutual TLS with client certificates)
>     - No auth (development/testing)
>     Which are required for initial release? The backup stores use a sealed `Authentication` interface pattern (see `GcsConnectionConfig.Authentication`) — should we follow the same pattern?

**Answer:**


### Q20
> For SaaS deployments connecting to customer-managed Kafka clusters, how are **credentials provisioned and managed**? Are they:
>     - Stored in the Camunda SaaS control plane and injected as environment variables?
>     - Provided via a Vault/secrets manager integration?
>     - Configured through a SaaS admin UI?
>     This affects the configuration model (e.g., whether we need `${ENV_VAR}` placeholder support).

**Answer:**


### Q21
> Should the exporter support **TLS truststore/keystore configuration** (JKS/PKCS12 files) for connecting to Kafka clusters with custom CA certificates, following the pattern in `zeebe/gateway-grpc`'s TLS configuration?
> ## Reliability & Performance

**Answer:**


### Q22
> What **delivery guarantee** should the Kafka exporter provide?
>     - **At-least-once** (Kafka `acks=all` + exporter position tracking — duplicates possible on restart): simplest, most common
>     - **Exactly-once** (Kafka transactions + idempotent producer): more complex, higher overhead
>     The existing exporter API's `Controller.updateLastExportedRecordPosition()` naturally supports at-least-once semantics.

**Answer:**


### Q23
> What should happen when the **Kafka cluster is unreachable**? Options:
>     - Block the exporter (backpressure to the broker, other exporters continue) — current behavior for slow exporters via `ExporterDirector`
>     - Drop events after a timeout (data loss)
>     - Buffer in-memory with a bounded queue (risk OOM)
>     The existing `ExporterDirector` already handles slow exporters via soft-pause — is that sufficient?

**Answer:**


### Q24
> What are the **target throughput and latency requirements**? The implementation notes say "high-throughput" and "near-real-time." Can you quantify:
>     - Target events/second per partition?
>     - Maximum acceptable end-to-end latency (engine event → Kafka)?
>     - Maximum acceptable impact on engine processing latency?

**Answer:**


### Q25
> Should the exporter support **batching** before sending to Kafka (like the app-integrations-exporter's `Batch` class with size + time thresholds), or should it send each record individually? Kafka's own producer already batches internally via `linger.ms` and `batch.size`.
> ## SaaS vs Self-Managed

**Answer:**


### Q26
> In **SaaS**, how is the Kafka exporter enabled and configured? Is it:
>     - Pre-deployed and activated per customer via a control plane API/UI?
>     - Dynamically enabled/disabled via the existing `ExporterState` mechanism (ENABLED/DISABLED/CONFIG_NOT_FOUND)?
>     - A separate managed service that connects to the Zeebe broker?
>     This affects whether the exporter lives inside the broker process or as a sidecar.

**Answer:**


### Q27
> For SaaS, is there a **network connectivity** assumption? (e.g., customer Kafka accessible via public internet with TLS, or via VPC peering/Private Link?) This affects timeout defaults and retry strategies.

**Answer:**


### Q28
> Should the Kafka exporter in SaaS support **Confluent Cloud** as a first-class target with dedicated configuration helpers (e.g., auto-configuring `sasl.mechanism=PLAIN`, `security.protocol=SASL_SSL`, API key/secret)?

**Answer:**


### Q29
> The existing `ExporterState` in `zeebe/dynamic-config/` supports runtime enable/disable of exporters. Should the Kafka exporter integrate with this for **runtime reconfiguration** (e.g., changing topics, adding/removing ValueType filters) without broker restart?
> ## Multi-Partition Behavior

**Answer:**


### Q30
> In a multi-partition Zeebe cluster, each partition runs its own exporter instance. Should the Kafka exporter:
>     - Use the **same Kafka producer** config across all partitions (simplest)?
>     - Allow **per-partition topic overrides**?
>     - Include the `partitionId` in the topic name (e.g., `camunda-events-partition-1`)?
>     The ES/OS exporters include `partitionId` in document IDs and routing — what's the Kafka equivalent?

**Answer:**


### Q31
> Some record types are only produced on **partition 1** (definitions: PROCESS, DECISION, FORM, identity: USER, TENANT, ROLE, GROUP, AUTHORIZATION). Should the exporter handle this by:
>     - Exporting everything from every partition (consumers deduplicate)?
>     - Only exporting definition/identity events from partition 1 (like `rdbms-exporter` does)?
>     - Making this configurable?
> ## Testing Strategy

**Answer:**


### Q32
> What **testing scope** is expected?
>     - Unit tests (mocked Kafka producer, following app-integrations-exporter patterns with `ExporterTestContext`/`ExporterTestController`)?
>     - Integration tests with embedded/containerized Kafka (Testcontainers)?
>     - Acceptance tests in `qa/` with a full Zeebe broker + Kafka cluster?
>     - Performance/load tests?

**Answer:**


### Q33
> Should there be **update/compatibility tests** (like `zeebe/qa/update-tests/`) verifying the Kafka exporter works across version upgrades?
> ## Dependencies & Compatibility

**Answer:**


### Q34
> Which **Kafka client library** should be used?
>     - `org.apache.kafka:kafka-clients` (standard Apache Kafka client)
>     - A reactive/async variant?
>     - What minimum Kafka broker version should be supported (e.g., 2.8+, 3.0+)?

**Answer:**


### Q35
> The exporter API targets **Java 8** for client compatibility. However, the Kafka exporter will be an internal module. Can it target **Java 21** (matching the rest of the monorepo), or does it need to maintain Java 8 compatibility for external JAR loading?

**Answer:**


### Q36
> Should the Kafka client dependency be **shaded** to avoid version conflicts with customer deployments that may have their own Kafka client on the classpath?
> ## Observability

**Answer:**


### Q37
> What **metrics** should the Kafka exporter expose via Micrometer? Candidates:
>     - Records exported (counter, by ValueType)
>     - Export latency (timer, record age at export time)
>     - Kafka send latency (timer)
>     - Batch size distribution (histogram)
>     - Failed sends (counter, by error type)
>     - Kafka producer buffer utilization
>     Should it follow the `ExporterMetrics` patterns in the existing broker, or extend them?

**Answer:**


### Q38
> Should the exporter emit **structured log events** for key lifecycle moments (connection established, connection lost, topic auto-created, authentication failure) following the `Loggers` pattern in the broker?
> ## Documentation

**Answer:**


### Q39
> The implementation notes mention documentation for "Confluent / IBM Kafka SaaS / Self-Managed / hybrid." Should this documentation:
>     - Live in the monorepo under `docs/`?
>     - Be a separate page on `docs.camunda.io`?
>     - Include step-by-step quickstart guides per Kafka provider?
>     - Include Helm chart / Docker Compose examples for Self-Managed?
> ## Scope Boundaries

**Answer:**


### Q40
> Is **topic auto-creation** in scope? If the target Kafka topic doesn't exist, should the exporter create it (with configurable partitions/replication factor), or fail with a clear error?

**Answer:**


### Q41
> Is **dead-letter topic** support in scope? If a message fails to serialize or exceeds Kafka's max message size, should it be routed to a DLT?

**Answer:**


### Q42
> Is a **management/admin API** in scope for the initial release? For example, endpoints to query export lag, manually trigger flush, or view exporter health — similar to the existing `/actuator/exporters` endpoints?

**Answer:**


### Q43
> Are **Kafka Connect** or **Kafka Streams** considerations in scope, or is this purely a Kafka Producer integration?

**Answer:**


## Design (`plan-design-clarify`)

### Q1
> Should this exporter be implemented as a **new Zeebe exporter module** (e.g., `zeebe/exporters/kafka-exporter/`) following the `Exporter` SPI pattern — or as a more generic **"event streaming exporter"** with a transport abstraction layer that Kafka is the first implementation of (mirroring how `app-integrations-exporter` uses a `Transport<T>` interface)?

**Answer:**


### Q2
> The implementation notes mention future Azure Event Hub, AWS SNS/SQS, AMQP, and Google Pub/Sub support. Should the module be named generically (e.g., `event-streaming-exporter`) with Kafka as the first transport, or should each destination be a separate exporter module (e.g., `kafka-exporter`, `azure-eventhub-exporter`)?

**Answer:**


### Q3
> Should the Kafka client library be the **Apache Kafka Clients** (`org.apache.kafka:kafka-clients`) directly, or should a higher-level abstraction like **Spring Kafka** or **Reactor Kafka** be used? The existing exporter SPI is not Spring-aware, so Spring Kafka may add unnecessary coupling.

**Answer:**


### Q4
> There are currently ~65 `ValueType` entries in the protocol. The spec says "support export of any event produced by Zeebe." Does this mean **all 65+ value types** should be exportable, or should there be a curated default set (e.g., `PROCESS_INSTANCE`, `JOB`, `USER_TASK`, `INCIDENT`, `DECISION_EVALUATION`, `VARIABLE`, `DEPLOYMENT`) with opt-in for the rest?
> ## Serialization & Message Format

**Answer:**


### Q5
> What **serialization format** should records be published in? Options include:
>    - JSON (like the ES/OS exporters use for indexing)
>    - Avro with Schema Registry (common for Kafka-first architectures)
>    - Protobuf
>    - Raw MsgPack (Zeebe's internal format)
>    - Configurable (user picks format)?

**Answer:**


### Q6
> Should the exporter use a **Kafka Schema Registry** (Confluent, Apicurio, etc.) for schema evolution, or ship with self-describing messages (e.g., JSON with embedded type info)?

**Answer:**


### Q7
> What should the **Kafka message key** be for each record type? Options include:
>    - `partitionId-position` (unique, matches ES/OS document ID pattern)
>    - `processInstanceKey` (enables per-instance ordering)
>    - Configurable per value type?

**Answer:**


### Q8
> Should Kafka **message headers** include metadata like `valueType`, `intent`, `recordType`, `brokerVersion`, `partitionId`, `tenantId` — so consumers can route/filter without deserializing the payload?
> ## Topic Strategy & Routing

**Answer:**


### Q9
> Should each `ValueType` go to a **separate Kafka topic** (e.g., `camunda.process-instance`, `camunda.job`, `camunda.incident`), or should all events go to a **single topic** (e.g., `camunda.events`), or should this be **user-configurable** (map value types to topics)?

**Answer:**


### Q10
> What **topic naming convention** should be used? Should there be a configurable prefix (like the ES/OS `index.prefix`)? Should tenant ID be part of the topic name for multi-tenant deployments?

**Answer:**


### Q11
> When users configure "mapping/filtering of event types to endpoints" (from the spec), does this mean:
>     - Filtering which `ValueType`s are exported at all (like the existing `FilterConfiguration` in ES/OS exporters)?
>     - Filtering by `Intent` within a value type (e.g., only `COMPLETED` events for process instances)?
>     - Filtering by `RecordType` (COMMAND vs EVENT vs COMMAND_REJECTION)?
>     - Routing different value types to different Kafka topics?
>     - All of the above?
> ## Reliability & Delivery Guarantees

**Answer:**


### Q12
> What **delivery guarantee** is required?
>     - **At-least-once** (the exporter SPI naturally supports this via position acknowledgment — records are replayed from the last acknowledged position on restart)?
>     - **Exactly-once** (requires Kafka transactions + idempotent producer)?
>     - Should this be configurable?

**Answer:**


### Q13
> How should **Kafka producer failures** be handled?
>     - Block and retry indefinitely (like the existing ES/OS exporters — they retry the `export()` call)?
>     - Drop and continue (like `app-integrations-exporter` with `continueOnError`)?
>     - Dead-letter queue for failed records?
>     - Configurable behavior?

**Answer:**


### Q14
> Should the exporter support **batching** records into fewer Kafka `send()` calls (like the ES bulk API pattern), or should each Zeebe record produce one Kafka message? What are the target latency SLAs (sub-second? seconds?)?

**Answer:**


### Q15
> If Kafka is unreachable, should the exporter **block the entire export pipeline** (backpressuring the engine like ES/OS exporters do), or should it decouple via an internal buffer with overflow policy?
> ## Configuration

**Answer:**


### Q16
> What **Kafka producer configuration properties** should be exposed? Should there be a pass-through mechanism for arbitrary `producer.*` properties (like `acks`, `linger.ms`, `batch.size`, `compression.type`, `max.block.ms`)?

**Answer:**


### Q17
> For **authentication to Kafka**, which mechanisms must be supported at launch?
>     - PLAINTEXT (no auth)
>     - SASL/PLAIN (username + password)
>     - SASL/SCRAM-SHA-256/512
>     - SASL/OAUTHBEARER (for Confluent Cloud, Azure Event Hub via Kafka protocol)
>     - mTLS (client certificate)
>     - Should there be a pass-through for `security.protocol` and `sasl.*` properties?

**Answer:**


### Q18
> How should **TLS/SSL** be configured? Should the exporter accept:
>     - Truststore/keystore paths (JKS/PKCS12)?
>     - PEM certificate paths (like the Atomix cluster TLS)?
>     - Both?

**Answer:**


### Q19
> Where should the exporter be **configured in the unified config hierarchy**? Under `camunda.data.exporters.kafkaExporter` (existing exporter config pattern), or under a new `camunda.eventExport` namespace?
> ## SaaS Considerations

**Answer:**


### Q20
> For **Camunda SaaS**, how will users configure the Kafka exporter? Is there a SaaS control plane API, a UI in the Camunda Console, or environment-variable-based configuration? Does SaaS require a different configuration surface than Self-Managed YAML?

**Answer:**


### Q21
> In SaaS, how will **credentials** (Kafka bootstrap servers, SASL passwords, Schema Registry URLs) be managed? Via Camunda Console secrets? Vault integration? This affects whether the exporter reads config from environment variables, a secrets manager, or YAML.

**Answer:**


### Q22
> Should SaaS deployments support **customer-managed Kafka** only (Camunda connects to customer's Kafka), or also **Camunda-managed Kafka** (Camunda operates the Kafka cluster)?

**Answer:**


### Q23
> Are there **network connectivity constraints** in SaaS? Will the exporter need to connect to customer Kafka clusters over the public internet (requiring TLS, authentication), through VPC peering, or via Private Link/PrivateLink endpoints?
> ## Multi-Tenancy & Data Isolation

**Answer:**


### Q24
> In a **multi-tenant** deployment, should events from different tenants be exported to:
>     - Different Kafka topics (e.g., `camunda.{tenantId}.process-instance`)?
>     - The same topic with `tenantId` in the message key or headers?
>     - Separate Kafka clusters?
>     - Configurable per tenant?

**Answer:**


### Q25
> Should the exporter enforce **data isolation** — ensuring a tenant's events only go to that tenant's configured Kafka endpoint? Or is this an infrastructure concern outside the exporter?
> ## Monitoring & Operability

**Answer:**


### Q26
> What **metrics** should the Kafka exporter expose? Baseline candidates from existing exporters include:
>     - Export latency (time from record commit to Kafka ack)
>     - Batch size distribution
>     - Failed/retried sends
>     - Records exported per value type
>     - Kafka producer buffer utilization
>     - Are there additional Kafka-specific metrics needed (e.g., topic partition lag)?

**Answer:**


### Q27
> Should the exporter expose a **health indicator** (Spring Boot actuator) reflecting Kafka connectivity status? The existing exporters have no dedicated health indicator.

**Answer:**


### Q28
> Should the exporter integrate with the existing **`/actuator/exporters` management API** for enable/disable/delete at runtime, or does it need additional management endpoints (e.g., to view topic assignments, lag)?
> ## Event Schema & Content

**Answer:**


### Q29
> Should exported events include **process variables**? Variables can be large (MBs of JSON). Options:
>     - Always include variables
>     - Never include variables (reference only)
>     - Configurable include/exclude by variable name (like the existing `VariableNameFilter`)
>     - Configurable max variable size with truncation

**Answer:**


### Q30
> Should the exporter produce events in a **domain-friendly schema** (e.g., flattened process instance events with business-meaningful field names) or a **raw protocol schema** (the full `Record<RecordValue>` with all internal metadata like positions, keys, partitions)?

**Answer:**


### Q31
> Should the exporter support the existing **audit log transformation** from `zeebe/exporter-common/auditlog/` to produce structured audit log entries, or should it export raw engine records and leave transformation to consumers?

**Answer:**


### Q32
> How should **binary/large data** (e.g., BPMN XML in deployment records, form JSON) be handled? Inline in the Kafka message, or a reference/pointer pattern?
> ## Security & Compliance

**Answer:**


### Q33
> Should the exporter support **field-level redaction** or masking of sensitive data before publishing to Kafka (e.g., masking variable values, PII in user task assignments)?

**Answer:**


### Q34
> Should the exporter validate that the **target Kafka cluster** meets minimum security requirements (e.g., TLS enabled, authentication configured), or should it allow any configuration?

**Answer:**


### Q35
> Does the exporter need to support **encryption at rest** for any local buffering, or is that delegated to the Kafka cluster's configuration?
> ## Testing & Rollout

**Answer:**


### Q36
> For **integration testing**, should the test suite use Testcontainers with a real Kafka broker (like the ES/OS exporter ITs), or an embedded Kafka? Should tests cover Confluent Cloud, MSK, and Azure Event Hub compatibility?

**Answer:**


### Q37
> Should the exporter be **enabled by default** in new deployments, or opt-in? The existing ES/OS/RDBMS exporters are pre-configured in Spring profiles (`application-elasticsearch.yaml` etc.).

**Answer:**


### Q38
> What is the **upgrade path** for customers currently using the community Zeebe Kafka Exporter? Should the official exporter be schema-compatible, or is a migration guide sufficient?
> ## Extensibility & Future Destinations

**Answer:**


### Q39
> For the planned **Azure Event Hub** support (next phase), should Azure Event Hub be treated as "Kafka protocol compatible" (using Kafka client with `SASL/OAUTHBEARER` and Event Hub namespace), or should it have its own native transport using the Azure SDK?

**Answer:**


### Q40
> Should the transport abstraction support **multiple simultaneous destinations** (e.g., export `PROCESS_INSTANCE` events to Kafka topic A *and* Azure Event Hub B in the same deployment), or is it one destination per exporter instance (users register multiple exporter instances for multiple destinations)?

**Answer:**


### Q41
> Should the exporter support **webhook/callback** for delivery confirmation, or is Kafka's built-in acknowledgment (`acks=all`) sufficient for the "reliable" requirement?

**Answer:**


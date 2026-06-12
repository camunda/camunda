# Plan Questions

> Session: 39f932d6
> Request: # Value Proposition Statement  Enable organisations to reliably, securely, and in near-real time, ex...
> Generated: 2026-03-19T10:27:00.457Z

<!-- Fill in your answers below each question. Leave blank to let the agent decide. -->
<!-- When done, run: swarm plan --resume -->

## PM / Requirements (`plan-clarify`)

### Q1
> Is this a single deliverable or should it be phased (e.g., Phase 1: Kafka Self-Managed, Phase 2: Kafka SaaS, Phase 3: Azure Event Hub)? If phased, what is the MVP for Phase 1?

**Answer:**

First Phase should be Camunda 8 Self-Managed. In first phase we do not need Azure Event Hub. Ideally we can support both Kafka Self-Managed and SaaS (Confluent) in first phase, but if scope is tight, Self-Managed ships first and SaaS follows in phase 2 (consistent with Q4). The Confluent/SaaS support in phase 1 refers to customers connecting to Confluent Cloud from their Self-Managed deployment, not the Camunda SaaS platform.


### Q2
> Should this ship as a built-in exporter (always available, configured via `camunda.*` properties) or as an optional external JAR that users deploy alongside the broker?

**Answer:**

Initially, this should be a built-in exporter, but should be designed in such a way that later extraction is possible should we wish to add support for a broader exporter system.


### Q3
> Is this exporter intended to replace the community Zeebe Kafka Exporter, or coexist with it? Should there be a migration path from the community exporter?

**Answer:**

Yes, it should replace it. Ideally, if possible, there would be docs to show how to go from community to camunda one. There can be breaking changes though.


### Q4
> What Camunda 8 version is this targeting (8.10, 8.11, later)? Does the SaaS delivery have a different timeline than Self-Managed?

**Answer:**

Ideally all for 8.10. Self-Managed can ship in first phase and SaaS second phase. We can split it and first focus on Self-Managed.


### Q5
> Should this be implemented as a Zeebe Exporter (implementing the `Exporter` SPI, running inside the broker JVM per partition) or as an external service consuming from the existing exporters (e.g., reading from Elasticsearch/OpenSearch and forwarding to Kafka)?

**Answer:**

For the initial implementation, it should be a new Exporter that implements the exporter interface. We would ideally use this opportunity to decouple exporters more generally though, so that they do not need to keep in lockstep.


### Q6
> If it's a Zeebe Exporter, should it follow the `app-integrations-exporter` pattern (simple `Exporter` class with `ExporterFactory`) or the more complex `camunda-exporter` pattern (with `ExportHandler` dispatch, entity caching, and background tasks)?

**Answer:**

It should follow the Camunda Exporter pattern, which has more robust configuration and error handling


### Q7
> Should the Kafka exporter run on every partition or only on specific partitions (like the RDBMS exporter which registers certain handlers only on partition 1 for definition-scoped records)?

**Answer:**

It should run on every partition, but it might be possible to limit some record types to partition 1 if those records are always processed there (i.e. deployments)


### Q8
> Should this exporter be additive (runs alongside the existing camunda-exporter/ES/OS/RDBMS exporter) or could it be a replacement for the secondary storage exporter in some deployments?

**Answer:**

It should be additive, but we should use the opportunity to further decouple exporters and allow them to proceed at their own rate. If Kafka exporting fails, other exporters should be able to proceed regardless.


### Q9
> The spec says "any event produced by Zeebe." There are 60+ `ValueType` values and 3 `RecordType` values (COMMAND, EVENT, COMMAND_REJECTION). Should all combinations be exportable, or only EVENTs (successfully processed records)? Should command rejections be included?

**Answer:**

Only Events, consider making it configurable for the end customer / user.


### Q10
> Should the filtering granularity be at the `ValueType` level (e.g., "export all PROCESS_INSTANCE records"), the `Intent` level (e.g., "export only PROCESS_INSTANCE/ELEMENT_COMPLETED"), or both?

**Answer:**

Consider making it configurable for maximum flexibility. Provisionally the default can be all records of all value types.


### Q11
> Should there be variable-level filtering (include/exclude variables by name pattern, exclude variable values, or filter by BPMN process ID) similar to what the `zeebe/exporter-filter` module already supports?

**Answer:**

Yes, good idea.


### Q12
> Should the audit log transformation layer (`zeebe/exporter-common/auditlog/`) be reused to produce audit-log-shaped events, or should raw Zeebe records be exported as-is, or both options?

**Answer:**

In the initial we can ignore the audit log transformation and allow the consumer to determine any transformations they see fit for audit purposes.


### Q13
> Should users be able to define multiple independent Kafka export configurations (e.g., process events → topic A on cluster X, incident events → topic B on cluster Y)?

**Answer:**

Yes


### Q14
> Which Kafka client library should be used? The standard Apache `kafka-clients` library, or a higher-level abstraction like Spring Kafka? What minimum Kafka broker version must be supported?

**Answer:**

Use the standard `kafka-clients` library version 3, and rely on its backwards compatibility guarantees to 2.x Kafka brokers


### Q15
> What serialization format should records use on the Kafka topic? Options include: (a) JSON (matching existing ES/OS export format), (b) Avro with Schema Registry, (c) Protobuf, (d) configurable. Should there be a schema evolution strategy?

**Answer:**

Eventually, this should be configurable. For the initial implementation, we could also start with JSON to ship something faster and move to configurable format later. This is consistent with the community exporter anyway.


### Q16
> How should Kafka topics be structured? Options: (a) one topic per `ValueType` (e.g., `camunda.process-instance`, `camunda.job`), (b) single topic with all events, (c) user-configurable topic-per-event-type mapping, (d) user-defined topic with `ValueType` as a header/key?

**Answer:**

Option C


### Q17
> What should the Kafka message key be? Options: partition ID, record key, process instance key, business key, or configurable? This affects Kafka partition ordering guarantees.

**Answer:**

Use `partitionId-position` as the Kafka message key (like the community exporter and consistent with Design Q7). This guarantees ordering per Zeebe partition. To also guarantee ordering per record key (as stated in Q38), all records from the same Zeebe partition must route to the same Kafka partition, which can be achieved through the Kafka partitioning strategy.


### Q18
> Should Kafka headers carry metadata (e.g., `valueType`, `intent`, `tenantId`, `partitionId`) for consumer-side filtering without deserialization?

**Answer:**

Yes, and also the record type. The values should still be in the JSON as well.


### Q19
> What delivery semantics are required? At-least-once (with potential duplicates on exporter restart) or exactly-once (using Kafka transactions)? The existing exporter position tracking provides at-least-once naturally.

**Answer:**

Keep it simple by maintaining at-least-once


### Q20
> Should the exporter support Kafka producer tuning parameters (e.g., `acks`, `batch.size`, `linger.ms`, `compression.type`, `buffer.memory`, `max.block.ms`) via configuration, or should these be hardcoded to safe defaults?

**Answer:**

Hardcoded with sensible defaults can be initially acceptable for some settings, but batch size and memory should be configurable. We could consider exposing a complete config property map that is flexible enough for any generic kafka config, allowing customers even more flexibility without having to expose iondividual application properties


### Q21
> In a multi-tenant deployment, should events be filtered by tenant so that each tenant's events go to a separate topic or Kafka cluster? Or should all tenants' events land on the same topic with `tenantId` as metadata?

**Answer:**

Same topic with tenantId as metadata.


### Q22
> For SaaS specifically, is this a per-cluster feature (each customer cluster has its own Kafka export config) or a platform-level feature (Camunda SaaS infrastructure routes events)?

**Answer:**

Per Cluster configuration.


### Q23
> Should tenants be able to configure their own Kafka endpoints in SaaS, or does Camunda operate a shared Kafka infrastructure that customers consume from?

**Answer:**

Customers configure their own Kafka endpoints (customer-managed Kafka). Camunda does not operate shared Kafka infrastructure. Consistent with Design Q22.


### Q24
> What authentication mechanisms must be supported for Kafka connections? SASL/PLAIN, SASL/SCRAM, SASL/OAUTHBEARER, mTLS, Kerberos? Which are required for MVP vs. later?

**Answer:**

Look at what we support for Kafka connectors today at Camunda and do it the same way.


### Q25
> Should the exporter support TLS encryption for Kafka connections? Is mTLS (mutual TLS) required?

**Answer:**

Not necessarily in first phase.


### Q26
> Should there be data masking/redaction for sensitive fields (e.g., process variables containing PII) before export? The current exporters have no masking — is this acceptable for Kafka too?

**Answer:**

Exclude PII/sensitive data handling in the first iteration. We could get inspiration from the filters we have built now for Optimize (in the Zeebe Elastic exporter) for future phases. See also Design Q33 for field-level redaction considerations.


### Q27
> How should Kafka credentials (SASL passwords, keystore passwords, OAuth client secrets) be managed? Via broker configuration properties, environment variables, HashiCorp Vault, or all of the above?

**Answer:**

For SM this should be handled via usual configuration / environment variables similar to others.


### Q28
> Should there be access control on which users/roles can configure the Kafka exporter, or is it purely an infrastructure/admin concern?

**Answer:**

It's an Admin concern for now in Console SaaS and in SM it's a backend configuration.


### Q29
> For compliance, should exported events include the actor identity (authenticated user/client who triggered the command), which is available in `record.getAuthorizations()`?

**Answer:**

Exclude that for the initial implementation


### Q30
> Should the Kafka exporter be configured via unified `camunda.*` properties (like RDBMS exporter), `zeebe.broker.exporters.*` properties (like ES/OS exporters), or both with migration support?

**Answer:**

Unified configuration properties only


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

Your proposal above looks fine, but nest export under `data`


### Q32
> Should topic-to-event-type mapping be configurable at runtime (hot-reload) or only at startup? The existing exporter framework only supports startup configuration.

**Answer:**

Startup config is fine.


### Q33
> Should there be a validation/dry-run mode that checks Kafka connectivity and topic existence without starting the export?

**Answer:**

Not required for the initial implementation, but we should at least log errors if connection cannot be made or misconfiguration is detected at startup.


### Q34
> What throughput target is expected? Records per second? The existing ES exporter handles thousands of records/second with batched bulk writes — should the Kafka exporter match or exceed this?

**Answer:**

Ideally match it.


### Q35
> How should the exporter handle Kafka unavailability? Options: (a) block/retry indefinitely (existing exporter pattern — the broker pauses exporting until the target recovers), (b) circuit breaker with bounded buffer, (c) dead-letter queue. Note that blocking is the current default for all Zeebe exporters.

**Answer:**

The exporter should never block other exporters. It should be completely decoupled with configuration to handle what happens when Kafka is unreachable (e.g., buffer with max size and time, drop, etc.). See Design Q15 for detailed strategy.


### Q36
> Should there be a bounded in-memory buffer with backpressure to the broker when Kafka is slow, or should it follow the existing exporter pattern of "retry until success, blocking all exports"?

**Answer:**

Yes, implement a bounded in-memory buffer with configurable overflow policy (drop oldest, drop newest, log and skip). This should be configurable and very clear for end-users in documentation.


### Q37
> Should the exporter support batching to Kafka (buffering N records or M bytes before producing), and if so, what are the default thresholds?

**Answer:**

Yes, this should be done in a way consistent with the Camunda Exporter. It can use the same defaults initially.


### Q38
> How should record ordering be guaranteed? Per partition? Per process instance? Global ordering is not feasible with Kafka partitioning — what ordering guarantee is acceptable to users?

**Answer:**

Ordering should be guaranteed per record key and per Zeebe partition ID


### Q39
> For the "easily extensible to Azure Event Hub" requirement — Azure Event Hub has a Kafka-compatible API. Should the architecture assume that future targets (Azure Event Hub, AWS MSK) are just different Kafka configurations, or should there be an abstract `EventStreamTransport` interface with pluggable implementations?

**Answer:**

The architecture should assume future targets beyond native Kafka, and should be designed abstractly so that extensibility can be achieved later


### Q40
> For truly non-Kafka targets (AWS SQS/SNS, Google Pub/Sub, AMQP), should the architecture anticipate a generic event streaming abstraction from the start, or should Kafka be implemented directly and refactored later?

**Answer:**

Aim for a healthy balance. The initial implementation can be quite biased towards Kafka, but should consider extensibility in future and using an appropriate level of abstraction to support that.


### Q41
> Should the extensibility be at the configuration level (different connection properties for different Kafka-compatible systems) or at the code level (pluggable transport implementations loaded via SPI)?

**Answer:**

I think it's unavoidable that it would be both initially. The configuration should be flexible in a similar way that the exporter configuration is flexible for ES/OS exporters. The code implementation can be Kafka based initially, but use interfaces that allow additional implementations later.


### Q42
> What metrics should be exposed? Suggestions: records exported (by type), export latency, Kafka producer errors, batch sizes, buffer utilization. Should these follow the existing Micrometer pattern used by other exporters?

**Answer:**

Yes, these suggestions are fine and should follow the existing Micrometer pattern used by other exporters.


### Q43
> Should there be a health check endpoint indicating the Kafka exporter's status (connected, lagging, erroring)?

**Answer:**

This is not essential for initial implementation, but would be nice to have.


### Q44
> Should there be an actuator endpoint to pause/resume the Kafka export at runtime, similar to the existing exporter pause/resume actuators?

**Answer:**

This is not essential for initial implementation, but would be nice to have.


### Q45
> What logging level of detail is expected for troubleshooting? Should individual record exports be logged at TRACE, batches at DEBUG, errors at WARN?

**Answer:**

Individual record exports at TRACE, batch send operations at DEBUG, Kafka producer errors and connection issues at WARN, configuration errors and fatal failures at ERROR. Follow existing broker logging conventions.


### Q46
> Should the Kafka record format be identical to the JSON format used by the ES/OS exporters (including the `sequence` field for deduplication), or should it be a different, potentially simpler format?

**Answer:**

It does not need to be the same as the ES/OS exporters so can exclude the sequence field - simpler is better with minimal or zero transformations from the raw record data. This allows for maximum flexibility for consumers to do their own transformations as needed.


### Q47
> Should there be a schema versioning strategy so consumers can handle format changes across Camunda versions? E.g., a `schemaVersion` field in each message or Kafka Schema Registry integration?

**Answer:**

A schema version is probably sufficient initially


### Q48
> Should the exporter include BPMN element metadata (element names, process definition names) that require process cache lookups, or just raw record data (keys and IDs only)?

**Answer:**

Raw record data only, to keep it simple and avoid the complexity of maintaining a process cache in the exporter. Consumers can enrich with metadata if needed by correlating with other data sources.


### Q49
> Should large variable values be truncated in the export (like ES's `ignore_above: 8191`), or always exported in full?

**Answer:**

Export in full by default, but consider making it configurable with a sensible default (e.g., 10KB) to prevent issues with extremely large variables. This is similar to how the ES exporter handles it, but we can allow users to disable truncation if they want the full data in Kafka.


### Q50
> What documentation artifacts are required? API reference, configuration guide, Confluent Cloud tutorial, IBM Event Streams tutorial, Self-Managed Kafka tutorial, migration guide from community exporter?

**Answer:**

- migration guide from community exporter
- configuration guide for integration with Kafka SM and Confluent Cloud
- Unified configuration guide
- Documentation reference for how it works with other exporters


### Q51
> Should there be example consumer applications (e.g., a sample Kafka consumer that writes to a database, or a Kafka Connect sink example)?

**Answer:**

Documented examples would be good, but they can be in the form of code snippets in the documentation rather than full sample applications.


### Q52
> For customers migrating from the community Zeebe Kafka Exporter, should the new exporter produce compatible message formats, or is a breaking change acceptable with migration documentation?

**Answer:**

Ideally compatible


## Engineering (`plan-eng-clarify`)

### Q1
> Should the Kafka exporter be a new Maven module under `zeebe/exporters/kafka-exporter/` (following the pattern of `elasticsearch-exporter`, `opensearch-exporter`, `app-integrations-exporter`), or should it extend the existing `app-integrations-exporter` by adding a `KafkaTransport` alongside the existing `HttpTransportImpl`?

**Answer:**

A new maven module under `zeebe/exporters/kafka-exporter/`


### Q2
> The app-integrations-exporter already has a clean `Transport<T>` interface abstraction. Should we refactor/generalize the app-integrations-exporter into a multi-transport exporter (HTTP + Kafka + future), or build a standalone Kafka exporter module that may share some infrastructure (batching, subscription) but has its own `Exporter` implementation?

**Answer:**

Build it independently of the app-integrations-exporter (this will eventually be removed), but reuse or repurpose parts where appropriate.


### Q3
> The implementation notes mention designing for extensibility toward Azure Event Hub, AWS SNS/SQS, Google Pub/Sub, etc. Should there be a shared abstract "event streaming exporter" module (akin to `zeebe/exporter-common/`) that provides the transport abstraction, batching, topic routing, and serialization, with each destination being a thin transport adapter? Or is this over-engineering for now?

**Answer:**

Yes if it can be implemented without over-engineering or blowing up complexity. Otherwise it would be fine to solve for the known use cases right now without complex layers of abstraction.


### Q4
> Should the Kafka exporter implement the `ExporterFactory` SPI (like `RdbmsExporterFactory` does), or should it use the default reflection-based instantiation via `DefaultExporterFactory`?

**Answer:**

Use the `ExporterFactory`.


### Q5
> What Kafka topic naming strategy should be used? Options include: one topic per `ValueType` (e.g., `camunda-process-instance`, `camunda-job`, `camunda-incident`) — ~69 possible topics; one topic per logical domain group (e.g., `camunda-process-events`, `camunda-task-events`, `camunda-identity-events`); a single topic (e.g., `camunda-events`) with all events, consumers filter by headers/payload; fully user-configurable topic mapping per `ValueType`. Which approach, or should all be supported via configuration?

**Answer:**

Ideally all of the above, handled via configuration


### Q6
> What should the Kafka message key be for partitioning? Options: `partitionId` (preserves Zeebe partition ordering in Kafka); `processInstanceKey` (groups all events for a process instance); `ValueType` + key (groups by entity); user-configurable key expression. This significantly affects consumer ordering guarantees.

**Answer:**

Zeebe partitionId-position is good for initial implementation, which will also guarantee entity ordering. Use `partitionId-position` as the Kafka **message key** (unique, consistent with PM Q17 and Design Q7). To ensure ordering per Zeebe partition, use a custom partitioner or key prefix strategy so all records from the same Zeebe partition route to the same Kafka partition.


### Q7
> Should the exporter support configurable topic-per-ValueType mapping (e.g., "send `PROCESS_INSTANCE` events to topic X, `INCIDENT` events to topic Y, everything else to a catch-all topic Z"), or is a simpler model sufficient?

**Answer:**

By default, have a default catch-all topic (`camunda-events` for example). Allow customers to configure value types to specific topics, with the default being used for anything not configured


### Q8
> Should Kafka headers be populated with metadata (e.g., `valueType`, `intent`, `partitionId`, `recordType`, `brokerVersion`) to enable consumer-side filtering without deserialization?

**Answer:**

Yes — include `valueType`, `intent`, `partitionId`, `recordType`, `tenantId`, and `brokerVersion` as Kafka headers (consistent with PM Q18 and Design Q8).


### Q9
> What serialization format should be used for Kafka messages? The codebase already uses: JSON (app-integrations-exporter, Jackson); MsgPack (engine internals); SBE (wire protocol); Protobuf (gRPC gateway). Should the Kafka exporter support JSON only, or also Avro (with Schema Registry) and/or Protobuf? Avro with Schema Registry is the Kafka ecosystem standard for schema evolution.

**Answer:**

JSON is fine for initial implementation, but we should target having configurable alternatives if achievable in scope, or at least design with a view to adding it later.


### Q10
> What should the event payload schema look like? Options: raw `Record.toJson()` output (full fidelity, includes all fields like `position`, `sourceRecordPosition`, `key`, metadata); a curated/simplified schema per `ValueType` (like the `Event` sealed interface in app-integrations-exporter which flattens/transforms fields); CloudEvents specification envelope wrapping the record data. Raw gives maximum flexibility; curated gives better DX but requires maintaining schema per type.

**Answer:**

Initially raw is fine.


### Q11
> Should the exporter produce a Kafka Schema Registry compatible schema (Avro or JSON Schema) for each event type? If so, should schemas be auto-generated from the `RecordValue` interfaces in `zeebe/protocol`, or manually maintained?

**Answer:**

Not for the initial implementation. JSON with a `schemaVersion` field in each message is sufficient (consistent with PM Q47). Schema Registry support can be added later when Avro/Protobuf serialization is implemented.


### Q12
> The `Record` interface includes `authorizations` and `agent` fields that are stripped by ES/OS exporters for security. Should these fields be excluded by default from Kafka messages as well, or should this be configurable?

**Answer:**

Exclude them


### Q13
> Should the Kafka exporter reuse the existing `DefaultRecordFilter` and `FilterConfiguration` from `zeebe/exporter-filter/`, or does it need its own filtering mechanism? The existing filter supports: ValueType inclusion/exclusion; RecordType filtering (EVENT vs COMMAND vs COMMAND_REJECTION); variable name/type filtering; BPMN process ID filtering. Are all of these relevant for Kafka, or should the Kafka exporter only support a subset?

**Answer:**

These are likely all relevant, but we can start with a subset (ValueType and RecordType) and add the more advanced filters later if needed.


### Q14
> The implementation notes say "support export of any event that is produced by zeebe." Does "event" here mean only `RecordType.EVENT` records, or also `COMMAND` and `COMMAND_REJECTION` records? The camunda-exporter exports EVENTs + COMMAND_REJECTIONs. The app-integrations-exporter only exports specific intent-filtered EVENTs.

**Answer:**

Default to `RecordType.EVENT` only (consistent with PM Q9: "Only Events"), but make it configurable so users can opt in to `COMMAND_REJECTION` records if needed. Do not export `COMMAND` records by default.


### Q15 (e.g., "topic A gets only process events, topic B gets only incident events"), or is a single global filter sufficient?

**Answer:**

Per topic filtering gives maximum flexibility, but we can start with a simpler global filter and add per-topic filtering later if needed.


### Q16
> How should the exporter be configured? The codebase has two patterns: broker-level YAML via `ExporterCfg.args` (map of arbitrary key-value pairs, instantiated via Jackson); unified configuration via `camunda.*` Spring properties (with `CamundaExporterConfigurationApplier`). Should the Kafka exporter support only the `ExporterCfg.args` approach, or also integrate into the unified `camunda.data.exporters.kafka.*` configuration namespace?

**Answer:**

Unified Configuration only


### Q17
> What Kafka-specific configuration properties are required? At minimum: `bootstrap.servers`; `topic` / topic mapping; authentication (SASL mechanism, credentials); TLS/SSL settings; producer tuning (`acks`, `linger.ms`, `batch.size`, `compression.type`, `max.block.ms`). Should we expose all Kafka producer properties as a passthrough map (e.g., `kafka.producer.*`), or curate a subset with sensible defaults?

**Answer:**

Expose all through a map


### Q18
> Should the configuration allow passing raw Kafka producer properties as a map (enabling any `org.apache.kafka.clients.producer.ProducerConfig` setting), similar to how Spring Boot's `spring.kafka.producer.properties.*` works?

**Answer:**

Yes

### Q19
> What Kafka authentication mechanisms must be supported? The Kafka ecosystem uses: SASL/PLAIN (username + password); SASL/SCRAM-SHA-256 / SCRAM-SHA-512; SASL/OAUTHBEARER (OAuth2 / OIDC tokens); mTLS (mutual TLS with client certificates); no auth (development/testing). Which are required for initial release? The backup stores use a sealed `Authentication` interface pattern (see `GcsConnectionConfig.Authentication`) — should we follow the same pattern?

**Answer:**

SASL/PLAIN and SASL/SCRAM and no auth


### Q20
> For SaaS deployments connecting to customer-managed Kafka clusters, how are credentials provisioned and managed? Are they: stored in the Camunda SaaS control plane and injected as environment variables? Provided via a Vault/secrets manager integration? Configured through a SaaS admin UI? This affects the configuration model (e.g., whether we need `${ENV_VAR}` placeholder support).

**Answer:**

Consider how this is already handled in Connectors and initially use the same pattern.

### Q21
> Should the exporter support TLS truststore/keystore configuration (JKS/PKCS12 files) for connecting to Kafka clusters with custom CA certificates, following the pattern in `zeebe/gateway-grpc`'s TLS configuration?

**Answer:**

Not as dedicated configuration properties in the first iteration. Since Kafka producer properties are exposed via a pass-through map (Eng Q17/Q18), users can configure `ssl.truststore.location`, `ssl.truststore.password`, etc. directly through that map. This keeps implementation simple while still supporting TLS.


### Q22 At-least-once (Kafka `acks=all` + exporter position tracking — duplicates possible on restart): simplest, most common; exactly-once (Kafka transactions + idempotent producer): more complex, higher overhead. The existing exporter API's `Controller.updateLastExportedRecordPosition()` naturally supports at-least-once semantics.

**Answer:**

At-least-once


### Q23
> What should happen when the Kafka cluster is unreachable? Options: block the exporter (backpressure to the broker, other exporters continue); drop events after a timeout (data loss); buffer in-memory with a bounded queue (risk OOM). The existing `ExporterDirector` already handles slow exporters via soft-pause — is that sufficient?

**Answer:**

The exporter should never block other exporters. Implement a bounded buffer with configurable overflow policies (drop oldest, drop newest, or log and skip). This should be configurable with clear documentation. See Q35/Q36 and Design Q15 for consistency.


### Q24
> What are the target throughput and latency requirements? The implementation notes say "high-throughput" and "near-real-time." Can you quantify: target events/second per partition? Maximum acceptable end-to-end latency (engine event → Kafka)? Maximum acceptable impact on engine processing latency?

**Answer:**

Target comparable throughput to the ES exporter (thousands of records/second per partition). End-to-end latency target of sub-second under normal load. No quantified SLA for the initial implementation — measure and document actual performance. The exporter must not measurably degrade engine processing latency.


### Q25 (like the app-integrations-exporter's `Batch` class with size + time thresholds), or should it send each record individually? Kafka's own producer already batches internally via `linger.ms` and `batch.size`.

**Answer:**

Yes, it should batch in a similar way to the Camunda Exporter. The same defaults are also fine


### Q26
> In SaaS, how is the Kafka exporter enabled and configured? Is it: pre-deployed and activated per customer via a control plane API/UI? Dynamically enabled/disabled via the existing `ExporterState` mechanism (ENABLED/DISABLED/CONFIG_NOT_FOUND)? A separate managed service that connects to the Zeebe broker? This affects whether the exporter lives inside the broker process or as a sidecar.

**Answer:**

Out of scope for phase 1 (Self-Managed only). For phase 2, pre-deploy the exporter and activate per cluster via the Console UI, using the existing `ExporterState` mechanism for enable/disable. Consistent with PM Q4 and Design Q20.


### Q27 (e.g., customer Kafka accessible via public internet with TLS, or via VPC peering/Private Link?) This affects timeout defaults and retry strategies.

**Answer:**

No assumption but document the different scenarios

### Q28
> Should the Kafka exporter in SaaS support Confluent Cloud as a first-class target with dedicated configuration helpers (e.g., auto-configuring `sasl.mechanism=PLAIN`, `security.protocol=SASL_SSL`, API key/secret)?

**Answer:**

No dedicated configuration helpers in the first iteration. Since all Kafka producer properties are exposed via the pass-through map, users can configure Confluent Cloud settings (`sasl.mechanism=PLAIN`, `security.protocol=SASL_SSL`, API key/secret) directly. Provide a documented Confluent Cloud configuration example in the quickstart guide (consistent with PM Q50).


### Q29
> The existing `ExporterState` in `zeebe/dynamic-config/` supports runtime enable/disable of exporters. Should the Kafka exporter integrate with this for runtime reconfiguration (e.g., changing topics, adding/removing ValueType filters) without broker restart?

**Answer:**

Nice stretch goal


### Q30
> In a multi-partition Zeebe cluster, each partition runs its own exporter instance. Should the Kafka exporter: use the same Kafka producer config across all partitions (simplest)? Allow per-partition topic overrides? Include the `partitionId` in the topic name (e.g., `camunda-events-partition-1`)? The ES/OS exporters include `partitionId` in document IDs and routing — what's the Kafka equivalent?

**Answer:**

Same is fine for intitial implementation, don't include the partition id in the topic name (it can be in the header)


### Q31
> Some record types are only produced on partition 1 (definitions: PROCESS, DECISION, FORM, identity: USER, TENANT, ROLE, GROUP, AUTHORIZATION). Should the exporter handle this by: exporting everything from every partition (consumers deduplicate)? Only exporting definition/identity events from partition 1 (like `rdbms-exporter` does)? Making this configurable?

**Answer:**

Only export partition 1-based records from partition 1, but document this behaviour clearly so consumers understand where to find which records.


### Q32
> What testing scope is expected? Unit tests (mocked Kafka producer, following app-integrations-exporter patterns with `ExporterTestContext`/`ExporterTestController`); integration tests with embedded/containerized Kafka (Testcontainers); acceptance tests in `qa/` with a full Zeebe broker + Kafka cluster; performance/load tests?

**Answer:**

All of the above


### Q33
> Should there be update/compatibility tests (like `zeebe/qa/update-tests/`) verifying the Kafka exporter works across version upgrades?

**Answer:**

Yes


### Q34
> Which Kafka client library should be used? `org.apache.kafka:kafka-clients` (standard Apache Kafka client); a reactive/async variant? What minimum Kafka broker version should be supported (e.g., 2.8+, 3.0+)?

**Answer:**

`org.apache.kafka:kafka-clients`. Use version 3.x and rely on its backwards compatibility guarantees to support Kafka 2.x brokers as well.


### Q35
> The exporter API targets Java 8 for client compatibility. However, the Kafka exporter will be an internal module. Can it target Java 21 (matching the rest of the monorepo), or does it need to maintain Java 8 compatibility for external JAR loading?

**Answer:**

Match the rest of the mono repo and document this clearly so users understand the Java version requirement for the Kafka exporter module.


### Q36
> Should the Kafka client dependency be shaded to avoid version conflicts with customer deployments that may have their own Kafka client on the classpath?

**Answer:**

Yes


### Q37
> What metrics should the Kafka exporter expose via Micrometer? Candidates: records exported (counter, by ValueType); export latency (timer, record age at export time); Kafka send latency (timer); batch size distribution (histogram); failed sends (counter, by error type); Kafka producer buffer utilization. Should it follow the `ExporterMetrics` patterns in the existing broker, or extend them?

**Answer:**

These are a good starting point


### Q38
> Should the exporter emit structured log events for key lifecycle moments (connection established, connection lost, topic auto-created, authentication failure) following the `Loggers` pattern in the broker?

**Answer:**

Minimal lifecycle logging only: log connection failures, authentication errors, and misconfiguration at WARN/ERROR level (consistent with PM Q33). Skip verbose lifecycle logging (topic auto-created, connection established) for the initial implementation. Individual record exports at TRACE, batch operations at DEBUG.


### Q39
> The implementation notes mention documentation for "Confluent / IBM Kafka SaaS / Self-Managed / hybrid." Should this documentation: live in the monorepo under `docs/`? Be a separate page on `docs.camunda.io`? Include step-by-step quickstart guides per Kafka provider? Include Helm chart / Docker Compose examples for Self-Managed?

**Answer:**

This should be in `docs.camunda.io` and include step-by-step quickstart guides for Confluent Cloud and Self-Managed Kafka, with examples for both.


### Q40
> Is topic auto-creation in scope? If the target Kafka topic doesn't exist, should the exporter create it (with configurable partitions/replication factor), or fail with a clear error?

**Answer:**

Ideally configurable with disabled by default. Log clearly if auto-creation is disabled and the topic doesn't exist


### Q41
> Is a dead-letter topic support in scope? If a message fails to serialize or exceeds Kafka's max message size, should it be routed to a DLT?

**Answer:**

Not in scope for initial implementation


### Q42
> Is a management/admin API in scope for the initial release? For example, endpoints to query export lag, manually trigger flush, or view exporter health — similar to the existing `/actuator/exporters` endpoints?

**Answer:**

A nice stretch goal


### Q43
> Are Kafka Connect or Kafka Streams considerations in scope, or is this purely a Kafka Producer integration?

**Answer:**

Out of scope initially

## Design (`plan-design-clarify`)

### Q1
> Should this exporter be implemented as a new Zeebe exporter module (e.g., `zeebe/exporters/kafka-exporter/`) following the `Exporter` SPI pattern — or as a more generic "event streaming exporter" with a transport abstraction layer that Kafka is the first implementation of (mirroring how `app-integrations-exporter` uses a `Transport<T>` interface)?

**Answer:**

new exporter module


### Q2
> The implementation notes mention future Azure Event Hub, AWS SNS/SQS, AMQP, and Google Pub/Sub support. Should the module be named generically (e.g., `event-streaming-exporter`) with Kafka as the first transport, or should each destination be a separate exporter module (e.g., `kafka-exporter`, `azure-eventhub-exporter`)?

**Answer:**

Name it `kafka-exporter` for now (consistent with Eng Q1: `zeebe/exporters/kafka-exporter/`). Use internal interfaces/abstractions that would allow extracting a shared module later, but avoid a generic module name with only one implementation — it adds confusion. We can rename or extract a common event-streaming module when a second transport is added.


### Q3
> Should the Kafka client library be the Apache Kafka Clients (`org.apache.kafka:kafka-clients`) directly, or should a higher-level abstraction like Spring Kafka or Reactor Kafka be used? The existing exporter SPI is not Spring-aware, so Spring Kafka may add unnecessary coupling.

**Answer:**

Use `org.apache.kafka:kafka-clients`


### Q4
> There are currently ~65 `ValueType` entries in the protocol. The spec says "support export of any event produced by Zeebe." Does this mean all 65+ value types should be exportable, or should there be a curated default set (e.g., `PROCESS_INSTANCE`, `JOB`, `USER_TASK`, `INCIDENT`, `DECISION_EVALUATION`, `VARIABLE`, `DEPLOYMENT`) with opt-in for the rest?

**Answer:**

All


### Q5
> What serialization format should records be published in? Options include: JSON (like the ES/OS exporters use for indexing); Avro with Schema Registry (common for Kafka-first architectures); Protobuf; raw MsgPack (Zeebe's internal format); configurable (user picks format)?

**Answer:**

JSON is fine for initial implementation, but we should target having configurable alternatives if achievable in scope, or at least design with a view to adding it later.


### Q6
> Should the exporter use a Kafka Schema Registry (Confluent, Apicurio, etc.) for schema evolution, or ship with self-describing messages (e.g., JSON with embedded type info)?

**Answer:**

Self-describing messages are fine for initial implementation, but we should design with a view to adding Schema Registry support later if needed.

### Q7
> What should the Kafka message key be for each record type? Options include: `partitionId-position` (unique, matches ES/OS document ID pattern); `processInstanceKey` (enables per-instance ordering); configurable per value type?

**Answer:**

`partitionId-position` (unique, matches ES/OS document ID pattern)


### Q8
> Should Kafka message headers include metadata like `valueType`, `intent`, `recordType`, `brokerVersion`, `partitionId`, `tenantId` — so consumers can route/filter without deserializing the payload?

**Answer:**

Yes, include metadata in message headers


### Q9
> Should each `ValueType` go to a separate Kafka topic (e.g., `camunda.process-instance`, `camunda.job`, `camunda.incident`), or should all events go to a single topic (e.g., `camunda.events`), or should this be user-configurable (map value types to topics)?

**Answer:**

configurable as above


### Q10
> What topic naming convention should be used? Should there be a configurable prefix (like the ES/OS `index.prefix`)? Should tenant ID be part of the topic name for multi-tenant deployments?

**Answer:**

prefix + value type. No tenant id in the name during first implementation, but include tenantId in the headers


### Q11
> When users configure "mapping/filtering of event types to endpoints" (from the spec), does this mean: filtering which `ValueType`s are exported at all (like the existing `FilterConfiguration` in ES/OS exporters)? Filtering by `Intent` within a value type (e.g., only `COMPLETED` events for process instances)? Filtering by `RecordType` (COMMAND vs EVENT vs COMMAND_REJECTION)? Routing different value types to different Kafka topics? All of the above?

**Answer:**

All of the above, but we can start with a simpler subset (e.g., ValueType and RecordType filtering) and add more advanced filters later if needed.

### Q12
> What delivery guarantee is required? At-least-once (the exporter SPI naturally supports this via position acknowledgment — records are replayed from the last acknowledged position on restart); exactly-once (requires Kafka transactions + idempotent producer); should this be configurable?

**Answer:**

At-least-once


### Q13
> How should Kafka producer failures be handled? Block and retry indefinitely (like the existing ES/OS exporters — they retry the `export()` call); drop and continue (like `app-integrations-exporter` with `continueOnError`); dead-letter queue for failed records; configurable behavior?

**Answer:**

Configurable with options including bounded buffer with overflow policies (drop oldest, drop newest, log and skip). The exporter should never block other exporters. See Q35/Q36 and Engineering Q23 for consistency.


### Q14
> Should the exporter support batching records into fewer Kafka `send()` calls (like the ES bulk API pattern), or should each Zeebe record produce one Kafka message? What are the target latency SLAs (sub-second? seconds?)?

**Answer:**

Yes, use batching


### Q15
> If Kafka is unreachable, should the exporter block the entire export pipeline (backpressuring the engine like ES/OS exporters do), or should it decouple via an internal buffer with overflow policy?

**Answer:**

Not it should never block exporting, it should be completely decoupled with configuration to handle what happens when kafka is unreachable (e.g., buffer with max size and time, drop, etc.)


### Q16
> What Kafka producer configuration properties should be exposed? Should there be a pass-through mechanism for arbitrary `producer.*` properties (like `acks`, `linger.ms`, `batch.size`, `compression.type`, `max.block.ms`)?

**Answer:**

Use the map-based configuration approach to allow pass-through of all Kafka producer properties, with sensible defaults for common settings like `acks=all`, `linger.ms=100`, `batch.size=16384`, etc. Document recommended settings for different use cases (e.g., high throughput vs low latency).


### Q17
> For authentication to Kafka, which mechanisms must be supported at launch? PLAINTEXT (no auth); SASL/PLAIN (username + password); SASL/SCRAM-SHA-256/512; SASL/OAUTHBEARER (for Confluent Cloud, Azure Event Hub via Kafka protocol); mTLS (client certificate); should there be a pass-through for `security.protocol` and `sasl.*` properties?

**Answer:**

SASL/PLAIN, SASL/SCRAM-SHA-256/512, and no auth (consistent with Eng Q19). Since all Kafka producer properties are exposed via the pass-through map, SASL/OAUTHBEARER and mTLS are technically available to advanced users without dedicated support. Document common configurations for each auth mechanism.


### Q18
> How should TLS/SSL be configured? Should the exporter accept: truststore/keystore paths (JKS/PKCS12)? PEM certificate paths (like the Atomix cluster TLS)? Both?

**Answer:**

Via the Kafka producer properties pass-through map. Users configure `ssl.truststore.location`, `ssl.keystore.location`, `security.protocol`, etc. directly as Kafka properties. No dedicated TLS configuration abstraction for the initial implementation — the pass-through map handles all cases. Consistent with Eng Q21.


### Q19
> Where should the exporter be configured in the unified config hierarchy? Under `camunda.data.exporters.kafkaExporter` (existing exporter config pattern), or under a new `camunda.eventExport` namespace?

**Answer:**

`camunda.data.exporters.kafka` would be sufficient

### Q20
> For Camunda SaaS, how will users configure the Kafka exporter? Is there a SaaS control plane API, a UI in the Camunda Console, or environment-variable-based configuration? Does SaaS require a different configuration surface than Self-Managed YAML?

**Answer:**

It cannot be env var based, so customers must have a UI in console to do this

### Q21
> In SaaS, how will credentials (Kafka bootstrap servers, SASL passwords, Schema Registry URLs) be managed? Via Camunda Console secrets? Vault integration? This affects whether the exporter reads config from environment variables, a secrets manager, or YAML.

**Answer:**

Out of scope for phase 1 (Self-Managed only). For phase 2, follow the pattern established by Connectors for credential management (consistent with Eng Q20). Likely Console-managed secrets injected at runtime.


### Q22
> Should SaaS deployments support customer-managed Kafka only (Camunda connects to customer's Kafka), or also Camunda-managed Kafka (Camunda operates the Kafka cluster)?

**Answer:**

Customer-managed only

### Q23
> Are there network connectivity constraints in SaaS? Will the exporter need to connect to customer Kafka clusters over the public internet (requiring TLS, authentication), through VPC peering, or via Private Link/PrivateLink endpoints?

**Answer:**

Internet with TLS and authentication, but document the different scenarios and how to configure for them

### Q24
> In a multi-tenant deployment, should events from different tenants be exported to: different Kafka topics (e.g., `camunda.{tenantId}.process-instance`)? The same topic with `tenantId` in the message key or headers? Separate Kafka clusters? Configurable per tenant?

**Answer:**

Keep it simple for the initial implementation with all tenants in the same topic and tenantId in the headers. We can consider more complex multi-tenant routing in a future phase if needed, but this is likely sufficient for most use cases and keeps the architecture simpler.

### Q25
> Should the exporter enforce data isolation — ensuring a tenant's events only go to that tenant's configured Kafka endpoint? Or is this an infrastructure concern outside the exporter?

**Answer:**

We don't have a SaaS multi-tenancy solution just yet, so keep this out of scope for the initial implementation

### Q26
> What metrics should the Kafka exporter expose? Baseline candidates from existing exporters include: export latency (time from record commit to Kafka ack); batch size distribution; failed/retried sends; records exported per value type; Kafka producer buffer utilization; are there additional Kafka-specific metrics needed (e.g., topic partition lag)?

**Answer:**

Those are good for the initial implementation

### Q27
> Should the exporter expose a health indicator (Spring Boot actuator) reflecting Kafka connectivity status? The existing exporters have no dedicated health indicator.

**Answer:**

Stretch goal would be nice

### Q28
> Should the exporter integrate with the existing `/actuator/exporters` management API for enable/disable/delete at runtime, or does it need additional management endpoints (e.g., to view topic assignments, lag)?

**Answer:**

Existing

### Q29
> Should exported events include process variables? Variables can be large (MBs of JSON). Options: always include variables; never include variables (reference only); configurable include/exclude by variable name (like the existing `VariableNameFilter`); configurable max variable size with truncation?

**Answer:**

Include variables by default. Consider configuration to exclude or truncate them

### Q30
> Should the exporter produce events in a domain-friendly schema (e.g., flattened process instance events with business-meaningful field names) or a raw protocol schema (the full `Record<RecordValue>` with all internal metadata like positions, keys, partitions)?

**Answer:**

The raw protocol schema is fine for the initial implementation, as it provides maximum flexibility for consumers to do their own transformations as needed

### Q31
> Should the exporter support the existing audit log transformation from `zeebe/exporter-common/auditlog/` to produce structured audit log entries, or should it export raw engine records and leave transformation to consumers?

**Answer:**

Export raw engine records and leave transformation to customers

### Q32
> How should binary/large data (e.g., BPMN XML in deployment records, form JSON) be handled? Inline in the Kafka message, or a reference/pointer pattern?

**Answer:**

Inline is fine initially

### Q33
> Should the exporter support field-level redaction or masking of sensitive data before publishing to Kafka (e.g., masking variable values, PII in user task assignments)?

**Answer:**

Not in the initial implementation. This could be added in a future phase, potentially leveraging the filter patterns from the Zeebe Elastic exporter used for Optimize. See also Q26.


### Q34
> Should the exporter validate that the target Kafka cluster meets minimum security requirements (e.g., TLS enabled, authentication configured), or should it allow any configuration?

**Answer:**

Allow any configuration, but document recommended security settings and best practices for production deployments.

### Q35
> Does the exporter need to support encryption at rest for any local buffering, or is that delegated to the Kafka cluster's configuration?

**Answer:**

Keep the buffer in-memory to avoid the complexity of disk-based buffering and encryption

### Q36
> For integration testing, should the test suite use Testcontainers with a real Kafka broker (like the ES/OS exporter ITs), or an embedded Kafka? Should tests cover Confluent Cloud, MSK, and Azure Event Hub compatibility?

**Answer:**

Each test class should use Testcontainers with a real Kafka broker

### Q37
> Should the exporter be enabled by default in new deployments, or opt-in? The existing ES/OS/RDBMS exporters are pre-configured in Spring profiles (`application-elasticsearch.yaml` etc.).

**Answer:**

Opt-in. Disabled by default but can be enabled optionally via configuration.


### Q38
> What is the upgrade path for customers currently using the community Zeebe Kafka Exporter? Should the official exporter be schema-compatible, or is a migration guide sufficient?

**Answer:**

No automated upgrade path, but clear migration documentation. Aim for a similar JSON structure to the community exporter where natural (raw record JSON), but don't constrain the design to maintain exact byte-level compatibility. Document differences clearly. This is consistent with PM Q3 ("there can be breaking changes") and PM Q52 ("ideally compatible").

### Q39
> For the planned Azure Event Hub support (next phase), should Azure Event Hub be treated as "Kafka protocol compatible" (using Kafka client with `SASL/OAUTHBEARER` and Event Hub namespace), or should it have its own native transport using the Azure SDK?

**Answer:**

Treat it as Kafka protocol compatible (using `SASL/OAUTHBEARER` with the Event Hub namespace as the Kafka-compatible endpoint). This is simpler and avoids a separate SDK dependency. A native Azure SDK transport can be considered later only if the Kafka-compatible approach proves insufficient.


### Q40
> Should the transport abstraction support multiple simultaneous destinations (e.g., export `PROCESS_INSTANCE` events to Kafka topic A and Azure Event Hub B in the same deployment), or is it one destination per exporter instance (users register multiple exporter instances for multiple destinations)?

**Answer:**

Each exporter instance should have one destination, but we can allow multiple exporter instances to be configured in the same deployment for multiple destinations

### Q41
> Should the exporter support webhook/callback for delivery confirmation, or is Kafka's built-in acknowledgment (`acks=all`) sufficient for the "reliable" requirement?

**Answer:**

acks=all is sufficient for the initial implementation


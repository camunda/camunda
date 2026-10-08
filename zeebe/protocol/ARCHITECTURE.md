---
architecture_md: 1
component: camunda/camunda/zeebe/protocol
system: { id: orchestration-cluster, map: "camunda/camunda/SYSTEM.md" }
lifecycle: production
kind: [library]
summary: "The Zeebe protocol: the SBE schemas and public Java interfaces (Record, RecordValue types, ValueType, intents) that define the gateway-to-broker command format, the record format written to the log and handed to exporters, and the identifiers persisted in broker state."
team: { name: camunda/core-features, contact: unknown }
intake: { how: issue, template: "2. feature_request.yml", labels: [component/protocol] }
owns:
  - "SBE schema protocol.xml (schema id 0) with common-types.xml: ExecuteCommandRequest/Response, ExecuteQueryRequest/Response, ErrorResponse, RecordMetadata (the envelope of every log record) and BrokerInfo"
  - "SBE schema cluster-management-protocol.xml (schema id 1, package io.camunda.zeebe.protocol.management): admin, backup and checkpoint request/response messages between brokers and management clients"
  - "The numeric codes of ValueType, RecordType, RejectionType, ChannelType, PartitionRole, PartitionHealthStatus and errorCode"
  - "The public record API in io.camunda.zeebe.protocol.record: Record, RecordValue, every *RecordValue interface and nested value type, and the Immutable* types generated from them via @ImmutableProtocol"
  - "Intent enums per value type (io.camunda.zeebe.protocol.record.intent) and their short codes, the ValueType to RecordValue to Intent mapping (ValueTypeMapping) and the set of user-command value types (ValueTypes)"
  - "Protocol constants: PROTOCOL_VERSION, little-endian byte order, key layout (51 key bits, 13 partition bits, at most 8192 partitions), START_PARTITION_ID, DEPLOYMENT_PARTITION, and the reserved io.camunda.zeebe: task header names"
  - "The catalogue of RocksDB column family IDs and their GLOBAL / PARTITION_LOCAL scope (ZbColumnFamilies); the state stored behind each ID is the engine's"
  - "The module's API compatibility baseline: revapi.json and ignored-changes.json"
does_not_own:
  - { concept: "Record value implementations, msgpack encoding of record values, UnifiedRecordValue and the record classes the engine writes", owner: camunda/camunda/zeebe/protocol-impl }
  - { concept: "JSON deserialization of records (ZeebeProtocolModule for Jackson)", owner: camunda/camunda/zeebe/protocol-jackson }
  - { concept: "Engine-layer authorization enums AuthorizationResourceType, PermissionType, AuthorizationOwnerType, AuthorizationScope, DefaultRole, EntityType (same Java package, other module) and AuthzModelMapper", owner: camunda/camunda/security }
  - { concept: "Which commands are accepted, which events and rejections they produce, event appliers, and the state kept in each column family", owner: camunda/camunda/zeebe/engine }
  - { concept: "Exporter SPI (Exporter, Context, Controller) through which records reach exporters", owner: camunda/camunda/zeebe/exporter-api }
  - { concept: "Sending SBE messages between gateway and broker (broker-client, transport) and writing records to the log", owner: camunda/camunda/zeebe/broker }
  - { concept: "Zeebe gRPC API and Orchestration Cluster REST API (gateway.proto, OpenAPI)", owner: camunda/camunda/zeebe/gateway-protocol }
  - { concept: "Exporter index templates and secondary-storage schemas for exported records", owner: camunda/camunda/zeebe/exporters }
depends_on:
  - id: security-protocol
    component: camunda/camunda/security
    kind: library
    contract: "io.camunda:camunda-security-protocol: AuthorizationResourceType, PermissionType, AuthorizationOwnerType and the other engine-layer authorization enums used by AuthorizationRecordValue and identity record values"
    architecture: security/ARCHITECTURE.md
    versions: "same monorepo release; Java 8"
    workaround_policy: never
  - id: agrona
    component: "Agrona (org.agrona:agrona)"
    kind: external
    contract: "DirectBuffer and MutableDirectBuffer used by the generated SBE codecs"
    versions: "version.agrona in parent/pom.xml"
    workaround_policy: never
  - id: sbe-tool
    component: "Simple Binary Encoding (uk.co.real-logic:sbe-tool)"
    kind: external
    contract: "build-time code generator run by exec-maven-plugin (generate-sbe) with sbe.decode.unknown.enum.values=true and generated interfaces"
    versions: "version.sbe in parent/pom.xml"
    workaround_policy: never
  - id: immutables
    component: "Immutables (org.immutables:value)"
    kind: external
    contract: "annotation processor generating the Immutable* protocol types; provided scope, jdkOnly, no Jackson integration"
    versions: "version.immutables in parent/pom.xml"
    workaround_policy: never
consumers:
  - { who: camunda/camunda/zeebe/engine, via: "record value interfaces, intents, ValueType, ZbColumnFamilies, Protocol key encoding", promise: "same monorepo release; old records stay readable for replay after an update" }
  - { who: "camunda/camunda/zeebe/broker (broker, broker-client, logstreams, stream-platform, transport, backup, restore, zb-db, atomix, dynamic-config)", via: "generated SBE codecs (RecordMetadata, ExecuteCommandRequest/Response, BrokerInfo, cluster management messages)", promise: "wire and log compatible between the previous and the current minor during rolling updates (docs/zeebe/rolling_updates.md)" }
  - { who: "camunda/camunda/zeebe/protocol-impl, protocol-jackson, protocol-asserts, protocol-test-util", via: "implement or wrap the record interfaces and Immutable* types", promise: "same monorepo release" }
  - { who: "camunda/camunda/zeebe/exporter-api and through it camunda/camunda/zeebe/exporters", via: "Record<?> and record value interfaces handed to every exporter", promise: "revapi-checked against the previous minor" }
  - { who: "Custom exporters and tools outside the repo (Maven Central, in the BOM)", via: "io.camunda:zeebe-protocol with Java 8 bytecode", promise: "revapi-checked against the previous minor; new interface methods may appear at any minor" }
  - { who: "camunda/camunda/zeebe/gateway, zeebe/gateway-grpc, zeebe/gateway-rest, gateways/gateway-mapping-http, gateways/gateway-mcp and camunda/camunda/service", via: "ValueType, intents and record value types used to build broker requests; Protocol key helpers", promise: "same monorepo release" }
  - { who: "camunda/camunda/search, camunda/camunda/webapps-schema", via: "shared enums and constants (TenantOwned.DEFAULT_TENANT_IDENTIFIER, BatchOperationType, HistoryDeletionType and others)", promise: "same monorepo release" }
  - { who: "Optimize (optimize/backend, not a system member)", via: "records exported by the Elasticsearch/OpenSearch exporters, read with zeebe-protocol and zeebe-protocol-jackson", promise: "same monorepo release; TODO(confirm)" }
  - { who: "Camunda Process Test (camunda/camunda-process-test RecordStreamLogger), qa/, load-tests/, microbenchmarks/, debug-cli/", via: "record API and ValueType", promise: "none beyond revapi" }
exposes:
  - { contract: "Zeebe record API (Record, RecordValue, *RecordValue interfaces, Intent enums, ValueType, RecordType, RejectionType)", spec: src/main/java/io/camunda/zeebe/protocol/record, policy: "Maven artifact io.camunda:zeebe-protocol, in the BOM, Java 8. Revapi against backwards.compat.version (previous minor): adding methods to record interfaces is allowed because consumers don't implement them; enum constant order may change because ordinals aren't used; any other break needs a justified entry in ignored-changes.json" }
  - { contract: "Zeebe SBE protocol (protocol.xml, schema id 0, version 10)", spec: src/main/resources/protocol.xml, policy: "SBE message versioning: append fields with sinceVersion, never change a field's id, type or meaning; never reuse an enum code (value 252 is retired); bump the schema version; old and new brokers must read each other's messages and records during a rolling update" }
  - { contract: "Cluster management protocol (cluster-management-protocol.xml, schema id 1, version 2)", spec: src/main/resources/cluster-management-protocol.xml, policy: "not public: excluded from revapi and allowed to break (revapi.json); TODO(confirm) whether rolling updates still require compatibility" }
  - { contract: "RocksDB column family catalogue (ZbColumnFamilies)", spec: src/main/java/io/camunda/zeebe/protocol/ZbColumnFamilies.java, policy: "IDs are persisted in snapshots: never reuse or change an ID once released; obsolete families stay as DEPRECATED_*; ZbColumnFamiliesTest checks unique, sorted, non-negative IDs" }
  - { contract: "Protocol constants and key encoding (Protocol)", spec: src/main/java/io/camunda/zeebe/protocol/Protocol.java, policy: "keys stored in the log, RocksDB, secondary storage and client applications carry the partition ID in their top bits: KEY_BITS and PARTITION_BITS never change" }
  - { contract: "Extension point (internal): a new protocol type or value type", spec: src/main/java/io/camunda/zeebe/protocol/record/ImmutableProtocol.java, policy: "annotate the interface with @ImmutableProtocol to generate its Immutable* type; register a new ValueType in ValueTypeMapping and its Intent in Intent; ValueTypeMappingTest and IntentConsistencyTest fail otherwise. Outside consumers read records; they don't add types" }
constraints:
  - { id: C1, name: Wire and log compatibility across a rolling update, hard: true, ref: ../../docs/zeebe/rolling_updates.md }
  - { id: C2, name: Public Java API compatibility (revapi), hard: true, ref: revapi.json }
  - { id: C3, name: Persisted identifiers never change, hard: true, ref: src/main/java/io/camunda/zeebe/protocol/ZbColumnFamilies.java }
  - { id: C4, name: A new record or property reaches every place, hard: true, ref: ../../docs/zeebe/developer_handbook.md }
  - { id: C5, name: Authorization enums change in security-protocol and CSL together, hard: true, ref: ../../security/security-protocol/README.md }
  - { id: C6, name: Java 8 and a small Apache-licensed dependency set, hard: true, ref: pom.xml }
decisions: ../docs/adr/README.md
planning: { issue_templates: { increment: "3. task.yml", epic: "4. epic breakdown.yml" } }
---

# Architecture — camunda/camunda/zeebe/protocol

> Draft from the repository; not reviewed by its team. Facts marked `TODO(confirm)` are inferred.

## 1. Purpose

The Zeebe protocol defines the binary format the cluster speaks and stores: the SBE messages a
gateway sends to a broker, the envelope of every record on a partition's log, and the Java
interfaces through which the engine, exporters and outside tools read those records. It is a
`contracts` member of the [Orchestration Cluster](../../SYSTEM.md) system.

## 2. Ownership boundary

**Owns:** the SBE schemas in [`src/main/resources/`](src/main/resources/), the record API in
`io.camunda.zeebe.protocol.record` (record value interfaces, intents, `ValueType`,
`ValueTypeMapping`), the protocol constants in [`Protocol`](src/main/java/io/camunda/zeebe/protocol/Protocol.java),
the column family catalogue `ZbColumnFamilies`, and the module's revapi baseline.

The record *values* are not SBE. `RecordMetadata` is an SBE message; the value it describes is a
msgpack document written by `protocol-impl`. Adding a property to a record value therefore changes
the Java interface here and the msgpack implementation in `protocol-impl`, not `protocol.xml`
(see also [zeebe ADR 0005](../docs/adr/0005-810-job-lease.md), "SBE governs only the envelope").

Ownership: CODEOWNERS names no team for `zeebe/protocol`. The front matter uses
`camunda/core-features` because `.github/workflows/ci.yml` lists the `PROTOCOL` test group under
"Zeebe modules owned by @camunda/core-features", and the engine (same team) is its main consumer.
TODO(confirm): the owning team and its contact, and whether a CODEOWNERS line should be added.

**Does not own (route here instead):**

| If you need… | It belongs to | How to ask |
|---|---|---|
| A record value implementation, its msgpack properties, `UnifiedRecordValue`, `JsonSerializableToJsonTest` | `camunda/camunda/zeebe/protocol-impl` | TODO(confirm): same team? |
| JSON deserialization of records (`ZeebeProtocolModule`) | `camunda/camunda/zeebe/protocol-jackson` | TODO(confirm): same team? |
| A new `AuthorizationResourceType` or `PermissionType` constant | `camunda/camunda/security` (`security/security-protocol`, `@camunda/identity`), plus CSL | issue, `component/identity` |
| How a command is processed, which events it emits, an event applier | `camunda/camunda/zeebe/engine` | issue, `component/zeebe-engine` |
| The exporter SPI | `camunda/camunda/zeebe/exporter-api` | issue |
| Transport of SBE messages, log append, partitions | `camunda/camunda/zeebe/broker` (`@camunda/zeebe-distributed-platform`) | issue, `component/zeebe-platform` |
| A gRPC or REST API field | `camunda/camunda/zeebe/gateway-protocol` | issue, `component/c8-api` |
| An exporter's index template | `camunda/camunda/zeebe/exporters` | issue |

The authorization enums are the most frequent misplacement: they sit in the Java package
`io.camunda.zeebe.protocol.record.value` but are compiled in `security/security-protocol`.

TODO(confirm): whether `protocol-impl`, `protocol-jackson`, `protocol-asserts` and
`protocol-test-util` are part of this component (as the engine's draft assumes) or components of
their own. The [SYSTEM.md](../../SYSTEM.md) lists only `zeebe/protocol`.

## 3. Structure

| Path | Contents |
|---|---|
| `src/main/resources/protocol.xml`, `common-types.xml` | Zeebe protocol, schema id 0, version 10; enums `ValueType`, `RecordType`, `RejectionType`, `ChannelType`, … |
| `src/main/resources/cluster-management-protocol.xml` | Cluster management protocol, schema id 1, version 2 (`io.camunda.zeebe.protocol.management`) |
| `target/generated-sources/sbe` | SBE codecs generated at `generate-sources`; never edited by hand |
| `io.camunda.zeebe.protocol` | `Protocol`, `ZbColumnFamilies`, `ColumnFamilyScope`, `EnumValue`, `PartitionState` |
| `io.camunda.zeebe.protocol.record` | `Record`, `RecordValue`, `ValueTypeMapping`, `ValueTypes`, `@ImmutableProtocol` |
| `io.camunda.zeebe.protocol.record.value` (+ `deployment`, `management`, `scaling`) | One `*RecordValue` interface per value type and nested types |
| `io.camunda.zeebe.protocol.record.intent` (+ `management`, `scaling`) | One `Intent` enum per value type |

Direction: this module depends on no other Zeebe module; only on `security-protocol` and the
libraries in the front matter. Every other protocol module depends on it. Packages are
`@NullMarked` (jspecify).

## 4. Binding decisions

ADR index: [`zeebe/docs/adr/`](../docs/adr/README.md); cross-cutting ones in
[`docs/adr/`](../../docs/adr/README.md). No ADR is about the protocol itself; these shape it most:

- **Records over SBE envelopes with msgpack values** ([developer handbook](../../docs/zeebe/developer_handbook.md#how-to-create-a-new-record)):
  extending a value doesn't touch the SBE schema.
- **Rolling updates between adjacent minors** ([rolling updates](../../docs/zeebe/rolling_updates.md#protocol)):
  a broken SBE layout corrupts data silently (issue #14957).
- **Authorization enum ownership** ([CSL ADR-0016](https://github.com/camunda/camunda-security-library/blob/main/docs/adr/0016-authz-enum-ownership-and-layered-usage.md),
  [engine-expert authz-enums](../../.claude/skills/engine-expert/authz-enums.md)): protocol enums
  stay below `AuthzModelMapper`; layers above use CSL enums.
- **Agent records** ([zeebe ADR 0010](../docs/adr/0010-810-agent-execution-in-engine-records.md)):
  agent execution is carried in engine records and the `agent` field of `RecordMetadata`.

## 5. Planning constraints

Every plan must answer each of these (applies / n/a + decision or reason).

### C1 — Wire and log compatibility across a rolling update
- **Question:** Does the change touch `protocol.xml` or `cluster-management-protocol.xml`? Is every
  new field appended with `sinceVersion` and optional presence, is the schema version bumped, and can
  a broker of the previous minor read what the new one writes (and the reverse)?
- **Hard:** yes
- **Detail:** [rolling updates § Protocol](../../docs/zeebe/rolling_updates.md#protocol),
  [SBE message versioning](https://github.com/real-logic/simple-binary-encoding/wiki/Message-Versioning)

### C2 — Public Java API compatibility (revapi)
- **Question:** Does revapi pass against `backwards.compat.version`? If not, is the break one the
  rules in [`revapi.json`](revapi.json) allow, or does it need an entry with a justification in
  [`ignored-changes.json`](ignored-changes.json)? Who outside the repo (custom exporters) breaks?
- **Hard:** yes
- **Detail:** [`revapi.json`](revapi.json); the release workflow sets `backwards.compat.version`
  to the previous minor and deletes `ignored-changes.json` files (`camunda-platform-release.yml`).
  TODO(confirm): that step's paths (`protocol/…`) and its `dryRun` condition look inverted; whether
  `zeebe/protocol/ignored-changes.json` is in fact cleared after a minor (it still holds entries
  added since October 2024).

### C3 — Persisted identifiers never change
- **Question:** Does the change renumber, reuse or remove a `ValueType` code, an intent code, an
  SBE enum value or a `ZbColumnFamilies` ID that a released version may have written? Obsolete
  entries are deprecated and keep their number.
- **Hard:** yes
- **Detail:** `ZbColumnFamiliesTest`; commit `336dfb0eda9` ("revert ZbColumnFamilies value —
  already released in stable/8.9").

### C4 — A new record or property reaches every place
- **Question:** For a new value type: `ValueType` in `protocol.xml`, an `Intent` enum, a
  `@ImmutableProtocol` `RecordValue`, `ValueTypeMapping`, `ValueTypes` if users send it, then
  `protocol-impl`, exporters, Camunda Process Test and the engine's supported types. For a new
  property: the interface here, `protocol-impl`, the Elasticsearch exporter template (strict). Who
  does each step?
- **Hard:** yes
- **Detail:** [developer handbook](../../docs/zeebe/developer_handbook.md#how-to-create-a-new-record),
  [engine-expert records](../../.claude/skills/engine-expert/records.md)

### C5 — Authorization enums change in security-protocol and CSL together
- **Question:** Does the change add an authorization resource or permission type? It goes into
  CSL, `security/security-protocol`, `AuthzModelMapper` and its test, then the SBE/record side here.
- **Hard:** yes
- **Detail:** [security-protocol README](../../security/security-protocol/README.md),
  [security ARCHITECTURE.md](../../security/ARCHITECTURE.md) C1–C2

### C6 — Java 8 and a small Apache-licensed dependency set
- **Question:** Does the change add a dependency or use an API newer than Java 8? The artifact is
  Apache 2.0 licensed and on the classpath of every exporter and outside tool.
- **Hard:** yes
- **Detail:** [`pom.xml`](pom.xml) (`version.java` 8), [`LICENSE`](LICENSE). TODO(confirm): why
  Java 8 is kept, and whether Apache 2.0 is a hard rule for new dependencies.

## 6. Data and persistence

No store of its own. The formats it defines are persisted by others: SBE `RecordMetadata` plus
values in the partition log and snapshots (broker), column family IDs in RocksDB (engine, `zb-db`),
keys in secondary storage and client applications. That is why C1 and C3 are hard.

## 7. Cross-cutting qualities

- **Security:** commands carry the caller's claims in the `authorization` field of
  `ExecuteCommandRequest` and `RecordMetadata`; the gateway's channel (`ChannelType`) and MCP tool
  name are recorded too. The engine decides; this module only carries the data.
- **Tenancy:** record values that belong to a tenant implement `TenantOwned`;
  `DEFAULT_TENANT_IDENTIFIER` is `<default>`.
- **Forward compatibility:** SBE codecs decode unknown enum values instead of failing
  (`sbe.decode.unknown.enum.values=true`, `EnumDecodingTest`).
- **Performance:** SBE codecs work on Agrona buffers without allocation; Immutable* types compute
  hashes lazily.

## 8. Delivery

As Orchestration Cluster SYSTEM.md (one monorepo release). Specific here: published to Maven Central
as `io.camunda:zeebe-protocol` and managed in the BOM; revapi compares against the previous minor
(`backwards.compat.version` in `parent/pom.xml`, set by the release workflow).
TODO(confirm): rules for backporting an SBE, intent or column family change to a `stable/*` branch,
where a patch release then writes records the previous patch cannot read.

## 9. Testing expectations

- Unit tests in this module: `ValueTypeMappingTest`, `IntentConsistencyTest`,
  `IntentEncodingDecodingTest`, `EnumDecodingTest`, `ZbColumnFamiliesTest`, `ProtocolTest`.
  New value types and intents must pass them.
- Revapi runs in the module build (`./mvnw verify -pl zeebe/protocol -DskipTests=false`).
- Serialization of every value: `JsonSerializableToJsonTest` in `protocol-impl`.
- Mixed-version behaviour: `zeebe/qa/update-tests` and `zeebe-version-compatibility.yml`.
  TODO(confirm): which suite proves rolling-update compatibility of an SBE change.

## 10. Planning conventions

- Issues: label `component/protocol` (in use for protocol issues), templates
  `2. feature_request.yml`, `3. task.yml`, `4. epic breakdown.yml`. The `create-issue` skill maps
  `zeebe/` to `component/zeebe-engine`. TODO(confirm): which label the team triages.
- ADRs for protocol decisions go to [`zeebe/docs/adr/`](../docs/adr/README.md) (`NNNN-<minor>-…`).
- TODO(confirm): plans directory and ID prefix for plan refs.

## 11. Glossary

- **Record:** one entry on a partition's log: SBE `RecordMetadata` plus a msgpack value.
- **Value type / intent:** what a record is about (`ValueType.JOB`) and what happened or is asked
  (`JobIntent.COMPLETE`, `JobIntent.COMPLETED`).
- **Record type:** `COMMAND`, `EVENT` or `COMMAND_REJECTION`.
- **Zeebe protocol vs gateway protocol:** this module is the broker-internal and exporter format;
  `zeebe/gateway-protocol` is the client-facing gRPC and REST API.
- **Column family:** a RocksDB keyspace for one kind of engine state, identified by a
  `ZbColumnFamilies` ID; `GLOBAL` ones are copied to new partitions.

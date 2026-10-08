---
architecture_md: 1
component: camunda/camunda/zeebe/exporters
system: { id: orchestration-cluster, map: "camunda/camunda/SYSTEM.md" }
lifecycle: production
kind: [library]
summary: "The exporters the broker runs on every partition: the Camunda and RDBMS exporters that turn engine records into the secondary-storage data Operate, Tasklist, Admin and the REST API read; the dedicated Elasticsearch and OpenSearch record exporters Optimize reads; and the analytics (OTLP telemetry) and app-integrations (HTTP) exporters."
team: { name: camunda/data-layer, contact: "#team-data-layer" }
intake: { how: issue, template: "2. feature_request.yml", labels: [component/data-layer] }
owns:
  - "Camunda exporter (camunda-exporter, io.camunda.exporter.CamundaExporter, autoconfigured id camundaexporter): the mapping from records to webapps-schema entities in Elasticsearch/OpenSearch, its ~90 ExportHandlers, the handler list in DefaultExporterResourceProvider and the exported value types in CamundaExporterRecordFilter"
  - "Camunda exporter write path: in-memory entity batching keyed by id and type (ExporterBatchWriter), flush thresholds (bulk size, memory limit, delay), ES/OS BatchRequest upserts and the exporter position acknowledged after a flush"
  - "Camunda exporter background tasks: the ES/OS archiver (dated archive indices, rollover), incident update and pending-incident tasks, batch-operation update, history deletion, usage-metrics and audit-log archiving, and the ILM/ISM retention settings it applies"
  - "RDBMS exporter (rdbms-exporter, factory id rdbms): its ~55 RdbmsExportHandlers, the exporter-position table bookkeeping, replay reconciliation on open, async-replication acknowledgement (ReplicationController) and TTL history cleanup tasks"
  - "Elasticsearch and OpenSearch record exporters (zeebe-elasticsearch-exporter, zeebe-opensearch-exporter): the zeebe-record-<value-type> index templates in src/main/resources, index naming and routing, per-value-type and variable filters, Optimize mode, ILM/ISM retention policy and the Jackson mixins that drop fields from the documents"
  - "Analytics exporter (analytics-exporter): the handler catalogue, OTLP event and metric names and camunda.* attributes, sequence numbers for gap detection, hash sampling and license-derived request signing towards telemetry.camunda.io"
  - "App-integrations exporter (app-integrations-exporter): the user-task event mapping, batching, retries and authentication (API key or OAuth client credentials) towards the App Integration Backend"
  - "Each exporter's args model (*ExporterConfiguration / Config classes) and the ExporterConfigMerger implementations that merge a physical tenant's args with the root entry"
  - "Exporter-specific metrics: zeebe.camunda.exporter.*, zeebe.rdbms.exporter.* (meters registered in db/rdbms), zeebe.app.integrations.exporter.*"
does_not_own:
  - { concept: "Exporter SPI (Exporter, Context, Controller, ExporterConfigMerger, StrictConfiguration)", owner: camunda/camunda/zeebe/exporter-api }
  - { concept: "Record format, record value interfaces, ValueType and intents", owner: camunda/camunda/zeebe/protocol }
  - { concept: "Which records the engine writes and what they mean", owner: camunda/camunda/zeebe/engine }
  - { concept: "Loading and running exporters, exporter positions in the broker, pause/resume and enable/disable of exporters", owner: camunda/camunda/zeebe/broker }
  - { concept: "ES/OS index templates, index descriptors and entity classes the Camunda exporter writes", owner: camunda/camunda/webapps-schema }
  - { concept: "RDBMS tables (Liquibase changelog), MyBatis mappers, RdbmsWriters and the execution queue", owner: camunda/camunda/db }
  - { concept: "Creating, migrating and initializing ES/OS indices and RDBMS schemas per physical tenant", owner: camunda/camunda/schema-manager }
  - { concept: "Reading exported data (queries, readers, authorization of reads) and the ES/OS connection factory", owner: camunda/camunda/search }
  - { concept: "camunda.data.* and camunda.physical-tenants.* properties and their mapping onto exporter args", owner: camunda/camunda/configuration }
  - { concept: "Spring wiring of the RDBMS exporter factory and the distributions that bundle the exporters", owner: camunda/camunda/dist }
  - { concept: "Importing zeebe-record indices into Optimize's own schema", owner: camunda/camunda/optimize }
  - { concept: "ES/OS snapshot backup and restore of exported data", owner: camunda/camunda/webapps-backup }
depends_on:
  - id: exporter-api
    component: camunda/camunda/zeebe/exporter-api
    kind: library
    contract: "io.camunda:zeebe-exporter-api: Exporter lifecycle (configure, open, export, close), Controller (position, scheduled tasks), Context (configuration, physical tenant, MeterRegistry, record filter), ExporterConfigMerger SPI"
    versions: "same monorepo release; revapi-checked public API"
    workaround_policy: never
  - id: protocol
    component: camunda/camunda/zeebe/protocol
    kind: schema
    contract: "io.camunda:zeebe-protocol: Record and *RecordValue interfaces, ValueType, intents"
    architecture: zeebe/protocol/ARCHITECTURE.md
    versions: same monorepo release
    workaround_policy: never
  - id: exporter-shared
    component: "camunda/camunda/zeebe/exporter-common, zeebe/exporter-filter, zeebe/exporter-config-support"
    kind: library
    contract: "zeebe-exporter-common (audit-log and wait-state transformers, entity cache, background tasks; camunda and rdbms exporters), zeebe-exporter-filter (record and variable filters; ES/OS exporters), zeebe-exporter-config-support (IndexPrefixValidation, ExporterIsolationClaims, ExporterConfigMergeSupport)"
    versions: same monorepo release
    workaround_policy: never
  - id: webapps-schema
    component: camunda/camunda/webapps-schema
    kind: schema
    contract: "io.camunda:webapps-schema: IndexDescriptors, templates and entity classes the Camunda exporter writes"
    versions: same monorepo release
    workaround_policy: never
  - id: schema-manager
    component: camunda/camunda/schema-manager
    kind: library
    contract: "io.camunda:camunda-schema-manager: SchemaManager the Camunda exporter calls on open (createSchema, isSchemaReadyForUse)"
    versions: same monorepo release
    workaround_policy: never
  - id: rdbms
    component: camunda/camunda/db
    kind: library
    contract: "io.camunda:camunda-db-rdbms: RdbmsWriters, ExecutionQueue, RdbmsSchemaManagerRegistry, writer metrics; tables from db/rdbms-schema"
    versions: same monorepo release
    workaround_policy: never
  - id: search
    component: camunda/camunda/search
    kind: library
    contract: "camunda-search-client-connect (ConnectConfiguration, ES/OS connectors incl. AWS) and camunda-search-domain"
    architecture: search/ARCHITECTURE.md
    versions: same monorepo release
    workaround_policy: never
  - id: security-protocol
    component: camunda/camunda/security
    kind: library
    contract: "io.camunda:camunda-security-protocol: authorization enums and AuthzModelMapper (Camunda and RDBMS exporters)"
    architecture: security/ARCHITECTURE.md
    versions: same monorepo release
    workaround_policy: never
  - id: security-library
    component: camunda/camunda-security-library
    kind: library
    contract: "io.camunda:camunda-security-library-api (Camunda exporter)"
    versions: "pinned by version.camunda-security-library in parent/pom.xml"
    workaround_policy: never
  - id: broker
    component: camunda/camunda/zeebe/broker
    kind: library
    contract: "io.camunda:zeebe-broker: ExporterFactory, implemented by RdbmsExporterFactory (rdbms-exporter only)"
    versions: same monorepo release
    workaround_policy: never
  - id: webapps-common
    component: camunda/camunda/webapps-common
    kind: library
    contract: "io.camunda:webapps-common (Camunda exporter)"
    versions: same monorepo release
    workaround_policy: never
  - id: secondary-storage
    component: "Elasticsearch, OpenSearch (incl. AWS OpenSearch), relational databases"
    kind: external
    contract: "elasticsearch-java, opensearch-java (+ AWS SDK v2 SigV4) clients; JDBC through db/rdbms"
    versions: "TODO(confirm): supported versions matrix (see § 6)"
    workaround_policy: never
  - id: otel
    component: "OpenTelemetry Java SDK and telemetry.camunda.io"
    kind: external
    contract: "OTLP/HTTP logs (/v1/logs) and metrics (/v1/metrics); SDK shaded to io.camunda.shaded.otel in the standalone jar"
    versions: "version.opentelemetry in parent/pom.xml; TODO(confirm)"
    workaround_policy: never
  - id: app-integration-backend
    component: "App Integration Backend (external service)"
    kind: runtime-api
    contract: "HTTP POST of batched user-task events with X-Org-Id, X-Cluster-Id, X-Physical-Tenant-Id headers"
    versions: "TODO(confirm): where the backend and its API are specified"
    workaround_policy: never
consumers:
  - { who: "camunda/camunda/search and camunda/camunda/db readers, and through camunda/camunda/service the REST API, Operate, Tasklist and Admin", via: "documents and rows the Camunda and RDBMS exporters write into the webapps-schema and rdbms-schema shapes", promise: "same monorepo release; data written by the previous minor stays readable after an upgrade" }
  - { who: "camunda/camunda/zeebe/broker", via: "Exporter implementations loaded by class name or factory, and their positions", promise: "same monorepo release" }
  - { who: "camunda/camunda/configuration and camunda/camunda/dist", via: "exporter configuration classes, CamundaExporterConfigurationApplier, RdbmsExporterFactory", promise: "same monorepo release" }
  - { who: "Optimize (optimize/, not a system member)", via: "zeebe-record-* indices from the Elasticsearch/OpenSearch exporters (Optimize mode, required value types)", promise: "TODO(confirm): document shape kept compatible within and across minors" }
  - { who: "Self-Managed users who run the ES/OS exporters for their own tooling", via: "zeebe-record-* indices and exporter args documented on docs.camunda.io", promise: "TODO(confirm): supported, not extended with new features" }
  - { who: "Camunda telemetry pipeline behind telemetry.camunda.io", via: "OTLP events, counters and the export_window gauge", promise: "event names and camunda.* attributes are a contract with the backend; TODO(confirm) its owner" }
  - { who: "App Integration Backend (Slack, Teams, webhooks)", via: "HTTP event batches", promise: "TODO(confirm)" }
  - { who: "camunda/camunda/webapps-backup, debug-cli, qa/acceptance-tests, load-tests", via: "exporter ids (camundaexporter), positions, exporters under test", promise: "none beyond same release" }
exposes:
  - { contract: "Exporter classes for broker configuration", spec: "camunda-exporter/src/main/java/io/camunda/exporter/CamundaExporter.java", policy: "class names io.camunda.exporter.CamundaExporter, io.camunda.zeebe.exporter.ElasticsearchExporter, io.camunda.zeebe.exporter.opensearch.OpensearchExporter, io.camunda.exporter.analytics.AnalyticsExporter, io.camunda.exporter.appint.AppIntegrationsExporter appear in user configuration; ids camundaexporter and rdbms are reserved and autoconfigured (ADR-0008 D2). TODO(confirm): renames need a deprecation period" }
  - { contract: "Exporter args (configuration models)", spec: "elasticsearch-exporter/src/main/java/io/camunda/zeebe/exporter/ElasticsearchExporterConfiguration.java", policy: "ES/OS args are @StrictConfiguration (unknown keys fail) and documented on docs.camunda.io; Camunda and RDBMS exporter args are set from camunda.data.secondary-storage.* by configuration. TODO(confirm): deprecation policy for args" }
  - { contract: "zeebe-record-<value-type> indices and templates (ES/OS exporters)", spec: elasticsearch-exporter/src/main/resources/elasticsearch, policy: "templates are strict and versioned per broker version; read by Optimize. Kept, not extended with new features (docs/architecture/overview.md)" }
  - { contract: "ExporterConfigMerger implementations (Camunda, ES, OS)", spec: camunda-exporter/src/main/resources/META-INF/services/io.camunda.zeebe.exporter.api.ExporterConfigMerger, policy: "registered via ServiceLoader; merge root and physical-tenant args per key, tenant wins (ADR-0008 D3)" }
  - { contract: "Analytics OTLP events and metrics", spec: analytics-exporter/AGENTS.md, policy: "event names in AnalyticsAttributes; snake_case camunda.* attributes; contiguous sequence numbers survive restart; no PII; default off" }
  - { contract: "Exporter metrics", spec: camunda-exporter/src/main/java/io/camunda/exporter/metrics, policy: "TODO(confirm): whether meter names are relied on by dashboards and alerts outside the repo" }
  - { contract: "Extension point (internal): Camunda exporter ExportHandler", spec: camunda-exporter/src/main/java/io/camunda/exporter/handlers/ExportHandler.java, policy: "teams in the repo add a handler (handlesRecord, generateIds, createNewEntity, updateEntity, flush, getIndexName) to DefaultExporterResourceProvider and its value type to CamundaExporterRecordFilter; handlers sharing an (index, id) must stay correct for any flush boundary. Not a customer extension point" }
  - { contract: "Extension point (internal): RdbmsExportHandler", spec: rdbms-exporter/src/main/java/io/camunda/exporter/rdbms/RdbmsExporterWrapper.java, policy: "registered per ValueType in RdbmsExporterWrapper; writes go through db/rdbms writers only" }
  - { contract: "Extension point (internal): AnalyticsHandler", spec: analytics-exporter/AGENTS.md, policy: "registered in AnalyticsHandlerCatalog with an exact-set test; the record filter is derived from the registry" }
constraints:
  - { id: C1, name: A new record or property reaches every exporter that must see it, hard: true, ref: ../../docs/zeebe/developer_handbook.md }
  - { id: C2, name: New export features go to the Camunda and RDBMS exporters, hard: true, ref: ../../docs/architecture/overview.md }
  - { id: C3, name: Storage parity across Elasticsearch/OpenSearch and RDBMS, hard: true, ref: ../../docs/monorepo-docs/architecture/components/secondary-storage/working-with-secondary-storage.md }
  - { id: C4, name: Schema changes are additive and land in the schema modules first, hard: true, ref: ../../docs/monorepo-docs/architecture/components/secondary-storage/working-with-secondary-storage.md }
  - { id: C5, name: Export is idempotent and independent of flush and replay boundaries, hard: true, ref: ../exporter-api/src/main/java/io/camunda/zeebe/exporter/api/Exporter.java }
  - { id: C6, name: Exporters must not stall the partition, hard: true, ref: ../../docs/adr/management/005-per-physical-tenant-schema-initialization.md }
  - { id: C7, name: Physical-tenant configuration and isolation, hard: true, ref: ../../docs/adr/orchestration-cluster/0008-physical-tenant-exporter-assignment-and-args-merge.md }
  - { id: C8, name: Upgrades from the previous minor, hard: true, ref: ../../docs/monorepo-docs/architecture/components/secondary-storage/archiving.md }
  - { id: C9, name: Write volume and archiving cost, hard: false, ref: ../../docs/monorepo-docs/architecture/components/secondary-storage/working-with-secondary-storage.md }
  - { id: C10, name: Analytics exporter invariants, hard: true, ref: analytics-exporter/AGENTS.md }
decisions: ../docs/adr/README.md
planning: { issue_templates: { increment: "3. task.yml", epic: "4. epic breakdown.yml" } }
---

# Architecture — camunda/camunda/zeebe/exporters

> Draft from the repository; not reviewed by its team. Facts marked `TODO(confirm)` are inferred.

## 1. Purpose

The exporters are the write side of the Orchestration Cluster's CQRS split: the broker hands every
record a partition commits to each configured exporter, and the exporters turn them into the
secondary-storage data the REST API, Operate, Tasklist, Admin and Optimize read, or forward them to
telemetry and app-integration backends. It is the `export` member of the
[Orchestration Cluster](../../SYSTEM.md) system ([architecture overview § Export path](../../docs/architecture/overview.md)).

## 2. Ownership boundary

**Owns:** six Maven modules under this directory, each an `Exporter` the broker loads:

| Exporter | Writes to | Read by | Status |
|---|---|---|---|
| Camunda exporter (`camunda-exporter`) | ES/OS indices defined in `webapps-schema` | `search` → service, REST API, webapps | Target of new export features; autoconfigured when ES/OS is the secondary storage |
| RDBMS exporter (`rdbms-exporter`) | Tables in `db/rdbms-schema` via `db/rdbms` writers | `db/rdbms` readers → service, REST API, webapps | Autoconfigured when an RDBMS is the secondary storage |
| Elasticsearch exporter (`elasticsearch-exporter`) | `zeebe-record-*` indices, templates owned here | Optimize, user tooling | Kept for Self-Managed and Optimize, not extended |
| OpenSearch exporter (`opensearch-exporter`) | `zeebe-record-*` indices, templates owned here | Optimize, user tooling | As the Elasticsearch exporter |
| Analytics exporter (`analytics-exporter`) | OTLP/HTTP to `telemetry.camunda.io` | Camunda telemetry pipeline | Opt-in, Self-Managed, 8.10+ only ([README](analytics-exporter/README.md)) |
| App-integrations exporter (`app-integrations-exporter`) | HTTP to the App Integration Backend | Slack, Teams, webhooks | Only user-task events today ([README](app-integrations-exporter/README.md)) |

The Camunda exporter decides *how a record becomes an entity* (handlers, batching, archiving) but not
*the shape of the entity*: index templates and descriptors are `webapps-schema`'s, and schema
creation is `schema-manager`'s, even though the exporter triggers it on open. The RDBMS exporter
likewise maps records to `db/rdbms` writers; tables, mappers and writers are `db`'s. The dedicated
ES/OS exporters are the exception: they own their `zeebe-record` templates.

Ownership: CODEOWNERS names no team for `zeebe/exporters`. The front matter uses
`camunda/data-layer` because the `Exporter` unit-test suite in `.github/workflows/ci.yml` is owned
by `@camunda/data-layer`, and [working with secondary storage](../../docs/monorepo-docs/architecture/components/secondary-storage/working-with-secondary-storage.md)
names `#team-data-layer` for exporter and schema questions. The same `ci.yml` comment block also
calls the exporter modules "owned by @camunda/core-features". TODO(confirm): the owning team, and
whether a CODEOWNERS line should be added.

TODO(confirm): whether the analytics and app-integrations exporters belong to this component. They
are not in the `ci.yml` exporter group, depend on none of the storage modules, and are mostly
committed by other people; they may be components of other teams.

**Does not own (route here instead):**

| If you need… | It belongs to | How to ask |
|---|---|---|
| A new method on `Exporter`, `Context` or `Controller`, or a change to `ExporterConfigMerger` | `camunda/camunda/zeebe/exporter-api` | issue; revapi-guarded public API |
| A new record, property or intent | `camunda/camunda/zeebe/protocol`, `zeebe/engine` | issue, `component/zeebe-engine` |
| A field or index for the Camunda exporter to write | `camunda/camunda/webapps-schema` (`@camunda/data-layer` on the templates) | PR with Data Layer sign-off |
| A table, column or writer for the RDBMS exporter | `camunda/camunda/db` (`db/rdbms-schema` changelog, `@camunda/data-layer`) | PR with Data Layer sign-off |
| Index creation, migration or per-tenant schema init | `camunda/camunda/schema-manager` | issue, `component/data-layer` |
| A query or filter over exported data | `camunda/camunda/search` (ES/OS), `camunda/camunda/db` (RDBMS) | issue, `component/data-layer` |
| A new `camunda.data.*` property | `camunda/camunda/configuration` | issue |
| How the broker runs, pauses or acknowledges exporters | `camunda/camunda/zeebe/broker` (`@camunda/zeebe-distributed-platform`) | issue, `component/zeebe-platform` |
| Optimize's import of `zeebe-record` data | `camunda/camunda/optimize` | issue, `component/optimize` |

TODO(confirm): owners of `zeebe/exporter-common`, `zeebe/exporter-filter`,
`zeebe/exporter-config-support` and `zeebe/exporter-test`. They are outside this directory and not
listed in [SYSTEM.md](../../SYSTEM.md), but `exporter-common` and `exporter-test` run in the same
`ci.yml` exporter group; they may belong to this component.

## 3. Structure

| Path | Contents |
|---|---|
| `camunda-exporter/…/exporter/CamundaExporter.java` | Entry point; `CamundaExporterRecordFilter` lists the exported value types (events and rejections) |
| `camunda-exporter/…/exporter/DefaultExporterResourceProvider.java` | The handler list, caches and index descriptors the exporter uses |
| `camunda-exporter/…/exporter/handlers/` | `ExportHandler` implementations (`auditlog`, `batchoperation`, `usage`, `waitstate`, …) |
| `camunda-exporter/…/exporter/store/` | `ExporterBatchWriter`, ES/OS `BatchRequest` |
| `camunda-exporter/…/exporter/tasks/` | Background tasks: `archiver`, `incident`, `batchoperations`, `historydeletion` |
| `camunda-exporter/…/exporter/{cache,config,index,notifier,metrics}` | Caffeine caches, args model and config merger, target-index location, incident webhook, metrics |
| `rdbms-exporter/…/exporter/rdbms/` | `RdbmsExporterFactory` → `RdbmsExporterWrapper` (handlers) → `RdbmsExporter` (positions, flush); `handlers`, `cache`, `replication`, `tasks` |
| `elasticsearch-exporter/…/zeebe/exporter/`, `opensearch-exporter/…/exporter/opensearch/` | Exporter, client, `RecordIndexRouter`, schema manager, config; templates in `src/main/resources/{elasticsearch,opensearch}/` |
| `analytics-exporter/…/exporter/analytics/` | Exporter, `handler/` catalogue, `sampling/`, OTel SDK manager ([AGENTS.md](analytics-exporter/AGENTS.md)) |
| `app-integrations-exporter/…/exporter/appint/` | `config`, `dispatch`, `mapper`, `subscription`, `transport` |

Direction: exporters depend on `exporter-api`, `protocol` and the storage modules, never on each
other, and nothing but the broker, `configuration` and `dist` depends on them (SYSTEM.md DR4,
DR5). The RDBMS exporter depends on `zeebe-broker` for `ExporterFactory`; TODO(confirm) whether
that interface should move to `exporter-api`. The ES and OS exporters are parallel copies: a change
to one (templates, mixins, filters) is made in both.

## 4. Binding decisions

ADR index: [`zeebe/docs/adr/`](../docs/adr/README.md) (no exporter ADR yet); cross-cutting ones in
[`docs/adr/`](../../docs/adr/README.md). The decisions that shape exporter work most:

- **New export features target the Camunda exporter; ES/OS exporters are kept, not extended**
  ([overview § Export path](../../docs/architecture/overview.md), SYSTEM.md § 5). Optimize still
  needs the Elasticsearch exporter.
- **Per-physical-tenant exporters** ([orchestration-cluster ADR-0008](../../docs/adr/orchestration-cluster/0008-physical-tenant-exporter-assignment-and-args-merge.md)):
  `camundaexporter` and `rdbms` are autoconfigured per tenant; other exporters run only when listed
  in `exporters-assigned`; args merge only through an `ExporterConfigMerger`; exporters declare
  isolation claims (index prefix, lifecycle policy).
- **Per-tenant schema initialization** ([management ADR-005](../../docs/adr/management/005-per-physical-tenant-schema-initialization.md)):
  the exporter retries per partition until its tenant's schema is ready.
- **Exporting state from dynamic config** ([management ADR-006](../../docs/adr/management/006-exporting-state-via-dynamic-config.md)):
  pause and resume of exporting is the broker's, sourced from dynamic cluster config.
- **RDBMS layer on MyBatis and Liquibase** (`docs/monorepo-docs/architecture/components/secondary-storage/rdbms/adr/`).
- **Analytics exporter design** (in-JVM fire-and-forget, OTLP, HMAC auth, sequence numbers):
  [analytics-exporter AGENTS.md § Confirmed Design Decisions](analytics-exporter/AGENTS.md).

## 5. Planning constraints

Every plan must answer each of these (applies / n/a + decision or reason).

### C1 — A new record or property reaches every exporter that must see it
- **Question:** For a new value type or record property: is it added to the ES and OS exporter
  templates (strict), schema managers, filter config and `TestSupport`; to
  `CamundaExporterRecordFilter` and a Camunda exporter handler; to an RDBMS handler? If it is not
  exported yet, which tests still need the mapping?
- **Hard:** yes
- **Detail:** [developer handbook § Support a RecordValue in Exporters](../../docs/zeebe/developer_handbook.md#support-a-recordvalue-in-exporters-and-test-setups),
  [RDBMS developer guide](../../docs/monorepo-docs/architecture/components/secondary-storage/rdbms/developer-guide.md)

### C2 — New export features go to the Camunda and RDBMS exporters
- **Question:** Does the plan add behaviour to the Elasticsearch or OpenSearch exporter beyond
  keeping new records exportable? If so, why (Optimize needs it?), and who agreed?
- **Hard:** yes
- **Detail:** [overview § Export path](../../docs/architecture/overview.md)

### C3 — Storage parity across Elasticsearch/OpenSearch and RDBMS
- **Question:** Does the data the Camunda exporter writes for this feature also get an RDBMS
  handler (and the reverse)? Do ES/OS archiving and RDBMS TTL cleanup both cover the new entity?
  Where behaviour differs by backend, what does the API return?
- **Hard:** yes. TODO(confirm): whether an RDBMS gap may ship behind a documented limitation.
- **Detail:** [working with secondary storage § 1.1](../../docs/monorepo-docs/architecture/components/secondary-storage/working-with-secondary-storage.md),
  [archiving](../../docs/monorepo-docs/architecture/components/secondary-storage/archiving.md)

### C4 — Schema changes are additive and land in the schema modules first
- **Question:** Which fields or columns does the exporter newly write? Are they added to the
  `webapps-schema` template (strict) or a `db/rdbms-schema` changeset first, without retyping or
  renaming existing ones? Is a template version bump (new index generation) really needed?
- **Hard:** yes
- **Detail:** [working with secondary storage § 2.5, § 4.6](../../docs/monorepo-docs/architecture/components/secondary-storage/working-with-secondary-storage.md);
  `SecondaryStorageSchemaConstraintsTest`, `RdbmsSchemaConstraintsTest`

### C5 — Export is idempotent and independent of flush and replay boundaries
- **Question:** After a restart the broker re-exports from the last acknowledged position. Does the
  change produce the same stored state when records are exported twice, or when a flush falls
  between two records? Does a new handler share an `(index, id)` with another handler, and is that
  covered in `ExporterBulkConsistencyIT`?
- **Hard:** yes
- **Detail:** [`Exporter` Javadoc](../exporter-api/src/main/java/io/camunda/zeebe/exporter/api/Exporter.java)
  (at-least-once, idempotent), [working with secondary storage § 5](../../docs/monorepo-docs/architecture/components/secondary-storage/working-with-secondary-storage.md)

### C6 — Exporters must not stall the partition
- **Question:** Can the change block the exporter actor (synchronous remote calls, unbounded retries,
  large caches) or keep a failing record from being acknowledged forever? A stuck exporter stops
  log compaction, so the partition's disk keeps growing.
- **Hard:** yes
- **Detail:** [management ADR-005](../../docs/adr/management/005-per-physical-tenant-schema-initialization.md)
  (consequences); TODO(confirm): a documented error-handling policy for the Camunda exporter
  (`errorhandling/`)

### C7 — Physical-tenant configuration and isolation
- **Question:** Does a new exporter arg resolve per physical tenant (through `configuration` for the
  autoconfigured exporters, through the exporter's `ExporterConfigMerger` otherwise)? Does a new
  write target or cluster-global resource declare an isolation claim?
- **Hard:** yes
- **Detail:** [orchestration-cluster ADR-0008](../../docs/adr/orchestration-cluster/0008-physical-tenant-exporter-assignment-and-args-merge.md),
  `zeebe/exporter-config-support` (`ExporterIsolationClaims`)

### C8 — Upgrades from the previous minor
- **Question:** After a rolling update, can the new exporter continue from data written by the
  previous minor (existing indices, archived indices, RDBMS rows, exporter metadata), and can it
  read records written by the previous broker version?
- **Hard:** yes. TODO(confirm): which test proves it (`zeebe/qa/update-tests`,
  `ElasticsearchExporterMigrationIT`, `OpensearchExporterMigrationIT`).
- **Detail:** [archiving](../../docs/monorepo-docs/architecture/components/secondary-storage/archiving.md);
  ES/OS exporter templates are versioned per broker version (`TemplateReader`)

### C9 — Write volume and archiving cost
- **Question:** How many documents or rows per record does the change add, at what write rate? Does
  it grow the Camunda exporter's batch memory or add work to the archiver?
- **Hard:** no
- **Detail:** [working with secondary storage § 1.1 question 5](../../docs/monorepo-docs/architecture/components/secondary-storage/working-with-secondary-storage.md)

### C10 — Analytics exporter invariants
- **Question:** For changes to the analytics exporter: does it stay non-blocking with eager position
  acknowledgement, free of PII, deterministic under replay (sequence numbers), and off by default?
  New attributes that could carry user data need legal/GDPR review.
- **Hard:** yes
- **Detail:** [analytics-exporter AGENTS.md § Hard Invariants](analytics-exporter/AGENTS.md)

## 6. Data and persistence

- **Elasticsearch / OpenSearch:** the Camunda exporter writes the indices `webapps-schema`
  describes (`IndexDescriptors`), archives completed process data into dated
  indices, and applies ILM/ISM retention when `history.retention` is enabled. The ES/OS exporters
  write `zeebe-record-<value-type>` indices named per broker version and date (alias
  `<prefix>-<value-type>`, document id `partitionId-position`); retention is off by
  default (`zeebe-record-retention-policy`, minimum age 30 days).
- **RDBMS:** the RDBMS exporter writes the `db/rdbms-schema` tables and an exporter-position table it
  reconciles with the broker on open (it may request replay). It does not create the schema; it
  waits for `RdbmsSchemaManagerRegistry` to report the tenant initialized. History is removed by TTL
  cleanup, not archiving.
- **Exporter metadata:** each exporter stores its position (and, for the Camunda and analytics
  exporters, serialized metadata such as sequence numbers) through `Controller` in the broker.
- TODO(confirm): the supported versions of Elasticsearch, OpenSearch and each RDBMS vendor, and
  where that matrix lives (`.github/actions/read-db-versions/` is a candidate).

## 7. Cross-cutting qualities

- **Security:** ES/OS credentials, TLS and AWS SigV4 come from `search`'s `ConnectConfiguration`.
  The Camunda and RDBMS exporters write authorization and audit-log data (actor from the record's
  claims) but do not check permissions; reads are authorized in `search` and `service`.
- **Tenancy:** tenant ids are written on every tenant-owned entity. Physical tenants each get their
  own exporter instances and targets (C7); the analytics and app-integrations exporters send the
  physical tenant in their requests.
- **Performance:** export runs on the partition's exporter actor; batching thresholds are the main
  tuning knob (Camunda exporter: 5,000 entities, 20 MB, 1 s; ES/OS exporters: 1,000, 10 MB, 5 s).
  Several handlers and the identity/definition handlers of the RDBMS exporter run on partition 1
  only.
- **Observability:** exporter metrics (`zeebe.camunda.exporter.*`, `zeebe.rdbms.exporter.*`,
  `zeebe.app.integrations.exporter.*`) plus the broker's per-exporter metrics (position, latency).

## 8. Delivery

As Orchestration Cluster SYSTEM.md (one monorepo release, backports via the backport action).
Specific here: the exporters ship inside the broker distribution (`dist`); only `zeebe-exporter-api`
is in the BOM. The analytics exporter exists from 8.10 and was reverted from `stable/8.8` and
`stable/8.9` (#59298). TODO(confirm): rules for backporting an exporter change that writes a new
field to a `stable/*` branch whose schema does not have it.

## 9. Testing expectations

- **Camunda exporter:** unit tests per handler; ITs (`*IT`) run against Elasticsearch and OpenSearch
  Testcontainers via `CamundaExporterITTemplateExtension` and `search-test-utils`;
  `ExporterBulkConsistencyIT` for multi-handler entities (C5).
- **RDBMS exporter:** unit tests in the module; ITs in
  `qa/acceptance-tests/src/test/java/io/camunda/it/rdbms/exporter/` (`RdbmsExporterIT`,
  `RdbmsExporterPhysicalTenantIT`, `RdbmsExporterBatchOperationsIT`) and `RdbmsExporterPositionRecoveryIT`;
  `@MultiDbTest` acceptance tests cover the read side on every backend.
- **ES/OS exporters:** `ElasticsearchExporterIT` / `OpensearchExporterIT`, `SchemaManagerIT`,
  `FaultToleranceIT`, `ExporterFilterIT`; `TestSupport` fails for a value type without a mapping.
- **Analytics:** unit, SDK and metric-pipeline tests in CI; `AnalyticsExporterOtelIT` is
  developer-run only ([AGENTS.md § Testing](analytics-exporter/AGENTS.md)).
- **App-integrations:** unit tests and WireMock ITs.
- CI: `ci.yml` runs the exporter group (`zeebe-elasticsearch-exporter`, `zeebe-opensearch-exporter`,
  `camunda-exporter`, `rdbms-exporter`, `zeebe-exporter-api`, `-common`, `-test`) as its own suite;
  `zeebe-search-integration-tests.yml` and `zeebe-rdbms-integration-tests.yml` run the storage ITs.

## 10. Planning conventions

- Issues: templates `2. feature_request.yml`, `3. task.yml`, `4. epic breakdown.yml`. Exporter
  issues carry `component/data-layer`, `component/exporter` or `component/zeebe`; the `create-issue`
  skill has no mapping for `zeebe/exporters`. TODO(confirm): which label the team triages.
- New analytics events follow the [`analytics-exporter` skill](../../.claude/skills/analytics-exporter/SKILL.md).
- ADRs: exporter-only decisions in [`zeebe/docs/adr/`](../docs/adr/README.md), storage-wide ones in
  `docs/adr/storage/`.
- TODO(confirm): plans directory and ID prefix for plan refs.

## 11. Glossary

- **Camunda exporter vs Elasticsearch exporter:** the Camunda exporter writes the webapp read model
  (`webapps-schema`) to ES or OS; the Elasticsearch exporter writes raw records (`zeebe-record-*`)
  for Optimize. They are configured and evolve independently.
- **Handler:** a Camunda exporter `ExportHandler` (or RDBMS `RdbmsExportHandler`) that maps one value
  type's records onto one entity type.
- **Flush:** writing the batched entities to storage and then acknowledging the exporter position.
- **Archiver:** Camunda exporter background jobs that move finished data to dated indices (ES/OS
  only); RDBMS uses **history cleanup** (TTL) instead.
- **Autoconfigured exporter:** `camundaexporter` or `rdbms`, created from
  `camunda.data.secondary-storage.*` rather than declared under `camunda.data.exporters`.
- **Optimize mode:** ES/OS exporter setting that limits export to the value types Optimize needs.

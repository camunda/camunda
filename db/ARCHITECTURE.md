---
architecture_md: 1
component: camunda/camunda/db
system: { id: orchestration-cluster, map: "camunda/camunda/SYSTEM.md" }
lifecycle: production
kind: [library]
summary: The relational (RDBMS) secondary-storage backend of the Orchestration Cluster, the Liquibase schema and its generated SQL scripts, plus the MyBatis readers, queued batch writers, schema-version and history-cleanup services that the RDBMS exporter and the search layer use per physical tenant.
team: { name: camunda/data-layer, contact: unknown }
intake: { how: issue, template: "2. feature_request.yml", labels: [component/data-layer] }
owns:
  - "The RDBMS schema (camunda-db-rdbms-schema): the Liquibase changelog in rdbms-schema/src/main/resources/db/changelog/rdbms-exporter/ (one changeset file per release), every table, column, index and foreign key, and the ${prefix} table-prefix convention"
  - "Per-vendor dialect settings (rdbms-schema/src/main/resources/db/vendor-properties/: h2, postgresql, mariadb, mysql, mssql, oracle) used by both Liquibase and the MyBatis mappers"
  - "The rolling-upgrade guardrail for schema changes: RollingUpgradeCompatibilityValidator and its allowlist of expand-only change types, run by LiquibaseScriptGenerator at build time"
  - "The released SQL scripts for manual schema management (camunda-db-rdbms-schema-<version>.zip: per-vendor create scripts and step-by-step upgrade scripts from 8.9.0)"
  - "Applying the schema at runtime per physical tenant: RdbmsSchemaManager, LiquibaseSchemaManager, RdbmsSchemaManagers/RdbmsSchemaManagerRegistry, and the RDBMS_SCHEMA_VERSION upgrade-path check (same or next minor only) in RdbmsSchemaVersionStore"
  - "The RDBMS read implementation of the search reader contract: one *DbReader per entity, DbQuery records, SearchColumn enums, keyset pagination (incl. backward paging), statistics readers, RdbmsTenantReaders"
  - "How authorization and tenant checks become SQL for RDBMS reads (RdbmsResourceAccessController, authorization criteria in DbQuery and mapper XML)"
  - "The MyBatis mappers (sql/*Mapper.java, resources/mapper/*.xml, Commons.xml fragments) and type handlers, including vendor-specific statements by databaseId"
  - "The RDBMS write path: *Writer services, the ExecutionQueue with JDBC batching and QueueItem mergers, RdbmsWriters as the single writer registry, flush listeners and writer metrics (zeebe.rdbms.exporter.*)"
  - "RDBMS data lifecycle: TTL history cleanup (HistoryCleanupService, HISTORY_CLEANUP_DATE), user-requested history deletion (HistoryDeletionService), purge (RdbmsPurger)"
  - "The exporter-position table and ExporterPositionService that keep RDBMS consistent with the Zeebe log, and that restore reads"
  - "RDBMS-only operational reads: replication LSN/lag providers for async read replicas, table row-count metrics, the schema-migration status for upgrade readiness"
  - "RDBMS storage of persistent web sessions (PersistentWebSessionWriter/DbReader/RdbmsClient)"
  - "Local database fixtures for RDBMS development (db/docker-compose.yml, docker-compose-init/, scripts/postgres-replica-lag.sh)"
does_not_own:
  - { concept: "The reader interfaces, search-domain entities, filters, queries and the AbstractResourceAccessController base every backend implements", owner: camunda/camunda/search }
  - { concept: "Mapping Zeebe records to RDBMS rows: the RDBMS exporter, its RdbmsExportHandlers, caches and async-replication controller", owner: camunda/camunda/zeebe/exporters }
  - { concept: "Spring wiring: DataSources, MyBatis SqlSessionFactory, per-tenant RdbmsService/readers, schema initialization gate and backend selection", owner: camunda/camunda/dist }
  - { concept: "camunda.data.secondary-storage.rdbms.* properties (prefix, auto-ddl, history, insert batching, query, async replication)", owner: camunda/camunda/configuration }
  - { concept: "ES/OS index templates and their schema rules", owner: camunda/camunda/webapps-schema }
  - { concept: "ES/OS index creation and migration", owner: camunda/camunda/schema-manager }
  - { concept: "Which permission a read requires; the service API around a query", owner: camunda/camunda/service }
  - { concept: "Permission model, SecurityContext and ResourceAccessChecks types", owner: camunda/camunda-security-library }
  - { concept: "RDBMS-aware Zeebe restore (RestoreManager realigning partitions to exported positions)", owner: camunda/camunda/zeebe/broker }
  - { concept: "Zeebe's primary state store (RocksDB, zeebe/zb-db), unrelated despite the name", owner: camunda/camunda/zeebe/broker }
depends_on:
  - id: search
    component: camunda/camunda/search
    kind: library
    contract: "io.camunda:camunda-search-client-reader (*Reader interfaces), camunda-search-client (AbstractResourceAccessController, PersistentWebSessionClient), camunda-search-domain (entities, filters, queries, sorts)"
    architecture: search/ARCHITECTURE.md
    versions: same monorepo release
    workaround_policy: never
  - id: security-library
    component: camunda/camunda-security-library
    kind: library
    contract: "io.camunda:camunda-security-library-api, -core: SecurityContext, ResourceAccessChecks, authorization and tenant checks"
    versions: "pinned by version.camunda-security-library in parent/pom.xml"
    workaround_policy: never
  - id: cluster
    component: camunda/camunda/cluster
    kind: library
    contract: "io.camunda:camunda-cluster: MigrationStatusProvider for upgrade readiness"
    versions: same monorepo release
    workaround_policy: never
  - id: zeebe-util
    component: camunda/camunda/zeebe/util
    kind: library
    contract: "io.camunda:zeebe-util: SemanticVersion, CurrentSchemaVersion, ObjectBuilder"
    versions: same monorepo release
    workaround_policy: never
  - id: mybatis
    component: org.mybatis:mybatis
    kind: external
    contract: "SQL mapping (mapper interfaces + XML, databaseId per vendor, batch executor)"
    versions: "managed in parent/pom.xml"
    workaround_policy: adapter-boundary
  - id: liquibase
    component: org.liquibase:liquibase-core
    kind: external
    contract: "changelog format, migrations (MultiTenantSpringLiquibase), offline SQL generation"
    versions: "managed in parent/pom.xml"
    workaround_policy: adapter-boundary
  - id: libraries
    component: "Jackson (+ jsr310), Caffeine, Micrometer, spring-beans / spring-core, commons-lang3, slf4j, jspecify"
    kind: external
    contract: "JSON columns, caches, writer and table metrics, SpringLiquibase supertypes and classpath scanning, nullness annotations"
    versions: "managed in parent/pom.xml"
    workaround_policy: adapter-boundary
  - id: databases
    component: "PostgreSQL (incl. Aurora), MySQL, MariaDB, Microsoft SQL Server / Azure SQL, Oracle, H2"
    kind: platform
    contract: "JDBC; drivers are supplied by dist, not by this component"
    versions: ".ci/db-versions.yml (PostgreSQL 15–18, MySQL 8.4/9.x, MariaDB 10.11–12.x, MSSQL 2022/2025, Azure SQL, Oracle 21/23); H2 for tests and local use"
    workaround_policy: adapter-boundary
consumers:
  - { who: camunda/camunda/zeebe/exporters, via: "RdbmsServiceFactory/RdbmsService, RdbmsWriters and every *Writer, ExecutionQueue, RdbmsSchemaManagerRegistry, ExporterPositionService, HistoryCleanupService, replication providers", promise: "internal Java API, same release; changed together with the RDBMS exporter in the same PR" }
  - { who: camunda/camunda/dist, via: "RdbmsServiceFactory, RdbmsMapperBundle, RdbmsTenantReaders, RdbmsSchemaManagers, PerTenantSchemaConfig, VendorDatabaseProperties, RdbmsResourceAccessController, persistent-web-session client, table-row-count metrics, ExporterPositionMapper (RestoreApp)", promise: "internal Java API, same release" }
  - { who: "camunda/camunda/service (and through it the REST API, Operate, Tasklist, Admin)", via: "the search reader contract, implemented here when secondary-storage.type is rdbms", promise: "same results, sorting and paging as ES/OS for every reader method; TODO(confirm) documented exceptions" }
  - { who: camunda/camunda/configuration, via: "RdbmsWriterConfig, RdbmsReaderConfig (property binding)", promise: "internal Java API, same release" }
  - { who: camunda/camunda/zeebe/broker, via: "ExporterPositionMapper in zeebe/restore-standalone (RDBMS-aware restore)", promise: "internal Java API, same release" }
  - { who: "Self-Managed operators and DBAs", via: "the database schema, table prefix, and the camunda-db-rdbms-schema zip of create/upgrade SQL scripts (release artifact)", promise: "expand-only changes within a minor and to the next minor; upgrades may not skip a minor; TODO(confirm) whether direct SQL on the tables is supported" }
  - { who: "C8 Run (c8run/)", via: "downloads camunda-db-rdbms-schema-<version>.zip and ships it as rdbms-schema/", promise: "same release" }
  - { who: "qa/acceptance-tests, qa/util (ScriptBasedSchemaManager), qa/archunit-tests, zeebe/qa/integration-tests", via: "readers, writers, LiquibaseScriptGenerator, RDBMS ArchUnit rules", promise: none }
exposes:
  - { contract: "RdbmsService / RdbmsServiceFactory: per-physical-tenant entry point to readers, writers and replication status", spec: rdbms/src/main/java/io/camunda/db/rdbms/RdbmsServiceFactory.java, policy: "internal; same release" }
  - { contract: "Extension point (internal): a new entity adds DbModel, DbQuery, SearchColumn, Mapper interface + XML, *DbReader, *Writer and a ContextType, registered in RdbmsWriters and RdbmsTenantReaders", spec: ../docs/monorepo-docs/architecture/components/secondary-storage/rdbms/developer-guide.md, policy: "internal; ArchUnit enforces records, registration, queue-only writes and IT coverage" }
  - { contract: "Extension point (internal): QueueItemMerger / PreFlushListener / PostFlushListener on the ExecutionQueue; a merger combines queued statements for one entity, listeners run around a flush", spec: rdbms/src/main/java/io/camunda/db/rdbms/write/queue/, policy: "internal" }
  - { contract: "Extension point (internal): ProcessInstanceDependant; a writer that extends it gets its rows deleted by history cleanup and history deletion", spec: rdbms/src/main/java/io/camunda/db/rdbms/write/service/ProcessInstanceDependant.java, policy: "internal; RdbmsProcessInstanceDependantArchTest" }
  - { contract: "RdbmsSchemaManager / RdbmsSchemaManagerRegistry: single-attempt per-tenant migration; retries and readiness belong to the caller", spec: rdbms/src/main/java/io/camunda/db/rdbms/RdbmsSchemaManager.java, policy: "internal" }
  - { contract: "RDBMS schema (tables, columns, indexes under the configured prefix)", spec: rdbms-schema/src/main/resources/db/changelog/rdbms-exporter/changelog-master.xml, policy: "expand-only across rolling upgrades (RollingUpgradeCompatibilityValidator); released changesets are immutable" }
  - { contract: "camunda-db-rdbms-schema-<version>.zip: liquibase/sql/create/<vendor>/<vendor>_master.sql and upgrade/<vendor>/<vendor>_upgrade_<from>_to_<to>.sql", spec: rdbms-schema/assembly.xml, policy: "customer-facing release asset; TODO(confirm) support promise for manually applied scripts" }
  - { contract: "Vendor properties (VendorDatabaseProperties) consumed by dist's MyBatis and DataSource setup", spec: rdbms-schema/src/main/java/io/camunda/db/rdbms/config/VendorDatabaseProperties.java, policy: "internal" }
constraints:
  - { id: C1, name: Works on every supported vendor, hard: true, ref: ../.ci/db-versions.yml }
  - { id: C2, name: Expand-only and immutable changesets, hard: true, ref: ../docs/monorepo-docs/architecture/components/secondary-storage/rdbms/developer-guide.md }
  - { id: C3, name: Upgrade path and manual DDL, hard: true, ref: rdbms/src/main/java/io/camunda/db/rdbms/RdbmsSchemaVersionStore.java }
  - { id: C4, name: Parity with the ES/OS readers, hard: true, ref: ../search/ARCHITECTURE.md }
  - { id: C5, name: Authorization tenant and physical-tenant scoping, hard: true, ref: ../docs/adr/orchestration-cluster/0005-physical-tenant-routing-of-authorization-reads.md }
  - { id: C6, name: History cleanup covers new data, hard: true, ref: ../qa/archunit-tests/src/test/java/io/camunda/RdbmsProcessInstanceDependantArchTest.java }
  - { id: C7, name: Writes go through the ExecutionQueue, hard: true, ref: ../qa/archunit-tests/src/test/java/io/camunda/RdbmsExecutionQueueArchTest.java }
  - { id: C8, name: Plain Java library without Spring IoC, hard: true, ref: ../docs/monorepo-docs/architecture/components/secondary-storage/rdbms/rdbms_architecture_docs.md }
  - { id: C9, name: Indexes and query cost, hard: false, ref: ../docs/monorepo-docs/architecture/components/secondary-storage/rdbms/developer-guide.md }
decisions: ../docs/monorepo-docs/architecture/components/secondary-storage/rdbms/rdbms_architecture_docs.md
planning: { issue_templates: { increment: "3. task.yml", epic: "4. epic breakdown.yml" } }
---

# Architecture — camunda/camunda/db

> Draft from the repository; not reviewed by its team. Facts marked `TODO(confirm)` are inferred.

## 1. Purpose

The relational database backend for the Orchestration Cluster's secondary storage, an alternative
to Elasticsearch/OpenSearch selected with `camunda.data.secondary-storage.type=rdbms`. It defines
the schema (Liquibase), applies it per physical tenant, gives the RDBMS exporter its writers and
gives the search layer its readers, without changing what API users see
([RDBMS architecture docs](../docs/monorepo-docs/architecture/components/secondary-storage/rdbms/rdbms_architecture_docs.md)).
A plain Java library wired by `dist`. Part of the [Orchestration Cluster system](../SYSTEM.md)
(role `storage`, rule DR5: nothing else queries the RDBMS directly).

## 2. Ownership boundary

**Owns:** the RDBMS schema and its rolling-upgrade rules, the released SQL scripts, runtime schema
migration and version checks, every MyBatis mapper, the `*DbReader`s and `*Writer`s, the batched
write queue, history cleanup/deletion/purge, exporter positions, and RDBMS-only operational reads
(replication lag, row counts). The full list is in the front matter.

Owner: GitHub [CODEOWNERS](../CODEOWNERS) assigns only `db/rdbms-schema/src/main/resources/db/changelog/`
and `RdbmsSchemaConstraintsTest` to `@camunda/data-layer`. The fine-grained
[`.codeowners`](../.codeowners) (codeowners-plus) assigns all of `/db/` to `@camunda/data-layer`
("Database and search infrastructure"); the front matter uses that team. TODO(confirm): data-layer
owns all of `db/`, and whether CODEOWNERS should get a `/db/` line. Contact channel: TODO(confirm).

**Does not own (route here instead):**

| If you need… | It belongs to | How to ask |
|---|---|---|
| A new reader method, search entity, filter or sort in the shared contract | `camunda/camunda/search` (then implement it here) | issue, `component/data-layer` |
| A new record type exported to RDBMS, or a change to how a record maps to rows | `camunda/camunda/zeebe/exporters` (`rdbms-exporter` handlers) | issue, `component/data-layer` |
| A new `camunda.data.secondary-storage.rdbms.*` property | `camunda/camunda/configuration` (bound to `RdbmsWriterConfig` / `RdbmsReaderConfig` here) | issue |
| DataSource, connection pool, JDBC driver, or how beans are wired per tenant | `camunda/camunda/dist` (`application/commons/rdbms/`) | issue |
| The same field in Elasticsearch/OpenSearch | `camunda/camunda/webapps-schema` | issue, `component/data-layer` |
| Which permission a query requires | `camunda/camunda/service` | issue |
| Restoring a broker against an RDBMS backup | `zeebe/restore-standalone` (`RestoreManager`), [backup and restore § 4](../docs/monorepo-docs/architecture/components/secondary-storage/backup-and-restore.md) | issue |

## 3. Structure

| Module | Holds | Depends on |
|---|---|---|
| `rdbms-schema` (`camunda-db-rdbms-schema`) | Liquibase changelog (`changelog-master.xml`, `changesets/<version>.xml`), `vendor-properties/*.properties`, `VendorDatabaseProperties`, `RollingUpgradeCompatibilityValidator`, `LiquibaseScriptGenerator` (runs at `process-classes`, writes the SQL scripts zipped by `assembly.xml`) | Liquibase, spring-core only |
| `rdbms` (`camunda-db-rdbms`) | see below | `rdbms-schema`, `search`, CSL, `cluster`, `zeebe-util` |

Inside `rdbms/src/main/java/io/camunda/db/rdbms/`:

- root: `RdbmsService(Factory)`, schema managers, `RdbmsSchemaVersionStore`, `RdbmsTableNames`.
- `read/`: `service/*DbReader` (via `AbstractEntityReader`), `domain/*DbQuery`, `mapper/` (DbModel →
  entity where MyBatis result maps are not enough), `security/RdbmsResourceAccessController`,
  `replication/`.
- `write/`: `service/*Writer`, `domain/*DbModel`, `queue/` (`ExecutionQueue`, mergers, listeners),
  `RdbmsWriters`, `RdbmsWriterConfig`, `RdbmsWriterMetrics`.
- `sql/`: MyBatis mapper interfaces, `columns/*SearchColumn`, `typehandler/`; XML in
  `src/main/resources/mapper/` with shared fragments in `Commons.xml`.

Direction rules (from the [developer guide](../docs/monorepo-docs/architecture/components/secondary-storage/rdbms/developer-guide.md)
and `qa/archunit-tests`):

- CQRS: readers call mappers directly; writers never do, they enqueue through the `ExecutionQueue`
  (`RdbmsExecutionQueueArchTest`). DbModels are records (`RdbmsDbModelMustBeRecordArchTest`) and
  never depend on search entities (`RdbmsDbModelDependencyArchTest`).
- `rdbms-schema` has no dependency on `rdbms`; `rdbms` uses it for vendor properties and the
  changelog.
- Vendor-specific code lives only in vendor properties, `<modifySql dbms=…>` in changesets and
  `databaseId` statements in mapper XML; Java code stays vendor-neutral. TODO(confirm): rule or
  current state (type handlers such as `OracleXmlArrayTypeHandler` are vendor-specific Java).
- Everything is per physical tenant: one DataSource, `RdbmsService`, reader bundle and schema
  manager per tenant; no shared default.

## 4. Binding decisions

RDBMS module ADRs live with the [architecture docs](../docs/monorepo-docs/architecture/components/secondary-storage/rdbms/rdbms_architecture_docs.md#9-architecture-decisions);
cross-cutting ones in [`docs/adr/`](../docs/adr/README.md) (domain `storage` covers `db/`). There is
no `db/docs/adr/`. The decisions that most often shape RDBMS work:

- [RDBMS ADR 0001: MyBatis as the ORM](../docs/monorepo-docs/architecture/components/secondary-storage/rdbms/adr/0001-use-mybatis-as-orm-framework.md):
  hand-written SQL per vendor, no JPA.
- [RDBMS ADR 0002: Liquibase for schema management](../docs/monorepo-docs/architecture/components/secondary-storage/rdbms/adr/0002-use-liquibase-for-schema-management.md).
- [Per-physical-tenant schema initialization](../docs/adr/management/005-per-physical-tenant-schema-initialization.md)
  (management ADR 005): on RDBMS every node holds startup until schemas settle; a single-tenant node
  fails fast instead of retrying.
- [Physical-tenant routing of authorization reads](../docs/adr/orchestration-cluster/0005-physical-tenant-routing-of-authorization-reads.md)
  (ADR 0005): per-tenant `RdbmsTenantReaders`, no default-pinned reader.
- [Remove numeric key from Identity entity filters](../docs/adr/storage/001-remove-numeric-key-from-identity-entity-filters.md)
  (storage ADR 001).

Rules without an ADR: "No Spring IoC in `db/rdbms`" (architecture docs § 2), the rolling-upgrade
allowlist and Liquibase/MyBatis conventions in the developer guide, and "RDBMS schema changes are
additive-only" in the [architecture overview](../docs/architecture/overview.md).

## 5. Planning constraints

Every plan must answer each of these (applies / n/a + decision or reason).

### C1 — Works on every supported vendor
- **Question:** Do the changeset and every new or changed statement work on H2, PostgreSQL (and
  Aurora), MariaDB, MySQL, MSSQL / Azure SQL and Oracle at every version in `.ci/db-versions.yml`?
  Which statements need a `databaseId` variant or vendor property (upsert, paging, booleans, Oracle
  empty-string/NULL, Oracle 1000-element `IN`, NULL ordering, timestamp types)?
- **Hard:** yes
- **Detail:** [`.ci/db-versions.yml`](../.ci/db-versions.yml), developer guide "Database-specific SQL"

### C2 — Expand-only and immutable changesets
- **Question:** Is the schema change in the current release's changeset file, made only of
  allowlisted changes (`createTable`, nullable `addColumn`, `createIndex`, `dropIndex`, reviewed
  raw `sql`), with idempotency preconditions, and without touching a released changeset? If a
  column must go or be renamed, what is the multi-release plan?
- **Hard:** yes
- **Detail:** developer guide "Rolling-upgrade-compatible schema evolution", `RollingUpgradeCompatibilityValidator`

### C3 — Upgrade path and manual DDL
- **Question:** Does the change work for customers with `auto-ddl=false` who apply the generated
  upgrade scripts themselves, and for an upgrade from the previous minor (no skipped minors)? What
  do rows written before the change return?
- **Hard:** yes
- **Detail:** `RdbmsSchemaVersionStore`, `LiquibaseScriptGenerator`. TODO(confirm): how manual
  upgrades are documented for customers.

### C4 — Parity with the ES/OS readers
- **Question:** Does every new reader method, filter, sort and aggregation in `search` have an RDBMS
  implementation here with the same results, ordering and paging (forward and backward keyset)?
  If RDBMS can't support it, what does the caller get?
- **Hard:** yes
- **Detail:** [search ARCHITECTURE.md](../search/ARCHITECTURE.md) C1, `RdbmsBackwardPagingArchTest`

### C5 — Authorization, tenant and physical-tenant scoping
- **Question:** Does the read apply the authorization and tenant criteria in SQL (search) or check
  after load (get) through `RdbmsResourceAccessController`, and does it run on the caller's physical
  tenant's DataSource only?
- **Hard:** yes
- **Detail:** [ADR 0005](../docs/adr/orchestration-cluster/0005-physical-tenant-routing-of-authorization-reads.md)

### C6 — History cleanup covers new data
- **Question:** Is every new table cleaned up: process-instance-scoped writers extend
  `ProcessInstanceDependant`, other tables carry `HISTORY_CLEANUP_DATE` and `PARTITION_ID` with a
  matching index, and history deletion and the purger include them?
- **Hard:** yes
- **Detail:** `RdbmsProcessInstanceDependantArchTest`, `PurgerCompletenessIT`

### C7 — Writes go through the ExecutionQueue
- **Question:** Do new writes enqueue `QueueItem`s (never call mapper write methods directly), is
  the writer registered in `RdbmsWriters`, and does a merger exist where one flush sees many updates
  to the same row? Is the flush still atomic with the exporter position?
- **Hard:** yes
- **Detail:** `RdbmsExecutionQueueArchTest`, `RdbmsWritersArchTest`, `RdbmsFlushRollbackIT`

### C8 — Plain Java library without Spring IoC
- **Question:** Is the new component constructed from `dist` by constructor injection, with no
  Spring annotations in `db/rdbms`?
- **Hard:** yes
- **Detail:** [Architecture docs § 2](../docs/monorepo-docs/architecture/components/secondary-storage/rdbms/rdbms_architecture_docs.md)

### C9 — Indexes and query cost
- **Question:** Are new filter, sort and join columns indexed (keys over strings, every FK, cleanup
  columns with `PARTITION_ID`), and does the query avoid N+1 lookups?
- **Hard:** no
- **Detail:** developer guide "Indexes", [benchmarking](../docs/monorepo-docs/architecture/components/secondary-storage/rdbms/benchmarking.md)

## 6. Data and persistence

This component defines all RDBMS secondary-storage data: one table set per physical tenant (own
DataSource, optional table prefix), versioned by `RDBMS_SCHEMA_VERSION` and Liquibase's tables.
Supported vendors and versions: [`.ci/db-versions.yml`](../.ci/db-versions.yml); H2 for tests and
local use (TODO(confirm): whether H2 is supported in production, the architecture docs say
"single-broker"). Data is written asynchronously by the exporter, so reads are eventually
consistent. Retention is TTL cleanup (no archiving, unlike ES/OS). Backups are the operator's,
coordinated with Zeebe backups ([backup and restore § 4](../docs/monorepo-docs/architecture/components/secondary-storage/backup-and-restore.md)).
Read replicas with async replication are supported through the replication providers, which let
the exporter pause when replicas lag.

## 7. Cross-cutting qualities

- **Security:** user values bind with `#{…}`; `${…}` only for vendor properties, the prefix and
  trusted column enums. Authorization and tenant checks per read (C5).
- **Tenancy:** physical tenants by separate DataSources and schema managers; multi-tenancy
  `tenantId` as a column filter.
- **Performance:** JDBC batching and queue merging on the write path (disabled for Oracle, which
  uses `INSERT ALL`); flush size and interval are exporter config. TODO(confirm): query limits or
  latency targets plans must respect.
- **Observability:** writer metrics (`zeebe.rdbms.exporter.*`), per-tenant table row counts,
  schema-migration status for upgrade readiness, replication lag.

## 8. Delivery

As [Orchestration Cluster SYSTEM.md](../SYSTEM.md) § 6. Exceptions: one changeset file per release
version (`changesets/<x.y.z>.xml`), upgrades must not skip a minor, and the schema zip is a release
artifact (`camunda-platform-release.yml`) that C8 Run downloads. TODO(confirm): the rule for
backporting a changeset to `stable/*` (the `8.9.9.xml` file suggests patch-level changesets)
without breaking the next minor's checksums.

## 9. Testing expectations

- **Unit:** `rdbms` and `rdbms-schema` modules on H2 (`./mvnw verify -pl db/rdbms -DskipTests=false -Dquickly`);
  `RollingUpgradeCompatibilityValidatorTest` and `RdbmsSchemaConstraintsTest` for changesets.
- **Integration:** `qa/acceptance-tests/.../it/rdbms/db/` per entity; ArchUnit requires a `*IT` and
  a `*SortIT` per DbModel (`RdbmsDbModelCoverageArchTest`, `RdbmsDbModelSortITCoverageArchTest`)
  and Oracle null-safety in entity mappers. `@MultiDbTest` acceptance tests run against RDBMS too
  ([acceptance testing](../docs/testing/acceptance.md)).
- **Vendor matrix:** `zeebe-rdbms-integration-tests.yml` runs every vendor and version on weekdays;
  run the target database locally with `db/docker-compose.yml` before merging SQL changes.

## 10. Planning conventions

- Issues: templates `2. feature_request.yml`, `3. task.yml`, `4. epic breakdown.yml` in
  `.github/ISSUE_TEMPLATE/`; label `component/data-layer` (create-issue skill: "`db/` or `search/`").
- TODO(confirm): plans directory and ID prefix for plan refs.

## 11. Glossary

| Term | Meaning here |
|---|---|
| DbModel | Write-side record mirroring a table row (`write/domain/`) |
| DbQuery | Read-side record with filter, authorization criteria, sort and page passed to a mapper |
| DbReader | RDBMS implementation of a `search` reader interface |
| SearchColumn | Enum mapping an API property to a column for sorting |
| ExecutionQueue | Queue of `QueueItem`s flushed in one JDBC batch and transaction |
| Vendor properties | Per-database settings used as `${…}` in changesets and mappers |
| Prefix | Configured table-name prefix (`${prefix}`), upper-cased |
| History cleanup / deletion / purge | TTL retention / user-requested delete / wipe all data |
| Auto-DDL | The app applies Liquibase itself; off means the DBA applies the shipped scripts |
| Physical tenant | Isolated cluster tenant with its own database; not the multi-tenancy `tenantId` |

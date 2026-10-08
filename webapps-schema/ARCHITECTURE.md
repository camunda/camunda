---
architecture_md: 1
component: camunda/camunda/webapps-schema
system: { id: orchestration-cluster, map: "camunda/camunda/SYSTEM.md" }
lifecycle: production
kind: [library]
summary: The Elasticsearch/OpenSearch secondary-storage schema of the Orchestration Cluster, the index and template mappings, the Java index descriptors that name, version, shard and back them up, and the document entity classes the Camunda exporter writes and search reads.
team: { name: camunda/data-layer, contact: "#team-data-layer" }
intake: { how: issue, template: "2. feature_request.yml", labels: [component/data-layer] }
owns:
  - "ES/OS mapping JSON for every Orchestration Cluster index and index template (schema/{elasticsearch,opensearch}/create/{index,template}/), and the OpenSearch ISM policy for archived indices (create/policy/operate_delete_archived_indices.json)"
  - "Index descriptors (io.camunda.webapps.schema.descriptors): index name, component prefix (operate, tasklist, camunda), descriptor version, full-qualified name and alias pattern, tenant-id and id field, default shard count, and the IndexDescriptors registry of every index the cluster creates"
  - "The plain-index vs template taxonomy (AbstractIndexDescriptor vs AbstractTemplateDescriptor) that decides which indices are single-shard and which are archived into dated indices"
  - "Archiver-dependency markers on descriptors: ProcessInstanceDependant, BatchOperationDependant, DecisionInstanceDependant"
  - "Backup ordering of indices: BackupPriority, Prio1Backup to Prio4Backup, BackupPriorities and SnapshotIndexCollection"
  - "Document entity classes (io.camunda.webapps.schema.entities, ExporterEntity and its subclasses) and the @SinceVersion / @BeforeVersion880 field-versioning rule"
  - "The ES/OS schema rules enforced in this module: approved field types, dynamic strict everywhere, identical ES and OS mappings (SecondaryStorageSchemaConstraintsTest), @SinceVersion with defaults on entity fields (EntityTest), semantic descriptor versions (IndexDescriptorTest)"
  - "Testcontainer ES/OS image versions for tests (SupportedVersions, filtered from parent/pom.xml)"
does_not_own:
  - { concept: "Creating, updating and migrating indices and templates at startup, per physical tenant; schema version checks and cleanup of legacy indices", owner: camunda/camunda/schema-manager }
  - { concept: "Mapping engine records to entities, flushing them, and the archiver jobs that move documents into dated indices", owner: camunda/camunda/zeebe/exporters }
  - { concept: "Queries over these indices, the search-domain entities returned to callers, and authorization of reads", owner: camunda/camunda/search }
  - { concept: "RDBMS tables, Liquibase changesets and their schema rules", owner: camunda/camunda/db }
  - { concept: "zeebe-record-* index templates of the dedicated Elasticsearch and OpenSearch exporters", owner: camunda/camunda/zeebe/exporters }
  - { concept: "Optimize's own indices", owner: camunda/camunda/optimize }
  - { concept: "Taking and restoring snapshots (the backup service that uses the priorities)", owner: camunda/camunda/webapps-backup }
  - { concept: "camunda.data.secondary-storage.* properties, including number-of-shards-per-index (NumberOfShardsPerIndex)", owner: camunda/camunda/configuration }
  - { concept: "Record value types, TenantOwned and the enums entities reuse", owner: camunda/camunda/zeebe/protocol }
  - { concept: "Permission and resource types (EntityType, PermissionType) stored in the authorization index", owner: camunda/camunda-security-library }
depends_on:
  - id: protocol
    component: camunda/camunda/zeebe/protocol
    kind: schema
    contract: "io.camunda:zeebe-protocol: TenantOwned.DEFAULT_TENANT_IDENTIFIER, BatchOperationType, HistoryDeletionType, GlobalListenerRecordValue enums used by entities"
    versions: same monorepo release
    workaround_policy: never
  - id: security-library
    component: camunda/camunda-security-library
    kind: library
    contract: "io.camunda:camunda-security-library-api: EntityType, PermissionType in authorization entities"
    versions: "pinned by version.camunda-security-library in parent/pom.xml"
    workaround_policy: never
  - id: libraries
    component: "Jackson annotations, SLF4J"
    kind: external
    contract: "JSON annotations on entities, logging"
    versions: "managed in parent/pom.xml"
    workaround_policy: adapter-boundary
  - id: search-engines
    component: "Elasticsearch, OpenSearch (incl. Amazon OpenSearch Service)"
    kind: platform
    contract: "index, index-template and mapping APIs the JSON files are written for; OpenSearch ISM for the archive policy"
    versions: ".ci/db-versions.yml; test images version.elasticsearch.container and version.opensearch.container in parent/pom.xml"
    workaround_policy: adapter-boundary
consumers:
  - { who: camunda/camunda/zeebe/exporters, via: "IndexDescriptors, template descriptors and field constants, entity classes, dependant markers (Camunda exporter handlers and archiver)", promise: "internal Java API, same release; changed together in the same PR" }
  - { who: camunda/camunda/search, via: "descriptors, field constants and entity classes in search-client-query-transformer (ES/OS transformers and *DocumentReader)", promise: "internal Java API, same release" }
  - { who: camunda/camunda/schema-manager, via: "IndexDescriptors, getMappingsClasspathFilename, versions, getDefaultShardCount, allowMissing, the JSON files and the OS archive policy", promise: "internal; same release; data written by the previous minor stays readable after an upgrade without migration (SchemaUpdateIT)" }
  - { who: camunda/camunda/webapps-backup, via: "BackupPriority tiers, BackupPriorities.indicesSplitBySnapshot", promise: "internal Java API, same release" }
  - { who: camunda/camunda/dist, via: "IndexDescriptors for search, backup and schema-init wiring; PersistentWebSessionTemplate for web sessions", promise: "internal Java API, same release" }
  - { who: camunda/camunda/configuration, via: "index names mirrored as string literals in NumberOfShardsPerIndex (test-scoped dependency checks the projection)", promise: "a new index needs a field there (storage ADR 002)" }
  - { who: "operators and users who read the indices directly", via: "index names, aliases and field mappings in Elasticsearch/OpenSearch", promise: "TODO(confirm): no stated promise for direct index access" }
  - { who: "qa/acceptance-tests, qa/util, zeebe/qa/util, qa/archunit-tests, debug-cli, operate/data-generator, search-test-utils", via: "descriptors, entities, SupportedVersions; BackupPriorityDescriptorArchTest", promise: none }
exposes:
  - { contract: "ES/OS mappings for every Orchestration Cluster index and template", spec: src/main/resources/schema/, policy: "additive only: no field removal, retyping or required field without default (README); dynamic strict; ES and OS identical; checked by SchemaUpdateIT in schema-manager" }
  - { contract: "IndexDescriptors registry and the IndexDescriptor / IndexTemplateDescriptor interfaces", spec: src/main/java/io/camunda/webapps/schema/descriptors/IndexDescriptors.java, policy: "internal; same release as every consumer" }
  - { contract: "Extension point: a new index is a descriptor class (extends AbstractIndexDescriptor or AbstractTemplateDescriptor) plus the ES and OS JSON, registered in IndexDescriptors; the base class sets its shard default, a marker interface joins it to an archiver job, a Prio*Backup tier puts it in backups", spec: src/main/java/io/camunda/webapps/schema/descriptors/, policy: "internal; BackupPriorityDescriptorArchTest requires a backup tier; NumberOfShardsPerIndex needs a matching field" }
  - { contract: "Document entity classes the exporter serializes and the transformers deserialize", spec: src/main/java/io/camunda/webapps/schema/entities/, policy: "internal; every field carries @SinceVersion and a non-null default unless requireDefault = false (EntityTest)" }
  - { contract: "Backup priorities and snapshot parts", spec: src/main/java/io/camunda/webapps/schema/descriptors/backup/, policy: "internal; ordering is a restore-correctness rule (backup-and-restore.md)" }
  - { contract: "SupportedVersions: ES/OS testcontainer image versions", spec: src/main/java/io/camunda/webapps/schema/SupportedVersions.java, policy: "test support, internal" }
constraints:
  - { id: C1, name: Additive-only schema change, hard: true, ref: README.md }
  - { id: C2, name: Elasticsearch and OpenSearch in step, hard: true, ref: src/test/java/io/camunda/webapps/schema/SecondaryStorageSchemaConstraintsTest.java }
  - { id: C3, name: Approved field types and Data Layer sign-off, hard: true, ref: ../docs/monorepo-docs/architecture/components/secondary-storage/working-with-secondary-storage.md }
  - { id: C4, name: Entity field versioning, hard: true, ref: src/main/java/io/camunda/webapps/schema/entities/SinceVersion.java }
  - { id: C5, name: Descriptor version bump means a new index generation, hard: true, ref: ../docs/monorepo-docs/architecture/components/secondary-storage/working-with-secondary-storage.md }
  - { id: C6, name: "New index wiring (shards, backup, archiving)", hard: true, ref: ../docs/adr/storage/002-per-index-shard-configuration.md }
  - { id: C7, name: RDBMS counterpart, hard: true, ref: ../docs/architecture/overview.md }
  - { id: C8, name: Volume and query cost, hard: false, ref: ../docs/monorepo-docs/architecture/components/secondary-storage/working-with-secondary-storage.md }
decisions: ../docs/adr/README.md
planning: { issue_templates: { increment: "3. task.yml", epic: "4. epic breakdown.yml" } }
---

# Architecture — camunda/camunda/webapps-schema

> Draft from the repository; not reviewed by its team. Facts marked `TODO(confirm)` are inferred.

## 1. Purpose

The shape of the Orchestration Cluster's data in Elasticsearch and OpenSearch: the mapping JSON
for about 40 indices and templates, the Java descriptors that name, version, shard and back them up,
and the entity classes written into them. The Camunda exporter writes these documents, `search`
reads them for the REST API, Operate, Tasklist and Admin, and `schema-manager` creates them at
startup ([architecture overview](../docs/architecture/overview.md), "ES/OS index schema"). A plain
Java library with no runtime behaviour of its own. Part of the [Orchestration Cluster
system](../SYSTEM.md) (role `storage`).

## 2. Ownership boundary

**Owns:** what fields each ES/OS index has and with which types, which indices exist and how they
are named and versioned, which ones are single-shard, archived or backed up in which order, and the
Java entities that mirror the documents. The full list is in the front matter.

Owner: GitHub [CODEOWNERS](../CODEOWNERS) ("Secondary storage schema") assigns
`/webapps-schema/src/main/resources/schema/` and `SecondaryStorageSchemaConstraintsTest` to
`@camunda/data-layer`; the fine-grained [`.codeowners`](../.codeowners) assigns the whole
`/webapps-schema/` to `@camunda/data-layer`. Contact `#team-data-layer` comes from the constraint
test's failure message and the [secondary-storage guide](../docs/monorepo-docs/architecture/components/secondary-storage/working-with-secondary-storage.md).
TODO(confirm): data-layer owns the Java descriptors and entities too, not only the JSON (GitHub
CODEOWNERS names no team for them).

**Does not own (route here instead):**

| If you need… | It belongs to | How to ask |
|---|---|---|
| An index created, updated or migrated at startup, per physical tenant | `camunda/camunda/schema-manager` | issue, `component/data-layer` |
| A record written into an existing field, or a new archiver job | `camunda/camunda/zeebe/exporters` (Camunda exporter) | issue, `component/data-layer` |
| A query, filter or sort over a field | `camunda/camunda/search` | issue, `component/data-layer` |
| The same field in RDBMS | `camunda/camunda/db` (`db/rdbms-schema` changelog) | PR with Data Layer sign-off |
| `zeebe-record-*` templates (Optimize's input) | `camunda/camunda/zeebe/exporters` (ES/OS exporters) | issue |
| A per-index shard, replica or refresh override | `camunda/camunda/configuration` ([storage ADR 002](../docs/adr/storage/002-per-index-shard-configuration.md)) | issue |
| A new record value or enum the entity copies | `camunda/camunda/zeebe/protocol` | issue, `component/zeebe-engine` |

## 3. Structure

| Path | Holds |
|---|---|
| `src/main/resources/schema/elasticsearch/create/{index,template}/` | ES mapping JSON, one file per descriptor, named `<component>-<index>.json` |
| `src/main/resources/schema/opensearch/create/{index,template}/` | OS mapping JSON, same file names and identical `mappings` |
| `src/main/resources/schema/opensearch/create/policy/` | OS ISM policy for archived indices (used by `schema-manager`) |
| `descriptors/` | `IndexDescriptor`, `IndexTemplateDescriptor`, abstract bases, `IndexDescriptors`, `ComponentNames`, dependant markers |
| `descriptors/index/` | Plain indices: identity (user, group, role, tenant, mapping rule, authorization), definitions (process, decision, form, deployed resource, agent definition), metadata, cluster variables, global listeners, history deletion, audit-log cleanup |
| `descriptors/template/` | Template indices with dated archive indices: list-view, flow-node instance, variable, incident, job, task, operation, batch operation, decision instance, usage metrics, audit log, agent instance and history, web session and others |
| `descriptors/backup/` | `BackupPriority`, `Prio1Backup`–`Prio4Backup`, `BackupPriorities`, `SnapshotIndexCollection` |
| `entities/` | `ExporterEntity` and per-domain entity packages; `SinceVersion`, `BeforeVersion880` |

Names: `<prefix->` `<component>-<index>-<version>_` with `alias` and `template` suffixes
(`AbstractIndexDescriptor`). The `operate-` and `tasklist-` component names are historical; new
indices use `camunda-` (TODO(confirm) that this is the rule).

No variant code: one descriptor serves ES and OS, picking its JSON by the `isElasticsearch` flag.
The module depends on nothing in the system but `zeebe/protocol` and the security library API; it
has no client code and no Spring.

TODO(confirm): `operate-import-position`, `tasklist-import-position`,
`operate-migration-steps-repository` and `operate-event` have JSON files but no descriptor in
`IndexDescriptors`, and `schema-manager`'s `SchemaCleanup` deletes the first three as legacy
indices. Are these files dead?

## 4. Binding decisions

ADR index: [`docs/adr/`](../docs/adr/README.md); domain `storage` covers this module. It has no
ADR folder of its own. The decisions and rules that most often shape schema work:

- [Per-index shard configuration](../docs/adr/storage/002-per-index-shard-configuration.md)
  (storage ADR 002): plain indices default to one shard, templates follow the global knob; the
  descriptor is the single source of per-index defaults; a new index needs a
  `NumberOfShardsPerIndex` field.
- [Per-physical-tenant schema initialization](../docs/adr/management/005-per-physical-tenant-schema-initialization.md):
  the same descriptors are applied once per physical tenant.
- Rules without an ADR: zero required data migrations across versions ([README](README.md));
  `"dynamic": "strict"` and additive-only ([overview](../docs/architecture/overview.md)); field
  types, multi-fields and version bumps ([secondary-storage guide](../docs/monorepo-docs/architecture/components/secondary-storage/working-with-secondary-storage.md) § 2, § 4.6);
  every descriptor has a backup tier (`BackupPriorityDescriptorArchTest`, #55578).

## 5. Planning constraints

Every plan must answer each of these (applies / n/a + decision or reason).

### C1 — Additive-only schema change
- **Question:** Does the change only add optional fields or new indices? No removal, retyping,
  renaming or required field without a default. What do reads return for documents written before
  the field existed?
- **Hard:** yes
- **Detail:** [README](README.md), `SchemaUpdateIT` in `schema-manager`

### C2 — Elasticsearch and OpenSearch in step
- **Question:** Is the same change made in both the `elasticsearch` and `opensearch` JSON, with
  identical `mappings`?
- **Hard:** yes
- **Detail:** `SecondaryStorageSchemaConstraintsTest`

### C3 — Approved field types and Data Layer sign-off
- **Question:** Does every new field use an approved type (`keyword` for IDs and keys, never
  `text`)? Does it need `binary`, `join`, `nested` or a new pattern, and has `#team-data-layer`
  signed off?
- **Hard:** yes
- **Detail:** [Secondary-storage guide](../docs/monorepo-docs/architecture/components/secondary-storage/working-with-secondary-storage.md) § 1.2, § 2

### C4 — Entity field versioning
- **Question:** Does every new entity field carry `@SinceVersion("<release>")` and a non-null
  default (or `requireDefault = false` with a reason)?
- **Hard:** yes
- **Detail:** [`SinceVersion`](src/main/java/io/camunda/webapps/schema/entities/SinceVersion.java), `EntityTest`

### C5 — Descriptor version bump means a new index generation
- **Question:** Does the change bump a descriptor's `getVersion()`? If so, what migrates or
  reindexes the old generation? Additive changes keep the version and rely on `schema-manager`'s
  in-place mapping update.
- **Hard:** yes
- **Detail:** [Secondary-storage guide](../docs/monorepo-docs/architecture/components/secondary-storage/working-with-secondary-storage.md) § 4.6

### C6 — New index wiring (shards, backup, archiving)
- **Question:** For a new index: plain index or template? Registered in `IndexDescriptors`? Which
  `Prio*Backup` tier? Which archiver marker (`ProcessInstanceDependant`, `BatchOperationDependant`)
  or its own cleanup? Field added to `NumberOfShardsPerIndex`? Tenant-id field set?
- **Hard:** yes
- **Detail:** [storage ADR 002](../docs/adr/storage/002-per-index-shard-configuration.md),
  [archiving](../docs/monorepo-docs/architecture/components/secondary-storage/archiving.md),
  [backup and restore](../docs/monorepo-docs/architecture/components/secondary-storage/backup-and-restore.md)

### C7 — RDBMS counterpart
- **Question:** Is the same data added to `db/rdbms-schema` (Liquibase, additive) so every
  secondary-storage backend can serve it, or is the feature ES/OS-only and why?
- **Hard:** yes
- **Detail:** [Architecture overview](../docs/architecture/overview.md), "RDBMS schema"

### C8 — Volume and query cost
- **Question:** What is the document volume and field cardinality, and will the field be
  aggregated or sorted on? Is the performance validation plan written?
- **Hard:** no
- **Detail:** [Secondary-storage guide](../docs/monorepo-docs/architecture/components/secondary-storage/working-with-secondary-storage.md) § 1.1

## 6. Data and persistence

Defines, does not store: indices live in the user's Elasticsearch or OpenSearch, created by
`schema-manager` with an optional prefix and once per physical tenant. Supported servers:
[`.ci/db-versions.yml`](../.ci/db-versions.yml). Template indices are archived by the Camunda
exporter into dated indices (`<name>_<date>`); on OpenSearch an ISM policy deletes archived indices.
Data volume is dominated by the template indices (list-view, flow-node instance, variable, job,
incident, sequence flow, decision instance, task); plain indices are small and read by key
([storage ADR 002](../docs/adr/storage/002-per-index-shard-configuration.md)).

## 7. Cross-cutting qualities

- **Tenancy:** template and index descriptors that hold tenant-owned data return their tenant-id
  field (`getTenantIdField`); `search` filters on it. Physical tenants get separate indices through
  `schema-manager`, not through this module.
- **Security:** the authorization index stores permission and resource types from the security
  library; access checks are `search`'s.
- **Backup:** every descriptor must declare a backup tier except `PersistentWebSessionTemplate`
  (`BackupPriorityDescriptorArchTest`). TODO(confirm): the backup-and-restore doc still lists
  `camunda-persistent-web-session` under Prio 4.
- **Performance:** see C8 and the guide's field-type rules.

## 8. Delivery

As [Orchestration Cluster SYSTEM.md](../SYSTEM.md) § 6. The Java API is internal and ships with
its callers. The schema itself must let a cluster upgraded from the previous minor read data written
before the upgrade, with no data migration ([README](README.md)). TODO(confirm): which version
pairs `SchemaUpdateIT` and the migration ITs in `qa/acceptance-tests` cover, and whether
backports may change a mapping.

## 9. Testing expectations

- **Unit (this module):** `SecondaryStorageSchemaConstraintsTest`, `EntityTest`,
  `IndexDescriptorTest`, `SnapshotIndexCollectionTest`.
  `./mvnw verify -pl webapps-schema -DskipTests=false -Dquickly`.
- **Architecture:** `BackupPriorityDescriptorArchTest` in `qa/archunit-tests`.
- **Integration:** `SchemaUpdateIT` and `SchemaManagerIT` in `schema-manager` on ES and OS;
  `@MultiDbTest` acceptance tests in [`qa/acceptance-tests`](../docs/testing/acceptance.md);
  `ExporterBulkConsistencyIT` when several exporter handlers write one entity.
- `configuration`'s `NumberOfShardsPerIndexTest` fails when an index has no shard field.

## 10. Planning conventions

- Issues: templates `2. feature_request.yml`, `3. task.yml`, `4. epic breakdown.yml` in
  `.github/ISSUE_TEMPLATE/`; label `component/data-layer` (TODO(confirm): the create-issue skill
  maps only `db/` and `search/` to it).
- TODO(confirm): plans directory and ID prefix for plan refs.

## 11. Glossary

| Term | Meaning here |
|---|---|
| Index (descriptor) | A plain ES/OS index for config, definition or singleton data; one shard by default, not archived |
| Template (descriptor) | An index template whose main index is archived into dated indices; shard count from configuration |
| Descriptor version | The `<version>` in the index name; bumping it creates a new, empty index generation |
| Dependant | A template archived together with its parent process instance or batch operation |
| Backup priority | The snapshot tier (1–4) an index is backed up in; earlier tiers hold state that later ones depend on |
| Component name | `operate`, `tasklist` or `camunda` index-name prefix; historical, not ownership |
| Strict mapping | `"dynamic": "strict"`: ES/OS rejects documents with fields not in the mapping |

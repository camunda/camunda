# Backup and Restore: Secondary Storage

This guide explains how Camunda 8 backs up and restores its secondary storage layer - covering
Elasticsearch (ES) and OpenSearch (OS) indices in both the Orchestration Cluster (Operate,
Tasklist, and shared Camunda indices) and Optimize, as well as the RDBMS secondary storage path.

> **Scope**
> This document covers the secondary storage layer only. Backup and restore of Zeebe's primary
> storage (RocksDB partition state) is handled separately via the Zeebe broker backup mechanism
> and the `zbctl` / `camunda-restore` CLI. Where the two interact - particularly for RDBMS-aware
> restore - that interaction is explained in [§4](#4-rdbms-secondary-storage).

---

## Table of Contents

1. [Orchestration Cluster (ES/OS)](#1-orchestration-cluster-esos)

- 1.1 [How Backups Are Taken](#11-how-backups-are-taken)
- 1.2 [Backup Priority System](#12-backup-priority-system)
- 1.3 [Snapshot Naming](#13-snapshot-naming)
- 1.4 [Backup States](#14-backup-states)
- 1.5 [Configuration](#15-configuration)
- 1.6 [API / Actuator Endpoints](#16-api--actuator-endpoints)

2. [Restore (Orchestration Cluster)](#2-restore-orchestration-cluster)
3. [Optimize (ES/OS)](#3-optimize-esos)

- 3.1 [How Optimize Backups Work](#31-how-optimize-backups-work)
- 3.2 [Snapshot Naming (Optimize)](#32-snapshot-naming-optimize)
- 3.3 [Restore (Optimize)](#33-restore-optimize)

4. [RDBMS Secondary Storage](#4-rdbms-secondary-storage)

- 4.1 [No ES/OS Snapshot Mechanism](#41-no-esos-snapshot-mechanism)
- 4.2 [RDBMS-Aware Zeebe Restore](#42-rdbms-aware-zeebe-restore)

5. [Key Classes and Locations](#5-key-classes-and-locations)
6. [References](#6-references)

---

## 1. Orchestration Cluster (ES/OS)

The Orchestration Cluster indices are a set of unified indices, prefixed with `operate`, `tasklist`,
or `camunda` depending on their pre-8.8 origin. When ES or OS is the secondary storage backend, all
of these share a single coordinated backup mechanism built around the ES/OS
[Snapshot API](https://www.elastic.co/guide/en/elasticsearch/reference/current/snapshot-restore.html) (
ES).

Before 8.8, the system was quite similar but each independent component managed their own indices.

### 1.1 How Backups Are Taken

A backup is **a series of ES/OS snapshots taken sequentially by priority level**. The ordering is
not arbitrary - it encodes data dependency relationships between indices (see §1.2).

The core components are:

|            Component            |      Module      |                                                                                                           Role                                                                                                            |
|---------------------------------|------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `BackupServiceImpl`             | `webapps-backup` | Orchestrates the priority-ordered snapshot queue using a Spring task executor (corePoolSize=1, maxPoolSize=8); ensures snapshots run sequentially via queue + callback scheduling, not by a strictly single-threaded pool |
| `BackupRepository`              | `webapps-backup` | Interface to the ES/OS snapshot API (`executeSnapshotting`, `deleteSnapshot`, `getBackupState`, etc.)                                                                                                                     |
| `ElasticsearchBackupRepository` | `webapps-backup` | ES implementation of `BackupRepository`                                                                                                                                                                                   |
| `OpensearchBackupRepository`    | `webapps-backup` | OS implementation of `BackupRepository`                                                                                                                                                                                   |
| `BackupPriorityConfiguration`   | `dist`           | Spring `@Configuration` that assembles the ordered `BackupPriorities` bean from all index/template descriptors                                                                                                            |

**Execution model**: Snapshots are enqueued into a `ConcurrentLinkedQueue` and executed **one at a
time** by `BackupServiceImpl` using a dedicated Spring `ThreadPoolTaskExecutor` (`webapps_backup_*`)
configured with `corePoolSize=1` and `maxPoolSize=8`. Each snapshot's completion callback schedules
the next one, so at most a single snapshot operation is active at any given time. This guarantees
that priority ordering is strictly respected and helps prevent the ES/OS cluster from being
overloaded with concurrent snapshot operations.

**Important**: A backup is not complete until _all_ snapshots have succeeded. If any snapshot
fails, the backup is marked `FAILED` and must be retried from the beginning with a new backup ID.
Partial backups cannot be resumed.

### 1.2 Backup Priority System

The priority system is the most important concept to understand when reasoning about backup
correctness. The ordering encodes the dependency chain between the importer, post-importer, and the
indices they write to.

**Core principle**: An index that tracks _state_ (e.g. import progress) must be snapshotted before
the indices that _depend_ on that state. Restoring a detail index (e.g. `operate-incident`) to a
point ahead of its parent (`operate-list-view`) would leave the index in an inconsistent state.

#### Priority Interfaces

Every index/template descriptor in `webapps-schema` implements `BackupPriority` via one of four
marker sub-interfaces:

|   Interface   |                           Intended for                           |
|---------------|------------------------------------------------------------------|
| `Prio1Backup` | State/progress tracking indices - must be snapshotted first      |
| `Prio2Backup` | Primary entity head indices (`list-view`, `task`)                |
| `Prio3Backup` | Detail/event indices that reference Prio 2 entities              |
| `Prio4Backup` | Reference data, user management, metrics, audit - least volatile |

`BackupPriority` also exposes a `required()` method. If `required()` returns `false` the index is
treated as optional: it is only considered for inclusion in a snapshot part if it already exists in
ES/OS during pre-checks. Currently, `required()==false` only affects pre-checking and snapshot-part
selection; the snapshot request itself is still executed against the full index list, so a missing
optional index that is explicitly listed can still cause snapshot creation to fail. All indices are
required by default.

#### Snapshot Parts Produced

`BackupPriorities.indicesSplitBySnapshot()` expands the four priority groups into up to **7
sequential snapshot parts** (empty parts are skipped):

| Part |                                                                                                                                                                                                                        Contents                                                                                                                                                                                                                         |                       Why this order                        |
|------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|-------------------------------------------------------------|
| 1    | **Prio 1 main**: `operate-metadata`, `camunda-history-deletion`                                                                                                                                                                                                                                                                                                                                                                                         | Import/post-import state - must precede everything else     |
| 2    | **Prio 2 main**: `operate-list-view`, `tasklist-task`                                                                                                                                                                                                                                                                                                                                                                                                   | Head entities; detail records (Prio 3) reference these      |
| 3    | **Prio 2 dated**: all `operate-list-view_<date>`, `tasklist-task_<date>` archived shards                                                                                                                                                                                                                                                                                                                                                                | Archived shards of Prio 2 templates                         |
| 4    | **Prio 3 main**: `operate-batch-operation`, `operate-operation`, `operate-decision-instance`, `operate-flow-node-instance`, `operate-incident`, `operate-job`, `operate-message`, `operate-post-importer-queue`, `operate-sequence-flow`, `operate-variable`, `tasklist-task-variable`, `tasklist-snapshot-task-variable`, `camunda-message-subscription`                                                                                               | Detail records dependent on Prio 2                          |
| 5    | **Prio 3 dated**: archived shards for all Prio 3 templates                                                                                                                                                                                                                                                                                                                                                                                              | Archived shards of Prio 3 templates                         |
| 6    | **Prio 4 main**: `operate-decision`, `operate-decision-requirements`, `operate-process`, `tasklist-form`, `camunda-authorization`, `camunda-group`, `camunda-mapping-rule`, `camunda-persistent-web-session`, `camunda-role`, `camunda-tenant`, `camunda-user`, `camunda-usage-metric`, `camunda-usage-metric-tu`, `camunda-audit-log`, `camunda-audit-log-cleanup`, `camunda-cluster-variable`, `camunda-job-metrics-batch`, `camunda-global-listener` | Reference/static data; least likely to change between parts |
| 7    | **Prio 4 dated**: archived shards for templated Prio 4 indices                                                                                                                                                                                                                                                                                                                                                                                          | Archived shards of Prio 4 templates                         |

> **Archiving interaction**: Operate and Tasklist use a time-window archiving strategy (see
> `docs/data-layer/archiving.md`) where completed data is periodically moved from "main" indices
> into dated indices (e.g. `operate-list-view_2024-01-01`). Because dated shards are snapshottable
> independently of main indices, each priority level produces two parts - the main index and its
> dated variants - with the main index always snapshotted first.

#### Adding New Indices

When a new index or template descriptor is created:

1. Decide which priority interface it implements (`Prio1Backup` through `Prio4Backup`) based on its
   dependency position in the import chain.
2. Add it to `BackupPriorityConfiguration.getBackupPriorities()` in the appropriate priority list.
3. If the index is only present when an optional component is deployed (e.g. a feature flag), set
   `required()` to `false` in the descriptor.
4. Update the tests in `BackupPrioritiesTest` to assert on the new total snapshot count.

### 1.3 Snapshot Naming

Snapshots for the Orchestration Cluster follow this naming format (defined in
`WebappsSnapshotNameProvider`):

```
camunda_webapps_{backupId}_{version}_part_{n}_of_{total}
```

For example:

```
camunda_webapps_20240601_8.6.0_part_1_of_7
camunda_webapps_20240601_8.6.0_part_2_of_7
...
camunda_webapps_20240601_8.6.0_part_7_of_7
```

Where:

- `{backupId}` - caller-supplied integer ID (e.g. a timestamp or sequential counter)
- `{version}` - Camunda version string at time of backup
- `{n}` / `{total}` - 1-based part index and total part count

The `{total}` can vary across backups (e.g. from 5 to 7) depending on which optional index sets are
active at backup time. Always use the `scheduledSnapshots` list returned by the take-backup response
to determine how many parts were created for a specific backup.

### 1.4 Backup States

`BackupStateDto` represents the aggregate state of a backup:

|     State      |                              Meaning                               |
|----------------|--------------------------------------------------------------------|
| `IN_PROGRESS`  | At least one snapshot is still being taken                         |
| `COMPLETED`    | All snapshots completed successfully                               |
| `FAILED`       | One or more snapshots failed                                       |
| `INCOMPLETE`   | Fewer snapshots exist than expected (e.g. backup interrupted)      |
| `INCOMPATIBLE` | Snapshot metadata version is incompatible with the running version |

### 1.5 Configuration

Configure the backup repository name via application properties:

```yaml
# Elasticsearch
camunda.data.secondary-storage.elasticsearch.backup.repository-name: my-backup-repo

# OpenSearch
camunda.data.secondary-storage.opensearch.backup.repository-name: my-backup-repo
```

The repository must already be registered with ES/OS before a backup is triggered. Camunda
validates that the repository exists (`validateRepositoryExists`) when the backup API is called and
returns an error if it is not found.

The backup executor uses a single-core thread pool with a queue capacity of 4096.

### 1.6 API / Actuator Endpoints

Backups are triggered, monitored, and deleted via Spring Boot actuator endpoints:

|                 Endpoint                  |                 Context                  |            Class             |
|-------------------------------------------|------------------------------------------|------------------------------|
| `POST/GET/DELETE /actuator/backupHistory` | Combined (all components together)       | `BackupController`           |
| `POST/GET/DELETE /actuator/backups`       | Standalone Operate / Tasklist deployment | `BackupControllerStandalone` |

Both endpoints are wired to the same `BackupServiceImpl` instance in the `HistoryBackupComponent`
Spring configuration (active only when secondary storage is ES or OS).

> **Not to be confused with the Zeebe broker backup endpoint**: The standalone Zeebe broker and
> gateway also expose a `/actuator/backups` endpoint (active under the `broker` and `gateway`
> Spring profiles) that manages **Zeebe RocksDB partition backups**, not secondary-storage
> snapshots. The two endpoints serve entirely different backup systems. If you are running a
> combined Camunda distribution, use `/actuator/backupHistory` for webapps secondary-storage
> backups; the broker's `/actuator/backups` is only reachable on a standalone broker/gateway
> process.

---

## 2. Restore (Orchestration Cluster)

Restoration of the Orchestration Cluster's ES/OS indices follows a straightforward sequential
process:

1. **Stop the application** - stop all Operate, Tasklist, and Camunda exporter processes that write
   to the ES/OS cluster to prevent writes during restore.
2. **Delete all application indices** - remove all existing indices matching the configured prefix
   (e.g. `camunda-*`, `operate-*`, `tasklist-*`). Restoring into a non-empty index set will fail
   or produce inconsistent results.
3. **Restore snapshots in order** - restore each part **in the original numbered order**
   (part 1, then part 2, …, then part N). The order is retrieved from the `scheduledSnapshots`
   field of the original `TakeBackupResponseDto`. **Do not restore parts out of order** - restoring
   Prio 3 detail indices before Prio 2 head indices will leave the data in an inconsistent state.
4. **Restart the application** - bring Operate/Tasklist back online. They will pick up from the
   import position stored in the restored `operate-metadata` index.
5. **Validate data integrity** - confirm that process instances, tasks, and incidents are visible
   and consistent.

> **Consistency with Zeebe**: If you are performing a full cluster restore (Zeebe + secondary
> storage), restore the Zeebe partition state first, then restore the ES/OS indices from a backup
> taken at the same backup ID. The Camunda exporter will re-process any records that occurred
> between the backup checkpoint and the present, so a small replay window after restore is expected
> and normal.

---

## 3. Optimize (ES/OS)

Optimize has its **own independent backup implementation**, separate from the webapps backup stack
described in §1. The two systems do not share a `BackupPriorityConfiguration` bean and their
snapshots are stored under different name prefixes.

> This is verified by `BackupPrioritiesTest`, which explicitly asserts that no Optimize index name
> appears in the webapps `BackupPriorityConfiguration`.

### 3.1 How Optimize Backups Work

Optimize always produces exactly **2 snapshots per backup** (
`EXPECTED_NUMBER_OF_SNAPSHOTS_PER_BACKUP = 2`),
regardless of how many indices are deployed. The split is:

|  Snapshot   |                              Contents                               |                                           Why                                            |
|-------------|---------------------------------------------------------------------|------------------------------------------------------------------------------------------|
| Part 1 of 2 | All **import indices** (ETL progress tracking indices)              | Must be captured before non-import data to preserve consistency with the import pipeline |
| Part 2 of 2 | All **non-import indices** (entity data, reports, dashboards, etc.) | Dependent on Part 1                                                                      |

Both snapshot calls are issued back-to-back inside a single `CompletableFuture.runAsync` block in
`BackupWriter.triggerSnapshotCreation`. Sequencing behavior depends on the backend:

- **Elasticsearch**: `SnapshotRepositoryES.triggerSnapshot` uses `waitForCompletion(true)`, making
  the ES HTTP call block until the snapshot finishes. Part 2 therefore only starts after Part 1's
  blocking call returns — sequencing is preserved, but by the blocking call, not by a chained
  `CompletableFuture`.
- **OpenSearch**: `SnapshotRepositoryOS.triggerSnapshot` issues a true async request (using the
  OpenSearch client's async API) and registers a `whenComplete` callback. Both Part 1 and Part 2
  are fired back-to-back without waiting, so **there is no sequencing guarantee** between them on
  OpenSearch.

The split is determined dynamically by
`mappingMetadataRepository.getIndexAliasesWithImportIndexFlag(boolean)`:
it queries the ES/OS index aliases and separates them into two groups based on whether the index is
flagged as an import index.

Key classes:

|         Component          |                                           Role                                           |
|----------------------------|------------------------------------------------------------------------------------------|
| `BackupService` (Optimize) | Validates the repository, prevents duplicate backup IDs, and delegates to `BackupWriter` |
| `BackupWriter`             | Triggers the two snapshots back-to-back                                                  |
| `BackupReader`             | Interface for reading snapshot state (implemented for ES and OS separately)              |
| `BackupRestService`        | `@RestControllerEndpoint(id = "backups")` actuator                                       |
| `SnapshotUtil`             | Snapshot naming utilities                                                                |

### 3.2 Snapshot Naming (Optimize)

Optimize snapshots follow a different naming scheme:

```
camunda_optimize_{backupId}_{version}_part_1_of_2   ← import indices
camunda_optimize_{backupId}_{version}_part_2_of_2   ← non-import indices
```

Note: the `camunda_optimize_` prefix is distinct from the `camunda_webapps_` prefix used by the
Orchestration Cluster. The ES/OS repository can be shared between both backup systems (they will
not interfere with each other based on prefix), but this is a deployment decision.

Optimize backup state uses its own `BackupState` enum: `COMPLETED`, `FAILED`, `INCOMPATIBLE`,
`IN_PROGRESS`, `INCOMPLETE`.

### 3.3 Restore (Optimize)

1. **Stop Optimize**.
2. **Delete all Optimize indices** (matching `camunda_optimize_*` or the configured prefix).
3. **Restore Part 1** (`..._part_1_of_2`, import indices).
4. **Restore Part 2** (`..._part_2_of_2`, non-import indices).
5. **Restart Optimize**.

Restoring Part 2 before Part 1 is not supported and will produce an inconsistent state where entity
data is ahead of the import pipeline's last recorded position.

---

## 4. RDBMS Secondary Storage

When the secondary storage type is `rdbms` (PostgreSQL or compatible), the ES/OS snapshot
mechanism is **not active**:

- `BackupPriorityConfiguration` is annotated
  `@ConditionalOnSecondaryStorageType({elasticsearch, opensearch})` - it is not instantiated for
  RDBMS deployments.
- `HistoryBackupComponent` is likewise conditional and will not create a `BackupServiceImpl`.

### 4.1 No ES/OS Snapshot Mechanism

RDBMS backups are the responsibility of the database operator, using native database tooling
(e.g. `pg_dump`, continuous WAL archiving, or a managed-database snapshot service). Camunda does
not provide a built-in trigger for RDBMS backups.

**Recommendation**: Take a point-in-time consistent RDBMS snapshot in coordination with a Zeebe
broker backup at the same logical checkpoint. Record the Zeebe backup ID alongside the RDBMS
snapshot so they can be matched during restore.

### 4.2 RDBMS-Aware Zeebe Restore

When RDBMS is the secondary storage, Zeebe's `RestoreManager` performs an enhanced restore that
aligns the Zeebe RocksDB partition state with what the RDBMS has already received from the exporter.
This prevents the exporter from re-sending records that the RDBMS has already processed (which
could cause duplicate or conflicting writes).

**How it works** (`RestoreManager.restoreRdbms()`):

1. Load the exported positions for each Zeebe partition from the RDBMS (`ExporterPositionMapper`).
2. Load backup metadata for each partition from the backup store in parallel.
3. `RestorePointResolver.resolve()` computes the optimal set of backup checkpoints per partition -
   choosing the most recent backup checkpoint that is **at or before** the position already
   exported to RDBMS.
4. Each partition is restored from its resolved checkpoint in parallel (using virtual threads).
5. After all partition data is restored, the cluster topology/configuration file is restored on
   node 0.

This differs from the ES/OS restore path, where Zeebe's checkpoint does not need to account for
the secondary storage state (the exporter will simply re-process any gap after restore).

**RDBMS restore summary**:

| Step |                                      Action                                       |
|------|-----------------------------------------------------------------------------------|
| 1    | Take a RDBMS point-in-time snapshot (coordinated with Zeebe backup)               |
| 2    | Restore the RDBMS snapshot using native DB tooling                                |
| 3    | Run `camunda-restore` with the RDBMS-aware flags, which triggers `restoreRdbms()` |
| 4    | `RestorePointResolver` selects the right Zeebe checkpoint per partition           |
| 5    | Zeebe partitions are restored in parallel                                         |
| 6    | Start the cluster - the exporter will not re-send already-processed records       |

---

## 5. Key Classes and Locations

|        Class / Interface        |      Module      |                                            Role                                            |
|---------------------------------|------------------|--------------------------------------------------------------------------------------------|
| `BackupPriority`                | `webapps-schema` | Marker interface for ES/OS index backup ordering                                           |
| `Prio1Backup` – `Prio4Backup`   | `webapps-schema` | Priority marker sub-interfaces                                                             |
| `BackupPriorities`              | `webapps-schema` | Record holding all 4 priority lists; produces ordered `SnapshotIndexCollection` list       |
| `SnapshotIndexCollection`       | `webapps-schema` | Required + skippable index names for one snapshot part                                     |
| `BackupPriorityConfiguration`   | `dist`           | Assembles `BackupPriorities` from descriptors; ES/OS only                                  |
| `BackupService` (webapps)       | `webapps-backup` | Interface: `takeBackup`, `deleteBackup`, `getBackupState`, `getBackups`                    |
| `BackupServiceImpl`             | `webapps-backup` | Single-threaded sequential snapshot executor                                               |
| `BackupRepository`              | `webapps-backup` | Interface to ES/OS snapshot API                                                            |
| `ElasticsearchBackupRepository` | `webapps-backup` | ES implementation                                                                          |
| `OpensearchBackupRepository`    | `webapps-backup` | OS implementation                                                                          |
| `WebappsSnapshotNameProvider`   | `webapps-backup` | `camunda_webapps_{id}_{ver}_part_{n}_of_{total}`                                           |
| `Metadata`                      | `webapps-backup` | Record: `backupId`, `version`, `partNo`, `partCount`                                       |
| `BackupStateDto`                | `webapps-backup` | `IN_PROGRESS`, `INCOMPLETE`, `COMPLETED`, `FAILED`, `INCOMPATIBLE`                         |
| `BackupController`              | `dist`           | `@WebEndpoint(id = "backupHistory")` - combined deployment actuator                        |
| `BackupControllerStandalone`    | `dist`           | `@WebEndpoint(id = "backups")` - standalone deployment actuator                            |
| `HistoryBackupComponent`        | `dist`           | Wires `BackupServiceImpl`; conditional on ES/OS                                            |
| `BackupConfig`                  | `dist`           | Configures repository props and executor thread pool                                       |
| `BackupService` (Optimize)      | `optimize`       | Orchestrates Optimize's 2-snapshot backup                                                  |
| `BackupWriter` (Optimize)       | `optimize`       | Triggers two snapshots back-to-back; sequential on ES (blocking), concurrent on OS (async) |
| `BackupReader` (Optimize)       | `optimize`       | Interface: reads snapshot state (ES + OS impls)                                            |
| `BackupRestService` (Optimize)  | `optimize`       | `@RestControllerEndpoint(id = "backups")` - Optimize actuator                              |
| `SnapshotUtil` (Optimize)       | `optimize`       | `camunda_optimize_{id}_{ver}_part_{1\|2}_of_2`                                             |
| `RestoreManager`                | `zeebe/restore`  | Restores Zeebe partition data; RDBMS-aware path included                                   |
| `RestorePointResolver`          | `zeebe/restore`  | Selects optimal backup checkpoint per partition aligned to RDBMS position                  |
| `ExporterPositionMapper`        | `db/rdbms`       | Reads per-partition exporter positions from RDBMS during restore                           |

---

## 6. References

|             Resource             |                                          Location                                           |
|----------------------------------|---------------------------------------------------------------------------------------------|
| Archiving (dated indices)        | `docs/data-layer/archiving.md`                                                              |
| Secondary storage guide          | `docs/data-layer/working-with-secondary-storage.md`                                         |
| RDBMS module documentation       | `docs/rdbms.md`                                                                             |
| Operate backup/restore QA tests  | `operate/qa/backup-restore-tests/`                                                          |
| Tasklist backup/restore QA tests | `tasklist/qa/backup-restore-tests/`                                                         |
| Operate component backup doc     | `operate/webapp/docs/backup.md`                                                             |
| Tasklist component backup doc    | `tasklist/webapp/docs/backup.md`                                                            |
| Webapps backup module            | `webapps-backup/`                                                                           |
| Backup priority configuration    | `dist/src/main/java/io/camunda/application/commons/backup/BackupPriorityConfiguration.java` |
| Backup priorities test           | `dist/src/test/java/io/camunda/application/commons/backup/BackupPrioritiesTest.java`        |
| Restore manager (Zeebe)          | `zeebe/restore/src/main/java/io/camunda/zeebe/restore/RestoreManager.java`                  |
| Optimize backup service          | `optimize/backend/src/main/java/io/camunda/optimize/service/BackupService.java`             |
| Optimize snapshot util           | `optimize/backend/src/main/java/io/camunda/optimize/service/util/SnapshotUtil.java`         |
| ES Snapshot API docs             | https://www.elastic.co/guide/en/elasticsearch/reference/current/snapshot-restore.html       |


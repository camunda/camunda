```yaml
---
applyTo: "zeebe/restore/**"
---
```
# zeebe/restore — Backup Restore Module

## Purpose

This module restores Zeebe broker partitions from backups stored in a `BackupStore`. It downloads backup snapshots and journal segments, reconstructs the partition's data directory (RocksDB snapshot + segmented journal), and restores the cluster topology file. It supports three restore modes: explicit backup IDs, time-range-based restore, and RDBMS-aware restore using exported positions.

## Architecture

```
RestoreApp (dist/)          ← Spring Boot entry point, parses --backupId / --from / --to
  └── RestoreManager        ← Orchestrator: resolves partitions, validates data dir, runs parallel restore
        ├── RestorePointResolver   ← Finds globally consistent checkpoint across partitions
        ├── PartitionRestoreService ← Downloads & applies backup per partition (snapshot + journal)
        └── RestoreValidator       ← Post-restore sanity check (partition dirs + topology file)
```

**Data flow:**
1. `RestoreApp` (in `dist/`) receives CLI args and creates `RestoreManager` with `BrokerCfg`, `BackupStore`, and optional `ExporterPositionMapper`.
2. `RestoreManager` verifies the data directory is empty, computes the partition distribution from `StaticConfigurationGenerator`, then spawns virtual threads to restore partitions in parallel.
3. For time-range/RDBMS restores, `RestorePointResolver.resolve()` loads `BackupMetadata` per partition, finds restorable ranges matching the time window and exported positions, and selects the latest common checkpoint across all partitions.
4. `PartitionRestoreService.restore()` downloads backups from the `BackupStore` into a temp directory, moves the snapshot from the first backup to `FileBasedSnapshotStore`, then copies journal records between checkpoint positions into a new `SegmentedJournal`.
5. On node 0, `RestoreManager.restoreTopologyFile()` writes a `PersistedClusterConfiguration` with an `UpdateRoutingState` change plan.
6. `RestoreValidator.validate()` performs post-restore checks: partition directories are non-empty and the topology file exists (node 0 only).

## Key Abstractions

- **`RestoreManager`** (`RestoreManager.java`): Top-level orchestrator. Implements `CloseableSilently`. Uses virtual threads (`Executors.newThreadPerTaskExecutor`) for parallel partition restore. Validates data directory emptiness with configurable ignore list.
- **`PartitionRestoreService`** (`PartitionRestoreService.java`): Per-partition restore logic. Downloads backups via `BackupStore.restore()`, reconstructs journal by copying records between checkpoint positions, moves snapshot files via `FileBasedSnapshotStore.restore()`.
- **`RestorePointResolver`** (`RestorePointResolver.java`): Pure static logic for resolving which backups to restore. Uses `BackupMetadata` (checkpoint entries + ranges) to find restorable ranges, a common checkpoint across partitions, and the list of required backup-type checkpoints. Returns `RestorableBackups` record.
- **`RestoreValidator`** (`RestoreValidator.java`): Post-restore sanity checker. Static `validate(BrokerCfg)` method verifies partition directories exist and contain files, and topology file is present on node 0.
- **`BackupValidator`** (functional interface in `PartitionRestoreService`): Per-backup validation hook. `ValidatePartitionCount` (inner class in `RestoreManager`) checks partition count matches. `BackupValidator.none()` skips validation.
- **`BackupNotFoundException`** (`BackupNotFoundException.java`): Thrown when no completed backup matches a given checkpoint ID.

## Key Dependencies

- **`zeebe-backup`**: `BackupStore` interface, `BackupMetadata`, `BackupIdentifierWildcard`, `BackupMetadataSyncer` — all backup storage abstractions.
- **`zeebe-journal`**: `SegmentedJournal`, `JournalReader` — write-ahead log for copying records between checkpoints.
- **`zeebe-snapshots`**: `FileBasedSnapshotStore`, `RestorableSnapshotStore` — RocksDB snapshot restoration.
- **`zeebe-broker`**: `BrokerCfg`, `RaftPartitionFactory`, `StaticConfigurationGenerator`, `PartitionDistribution` — partition topology computation.
- **`zeebe-dynamic-config`** (`zeebe-cluster-config`): `ClusterConfigurationManagerService`, `PersistedClusterConfiguration`, `ProtoBufSerializer` — topology file persistence.
- **`camunda-db-rdbms`**: `ExporterPositionMapper` — queries RDBMS for last exported positions (RDBMS-aware restore only).

## Restore Modes

1. **Explicit backup IDs** (`--backupId=100`): All partitions restore from the same backup ID(s).
2. **Time-range** (`--from=... --to=...`): `RestorePointResolver` finds the best common checkpoint within the time window. Requires `from` when not using RDBMS.
3. **RDBMS-aware** (no `--from`/`--to`, RDBMS configured): Reads exported positions from RDBMS, resolves restore point ensuring no data loss relative to what was already exported.

## Multi-Backup Journal Stitching

When restoring from multiple backups (`long[] backupIds`), `PartitionRestoreService` stitches journals:
- Only the **first** backup's snapshot is used; subsequent snapshots are redundant.
- For each backup, journal records are copied from `(previousCheckpointPosition, currentCheckpointPosition]`.
- `copyBetweenCheckpoints()` skips over the previous checkpoint record, then copies until the current checkpoint position.
- The target journal is reset to match the source journal's first index on the initial backup.

## Extension Points

- **New restore mode**: Add a new method to `RestoreManager` following the `restoreTimeRange`/`restoreRdbms` pattern, producing a `Map<Integer, long[]>` of backup IDs per partition.
- **Custom backup validation**: Implement `PartitionRestoreService.BackupValidator` functional interface.
- **Post-restore actions**: Implement `RestoreApp.PostRestoreAction`/`PreRestoreAction` interfaces (wired via Spring in `dist/`).

## Invariants

- The data directory **must** be empty before restore starts (checked by `verifyDataFolderIsEmpty`). On failure, contents are deleted.
- Backup IDs are sorted ascending before processing (`validateAndSortBackupIds`).
- The topology file is only restored on **node 0** (`configuration.getCluster().getNodeId() == 0`).
- Journal records must contain a record at the exact checkpoint position; otherwise restore aborts with `IllegalStateException`.
- Backup contiguity is verified: no data gaps between consecutive backups (`verifyBackupContiguity`).

## Testing Patterns

- Use `TestRestorableBackupStore` (in-memory `BackupStore` implementation) for unit tests.
- `PartitionRestoreServiceTest` uses real `SegmentedJournal` + `FileBasedSnapshotStore` + `BackupService` with `@TempDir`.
- `RestorePointResolverTest` tests pure resolution logic with hand-crafted `BackupMetadata` — runs concurrently (`@Execution(ExecutionMode.CONCURRENT)`).
- `RestoreValidatorTest` tests filesystem-level validation with `@TempDir`.
- All tests use JUnit 5, AssertJ, `// given / when / then` structure, `should` prefix.
- Run scoped: `./mvnw -pl zeebe/restore -am test -DskipITs -DskipChecks -T1C`

## Common Pitfalls

- Never modify `RestorePointResolver` resolution logic without considering all three restore modes (explicit, time-range, RDBMS).
- The `from` parameter is **required** for non-RDBMS time-range restores but **optional** for RDBMS restores.
- `exportedPositions` map can be `null` (non-RDBMS path) — guard all access.
- Virtual threads are used for parallel partition restore; avoid blocking on synchronized locks.
- Checkpoint types have a `shouldCreateBackup()` flag — `MARKER` checkpoints are skipped when collecting restorable backups.

## Key Files

- `src/main/java/.../RestoreManager.java` — orchestrator, parallel restore, topology file
- `src/main/java/.../PartitionRestoreService.java` — per-partition download + journal stitching
- `src/main/java/.../RestorePointResolver.java` — checkpoint resolution algorithm
- `src/main/java/.../RestoreValidator.java` — post-restore sanity checks
- `dist/src/main/java/.../RestoreApp.java` — Spring Boot entry point (in `dist/` module)
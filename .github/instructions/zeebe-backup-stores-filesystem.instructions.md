```yaml
---
applyTo: "zeebe/backup-stores/filesystem/**"
---
```
# Filesystem Backup Store Module

## Purpose

This module implements the `BackupStore` interface (`zeebe/backup/api/BackupStore.java`) for local filesystem storage. It persists Zeebe partition backups (snapshots + segments) as files on disk, with JSON manifests tracking backup lifecycle state. Used when the broker is configured with `camunda.broker.data.backup.store=FILESYSTEM` via `FilesystemBackupStoreConfig` in `zeebe/broker/.../backup/FilesystemBackupStoreConfig.java`.

## Architecture

The module has three core components orchestrated by `FilesystemBackupStore`:

- **`FilesystemBackupStore`** — Implements `BackupStore`; delegates file I/O to `FileSetManager` and manifest I/O to `ManifestManager`. All public methods return `CompletableFuture` executed on a virtual-thread-per-task executor (`Executors.newVirtualThreadPerTaskExecutor()`).
- **`FileSetManager`** — Copies backup file sets (snapshot, segments) to/from the `contents/` subdirectory. Handles save, delete, and restore operations with `FileUtil.flush()` calls for durability.
- **`ManifestManager`** — Manages JSON manifest files in the `manifests/` subdirectory using Jackson. Tracks backup lifecycle through the `Manifest` sealed type hierarchy (IN_PROGRESS → COMPLETED/FAILED → DELETED).

### Directory Layout on Disk

```
basePath/
├── contents/{partitionId}/{checkpointId}/{nodeId}/{snapshot|segments}/  # backup data files
├── manifests/{partitionId}/{checkpointId}/{nodeId}/manifest.json        # backup state
├── metadata/{partitionId}/metadata.json                                 # partition metadata
└── ranges/                                                              # reserved directory
```

## Key Abstractions

| Class | Role |
|-------|------|
| `FilesystemBackupStore` | `BackupStore` implementation; owns executor lifecycle and coordinates managers |
| `FileSetManager` | File copy operations for snapshot and segment file sets |
| `ManifestManager` | JSON-serialized manifest CRUD with state machine transitions |
| `FilesystemBackupConfig` | Record holding `basePath`; use `Builder` for construction |
| `FilesystemBackupStoreException` | Abstract base for module-specific exceptions (currently unused directly — errors come from `BackupStoreException.UnexpectedManifestState` in `zeebe-backup-store-common`) |

## Data Flow

### Save
1. `ManifestManager.createInitialManifest()` writes `manifest.json` with `IN_PROGRESS` status (uses `CREATE_NEW` to prevent overwrites)
2. `FileSetManager.save()` copies snapshot files, then segment files, flushing each file and directory
3. `ManifestManager.completeManifest()` overwrites manifest to `COMPLETED`
4. On failure: `ManifestManager.markAsFailed()` transitions manifest to `FAILED`

### Delete (two-phase)
1. Caller must first call `markDeleted()` → manifest transitions to `DELETED` status
2. Then `delete()` verifies `DELETED` status, removes file sets, removes manifest, and cleans up empty parent directories via `backtrackDeleteEmptyParents()`

### Restore
1. Reads manifest and verifies `COMPLETED` status (rejects `IN_PROGRESS`, `FAILED`, `DELETED`)
2. `FileSetManager.restore()` copies files from backup to `targetFolder`, flushing for durability
3. Returns `BackupImpl` with restored `NamedFileSet` references

## Dependencies

- **`zeebe-backup`** (`zeebe/backup/`) — `BackupStore`, `BackupIdentifier`, `NamedFileSet`, `BackupStatus` API interfaces
- **`zeebe-backup-store-common`** (`zeebe/backup-stores/common/`) — `Manifest` sealed hierarchy, `BackupStoreException`, `FileSet`, `BackupImpl`, `BackupStatusImpl`
- **`zeebe-util`** — `FileUtil.ensureDirectoryExists()`, `flush()`, `flushDirectory()`, `deleteFolder()` for durable filesystem operations
- **Jackson** — Manifest JSON serialization with `Jdk8Module` + `JavaTimeModule`, `NON_ABSENT` inclusion, ISO dates (not timestamps)

## Consumers

- `zeebe/broker/.../BackupStoreTransitionStep.java` — instantiates store during partition transitions
- `zeebe/broker/.../BackupCfg.java` — maps broker config to `FilesystemBackupConfig`
- `dist/.../BackupStoreComponent.java` — wires store into Spring context
- `configuration/.../PrimaryStorageBackup.java` — unified config mapping

## Design Patterns and Invariants

- **All I/O is flushed**: Every file write and directory mutation calls `FileUtil.flush()`/`flushDirectory()` to ensure durability. Never skip flushes when adding new I/O paths.
- **Manifest state machine**: Transitions are enforced by the `Manifest` sealed hierarchy. `IN_PROGRESS` → `COMPLETED`/`FAILED` → `DELETED`. Deleted manifests cannot transition to `FAILED`. Always check `statusCode()` before calling `asCompleted()`/`asFailed()`.
- **Atomic manifest creation**: Uses `StandardOpenOption.CREATE_NEW` to prevent duplicate backups. If manifest already exists, throws `UnexpectedManifestState`.
- **Virtual threads**: The executor uses `newVirtualThreadPerTaskExecutor()`. Do not introduce blocking synchronized sections or thread-local state.
- **Two-phase deletion**: `delete()` requires manifest to be in `DELETED` state first. Always call `markDeleted()` before `delete()`.
- **Empty parent cleanup**: After deleting file sets or manifests, `backtrackDeleteEmptyParents()` removes empty directories up to the partition directory. This prevents directory bloat.
- **Backup path scheme**: `{partitionId}/{checkpointId}/{nodeId}` — consistent across contents and manifests directories. The `BackupIdentifier` triple is the primary key.

## Testing

- **Unit tests** (`*Test`): `ConfigTest` validates config, `FileSetManagerTest` tests file copy/delete/restore, `ManifestManagerTest` tests all manifest state transitions including `@Nested ManifestDeleteTransitionTest` for delete edge cases.
- **Integration test** (`*IT`): `FilesystemBackupStoreIT` implements `BackupStoreTestKit` — the shared test contract from `zeebe-backup-testkit`. This verifies the full `BackupStore` contract (save, restore, delete, status transitions, metadata).
- Use `@TempDir` for all test directories — never use fixed paths.
- Use `TestBackupProvider` from testkit for standard backup test data.
- Run scoped: `./mvnw -pl zeebe/backup-stores/filesystem -am verify -DskipChecks -T1C`

## Extension Points

- To add a new stored artifact (beyond snapshot/segments): add a new `FILESET_NAME` constant in `FilesystemBackupStore`, call `FileSetManager.save()/restore()/delete()` with it, and update the manifest's `FileSet` tracking in `zeebe-backup-store-common`.
- To add a new subdirectory under `basePath`: create it in the `FilesystemBackupStore` constructor alongside `contents`, `manifests`, `ranges`, `metadata`.
- New backup store implementations should follow the same `FileSetManager`/`ManifestManager` split seen here and in sibling modules (`s3/`, `gcs/`, `azure/`), and implement `BackupStoreTestKit` for contract tests.

## Common Pitfalls

- **Forgetting flush calls**: All file writes must call `FileUtil.flush(path)` and directory changes must call `FileUtil.flushDirectory(dir)`. Missing flushes risk data loss on crash.
- **Wrong manifest state check**: Always verify manifest `statusCode()` before casting with `asCompleted()`/`asFailed()`/`asInProgress()`. Mismatched casts throw.
- **Skipping two-phase delete**: Calling `delete()` without prior `markDeleted()` throws `UnexpectedManifestState`.
- **Not implementing BackupStoreTestKit**: Any new test class verifying the full store contract must implement `BackupStoreTestKit` and provide `getStore()`, `getBackupInInvalidStateExceptionClass()`, `getFileNotFoundExceptionClass()`.

## Key Files

- `FilesystemBackupStore.java` — main `BackupStore` implementation, entry point
- `ManifestManager.java` — manifest CRUD and state transitions with Jackson serialization
- `FileSetManager.java` — file copy/delete/restore with flush guarantees
- `FilesystemBackupStoreIT.java` — integration test implementing `BackupStoreTestKit`
- `ManifestManagerTest.java` — manifest state machine unit tests
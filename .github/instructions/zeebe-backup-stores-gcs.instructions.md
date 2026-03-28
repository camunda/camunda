```yaml
---
applyTo: "zeebe/backup-stores/gcs/**"
---
```
# Zeebe GCS Backup Store

## Purpose

This module implements the `BackupStore` interface (`zeebe/backup/src/main/java/.../BackupStore.java`) for Google Cloud Storage. It persists Zeebe engine snapshots and journal segments as GCS blobs, tracks backup lifecycle via JSON manifests, and supports parallel file transfers using virtual threads. It is one of four backup store implementations alongside S3, Azure, and filesystem (`zeebe/backup-stores/`).

## Architecture

The module has three core components orchestrated by `GcsBackupStore`:

```
GcsBackupStore (implements BackupStore)
├── ManifestManager — JSON manifest CRUD with optimistic concurrency (GCS generations)
├── FileSetManager — parallel blob upload/download with semaphore-based concurrency control
└── GcsBackupConfig / GcsConnectionConfig — configuration records with validation
```

All `BackupStore` methods return `CompletableFuture` and execute on a virtual-thread-per-task executor (`Executors.newVirtualThreadPerTaskExecutor()`).

## GCS Object Layout

Backups are stored under an optional `basePath` prefix with two top-level directories:

```
[basePath/]manifests/{partitionId}/{checkpointId}/{nodeId}/manifest.json
[basePath/]contents/{partitionId}/{checkpointId}/{nodeId}/snapshot/{fileName}
[basePath/]contents/{partitionId}/{checkpointId}/{nodeId}/segments/{fileName}
[basePath/]metadata/{partitionId}/metadata.json
```

See `GcsBucketIT.shouldStoreBackupInCorrectPaths` and `shouldStoreBackupInCorrectPathsUnderBase` for verified path examples.

## Key Abstractions

- **`GcsBackupStore`** (`GcsBackupStore.java`): Entry point implementing `BackupStore`. Factory method `of(GcsBackupConfig)` builds the client. Orchestrates save/restore/delete lifecycle by coordinating `ManifestManager` and `FileSetManager`.
- **`ManifestManager`** (`ManifestManager.java`): Manages `manifest.json` blobs. Uses GCS object generations for optimistic concurrency — `BlobTargetOption.doesNotExist()` on create, `BlobTargetOption.generationMatch(generation)` on complete. HTTP 412 indicates concurrent modification. Serializes manifests via a module-level Jackson `ObjectMapper` (`MAPPER`) configured with `Jdk8Module`, `JavaTimeModule`, and ISO date formatting.
- **`FileSetManager`** (`FileSetManager.java`): Uploads/downloads snapshot and segment file sets in parallel. Uses a `Semaphore` to cap concurrent GCS operations (default 50, configurable via `maxConcurrentTransfers`). Uploads use `InputStream`-based `createFrom()` to avoid CRC32C checksum races when files change during upload (see issue #45636).
- **`GcsBackupConfig`** (`GcsBackupConfig.java`): Java record with `Builder`. Validates `bucketName` (required, non-blank) and sanitizes `basePath` (strips leading/trailing slashes). Default `maxConcurrentTransfers` is 50.
- **`GcsConnectionConfig`** (`GcsConnectionConfig.java`): Record holding host URL and sealed `Authentication` interface — `Auto` (Application Default Credentials) or `None` (for testing with `NoCredentials`).
- **`GcsBackupStoreException`** (`GcsBackupStoreException.java`): Abstract exception with `ConfigurationException` and `UploadException` subclasses.

## Backup Lifecycle (State Machine)

Manifests transition through states defined in `io.camunda.zeebe.backup.common.Manifest.StatusCode`:

```
IN_PROGRESS → COMPLETED → DELETED
IN_PROGRESS → FAILED    → DELETED
```

1. **save**: Creates `IN_PROGRESS` manifest → uploads snapshot + segments in parallel → transitions to `COMPLETED` (or `FAILED` on error).
2. **delete**: Requires manifest to be in `DELETED` state first; then removes file blobs and manifest.
3. **restore**: Only allowed from `COMPLETED` state; downloads files in parallel.
4. **markFailed/markDeleted**: State transition methods that update the manifest blob.

Never delete a backup without first marking it as `DELETED` via `markDeleted()`.

## Concurrency and Error Handling

- All operations run on virtual threads. The executor is shut down in `closeAsync()` with a 1-minute timeout.
- `FileSetManager` uses a `Semaphore(maxConcurrentTransfers)` for both uploads and downloads to prevent resource exhaustion.
- Optimistic concurrency on manifests uses GCS object generation numbers. A `StorageException` with code 412 (PRECONDITION_FAILED) is caught and translated to `UnexpectedManifestState`.
- On save failure, the manifest is marked as `FAILED` before re-throwing `UploadException`.

## Dependencies

- **`zeebe-backup`** — defines `BackupStore`, `BackupIdentifier`, `BackupStatus`, `NamedFileSet` interfaces.
- **`zeebe-backup-store-common`** — provides `Manifest`, `BackupImpl`, `BackupStatusImpl`, `FileSet`, shared exception types.
- **`google-cloud-storage`** / **`google-cloud-core`** — GCS Java client. Do not pin transitive Google library versions explicitly; let `google-cloud-core` manage them (see `pom.xml` `ignoredUsedUndeclaredDependencies`).
- **Jackson** (`jackson-datatype-jdk8`, `jackson-datatype-jsr310`) — manifest JSON serialization.

## Testing

- **Unit tests** (`*Test.java`): Mock `Storage` client with Mockito. `FileSetManagerTest` and `ManifestManagerTest` verify upload/download/delete behavior and error handling against mocked GCS operations.
- **Integration tests** (`*IT.java`): Use `GcsContainer` (Testcontainers wrapper around `fsouza/fake-gcs-server`). `GcsBackupStoreIT` implements `BackupStoreTestKit` (from `zeebe-backup-testkit`) which provides a shared contract test suite. Two `@Nested` variants test with and without `basePath`.
- **Serialization tests**: `ManifestSerializationTest` verifies JSON round-trip for all manifest states.
- Run scoped: `./mvnw -pl zeebe/backup-stores/gcs -am test -DskipITs -DskipChecks -T1C`
- Run ITs: `./mvnw -pl zeebe/backup-stores/gcs -am verify -DskipUTs -DskipChecks -T1C`

## Extension Points

- To add a new backup store implementation, create a new module under `zeebe/backup-stores/`, implement `BackupStore`, and have ITs implement `BackupStoreTestKit`.
- To modify GCS object layout, update path format constants in `ManifestManager` (`MANIFEST_PATH_FORMAT`) and `FileSetManager` (`PATH_FORMAT`).
- To add new authentication modes, add a new record implementing the sealed `GcsConnectionConfig.Authentication` interface.

## Common Pitfalls

- Never use `client.create(blobInfo, bytes)` for file uploads — use `client.createFrom(blobInfo, inputStream, ...)` to avoid CRC32C checksum mismatch when files are modified during upload.
- Never skip the `doesNotExist()` precondition on initial manifest creation — it prevents duplicate backups.
- Never delete backup files without first verifying the manifest is in `DELETED` state.
- The `basePath` in config is sanitized (leading/trailing slashes stripped). A trailing `/` is appended internally by `GcsBackupStore` constructor. Do not add your own trailing slash.
- Do not explicitly declare versions for transitive Google API libraries — they are managed by `google-cloud-core` BOM.

## Key Files

- `src/main/java/.../GcsBackupStore.java` — main `BackupStore` implementation, client construction, validation
- `src/main/java/.../ManifestManager.java` — manifest CRUD with optimistic locking via GCS generations
- `src/main/java/.../FileSetManager.java` — parallel file transfer with semaphore concurrency control
- `src/main/java/.../GcsBackupConfig.java` — configuration record with builder and validation
- `src/test/java/.../GcsBackupStoreIT.java` — integration tests using `BackupStoreTestKit` contract
- `src/test/java/.../util/GcsContainer.java` — Testcontainers wrapper for `fake-gcs-server`
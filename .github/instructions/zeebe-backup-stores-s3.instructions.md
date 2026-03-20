```yaml
---
applyTo: "zeebe/backup-stores/s3/**"
---
```
# S3 Backup Store Module

## Purpose

Implements the `BackupStore` interface (`zeebe/backup/src/main/java/.../api/BackupStore.java`) for AWS S3 and S3-compatible storage (MinIO). Stores Zeebe partition snapshots, segment files, and manifest metadata as S3 objects. One of four backup store backends alongside GCS, Azure, and filesystem under `zeebe/backup-stores/`.

## Architecture

```
S3BackupStore (implements BackupStore)
├── FileSetManager          — upload/download of snapshot + segment file sets
├── manifest/               — state machine for backup lifecycle (JSON in S3)
│   ├── Manifest (sealed)   — NoBackupManifest | ValidBackupManifest
│   └── ValidBackupManifest — InProgress | Completed | Failed | Deleted
└── util/
    ├── AsyncAggregatingSubscriber — reactive stream → CompletableFuture aggregation
    └── CompletableFutureUtils     — parallel async map over collections
```

All public API methods return `CompletableFuture`. The module uses the AWS SDK v2 async client (`S3AsyncClient`) exclusively — never the synchronous client.

## S3 Object Layout

Objects are keyed by `basePath/directory/partitionId/checkpointId/nodeId/`:
- **Manifests**: `manifests/<id>/manifest.json` — JSON-serialized `ValidBackupManifest`
- **Contents**: `contents/<id>/snapshot/*` and `contents/<id>/segments/*`
- **Legacy** (≤8.8): `<id>/manifest.json`, `<id>/snapshot/*`, `<id>/segments/*` (flat, no directory prefix)
- **Metadata**: `metadata/<partitionId>/metadata.json` — raw byte storage for partition metadata

Legacy path detection uses `SemanticVersion.parse(descriptor.brokerVersion())` — backups with `minor <= 8` use the legacy layout. See `S3BackupStore.isLegacyBackup()` and `derivePath()`.

## Manifest State Machine

`Manifest` is a sealed interface with two direct permits: `NoBackupManifest` and `ValidBackupManifest`. `ValidBackupManifest` is a sealed interface with four record implementations discriminated by Jackson `@JsonTypeInfo(property = "statusCode")`:

```
NoBackupManifest (DOES_NOT_EXIST)
  └─ asInProgress() → InProgressBackupManifest (IN_PROGRESS)
                         ├─ asCompleted() → CompletedBackupManifest (COMPLETED)
                         ├─ asFailed()    → FailedBackupManifest (FAILED)
                         └─ asDeleted()   → DeletedBackupManifest (DELETED)
```

Transitions are enforced via `Manifest.expectNoBackup()`, `expectInProgress()`, `expectCompleted()` which throw `BackupInInvalidStateException` on violation. Never construct manifest records directly in transition logic — use the `as*()` factory methods on the source manifest.

## Key Design Decisions

- **Immutable state transitions**: Each `as*()` method returns a new record instance, preserving `createdAt` and updating `modifiedAt` to `Instant.now()`.
- **Optimistic concurrency**: Manifest read-then-write is not atomic. `updateManifestObject()` reads the current manifest, applies a transition function, then writes back. S3 does not support conditional writes.
- **Compression**: Files > 8 MiB are compressed via `CompressorStreamFactory` (commons-compress) when `compressionAlgorithm` is configured. Compressed files are written to temp files, uploaded, then cleaned up. Metadata tracks compression per file in `FileSet.FileMetadata`.
- **Concurrency control**: `FileSetManager` uses a `Semaphore` set to half of `maxConcurrentConnections` to prevent connection pool exhaustion during large backups. Tasks run on virtual threads (`Thread.ofVirtual()`).
- **Backwards compatibility**: `FileSet` has a custom Jackson `Deserializer` that supports both the 8.1 format (plain JSON array of filenames) and the current format (map of filename → `FileMetadata` with compression info). `@JsonAlias("snapshotFileNames")` / `@JsonAlias("segmentFileNames")` handle renamed fields.

## Configuration (`S3BackupConfig`)

A Java record with a nested `Builder`. Required: `bucketName`. Optional: `endpoint`, `region`, `credentials`, `apiCallTimeout`, `forcePathStyleAccess`, `compressionAlgorithm`, `basePath`, `maxConcurrentConnections` (default 50), `connectionAcquisitionTimeout` (default 45s), `supportLegacyMd5`. The `basePath` must not start or end with `/`. Use `S3BackupStore.validateConfig()` to verify configuration eagerly (builds and closes a throwaway client).

## Error Handling

`S3BackupStoreException` is a sealed abstract class with five final subclasses:
- `ManifestParseException` — corrupted manifest JSON (non-recoverable)
- `BackupReadException` — transient S3 read failure
- `BackupInInvalidStateException` — invalid state transition
- `BackupDeletionIncomplete` — partial deletion (retryable)
- `BackupCompressionFailed` — compression/decompression I/O failure

Always use these specific exception types. `NoSuchKeyException` from the AWS SDK is caught and mapped to `NoBackupManifest` (not an error).

## Save Flow

1. `save()` reads manifest → expects `NoBackupManifest` → writes `InProgressBackupManifest`
2. Uploads snapshot and segment files in parallel via `FileSetManager.save()`
3. On success: updates manifest to `CompletedBackupManifest` with actual `FileSet` metadata
4. On failure: updates manifest to `FailedBackupManifest`, then re-throws the original error

## Delete Flow

Deletion is two-phase: first `markDeleted()` transitions the manifest to `DELETED`, then `delete()` verifies the `DELETED`/`DOES_NOT_EXIST` status and removes all S3 objects (manifests first, then contents). Both legacy and new paths are cleaned.

## Testing Patterns

- **`S3BackupStoreTests`**: A JUnit 5 interface that extends `BackupStoreTestKit` (from `zeebe-backup-testkit`). Provides parameterized S3-specific tests. Implementations (`MinioBackupStoreIT`, `CustomBasePathIT`) supply the Testcontainers-managed S3 endpoint.
- **Integration tests** (`*IT`): Use Testcontainers with MinIO or LocalStack. Each test gets a random `basePath` for isolation within a shared bucket.
- **Unit tests** (`*Test`): `ManifestCompatabilityTest` validates deserialization of historical manifest formats from `/manifests/8.1/*.json` resources. `ConnectionErrorTest` verifies failure behavior without a running S3.
- **`S3TestBackupProvider`**: Creates test backups with either legacy (`≤8.8`) or current version strings to exercise both path layouts.
- Run scoped: `./mvnw -pl zeebe/backup-stores/s3 -am test -DskipITs -DskipChecks -Dtest=<TestClass> -T1C`

## Extension Points

- To add new manifest state: Add a record implementing `ValidBackupManifest`, add `@Type` entry in `ValidBackupManifest`'s `@JsonSubTypes`, add transition methods on existing manifests.
- To add file metadata: Extend `FileSet.FileMetadata` record fields. Ensure the custom `FileSet.Deserializer` handles the new shape while remaining backwards compatible with older formats.

## Common Pitfalls

- Never modify the `FileSet.Deserializer` without verifying `ManifestCompatabilityTest` passes — it guards backwards compatibility with 8.1 manifests.
- The `save()` flow's `exceptionallyComposeAsync` marks the backup as failed but still propagates the original exception — do not swallow it.
- `basePath` validation rejects empty strings, leading `/`, and trailing `/` — test config construction accordingly.
- Files uploaded via `AsyncRequestBody.fromInputStream()` (not `fromFile()`) to avoid AWS SDK's file modification time check (see issue #44035).
- `deleteBackupObjects()` silently succeeds on empty collections — this is intentional since S3 rejects empty delete requests.

## Key Files

- `S3BackupStore.java` — Core `BackupStore` implementation, S3 client construction, object path logic
- `S3BackupConfig.java` — Configuration record with builder and validation
- `manifest/Manifest.java` — Sealed manifest interface with state expectation methods
- `manifest/ValidBackupManifest.java` — Jackson-polymorphic sealed interface for persisted manifests
- `FileSetManager.java` — File upload/download with compression and concurrency control
- `S3BackupStoreTests.java` — Shared S3-specific test interface extending `BackupStoreTestKit`
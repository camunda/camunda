```yaml
---
applyTo: "zeebe/backup-stores/azure/**"
---
```
# Zeebe Azure Backup Store

## Purpose

This module implements the `BackupStore` interface (`zeebe/backup/src/main/java/.../api/BackupStore.java`) for Azure Blob Storage. It persists Zeebe partition backups (snapshots + log segments) and their manifests as blobs in an Azure Storage container. It is one of four backup store backends (S3, GCS, filesystem, Azure).

## Architecture

The module has three core collaborators orchestrated by `AzureBackupStore`:

```
AzureBackupStore (BackupStore interface)
├── ManifestManager   — CRUD for JSON manifest blobs (state machine lifecycle)
├── FileSetManager    — upload/download/delete of snapshot and segment file blobs
└── AzureBackupConfig — record holding auth and container configuration
```

**Blob path layout** — two top-level prefixes in the container:
- `manifests/{partitionId}/{checkpointId}/{nodeId}/manifest.json` — JSON manifest tracking backup state
- `contents/{partitionId}/{checkpointId}/{nodeId}/{snapshot|segments}/{fileName}` — actual data files
- `metadata/{partitionId}/metadata.json` — partition-level backup metadata

All operations return `CompletableFuture` and run on a virtual-thread-per-task executor (`Executors.newVirtualThreadPerTaskExecutor()`).

## Key Abstractions

- **`AzureBackupStore`** (`AzureBackupStore.java`): Entry point implementing `BackupStore`. Factory method `AzureBackupStore.of(config)` is used by the broker's `BackupStoreTransitionStep`. Delegates manifest lifecycle to `ManifestManager` and file I/O to `FileSetManager`.
- **`ManifestManager`** (`ManifestManager.java`): Manages the backup state machine (IN_PROGRESS → COMPLETED/FAILED → DELETED). Uses ETag-based optimistic concurrency on blob uploads to prevent concurrent overwrites. Serializes `Manifest` objects (from `zeebe-backup-store-common`) as JSON via Jackson with `Jdk8Module` + `JavaTimeModule`.
- **`FileSetManager`** (`FileSetManager.java`): Uploads/downloads/deletes file sets (snapshot files, segment files) as individual blobs. Uses `BinaryData.fromFile()` for uploads and `BlockBlobClient.downloadToFile()` for restores.
- **`AzureBackupConfig`** (`AzureBackupConfig.java`): Java record with builder. Holds endpoint, credentials (account key, connection string, SAS token), container name, and `createContainer` flag.
- **`SasTokenConfig` / `SasTokenType`**: SAS token authentication support with three types: `ACCOUNT`, `SERVICE`, `DELEGATION`. Non-account tokens skip container existence checks and creation.
- **`AzureBackupStoreException`**: Module-specific exceptions — `BlobAlreadyExists` and `ContainerDoesNotExist`.

## Authentication Priority

`AzureBackupStore.buildClient()` selects credentials in this order:
1. SAS token (`sasTokenConfig.value()`) + endpoint
2. Connection string
3. Account name + account key (both required) + endpoint
4. `DefaultAzureCredentialBuilder` (Azure Identity) + endpoint

Use `AzureBackupStore.validateConfig()` to check config validity before constructing the store.

## Manifest State Machine

Manifests follow the state transitions defined in `zeebe-backup-store-common`'s `Manifest` class:
- `IN_PROGRESS` → `COMPLETED` (via `completeManifest` with ETag match)
- `IN_PROGRESS` → `FAILED` (via `markAsFailed`)
- `IN_PROGRESS` → `DELETED` (via `markAsDeleted`)
- `COMPLETED` → `FAILED` or `DELETED`
- `FAILED` → `DELETED`
- `DELETED` is terminal

Optimistic concurrency: `createInitialManifest` sets `If-None-Match: *` to prevent duplicate creation. `completeManifest` sets `If-Match: <eTag>` to ensure no concurrent modification.

## Wiring into Broker

`AzureBackupStoreConfig` in `zeebe/broker/.../configuration/backup/` maps `camunda.zeebe.broker.data.backup.azure.*` properties to `AzureBackupConfig`. The `BackupStoreTransitionStep` instantiates the store via `AzureBackupStore.of(config)` when `BackupStoreType.AZURE` is configured.

## Container Creation

- If `createContainer` is `true` (default), both `ManifestManager` and `FileSetManager` lazily call `createIfNotExists()` on first use.
- If `false`, the constructor validates the container exists (except for delegation/service SAS tokens which lack list permissions).
- Delegation and service SAS tokens always skip container creation regardless of `createContainer`.

## Testing

- **`AzureBackupStoreIT`**: Integration test implementing `BackupStoreTestKit` (from `zeebe-backup-testkit`). This shared test kit provides parameterized tests for all `BackupStore` contract methods (save, restore, delete, status, list, metadata). Uses Azurite via Testcontainers.
- **`AzureBackupStoreContainerCredentialsIT`**: Tests SAS token authentication (account and service types). User delegation tokens cannot be tested with Azurite (requires Microsoft Entra).
- **`ConfigIT`**: Unit-style tests for `AzureBackupStore.validateConfig()` — validates credential combinations and error messages.
- **`ManifestManagerTest`**: Tests manifest delete transitions across all states using `@Nested` classes.
- **`AzuriteContainer`** (`util/AzuriteContainer.java`): Testcontainers wrapper for `mcr.microsoft.com/azure-storage/azurite` image, exposing the Blob service on port 10000 with well-known credentials.

Run tests scoped: `./mvnw -pl zeebe/backup-stores/azure -am verify -DskipChecks -T1C`

## Dependencies

- `zeebe-backup` — `BackupStore` API interface and related types
- `zeebe-backup-store-common` — `Manifest`, `BackupImpl`, `FileSet`, shared exception types
- `azure-storage-blob` / `azure-core` / `azure-identity` — Azure SDK for Blob Storage
- `jackson-datatype-jdk8` + `jackson-datatype-jsr310` — JSON serialization with Optional and java.time support
- `zeebe-backup-testkit` (test) — shared `BackupStoreTestKit` contract tests

## Common Pitfalls

- Never upload a manifest without ETag conditions — concurrent brokers can corrupt backup state. Use the `BlobRequestConditions` pattern in `ManifestManager`.
- Always call `assureContainerCreated()` before any blob operation when `createContainer` is enabled. Both `ManifestManager` and `FileSetManager` handle this independently.
- Do not assume SAS tokens have container list/create permissions — delegation and service tokens are restricted. Check `SasTokenType.isAccount()` before container operations.
- `AzureBackupStore.delete()` requires the manifest to be in `DELETED` state first (call `markDeleted` before `delete`). This is a two-phase deletion pattern.
- The `AzuriteContainer` test utility starts a nested `GenericContainer` in its constructor rather than using standard lifecycle — be aware of this when debugging container issues.

## Key Files

- `src/main/java/.../AzureBackupStore.java` — main store implementation and client builder
- `src/main/java/.../ManifestManager.java` — manifest CRUD with optimistic concurrency
- `src/main/java/.../FileSetManager.java` — blob upload/download for file sets
- `src/main/java/.../AzureBackupConfig.java` — configuration record with builder
- `src/test/java/.../AzureBackupStoreIT.java` — primary integration test using shared test kit
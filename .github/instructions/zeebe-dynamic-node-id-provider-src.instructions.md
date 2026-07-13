```yaml
---
applyTo: "zeebe/dynamic-node-id-provider/src/**"
---
```
# Dynamic Node ID Provider Module

## Purpose

This module provides dynamic node identity assignment for Zeebe brokers running in environments without stable node identifiers (e.g., AWS ECS). It uses a CAS-capable object store (S3) as a distributed coordination backend to assign, lease, and renew unique integer node IDs across cluster members. It also manages versioned data directories on the local filesystem so that a newly assigned node ID can inherit state from its predecessor.

## Architecture

The module has three layers:

1. **Core domain** (`io.camunda.zeebe.dynamic.nodeid`): `NodeIdProvider` interface, `Lease` record (serialized to S3), `NodeInstance` (nodeId + version pair), `Version` (monotonically increasing counter), readiness checking, and restore coordination.
2. **Repository** (`io.camunda.zeebe.dynamic.nodeid.repository`): `NodeIdRepository` interface abstracting the CAS-capable storage; `S3NodeIdRepository` is the only implementation, using S3 conditional PUTs (`ifMatch`/`ifNoneMatch`) for atomic lease operations.
3. **Filesystem** (`io.camunda.zeebe.dynamic.nodeid.fs`): `DataDirectoryProvider` strategy interface with three implementations — `ConfiguredDataDirectoryProvider` (static path), `NodeIdBasedDataDirectoryProvider` (appends nodeId), `VersionedNodeIdBasedDataDirectoryProvider` (versioned subdirectories with copy-on-version-change and garbage collection).

## Key Abstractions

- **`NodeIdProvider`** (`NodeIdProvider.java`): Primary interface consumed by the broker. Methods: `initialize(clusterSize)`, `scale(newClusterSize)`, `currentNodeInstance()`, `isValid()`, `previousNodeGracefullyShutdown()`, `setMembers(Set<Member>)`, `awaitReadiness()`. Includes an inline `staticProvider(int nodeId)` factory for non-dynamic environments.
- **`RepositoryNodeIdProvider`** (`RepositoryNodeIdProvider.java`): Production implementation. Uses a single-threaded `ScheduledExecutorService` for lease renewal and a virtual-thread executor for background tasks (scaling). Acquires initial lease with exponential backoff, schedules periodic renewal at `leaseDuration / 3`, and triggers `onLeaseFailure` callback if renewal fails.
- **`Lease`** (`Lease.java`): Immutable record serialized as JSON to S3. Contains `taskId`, `timestamp` (expiry epoch millis), `NodeInstance`, and `VersionMappings` (sorted map of nodeId → version from SWIM gossip). Renewal produces a new `Lease` instance — never mutate.
- **`NodeIdRepository`** (`repository/NodeIdRepository.java`): Storage abstraction with `initialize`, `scale`, `getLease`, `acquire`, `release`, `getRestoreStatus`, `updateRestoreStatus`. Contains a sealed `StoredLease` interface with three variants: `Uninitialized`, `Initialized`, `Unusable`.
- **`StoredLease`** (sealed interface in `NodeIdRepository.java`): `Uninitialized` = released/new slot; `Initialized` = held by a node with an active `Lease`; `Unusable` = marked during scale-down. Use pattern matching (`switch` expressions) to handle all variants exhaustively.
- **`Metadata`** (`repository/Metadata.java`): Record stored as S3 object metadata (key-value map). Keys are lowercase: `taskid`, `version`, `acquirable`. Metadata keys MUST remain lowercase — S3 normalizes header keys.
- **`DataDirectoryProvider`** (`fs/DataDirectoryProvider.java`): Strategy interface returning `CompletableFuture<Path>`. Three implementations for different deployment modes.
- **`VersionedDirectoryLayout`** (`fs/VersionedDirectoryLayout.java`): Manages the `node-<id>/v<version>/` directory structure. Writes `directory-initialized.json` atomically to mark valid directories.

## Data Flow

### Lease Lifecycle
1. `initialize(clusterSize)` → `S3NodeIdRepository.initialize()` creates `0.json`..`(n-1).json` objects with marker file.
2. `acquireInitialLease()` iterates nodeIds 0..n-1 with exponential backoff, calling `S3NodeIdRepository.acquire()` using CAS (`ifMatch` eTag).
3. On success, `startRenewalTimer()` schedules renewal at `leaseDuration / 3` interval.
4. Each renewal writes updated `Lease` JSON + `Metadata` via conditional PUT.
5. `setMembers()` updates `knownVersionMappings` from Atomix SWIM membership — these are persisted in the lease so other nodes can verify cluster convergence.
6. `close()` cancels renewal, releases lease (clears body, updates metadata), shuts down executors.

### Filesystem Initialization
1. `VersionedNodeIdBasedDataDirectoryProvider.initialize(rootDir)` creates `node-<id>/v<version>/`.
2. If a valid previous version exists (has `directory-initialized.json`), copies via `DataDirectoryCopier`.
3. After validation, writes `directory-initialized.json` and triggers async GC of old versions.

## Concurrency Model

- `RepositoryNodeIdProvider` serializes all lease operations on a single daemon thread (`NodeIdProvider` executor). The `currentLease` field is `volatile` for safe reads from health checks.
- Background tasks (scaling, readiness checks) use virtual threads (`Executors.newVirtualThreadPerTaskExecutor()`).
- S3 provides the distributed concurrency primitive — all inter-node coordination happens via conditional PUTs (CAS on eTag). Never rely on in-memory locking for multi-node coordination.

## Extension Points

- **New storage backend**: Implement `NodeIdRepository`. The interface requires CAS semantics on `acquire()` (eTag-based). Place implementation in `repository/<backend>/`.
- **New data directory strategy**: Implement `DataDirectoryProvider` in the `fs` package.
- **Custom copier**: Implement `DataDirectoryCopier` for environment-specific file copy strategies (e.g., hard-link vs full copy).

## Consumers

This module is consumed by:
- `zeebe/broker` — `SystemContext`, `BrokerStartupContext`, health indicators (`NodeIdProviderHealthIndicator`, `NodeIdProviderReadyHealthIndicator`).
- `dist/` — `NodeIdProviderConfiguration`, `RestoreNodeIdProviderConfiguration`, `BrokerModuleConfiguration` wire Spring beans.
- `configuration/` — `Restore` configuration class references restore-related types.
- `qa/acceptance-tests/` and `zeebe/qa/integration-tests/` — S3-based integration tests.

## Invariants

- A lease is valid only while `now <= timestamp`. Never use a lease after expiry — the node MUST shut down via `onLeaseFailure`.
- `Lease` renewal creates a new immutable instance; never modify fields on an existing `Lease`.
- `Metadata` map keys MUST be lowercase (S3 normalizes HTTP headers).
- `Version` is monotonically increasing and non-negative. Each lease acquisition increments: `previousVersion + 1`.
- `StoredLease` is sealed — always handle all three variants (`Uninitialized`, `Initialized`, `Unusable`) in switch expressions.
- `directory-initialized.json` must be the LAST file written during directory initialization — it is the atomicity marker.
- The `acquirable` field in `Metadata` defaults to `true` for backwards compatibility with pre-scaling leases.

## Common Pitfalls

- Do not call services on `RepositoryNodeIdProvider` before `initialize()` completes — `currentLease` is null until acquisition succeeds.
- Do not skip eTag on `acquire()` — without CAS, concurrent nodes can claim the same ID.
- `S3NodeIdRepository.initializeForNode()` uses `ifNoneMatch("*")` to prevent overwriting existing leases; a 412 response is expected and safe to ignore.
- `renewalDelay` is `leaseDuration / 3` — changing the lease duration without adjusting the renewal schedule risks expiry.
- The readiness checker completes with `true` on timeout, not `false` — this is intentional to avoid blocking startup indefinitely.
- `RestoreStatusManager` retries indefinitely on concurrent update conflicts — ensure the repository is available before calling.

## Key Reference Files

- `src/main/java/io/camunda/zeebe/dynamic/nodeid/NodeIdProvider.java` — primary interface
- `src/main/java/io/camunda/zeebe/dynamic/nodeid/RepositoryNodeIdProvider.java` — production implementation with lease lifecycle
- `src/main/java/io/camunda/zeebe/dynamic/nodeid/Lease.java` — lease domain record with serialization
- `src/main/java/io/camunda/zeebe/dynamic/nodeid/repository/NodeIdRepository.java` — storage abstraction with sealed `StoredLease`
- `src/main/java/io/camunda/zeebe/dynamic/nodeid/repository/s3/S3NodeIdRepository.java` — S3 CAS implementation
- `src/main/java/io/camunda/zeebe/dynamic/nodeid/fs/VersionedNodeIdBasedDataDirectoryProvider.java` — versioned directory initialization with copy and GC
- `src/main/java/io/camunda/zeebe/dynamic/nodeid/fs/VersionedDirectoryLayout.java` — directory structure conventions
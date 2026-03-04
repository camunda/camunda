```yaml
---
applyTo: "zeebe/snapshot/src/**"
---
```
# Zeebe Snapshot Module

## Purpose

This module provides the snapshot abstraction and file-based implementation for persisting Zeebe engine state (RocksDB) to disk. Snapshots enable crash recovery, Raft log compaction, inter-node replication, and bootstrap of new partitions during cluster scaling. It is consumed by `zeebe/atomix` (Raft consensus), `zeebe/broker` (partition lifecycle), and `zeebe/restore`.

## Architecture

The module has a clean API/impl separation:

```
io.camunda.zeebe.snapshots          ← Public API (interfaces in main package)
io.camunda.zeebe.snapshots.impl     ← File-based implementation
io.camunda.zeebe.snapshots.transfer ← Chunk-based snapshot transfer between nodes
```

### Interface Hierarchy (Store)

```
BootstrapSnapshotStore          ← Bootstrap snapshot for new partitions (scaling)
  └─ PersistedSnapshotStore     ← Core store: latest snapshot, listeners, delete
       ├─ ConstructableSnapshotStore  ← Creates TransientSnapshot locally (leader)
       └─ ReceivableSnapshotStore     ← Receives ReceivedSnapshot via chunks (follower)
            └─ RestorableSnapshotStore ← Restores from backup files
```

`FileBasedSnapshotStore` (Actor) implements ALL four store interfaces, delegating to `FileBasedSnapshotStoreImpl` for logic.

### Snapshot Lifecycle

```
TransientSnapshot ──take()──► [temporary dir] ──persist()──► PersistedSnapshot
ReceivedSnapshot  ──apply(chunk)* ──persist()──► PersistedSnapshot
                                                      │
                                                      ├── reserve() → SnapshotReservation
                                                      ├── newChunkReader() → SnapshotChunkReader
                                                      └── delete()
```

Both `TransientSnapshot` and `ReceivedSnapshot` extend `PersistableSnapshot` (abort/persist/snapshotId/getPath).

### Concurrency Model

All mutable operations run on the Zeebe `Actor` (cooperative scheduler). `FileBasedSnapshotStore` extends `Actor` and passes `ConcurrencyControl` to `FileBasedSnapshotStoreImpl`. Use `actor.run()` or `actor.call()` for thread-safe state access. The only exception: `getLatestSnapshot()` uses an `AtomicReference` for lock-free reads.

## Key Abstractions

- **`SnapshotId`**: Composite identifier — `index-term-processedPosition-exportedPosition-brokerId[-checksum]`. Compared by index, then processedPosition, then exportedPosition. Implemented by `FileBasedSnapshotId` which parses from directory names via `ofFileName()`/`ofPath()`. Uses a sealed `SnapshotParseResult` interface (`Parsed | Invalid`).
- **`SnapshotMetadata`**: JSON-serialized file (`zeebe.metadata`) inside snapshot directory. Contains `version`, `processedPosition`, `minExportedPosition`, `maxExportedPosition`, `lastFollowupEventPosition`, `isBootstrap`. Implemented by `FileBasedSnapshotMetadata` (Jackson record).
- **`SnapshotChunk`**: A piece of a snapshot file with `snapshotId`, `chunkName`, `content`, `checksum`, `fileBlockPosition`, `totalFileSize`. Used for replication.
- **`SnapshotChunkReader`**: Iterator over `SnapshotChunk` with seek/reset. `FileBasedSnapshotChunkReader` splits files by `maximumChunkSize`.
- **`ImmutableChecksumsSFV` / `MutableChecksumsSFV`**: CRC32C checksums in SFV format. Implemented by `SfvChecksumImpl`. Per-file checksums stored in `<snapshotId>.checksum` sibling file.
- **`SnapshotReservation`**: Prevents deletion during replication. Non-persistent (lost on restart).

## Data Flow

### Local Snapshot (Leader)

1. `ConstructableSnapshotStore.newTransientSnapshot()` → `Either<SnapshotException, TransientSnapshot>` (rejects if positions not advanced)
2. `TransientSnapshot.take(path -> writeRocksDBToPath)` → writes to `transient-<random>` temp directory
3. Checksum calculated via `SnapshotChecksum.calculateWithProvidedChecksums()` using `CRC32CChecksumProvider` (RocksDB live checksums)
4. `persist()` → writes `zeebe.metadata`, moves directory to final name with checksum suffix, writes `.checksum` file atomically, notifies listeners

### Received Snapshot (Follower)

1. `ReceivableSnapshotStore.newReceivedSnapshot(snapshotId)` → validates and creates directory
2. `ReceivedSnapshot.apply(chunk)` repeatedly — validates chunk checksum, writes file blocks
3. `persist()` → delegates to `FileBasedSnapshotStoreImpl.persistNewSnapshot()`, same atomic commit path

### Snapshot Transfer (Bootstrap)

`SnapshotTransferImpl` (Actor) uses `SnapshotTransferService` to pull chunks from a sender. `SnapshotTransferServiceImpl` is the sender — it creates bootstrap snapshots via `copyForBootstrap()` and serves chunks via `FileBasedSnapshotChunkReader`. Transfer is sequential: get first chunk, then repeatedly `getNextChunk()` until null.

## Directory Layout on Disk

```
<partition-root>/
  snapshots/
    <index>-<term>-<processedPos>-<exportedPos>-<brokerId>-<checksum>/
      *.sst, MANIFEST, CURRENT, zeebe.metadata, ...
    <snapshotId>.checksum          ← SFV checksum file (marker for completeness)
    transient-<hex>/               ← Temporary, cleaned up on start
  bootstrap-snapshots/
    1-1-0-0-<brokerId>/            ← Bootstrap snapshot (zeroed positions)
```

## Extension Points

- **New checksum provider**: Implement `CRC32CChecksumProvider` to supply pre-computed checksums (e.g., from RocksDB `GetLiveFilesChecksums`).
- **New transfer transport**: Implement `SnapshotTransferService` to change how chunks are fetched (e.g., gRPC instead of Atomix messaging).
- **Snapshot listener**: Register `PersistedSnapshotListener` via `addSnapshotListener()` to react to new snapshots (used by Raft for log compaction).

## Invariants

- The `.checksum` file is the marker of a complete snapshot. Never write it until the directory move is finished. Delete it first when deleting a snapshot.
- Only one snapshot is "current" at a time per store. Older unreserved snapshots are deleted after a new one is committed.
- Metadata file checksum must be appended last to the SFV collection (non-metadata files sorted first) to match recalculation order.
- Transient snapshot directories use random hex names to avoid collisions; final directories include the combined checksum to allow re-snapshots at same positions.
- All mutable store operations must execute on the Actor thread. `getLatestSnapshot()` is the sole lock-free read via `AtomicReference`.
- Bootstrap snapshots have zeroed positions (`1-1-0-0-brokerId`) and `isBootstrap=true` metadata. They are excluded from `availableSnapshots` and listener notifications.

## Common Pitfalls

- Do not modify checksum calculation order — metadata file must be last. See `SnapshotChecksum.createChecksumForSnapshot()`.
- Do not call `persistNewSnapshot()` outside the actor thread — it uses `compareAndSet` on `currentSnapshot` and mutates `availableSnapshots`.
- `SnapshotChunkReader` is one-time-use and not thread-safe. Create a new reader per transfer.
- `FileBasedSnapshotId.ofFileName()` returns a sealed `SnapshotParseResult` — always handle both `Parsed` and `Invalid` variants.
- The module exports a test-jar (`maven-jar-plugin` test-jar goal) — test utilities like `TestFileBasedSnapshotStore` are available to downstream modules.

## Key Files

| File | Role |
|------|------|
| `main/.../PersistedSnapshotStore.java` | Core store interface (latest snapshot, listeners, delete) |
| `main/.../impl/FileBasedSnapshotStoreImpl.java` | All store logic: create, receive, persist, restore, bootstrap |
| `main/.../impl/FileBasedTransientSnapshot.java` | Local snapshot lifecycle (take → persist) |
| `main/.../impl/FileBasedReceivedSnapshot.java` | Replication receiver (apply chunks → persist) |
| `main/.../impl/FileBasedSnapshotId.java` | Snapshot ID parsing with sealed `SnapshotParseResult` |
| `main/.../impl/SnapshotChecksum.java` | SFV checksum read/write/calculate with provider support |
| `main/.../impl/SfvChecksumImpl.java` | CRC32C checksum collection (SFV format) |
| `main/.../transfer/SnapshotTransferImpl.java` | Actor that pulls snapshot chunks from sender |
| `main/.../transfer/SnapshotTransferServiceImpl.java` | Sender: serves bootstrap snapshot chunks |

## Testing

Run scoped tests: `./mvnw -pl zeebe/snapshot -am test -DskipITs -DskipChecks -T1C`

Tests use `TestFileBasedSnapshotStore` (in test-jar) which wraps `FileBasedSnapshotStore` with a `ControllableActorSchedulerRule`. Tests follow the `// given`, `// when`, `// then` pattern and use AssertJ + Awaitility. Key test classes: `FileBasedSnapshotStoreTest`, `FileBasedTransientSnapshotTest`, `FileBasedReceivedSnapshotTest`, `SnapshotTransferTest`.
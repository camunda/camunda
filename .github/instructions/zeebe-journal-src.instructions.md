```yaml
---
applyTo: "zeebe/journal/src/**"
---
```
# Zeebe Journal Module

The journal is a segmented, file-backed write-ahead log (WAL) used by Zeebe's Raft consensus layer to persist replicated records durably before they are applied to the state machine. It provides append-only writes, indexed reads, flushing, truncation, and compaction — all operating on memory-mapped segment files with SBE-encoded records and CRC32C checksums.

## Architecture

```
  Journal (interface)
      │
  SegmentedJournal ──────── SegmentedJournalBuilder (builder pattern)
      │
      ├── SegmentedJournalWriter ── writes to current Segment, rolls to next on full
      ├── SegmentedJournalReader ── iterates across segments, acquires read locks
      ├── SegmentsManager ───────── owns ConcurrentSkipListMap<Long, Segment>, handles lifecycle
      ├── SparseJournalIndex ────── sparse in-memory index (every Nth record) for seek optimization
      └── JournalMetaStore ──────── persists lastFlushedIndex externally (e.g., RocksDB)
```

Each `Segment` wraps a `MappedByteBuffer` and owns one `SegmentWriter` plus zero or more `SegmentReader` instances. Records are framed with a 1-byte version prefix (`FrameUtil`), followed by SBE-encoded `RecordMetadata` (checksum + length) and `RecordData` (index + ASQN + data blob).

## Key Abstractions

- **`Journal`** (`Journal.java`): Public API — `append`, `deleteAfter`, `deleteUntil`, `reset`, `flush`, `openReader`, `getTailSegments`. Sole implementation is `SegmentedJournal`.
- **`JournalRecord`** (`JournalRecord.java`): Immutable record with `index()`, `asqn()`, `checksum()`, `data()`, `serializedRecord()`. Implementation: `PersistedJournalRecord` (record type composing `RecordMetadata` + `RecordData`).
- **`JournalReader`** (`JournalReader.java`): Extends `Iterator<JournalRecord>` — supports `seek(index)`, `seekToAsqn(asqn)`, `seekToFirst()`, `seekToLast()`.
- **`JournalMetaStore`** (`JournalMetaStore.java`): SPI for persisting `lastFlushedIndex` externally. Has built-in `InMemory` implementation. In production, backed by RocksDB via the atomix module.
- **`JournalIndex`** / `SparseJournalIndex`: In-memory sparse index mapping `index→position` and `asqn→index`. Density is configurable (default: every 100th record). Used to accelerate seeks without scanning all records.
- **`SegmentDescriptor`** (`SegmentDescriptor.java`): Java record storing segment metadata — `id`, `index`, `maxSegmentSize`, `lastIndex`, `lastPosition`. Serialized via SBE (`SegmentDescriptorSerializerSbe`), versioned (current: v2).
- **`SegmentAllocator`** (`SegmentAllocator.java`): Strategy interface for disk pre-allocation. Default: `PosixSegmentAllocator` with `posix_fallocate` syscall, falling back to zero-fill via Agrona `IoUtil.fill`.

## Data Flow

### Append Path
1. `SegmentedJournal.append(asqn, writer)` → delegates to `SegmentedJournalWriter`
2. `SegmentedJournalWriter.appendInCurrentSegmentOrNext()` → tries `SegmentWriter.append()` → returns `Either<SegmentFull, JournalRecord>`
3. If `SegmentFull`, creates new segment via `SegmentsManager.getNextSegment()` (pre-allocated async via `CompletableFuture<UninitializedSegment>`) and retries
4. `SegmentWriter` serializes `RecordData` via `SBESerializer`, computes CRC32C checksum, writes `RecordMetadata`, writes frame version byte, updates `JournalIndex`

### Read Path
1. `SegmentedJournalReader` iterates within current `SegmentReader`, crossing segment boundaries via `journal.getNextSegment()`
2. `SegmentReader.hasNext()` checks `FrameUtil.hasValidVersion()` at current buffer position
3. `SegmentReader.next()` delegates to `JournalRecordReaderUtil.read()` which validates checksum and index continuity

### Flush Path
1. `SegmentedJournal.flush()` acquires read lock (allows concurrent appends, blocks truncation/reset)
2. `SegmentedJournalWriter.flush()` gets tail segments from `lastFlushedIndex + 1`, calls `MappedByteBuffer.force()` on each
3. Updates `lastFlushedIndex` in both writer and `JournalMetaStore`

## Concurrency Model

- `StampedLock` in `SegmentedJournal` protects structural mutations (`deleteAfter`, `deleteUntil`, `reset` take write lock; `flush` and `openReader` take read lock)
- Append operations are NOT lock-protected — designed for single-writer access from the Raft thread
- `SegmentReader.hasNext()` and `next()` acquire the journal's read lock to prevent reading from concurrently deleted segments
- `Segment.open` and `markedForDeletion` are volatile for cross-thread visibility
- Segment deletion is deferred: file is renamed (`.deleted` suffix), actual deletion occurs when all readers close

## Record Format (on disk)

```
┌─────────┬────────────────────┬──────────────────────────┐
│ Frame   │ RecordMetadata     │ RecordData (SBE)         │
│ (1 byte)│ (SBE: checksum,len)│ (index, asqn, data blob) │
└─────────┴────────────────────┴──────────────────────────┘
```
- Frame: `0x01` = valid record, `0x00` = ignored/invalidated
- SBE schema: `src/main/resources/journal-schema.xml` (schema ID 7, version 2)
- Checksums: CRC32C via `ChecksumGenerator` (uses `java.util.zip.CRC32C`)
- Byte order: little-endian throughout

## Segment File Layout

```
┌──────────────────────┬──────────┬──────────┬─────┐
│ SegmentDescriptor    │ Record 0 │ Record 1 │ ... │
│ (version + SBE meta  │          │          │     │
│  + SBE descriptor)   │          │          │     │
└──────────────────────┴──────────┴──────────┴─────┘
```
- File naming: `{name}-{id}.log` (e.g., `journal-1.log`), deleted: `{name}-{id}.log_{n}-deleted`
- Default max segment size: 32 MB. Pre-allocated on creation.
- Descriptor occupies first bytes; records start at `descriptor.encodingLength()` offset

## Extension Points

- **New `SegmentAllocator`**: Implement the `SegmentAllocator` functional interface and pass to `SegmentedJournalBuilder.withSegmentAllocator()`.
- **New `JournalMetaStore`**: Implement `JournalMetaStore` interface (4 methods) for alternative flush-index persistence.
- **SBE schema changes**: Modify `journal-schema.xml`, increment `sinceVersion` for new fields (backward compatible), increment `SegmentDescriptorSerializer.CUR_VERSION` for breaking changes.

## Invariants

- Record indices are strictly sequential within a segment and across segments (no gaps). `InvalidIndex` is thrown on violation.
- ASQN (Application Sequence Number) must be monotonically increasing across records that carry one. `ASQN_IGNORE = -1` marks records without ASQN (e.g., Raft leadership changes).
- `lastFlushedIndex` in `JournalMetaStore` must be updated **before** deleting segments during truncation to prevent corruption on crash recovery.
- After `reset()`, all existing readers must be explicitly reset — they will return `hasNext() = false` until repositioned.
- `SegmentDescriptor` is immutable (Java record); updates create new instances via `withUpdatedIndices()` or `reset()`.
- Released `SegmentDescriptorSerializer` versions must never be modified — create a new version instead.

## Common Pitfalls

- Never call `append()` concurrently from multiple threads — the writer assumes single-threaded access.
- Always call `flush()` to guarantee durability; appends write to memory-mapped buffers which may not be persisted immediately.
- When modifying segment lifecycle (delete/truncate), always update `JournalMetaStore` **first** to maintain crash consistency.
- `SegmentReader.seek()` will throw if the segment is closed — check `segment.isOpen()` before seeking.
- Do not use `MappedByteBuffer` after calling `IoUtil.unmap()` — the buffer is invalidated. The `flush()` method handles this case by checking `isOpen()`.
- The `SparseJournalIndex` is probabilistic for `hasIndexed()` — it may return false positives. Always handle the case where lookup returns `null`.

## Key Files

- `Journal.java` — public API contract (all consumers depend on this interface)
- `SegmentedJournal.java` — sole `Journal` implementation, orchestrates writer/reader/segments
- `SegmentWriter.java` — core write logic: serialization, checksum, index management, truncation
- `SegmentsManager.java` — segment lifecycle: creation, loading from disk, deletion, corruption handling
- `journal-schema.xml` — SBE schema defining on-disk record and descriptor formats

## Build & Test

```bash
# Unit tests only
./mvnw -pl zeebe/journal -am verify -DskipITs -DskipChecks -T1C
# Specific test
./mvnw -pl zeebe/journal -am test -DskipITs -DskipChecks -Dtest=SegmentedJournalTest -T1C
```
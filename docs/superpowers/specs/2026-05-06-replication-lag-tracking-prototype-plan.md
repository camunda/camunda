# Replication Lag Tracking — Prototype Plan (Slice 1)

**Scope:** End-to-end prototype of `zeebe.raft.replication.lag.bytes` (gauge,
labels: `partition`, `follower`), wired into Grafana, with happy-path tests
only. The metric is the input to the rebalance pre-check in Slice 3.

**In scope:** log lag, snapshot install lag, bounded init computation, snapshot
size persisted in `SnapshotMetadata`.
**Non-goals:** exhaustive edge-case tests, benchmarking.

---

## Approach

Two independent paths, both producing per-follower byte counts that sum into
the published lag.

### Log lag — cumulative byte counter on the journal

Hidden behind a single `Journal.bytesBetween(fromIndexExclusive, toIndexInclusive)`
method that returns an upper-bound estimate of the bytes occupied by entries
in the open-closed range. Internally:

- **`bytesWrittenLifetime`** — monotonic `long` on `SegmentedJournal`,
  incremented by `entrySize` on every successful local append. Never decreases.
  Resets to `sum(live segment bytes)` on journal re-open.
- **Sparse-index extension** — alongside each existing sparse-index entry
  `(index, position)`, also store `bytesWrittenAtIndex` (the value of
  `bytesWrittenLifetime` captured at append time of that entry). One extra
  `long` per density-th entry (~negligible memory). Updated at the existing
  sparse-index update site.
- **`bytesAtFirstIndex` anchor** — `long` on the journal. On creation: 0.
  On compaction: `anchor += sum(bytes of deleted segments)` (Zeebe compacts
  whole segments, so this is exact). Maintains the invariant
  `anchor + sum(live segment bytes) == bytesWrittenLifetime`.
- **`bytesBetween` lookup**:

  ```
  long upper = sparseIndex.cumulativeBytesAt(toIndexInclusive)        // floor lookup
                     ?? bytesWrittenLifetime                          // toIndex past last sparse point
  long lower = sparseIndex.cumulativeBytesAt(fromIndexExclusive)      // floor lookup
                     ?? bytesAtFirstIndex                             // fromIndex below firstIndex
  return Math.max(0, upper - lower)
  ```

  Pure in-memory `ConcurrentSkipListMap.floorEntry` lookups. **Zero I/O at
  query time, ever.** Result may overestimate by up to `density (= 100)`
  entries on each side — biased high, the safe direction for the rebalance
  pre-check.

The `bytesWrittenLifetime` / sparse-cumulative mechanics are internal. No
consumer reads them directly. Persistence is a non-issue: both fields are
in-memory, rebuilt by the existing segment scan on journal re-open.

### Snapshot install lag — total size in snapshot metadata

**Completed** (PR #52609 on `feat/snapshot-total-size-in-metadata`).

- **`SnapshotMetadata.totalSizeBytes`** — new field on the snapshot metadata
  record. Jackson JSON with `@JsonIgnoreProperties(ignoreUnknown = true)` and
  `0L` default for backward compatibility (same pattern as `maxExportedPosition`).
- **Captured at persist time** via `FileUtil.directorySize(directory, SnapshotManifests::isNotMetadataFile)`,
  called before the metadata file is written (avoids circular dependency).
  `SnapshotManifests.isNotMetadataFile` is promoted to package-private and
  shared — the exclusion reason is co-located with the existing identical
  exclusion in checksum computation. `FileUtil.directorySize(Path, Predicate<Path>)`
  added to `zeebe-util` using `SimpleFileVisitor` so `attrs.size()` is read
  from traversal metadata at no extra `stat()` cost.
- **Bootstrap snapshots** also carry `totalSizeBytes` via `forBootstrap(int, long)`.
  The no-arg `forBootstrap(int)` was removed (no callers outside the module;
  the 1-arg version would have produced a wrong `0` value).
- **Legacy fallback** — **eager, not lazy**. Three eager-compute points ensure
  `totalSizeBytes` is always populated before a `FileBasedSnapshot` is
  constructed:
  1. `FileBasedTransientSnapshot.persistInternal` — computes before writing metadata
  2. `FileBasedReceivedSnapshot.persistInternal` — accumulates `receivedDataBytes`
     inline as each non-metadata chunk arrives; no directory walk at persist time
  3. `FileBasedSnapshotStoreImpl.collectSnapshot` (store open) — computes from
     disk for legacy snapshots whose on-disk metadata predates the field; in-memory
     only (on-disk metadata is not modified — it is part of the SFV checksum)
     `FileBasedSnapshot.getTotalSizeInBytes()` **throws `IllegalStateException`** if
     `totalSizeBytes == 0` at access time — surfaces upstream bugs loudly. Whether to
     add a lazy walk as a graceful fallback is a deferred decision point.
- **`PersistedSnapshot.getTotalSizeInBytes()`** — default method on the interface,
  delegates to `getMetadata().totalSizeBytes()`. No I/O.
- **Type naming**: all `*Checksum*` types were renamed to reflect that their scope
  is expanding to also carry file-size metadata:
  `CRC32CChecksumProvider` → `SnapshotFileInfoProvider`,
  `MutableChecksumsSFV` → `MutableSnapshotManifest`,
  `ImmutableChecksumsSFV` → `SnapshotManifest`,
  `SfvChecksumImpl` → `SfvManifestImpl`,
  `SnapshotChecksum` → `SnapshotManifests`.
  The SFV file on disk remains a checksum-only artifact; documented on `SnapshotManifest`.

Per-member tracking lives on `RaftMemberContext`:

- `snapshotInstallRemainingBytes` — `long`, default 0.
- **Install start** (`buildInstallRequest`, when `nextSnapshotIndex` transitions
  from 0 to a real index): `member.snapshotInstallRemainingBytes =
  persistedSnapshot.getTotalSizeInBytes()`. Field read; no I/O.
- **Chunk ack** (`handleInstallResponseOk`, success branch): decrement by
  `request.data().remaining()`. Floor at 0. No I/O.
- **Install complete** (`request.complete()` branch already at
  `LeaderAppender.handleInstallResponseOk` line 504): zero out. No log-lag
  recompute needed — the cumulative scheme handles the matchIndex jump
  automatically.

### Why the lag must live on RaftMemberContext, not just in the metric

The Slice 3 rebalance pre-check reads lag programmatically to decide
"transfer" vs "skip." Metrics are a one-way publication mechanism, not a
queryable source of truth. So:

```java
class RaftMemberContext {
  long getReplicationLagBytes() {
    return raft.getLog().bytesBetween(matchIndex, raft.getLog().getLastIndex())
         + snapshotInstallRemainingBytes;
  }
}
```

The metric publishes `getReplicationLagBytes()`. The pre-check calls the
same accessor. Single source of truth.

### What we deliberately skip in the prototype

| Skipped | Why | When picked up |
|---|---|---|
| Tests for term changes, partial acks, retries, concurrent installs | Happy path only per scope | Production hardening |

### Deferred decisions

| Item | Decision posture |
|---|---|
| Lazy `Files.size()` walk fallback in `FileBasedSnapshot.getTotalSizeInBytes()` when `totalSizeBytes == 0` is somehow observed at access time | Currently throws `IllegalStateException` — surfaces upstream bugs loudly. If it fires in practice, decide between fixing the missed eager-compute path or adding a lazy fallback. The fallback is a one-method change. |
| Extending `SnapshotFileInfoProvider` to also return file sizes (from `LiveFileMetaData.size()`) alongside checksums | This would allow `SnapshotManifest` to track both checksum and size without a separate directory walk. Deferred until the manifest API stabilises. |

---

## Hook points

### Journal layer (cumulative log lag)

| # | File | Change |
|---|---|---|
| J1 | `SparseJournalIndex.java` | Add `bytesWrittenAtIndex` to each indexed point. Provide `cumulativeBytesAt(index)` — floor lookup returning the stored value, or `null` if nothing matches. |
| J2 | `SegmentedJournal.java` (or wherever appends are sequenced) | Add `bytesWrittenLifetime` (long, monotonic). Increment by `entrySize` on each successful append. Pass to sparse index when indexing. |
| J3 | `SegmentedJournal.java` | Add `bytesAtFirstIndex` (long, anchor). On compaction, advance by sum of deleted segment sizes. On re-open, reset to 0 and rebuild `bytesWrittenLifetime` from segment scan. |
| J4 | `Journal.java` interface | New method `bytesBetween(long fromIndexExclusive, long toIndexInclusive)`. Implementation in `SegmentedJournal` does the two floor lookups + subtraction described above. |
| J5 | Unit test | `SegmentedJournalBytesBetweenTest` covering: empty range, range spanning multiple segments, range crossing compaction boundary, range with `fromIndex < firstIndex`, range starting/ending mid-density. |

### Snapshot layer (snapshot install lag) — **DONE** (PR #52609)

| # | File | Change |
|---|---|---|
| S1 | `SnapshotMetadata.java` | Add `long totalSizeBytes()` to interface. |
| S2 | `FileBasedSnapshotMetadata.java` | Add `totalSizeBytes` field with backward-compatible `0L` default in `@JsonCreator`. Remove dead `forBootstrap(int)` overload; only `forBootstrap(int, long)` remains. |
| S3 | `FileUtil.java` (`zeebe-util`) | Add `directorySize(Path, Predicate<Path>)` using `SimpleFileVisitor` so `attrs.size()` is free from traversal metadata. |
| S4 | `SnapshotManifests.java` (was `SnapshotChecksum`) | Promote `isNotMetadataFile` to package-private — exclusion reason co-located with existing identical exclusion in checksum computation. |
| S5 | `FileBasedTransientSnapshot.java` | Call `FileUtil.directorySize(directory, SnapshotManifests::isNotMetadataFile)` before writing metadata. Pass `totalSizeBytes` to both bootstrap and regular metadata constructors. |
| S6 | `FileBasedReceivedSnapshot.java` | Accumulate `receivedDataBytes` inline as each non-metadata chunk arrives (`receivedDataBytes += chunk.getContent().length`). Use at persist time when sender's metadata has `totalSizeBytes == 0`. No directory walk. |
| S7 | `FileBasedSnapshotStoreImpl.java` | `ensureTotalSizeBytes()` — eager compute at store-open for legacy snapshots. In-memory only; on-disk metadata not modified (part of SFV checksum). |
| S8 | `PersistedSnapshot.java` + `FileBasedSnapshot.java` | `getTotalSizeInBytes()` as default method on interface. Override in `FileBasedSnapshot` throws `IllegalStateException` when `0` — safety net for missed eager-compute paths. |
| S9 | Rename | `CRC32CChecksumProvider` → `SnapshotFileInfoProvider`, `MutableChecksumsSFV` → `MutableSnapshotManifest`, `ImmutableChecksumsSFV` → `SnapshotManifest`, `SfvChecksumImpl` → `SfvManifestImpl`, `SnapshotChecksum` → `SnapshotManifests`, `ChecksumProviderRocksDBImpl` → `RocksDbSnapshotFileInfoProvider`. |

### Atomix layer (per-follower state + metric)

| # | File | Change |
|---|---|---|
| A1 | `RaftMemberContext.java` | Add `snapshotInstallRemainingBytes`. `getReplicationLagBytes()` derives log lag via `raft.getLog().bytesBetween(matchIndex, lastIndex)` and adds `snapshotInstallRemainingBytes`. `resetState` zeros the snapshot field. |
| A2 | `LeaderAppender.buildInstallRequest` | When transitioning to a new snapshot install for a member, set `member.snapshotInstallRemainingBytes = persistedSnapshot.getTotalSizeInBytes()`. |
| A3 | `LeaderAppender.handleInstallResponseOk` | On success: `member.subtractSnapshotInstallRemainingBytes(request.data().remaining())`. On `request.complete()` branch (line 504): zero snapshot remaining. |
| A4 | New: `RaftReplicationLagMetrics` (or extend `LeaderAppenderMetrics`) | Per-follower `StatefulGauge` updated on every `matchIndex`/snapshot-bytes change. Reads `member.getReplicationLagBytes()`. |
| A5 | `RaftReplicationMetricsDoc` (or `LeaderMetricsDoc`) | New `REPLICATION_LAG_BYTES` doc enum entry: name `atomix.partition.raft.replication.lag.bytes`, type GAUGE, follower + partition tags. |
| A6 | `monitor/grafana/zeebe.json` | Add panel **"Replication lag (bytes)"** in the Raft row, immediately after "Leader append data rate" (line ~11975). Query: `sum(atomix_partition_raft_replication_lag_bytes{cluster=~"$cluster", namespace=~"$namespace", partition=~"$partition"}) by (partition, follower)`, unit `bytes`. |

### Entry size source

`IndexedRaftLogEntry.getReplicatableJournalRecord().approximateSize()` is the
existing reference (used at line 187 of `LeaderAppender`). Same value used
everywhere. The number is the serialized record size + 24 bytes of header
overhead — close enough for replication accounting.

---

## Tests (happy-path only)

1. **`SegmentedJournalBytesBetweenTest`** — append a known sequence spanning
   3 segments, query `bytesBetween(from, to)` for various combinations
   including: range fully inside a segment, range spanning segments, range
   with `fromIndex < firstIndex` (post-compaction case), range with
   `toIndex == lastIndex`. Assert results match the actual sum within the
   sparse-density tolerance.
2. **Replication integration test** — extend an existing leader/follower
   test: append K entries, assert `member.getReplicationLagBytes()` rises
   to ~`sum(approximateSize)` while replication is in flight, then settles
   back to 0 once acks land.
3. **Snapshot install integration test** — extend an existing install test:
   force an install (follower far behind), assert
   `member.getReplicationLagBytes()` ≈ `snapshot.totalSize` mid-install and
   drops to 0 after install completes and log replication catches up.

Three tests. The journal-layer test covers the heart of the cumulative scheme;
the two integration tests cover the end-to-end gauge behaviour through the
two paths.

---

## Grafana panel

Mirror the structure of "Leader append data rate" (line ~11876–11975 in
`zeebe.json`):

- Title: `Replication lag (bytes)`
- Description: `Per-follower replication lag in bytes — input to coordinated leadership transfer`
- Unit: `bytes`
- Query: `sum(atomix_partition_raft_replication_lag_bytes{cluster=~"$cluster", namespace=~"$namespace", partition=~"$partition"}) by (partition, follower)`
- Legend: `p{{partition}} to {{follower}}`
- gridPos: same width/height as adjacent panels, placed after the data rate
  panel in the Raft row (line ~13498)

---

## Sequencing

1. ~~**Snapshot metadata totalSize** (S1–S9): metadata field, eager-compute at
   three points, `getTotalSizeInBytes()`, type renames~~ **DONE** — PR #52609
2. **Journal cumulative scheme** (J1–J5): sparse index extension, lifetime
   counter, anchor field, `bytesBetween` API, unit test
3. **Atomix wiring** (A1–A3): `RaftMemberContext.getReplicationLagBytes()`,
   install-start / chunk-ack / install-complete hooks
4. **Metric** (A4–A5): gauge + doc enum
5. **Grafana** (A6): panel
6. **Integration tests** (replication + install)

Format/build/spotless after each commit per `AGENTS.md`. Commits split per
section so review is cheap.

# Cut plan: write-behind PR 2 (store) and PR 3 (key index)

Produced 2026-11-12 by a read-only analysis of `origin/cs/write-behind-generic-core` (B).
Paths under `zeebe/zb-db/writebehind/src/main/java/io/camunda/zeebe/db/writebehind/`.

## PR 2: zeebe-db-writebehind, no key index

| File | Action |
|---|---|
| pom.xml, build.gradle.kts, package-info | Keep; add agrona, zeebe-util (BufferUtil.startsWith) or inline; drop errorprone @GuardedBy; add to parent + dependencyManagement. zeebe-protocol stays (EnumValue/ScopedColumnFamily in signatures) but no ZbColumnFamilies. |
| RawKey, KeyRange, Version | Keep |
| Backend | Strip groupRange, groupLastSeekKey (PR 3). Keep knownAbsent default false. |
| RawBackend | Strip groupRange, groupLastSeekKey, prefixOf, LAST_KEY_SUFFIX_LENGTH. Keep prefixRange. Rework value paths. |
| Values | Rewrite without Copyable; add ValueInstances. |
| WriteBehindStore | Strip key index + RDBMS-only APIs (below). |
| AsyncFlusher | Keep (drop @GuardedBy annotation, keep comment). |
| ViewSource | Keep; readValueUnlessStale -> void readValue. |
| CommittedSource | PR 3. MergingCursor uses ViewSource directly in PR 2. |
| MergingCursor | seekCommitted = close(); committed = openView(); seekInDirection(key). Drop restartFromView, isStale checks, stale path in readValue (void), restart in advancePast. |
| WriteBehindColumnFamily | Drop transaction.prepareScan in iterateInTransaction; readValue void in visitEntries; per-instance value scratch buffer. |
| WriteBehindTransaction(+Context) | Drop prepareScan. Drop listeners/addListener/notifyOutcome/addTransactionListener unless PR 1 kept TransactionListener. |
| WriteBehindConfiguration | Record = (maxPendingBytes, cacheMaxBytes, maxWriteRetries, flushTimeout, flushDelay, flushThresholdBytes). Keep 4-arg ctor, defaults(), withCacheMaxBytes/MaxPendingBytes/FlushDelay/FlushThresholdBytes. Strip HOTSPOT_POLICIES, DEFAULT_KEY_INDEX_*, toIds, keyIndex* builders/accessors. |
| WriteBehindMetrics | Strip keyIndexLoads/DiscardedLoads/Disables/indexedScans/Switches/Handovers/Refills/DiscardedRefills. Rename keyIndexAbsent -> knownAbsent (`lookup.known.absent`). |
| WriteBehindZeebeDb(+Factory) | Keep |
| HandoverSource, IndexSource, KeyIndex, KeyIndexPolicy | PR 3 |

WriteBehindStore strip list: fields keyIndexes, MAX_DISCARDED_LOADS, LOG; ctor keyIndexColumnFamilies loop;
commit's updateKeyIndexes; absentFromKeyIndex branches in get/exists; methods absentFromKeyIndex,
prepareKeyIndex, loadKeyIndex, refillKeyIndex, seekToCoveredEnd, collect, keyIndex(long),
updateKeyIndexes, disableKeyIndex. RDBMS-only, drop: unflushed(), Unflushed, RangeView,
MergedIterator, public committedSeq().

Tests PR 2:
- writebehind: keep InMemoryRawDbAccess, CountingRawDbAccess, FlushDelayTest, MetricsExportTest,
  FlushedValueCacheTest (drop withKeyIndexColumnFamilies). Add ValuesTest (msgpack, DbForeignKey,
  DbCompositeKey, DbNil, DbEnumValue, cross-class copy) and a concurrent reader+writer encode test (R1).
- zeebe-db-tests: WriteBehindTestFactory (create(), create(config)), WriteBehind{ColumnFamily,
  ColumnFamilyRandomizedProperty, CompositeKeyColumnFamily, StringColumnFamily,
  TenantAwareKeyColumnFamily, Transaction, TransactionIsolation}Test,
  RocksDbVsWriteBehindDifferentialTest (+ DifferentialRandomizedContractTest if PR 1 lacks it).
- Drop DbValueCopyTest (needs Copyable).

Commits PR 2: (1) module skeleton, (2) RawKey/KeyRange/Version/Backend/Values+ValueInstances/RawBackend,
(3) Store/AsyncFlusher/Config/Metrics, (4) ViewSource/MergingCursor/tx+ctx/ColumnFamily/ZeebeDb+factory,
(5) unit tests, (6) contract+differential tests, (7) one commit per correctness fix R1-R4.

## PR 3: key index with windows

Add KeyIndexPolicy (DISABLE, KEEP_LOWEST, KEEP_HIGHEST), KeyIndex, CommittedSource, IndexSource,
HandoverSource, Backend.groupRange/groupLastSeekKey (+RawBackend). Restore stripped bits in Store,
MergingCursor (index selection, restartFromView, stale, boolean readValue), prepareScan, metrics.
Config: `int keyIndexMaxKeys`, `Map<Long, KeyIndexPolicy> keyIndexPolicies` (default empty = no
index). Proposal: drop keyIndexColumnFamilies; indexed iff key present in map. Builders
withKeyIndexPolicies, withKeyIndexMaxKeys. No ZbColumnFamilies.
Tests: KeyIndexTest, IndexedScanTest, WindowedIndexTest; contract WriteBehindKeyIndex* (8) and
WriteBehindKeyIndexWindow* (7); RocksDbVsWriteBehindKeyIndex{,LowestWindow,HighestWindow}DifferentialTest.
Commits: group range; policy/KeyIndex/config/metrics; store load/update/absent/disable;
CommittedSource/IndexSource/cursor switching; windows HandoverSource/refill; tests.
Carry-over open issues: refill stalls processing thread (chunk maxKeys/4); IndexSource
MAX_STORAGE_READS=4 stale; ~30 MB/partition; no engine/load validation.

## Values without Copyable

| Branch call | Caller / thread | New approach |
|---|---|---|
| Values.copyOf | ColumnFamily insert/update/upsert; processing, exporter, async-task actors | encode into CF scratch ExpandableArrayBuffer -> exact byte[] -> ValueInstances.newInstance(cls).wrap(fresh) |
| Values.copy | ColumnFamily.get, MergingCursor/ViewSource readValue, RawBackend.copy | encode source, target.wrap(fresh byte[]) |
| Values.decode | RawBackend.get (reader) | ValueInstances.newInstance + wrap storage byte[] |
| Values.encode | RawBackend.Batch.put (writer thread) | unchanged |

ValueInstances: DbNil -> INSTANCE; DbKey -> DbKeyInstanceFactory; else ClassValue<Supplier> via
getDeclaredConstructor + setAccessible (package-private ctors exist). GAP: DbEnumValue has no
no-arg ctor (verified) -> needs an additive accessor for the enum class + a case.

Never wrap targets over a reusable scratch buffer: StringValue/BinaryValue keep views; RocksDB
wraps a fresh byte[] per get, so engine may hold values across gets. Scratch buffer per column
family instance (bound to one context), not ThreadLocal.

## Correctness risks found

- R1 (VERIFIED): UnpackedObject.write reuses its `writer` field; shared held values are encoded
  concurrently by writer thread (flush) and readers (copy-out) -> corrupt output.
  (a) hold immutable encoded byte[] alongside the object (copy-out = wrap(bytes), flush writes
  bytes, exact weights); (b) synchronized(source) around write. -> NEEDS OWNER DECISION.
- R2: b3aa70fd98 (ehcache branch) mostly fixed structurally on B; remaining window: active.put
  before committedSeq written, unlocked point reads. Fix: awaitFlushed reads lastSeq under
  publishLock.readLock().
- R3: torn point reads across contexts (lookupInMemory without seq). Fix: read committedSeq first,
  Batches.getAsOf(key, seq), fall back active -> sealed -> cache.
- R4: readStorage check-then-act cache.put can overwrite newer flushed value. Fix:
  asMap().putIfAbsent, re-check epoch, remove(key, mine) if changed.
- R5 (docs): snapshot wait blocks IO pool thread up to flushTimeout; silent writer failure (no
  health); fixed size estimates; per-partition heap; raw daemon thread outside actor scheduler;
  version chains retain overwrites until flush.

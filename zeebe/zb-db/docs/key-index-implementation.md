# Key index for hot column families: implementation plan

Status: ready to implement. Target branch: `cs/rdbms-cache-own-impl`. Module:
`zeebe/zb-db/writebehind` (Gradle project `:zeebe-db-writebehind`, package
`io.camunda.zeebe.db.writebehind`).

This document is the complete brief for one implementation session. Read all of it before you
change code. Read [write-behind-cache-design.md](write-behind-cache-design.md) first. It describes
the current code. Section 9 lists the rules for builds and commits.

## 1. Problem

Every scan calls `WriteBehindStore#openView`. That call opens a native RocksDB iterator and seeks
it, even when the result is already in memory. Profiles show this as a hot spot. Most engine scans
are short prefix scans that find zero or a few keys, so the iterator open and seek costs more than
the scan.

The same cost applies to point reads of keys that do not exist. Each `insert` with consistency
checks calls `exists`. A new key is not in the dirty index or the cache, so `exists` reads RocksDB.

## 2. Goal

For a configured set of column families, the store keeps **every committed key** in memory. The
store loads the keys of a column family on its first scan. After that:

- A scan of that column family reads keys from memory and opens no RocksDB iterator.
- `exists` and `get` answer "absent" from memory when the key is not in the index.

The index holds **keys only**. Values still come from the dirty index, then the Caffeine cache,
then a RocksDB point read.

A column family that is not configured, not loaded yet, or disabled uses the merged scan of today
(`MergingCursor` over the dirty copy and RocksDB). Nothing changes for it.

Non-goals:

- No change to `ZeebeDb`, `ColumnFamily`, `zeebe-db-api`, `zeebe-db-rocksdb` or the snapshot format.
- No persistence. The index is empty after each restart.
- No change to engine code. Reducing the number of scans in the engine is separate work.
- No metrics registry wiring. Expose plain counters on the store for tests (section 8).

## 3. Decisions

These decisions are final for this session. Do not reopen them. If one of them cannot work, stop
and report why (section 10).

1. **One index per column family, not per key range.** Coverage is all or nothing for a column
   family. There are no partial ranges and no range merges.
2. **Selected column families only.** `WriteBehindConfiguration` lists the column family IDs to
   index (the value of `EnumValue#getValue`). Other column families never get an index.
3. **Load on first scan.** The first scan of a selected column family loads all of its keys on the
   thread that scans. The load does not happen at startup.
4. **Cap per column family.** If a column family has more than `keyIndexMaxKeys` keys (default
   100 000), at load time or later through commits, the store disables its index until the next
   restart. A disabled column family uses the merged scan. There is no reload, so the state cannot
   flap.
5. **Skip list, keys as `RawKey`.** Each index is a `ConcurrentSkipListSet<RawKey>` that holds the
   full key bytes, column family prefix included. Keys are created and deleted all the time, and a
   skip list changes in place at `O(log n)`. Do not add typed or immutable `Db*` key classes. Bytes
   give one implementation for all key types, and `RawKey` already has RocksDB's unsigned bytewise
   order.
6. **The index follows commits, not the cache.** A Caffeine eviction has no effect on the index.
   Only a commit, a load, or a disable changes it.
7. **Consistency is checked per step, not per scan.** A scan reads the live skip list and checks a
   version counter after each key and each value. If a commit changed the key set, the scan
   continues from its current key with a merged RocksDB view. Section 5.6 explains why this is
   acceptable.

## 4. Data model

Add one class per indexed column family, `KeyIndex`. `WriteBehindStore` holds a
`Map<Long, KeyIndex>` that it builds in its constructor from the configured IDs, and never changes
afterwards. Use an immutable map, so that readers need no lock.

```java
final class KeyIndex {
  enum State { UNLOADED, LOADED, DISABLED }

  final long columnFamily;                         // the 8-byte prefix as a long
  private final ConcurrentSkipListSet<RawKey> keys = new ConcurrentSkipListSet<>();
  private volatile State state = State.UNLOADED;
  private volatile long version;                   // seqlock: odd while the key set changes
  private long lastCommitSeq;                      // guarded by publishLock's write side
  private int discardedLoads;                      // guarded by publishLock's write side
}
```

Invariants while the state is `LOADED`:

- **I1.** A key is in `keys` if and only if it has a committed, non-deleted value. "Committed"
  includes unflushed versions in the dirty index.
- **I2.** Each key in `keys` starts with the 8-byte prefix of `columnFamily`.

In the states `UNLOADED` and `DISABLED`, `keys` is empty.

All mutations of a `KeyIndex` happen while the caller holds `publishLock`'s write side. Reads use
no lock.

The column family of a key is its first 8 bytes, read as a big-endian `long`. Add a helper for
this to `RawKey`. Every key that `WriteBehindColumnFamily` encodes has at least 8 bytes.

## 5. Behavior

### 5.1 Version counter (seqlock)

A change to `keys` or to `state` happens like this, under `publishLock`'s write side:

```text
version++            // now odd
change keys and/or state
version++            // now even
```

A reader takes `v = version`. If `v` is odd, the reader does not use the index. After it reads a
key or a value, the reader checks `version == v`. If the check fails, the reader stops using the
index (section 5.4).

Bump `version` only when the key set or the state really changes. An update of an existing key does
not change the key set, so it must not bump `version`. The processor updates existing keys often,
and each bump makes running scans switch to RocksDB.

### 5.2 Commit

In `WriteBehindStore#commit`, inside the existing write-lock block, after the dirty index update.
The keys of a transaction arrive in sorted order (they come from the transaction's skip list), so
the keys of one column family are consecutive.

```text
for each column family cf that the transaction writes and that has a KeyIndex:
  index.lastCommitSeq = seq
  if index.state != LOADED: continue
  for each (key, value) of that column family:
    changed |= value != null ? keys.add(key) : keys.remove(key)
  if keys.size() > keyIndexMaxKeys: disable (section 5.5)
bump version once per changed index (one odd/even pair around all its changes)
```

`ConcurrentSkipListSet#size` counts all elements. Keep an `int` size field in `KeyIndex` instead,
updated from the return values of `add` and `remove`.

### 5.3 Load

`WriteBehindColumnFamily#iterateInTransaction` creates the cursor. Before it does, if the column
family has a `KeyIndex` in state `UNLOADED`, the store loads it:

```text
view = openView(cfPrefix)                          // records viewSeq under the read lock
collect the committed keys of the view, in order: the dirty copy merged with RocksDB, without
  dirty tombstones, and without the own writes of the transaction
stop and disable if the count exceeds keyIndexMaxKeys
close the view
publishLock.writeLock()
  if index.state != UNLOADED: discard              // another thread loaded or disabled it
  else if index.lastCommitSeq > viewSeq:           // a commit wrote to the column family
    discard; if ++discardedLoads >= 3: disable
  else: add all keys, state = LOADED, bump version
publishLock.unlockWrite()
```

Rules:

- `openView` must record `viewSeq = lastSeq` under the read lock, and return it in `View`.
- The load reads keys only. It must not decode or copy values.
- The load runs on the thread that scans. For an engine column family, that is the stream
  processor, which is also its only writer. So a discard is rare. The `discardedLoads` limit stops
  a load loop if another context commits to the column family often.
- After a restart, the first scan of each indexed column family pays one full scan of that column
  family. The cap limits that cost.

### 5.4 Scan of a loaded column family

**Refactor first (Phase 1).** `MergingCursor` merges the own writes with **one** committed source,
behind a small package-private interface. Two implementations:

- `ViewSource`: today's dirty copy plus the RocksDB iterator, moved out of `MergingCursor`.
- `IndexSource`: walks `KeyIndex.keys`, and reads values through the store.

`MergingCursor` picks the source at each `seek` and `seekForPrev`:

```text
index = store.keyIndex(column family of the seek target)
v = index == null ? 0 : index.version
if index == null or index.state != LOADED or v is odd: use ViewSource
else: use IndexSource(index, v)
```

`IndexSource`:

- Forward: `head = keys.ceiling(target)`, then `keys.higher(head)`. Reverse: `keys.floor(target)`,
  then `keys.lower(head)`.
- The set holds only one column family, so the source ends at the end of the set. The column
  family loop already stops at the first key outside the scan prefix.
- After each read from `keys`, check `index.version == v`. If the check fails, switch.

**Values.** `IndexSource` reads a value only when `MergingCursor#readValue` asks for it. That keeps
scans that read keys only (`forEachKey`, `count`, `isEmpty`, foreign-key prefix checks) free of
value reads. Read the value with the same path as `WriteBehindStore#get`: dirty index, then cache,
then RocksDB. Pass the caller's target as the decode prototype. Check `version` after the read.
Count the RocksDB reads per cursor. After `MAX_STORAGE_READS_PER_INDEXED_SCAN = 4` of them, switch.
One RocksDB iterator is cheaper than many random reads.

If the value read returns null for a key that the index returned, a concurrent commit deleted it.
The version check fails in that case, so the switch handles it.

**Switch.** A switch replaces the `IndexSource` with a new `ViewSource` (`openView` for the scan
prefix) and seeks it to the current key. If the view's head equals the current key, the switch
keeps it as the current entry when the switch happened before the cursor returned that key, and
skips it otherwise. A switch must not return a key twice and must not skip a key.

### 5.5 Disable

Under `publishLock`'s write side: `state = DISABLED`, `keys.clear()`, size `0`, bump `version`.
Log one INFO line with the column family ID and the reason (cap at load, cap through commits, or
repeated discarded loads). A disabled index stays disabled until the store closes.

### 5.6 Why per-step checks are acceptable

A RocksDB iterator shows one instant. The per-step check is weaker only when a second context
inserts or deletes keys of an indexed column family while a scan of it runs. Then the scan can
return an older entry and then a newer entry. Each returned entry is still a committed value.

In practice the stream processor is the only writer of the engine column families, and it does not
commit during its own scans. `NoSnapshotIsolationTest` already allows a transaction to see commits
made after it started. Write this guarantee in the Javadoc of `IndexSource`. Section 7 has the test
that pins it.

### 5.7 Point reads

In `WriteBehindStore#get` and `WriteBehindStore#exists`, after a miss in the dirty index and in the
cache, and before the read-lock block:

```text
index = keyIndex(column family of key)
if index != null:
  v = index.version
  if v is even and index.state == LOADED and !index.keys.contains(key) and index.version == v:
    return absent (null for get, false for exists)
```

A key that is in `keys` gives no shortcut, because the index holds no values. Continue on the
existing path.

Why this is safe: a commit changes the dirty index and the key index in the same critical section.
A reader that missed the dirty index before a commit, and then sees the new key set, sees the key
and takes the normal path. A reader that sees the old key set returns the state from before the
commit.

### 5.8 Configuration

Add to `WriteBehindConfiguration`:

|         Property         |  Default  |                                Notes                                 |
|--------------------------|-----------|----------------------------------------------------------------------|
| `keyIndexColumnFamilies` | see below | `Set<Long>` of column family IDs. An empty set disables the feature. |
| `keyIndexMaxKeys`        | 100 000   | Cap per column family (decision 4).                                  |

Add `withKeyIndexColumnFamilies` and `withKeyIndexMaxKeys`, as the other `with…` methods do.

Default set, from `ZbColumnFamilies` (`zeebe-protocol` is already a dependency):
`ELEMENT_INSTANCE_PARENT_CHILD`, `NUMBER_OF_TAKEN_SEQUENCE_FLOWS`, `TIMER_DUE_DATES`,
`JOB_DEADLINES`, `JOB_BACKOFF`, `MESSAGE_DEADLINES`. This is a starting list. Do not add
`VARIABLES` or `EVENT_TRIGGER`. The engine will stop scanning them instead.

Memory: a skip-list entry with a `RawKey` costs about 100 to 130 bytes. At the cap, one column
family uses about 10 to 13 MiB. Six column families use at most about 80 MiB per partition.

### 5.9 Close and failure

The index needs no work at close or at a writer failure. It is heap memory and has no thread. After
`ensureUsable` fails, scans and reads throw before they reach the index.

## 6. Implementation phases

Commit at the end of each phase (section 9). Each phase must compile and pass its exit criteria
before the next phase starts.

### Phase 1: one committed source in `MergingCursor` (`refactor:`)

- Add the committed-source interface and `ViewSource`. Move the dirty-copy and RocksDB merge from
  `MergingCursor` into `ViewSource`. `MergingCursor` merges own writes with one source.
- No behavior change.
- **Exit:** all `WriteBehind*Test` classes and `RocksDbVsWriteBehindDifferentialTest` in
  `:zeebe-db-tests` pass.

### Phase 2: keep the index current (`feat:`)

- `KeyIndex`, the `RawKey` column family helper, configuration (5.8), the commit path (5.2), load
  (5.3) and disable (5.5). `viewSeq` in `View`.
- Scans and point reads do not use the index yet.
- Add `KeyIndexTest` in `zeebe/zb-db/writebehind/src/test/java` (create the test source set if it
  does not exist). Use the store with a real RocksDB. After random commits, deletes, loads and
  disables, check I1 against a full merged scan.
- **Exit:** Phase 1 exit criteria, plus `KeyIndexTest`.

### Phase 3: serve scans and point reads from the index (`feat:`)

- `IndexSource`, source selection, switches and the value-read limit (5.4). The point-read
  shortcut (5.7).
- **Exit:** Phase 1 exit criteria with the index on and off, plus all tests from section 7.

### Phase 4: broker setting and engine suite (`feat:`, then `test:` if needed)

- Add `keyIndexColumnFamilies` (a list of `ZbColumnFamilies` names) and `keyIndexMaxKeys` to
  `WriteBehindCfg` in `zeebe/broker`, next to `maxPendingSize` and `cacheSize`. Map the names to
  IDs in `createConfiguration`. Default: the defaults of `WriteBehindConfiguration`. Extend
  `LegacyWriteBehindTest`.
- Run the engine tests against the write-behind DB (section 9). Fix every failure that the key
  index causes. To tell index failures from existing failures, run again with an empty
  `keyIndexColumnFamilies`. Report existing failures. Do not fix them.

## 7. Tests

Put tests in `:zeebe-db-tests` (package `io.camunda.zeebe.db.contract`) when they use the public
API. Put them in `:zeebe-db-writebehind` when they need package-private access. The contract tests
use `DefaultColumnFamily.DEFAULT`, ID `0`. Follow the repository test conventions: JUnit 5,
AssertJ, `should…` names, `// given`, `// when`, `// then`, Awaitility instead of `Thread.sleep`.

|                                                                         Test                                                                         |                                         What it proves                                          |
|------------------------------------------------------------------------------------------------------------------------------------------------------|-------------------------------------------------------------------------------------------------|
| A second differential test class: tiny cache, tiny pending budget, column family `0` indexed, `keyIndexMaxKeys` small enough that random runs hit it | Random operations give the same results as RocksDB with the index loaded, and after a disable   |
| The contract test classes again, with column family `0` indexed (one subclass each, as `WriteBehind*Test` does today)                                | The full contract holds with the index                                                          |
| Scan once, then insert, update and delete keys in later transactions, then scan again                                                                | Commits keep the index complete (I1)                                                            |
| Scan twice. Count the RocksDB iterators that each scan opens (a counting `RawDbAccess` wrapper)                                                      | The second scan opens no iterator                                                               |
| An update of an existing key during a scan from a second context                                                                                     | `version` does not change, and the scan does not switch                                         |
| `exists` and `get` of an absent key in a loaded column family                                                                                        | No RocksDB read happens, and the result is absent                                               |
| Load race: start a load, commit to the same column family from a second context before the install                                                   | The load is discarded, and the next scan loads again and sees the new key                       |
| Three discarded loads in a row                                                                                                                       | The index is disabled, and scans still return the same results as RocksDB                       |
| Load a column family with more keys than the cap. Separately, grow a loaded column family past the cap through commits                               | The index is disabled, and results match RocksDB                                                |
| A second context inserts and deletes keys while a scan is between two keys (pause the visitor with a latch)                                          | The scan switches. It returns no key twice, skips no key, and each returned value was committed |
| Own writes during an indexed scan: insert and delete while iterating (as `ColumnFamilyContractTest#shouldDeleteOnForeachPair` does)                  | Own writes still merge live                                                                     |
| An indexed scan where all values were evicted from the cache                                                                                         | After 4 RocksDB reads the cursor switches to an iterator, and results match RocksDB             |
| Reverse scans (`whileTrueReverse`) over a loaded column family                                                                                       | Reverse order and the reverse switch match RocksDB                                              |
| A column family that is not configured                                                                                                               | It uses the merged scan only, and no `KeyIndex` exists for it                                   |

## 8. Files to change

- `WriteBehindStore`: hold the `KeyIndex` map. Change `commit`, `get`, `exists` and `openView`. Add
  the load. Add test counters: iterators opened, loads, discarded loads, disables, indexed scans,
  switches.
- `MergingCursor`: Phase 1 refactor and source selection.
- New: `KeyIndex`, `ViewSource`, `IndexSource`, and the source interface.
- `RawKey`: column family helper.
- `WriteBehindConfiguration`: the two new properties.
- `WriteBehindColumnFamily`: trigger the load before it creates a cursor.
- `zeebe/broker`: `WriteBehindCfg` and `LegacyWriteBehindTest` (Phase 4 only).
- Tests as in section 7.

Do not change `zeebe-db-api`, `zeebe-db-rocksdb` or the engine's main code.

## 9. Build, format and commit rules

Use Gradle. Do not use Maven.

```bash
# fast loop
./gradlew :zeebe-db-writebehind:test
./gradlew :zeebe-db-tests:test --tests 'io.camunda.zeebe.db.contract.WriteBehind*'
./gradlew :zeebe-db-tests:test --tests 'io.camunda.zeebe.db.contract.RocksDbVsWriteBehind*'

# before each commit
./gradlew :zeebe-db-writebehind:spotlessApply :zeebe-db-tests:spotlessApply
./gradlew :zeebe-db-writebehind:test :zeebe-db-tests:test
./gradlew testClasses          # whole-repo compile; never run ./gradlew build

# engine suite against the write-behind DB (Phase 4)
ZEEBE_DB_WRITE_BEHIND_ENABLED=true ./gradlew :zeebe-engine:test --no-configuration-cache
```

Check the engine and broker project names in `settings.gradle.kts` before you run them. Run
`spotlessApply` for every project that you change.

Commits:

- Conventional Commits, no scope, header of 120 characters or fewer. Example:
  `feat: scan hot column families from an in-memory key index`.
- Each commit has a body, wrapped at about 72 columns. Put the problem first, then the approach
  and why you chose it.
- Keep the Phase 1 refactor in its own `refactor:` commit.
- Do not add a `Co-Authored-By` line or any other AI attribution.
- Commit only the files that this work changes.
- Push to `cs/rdbms-cache-own-impl`. Do not force-push. Do not open a pull request.

Code style:

- Google Java Format through Spotless.
- New classes are package-private and `final`. Use jspecify `@Nullable`. The package is already
  `@NullMarked`.
- Write a comment only when a reader would do something different after they read it.

## 10. When to stop and report

Stop, commit what passes, and write a short report in the final message if:

- A decision in section 3 cannot work. Name the decision and show the failing case.
- A contract test or the differential test fails, and the cause is not in the key index.
- The engine suite fails with an empty `keyIndexColumnFamilies` too. List these as existing
  failures.

The final message lists the commits, the test results per phase, and every deviation from this
document.

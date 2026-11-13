# Write-behind cache for ZeebeDb: PR stack tracker

Living notes to resume work across sessions. Last update: 2026-11-12.

## Source material

- Workshop branch (source of truth): `origin/cs/write-behind-generic-core` (tip `a39ea1d9e04`).
  - Branched from `poc-single-storage-workshop-baseline` (`237f417f5cd`), NOT from `main`.
  - 183 commits, 628 files: mixes the cache with workshop work (RDBMS log, engine-state-rdbms,
    load tests, JDBC ZeebeDb). Only the zb-db parts and their direct needs come over.
- Design docs on that branch:
  - `zeebe/zb-db/docs/write-behind-cache-design.md`: RocksDB-backed write-behind, key index (sec 5).
  - `zeebe/zb-db/docs/write-behind-rdbms-engine-state.md`: final store design (Batches
    active/sealed, version chains, flushEpoch, evict-on-delete) used by the RDBMS integration.
  - Also: `engine-state-iteration.md`, `key-index-implementation.md`, `zeebe/zb-db/issues/*`.
- Dev branch here: `claude/compassionate-albattani-ja4on0` (camunda/camunda), based on `main`
  `a0761b3ef`.

## Branch layout of zb-db (target)

| Module | Artifact | Content |
|---|---|---|
| `zeebe/zb-db` | `zeebe-db` (now `pom`) | parent |
| `zeebe/zb-db/api` | `zeebe-db-api` | interfaces, key/value types, `DbIterator`, `RawDbAccess`, `RawAccessibleDb`, `ZeebeTransaction`, `TransactionListener`, `DbKeyInstanceFactory` |
| `zeebe/zb-db/rocksdb` | `zeebe-db-rocksdb` | RocksDB impl, `RocksDbRawAccess`, `RocksDbIterator` |
| `zeebe/zb-db/writebehind` | `zeebe-db-writebehind` | the cache (`Backend<K,V>`, `RawBackend`, `WriteBehindStore`, `AsyncFlusher`, ...) |
| `zeebe/zb-db/tests` | `zeebe-db-tests` | contract tests + differential tests vs RocksDB |
| `zeebe/zb-db/jdbc` | - | RDBMS-only, OUT OF SCOPE |

## Notable cross-module changes on the branch

- `DbValue extends Copyable<DbValue>` (new `zeebe/util/.../Copyable.java`): values stay objects
  in memory. Touches msgpack-value (`UnpackedObject`, `BaseValue` & subclasses, `BaseProperty`),
  protocol-impl (`UnifiedRecordValue`, `AuthInfoProperty`) and ~86 engine `Db*`/`Persisted*`
  value classes.
- `TransactionContext` gains `addTransactionListener`, `markAsReplaying` (default). The latter is
  RDBMS-only.
- `WriteBehindConfiguration` hardcodes key-index policies by `ZbColumnFamilies` (depends on
  zeebe-protocol).

## Decisions (2026-11-12, from Carlo)

1. **No `Copyable` on `DbValue`.** That API change comes with camunda/camunda#69223 (generated
   msgpack codecs, faster `copyTo`). The cache keeps the copyTo *concept* internally (values stay
   objects), but implements copy via the underlying buffer: encode source -> `wrap` into target.
   New instances: reflective no-arg ctor, cached per class (like `DbKeyInstanceFactory` default
   path). No change to `DbValue`, msgpack-value, protocol-impl or engine value classes.
2. **Artifacts:** `zeebe-db` stays the RocksDB artifact (no downstream coordinate change). Parent
   pom becomes `zeebe-db-parent`; new `zeebe-db-api` (+ later `zeebe-db-writebehind`,
   `zeebe-db-tests`).
3. **Key-index policies are caller-supplied** (`Map<Long, KeyIndexPolicy>`); zb-db ships no
   `ZbColumnFamilies` defaults and does not depend on zeebe-protocol for them.
4. **Branches:** stacked `cs/wb-1-zb-db-api`, `cs/wb-2-write-behind-store`,
   `cs/wb-3-key-index-windows` (each based on the previous). This tracker lives only on
   `claude/compassionate-albattani-ja4on0`, never in a PR.
5. Defaults (not objected): drop `markAsReplaying`, `jdbc` module, Bloom filter / RDBMS bits;
   `Backend#knownAbsent` stays as a default-false hook; metrics + contract/differential tests in
   PR 2; key index (KEEP_LOWEST/KEEP_HIGHEST/DISABLE) entirely in PR 3.
6. **R1 (2026-11-12): hold the Java object AND its encoded bytes** (Carlo: the cache must hold
   Java values, not only buffers). At put time, encode once into a fresh immutable `byte[]` and
   keep it next to the object, which wraps that same array. Flush writes the bytes (no encode on
   the writer thread, so no race on the shared msgpack writer). Weights use the real length.
   Copy-out into the caller's instance is `target.wrap(bytes)` until #69223 brings a fast
   `copyTo` from the held object. Handing the held object out directly is unsafe because callers
   mutate their instance. Risks R2-R4 get fixed in PR 2, one commit each.
7. **PR 1 scope (2026-11-12):** no byte-level `io.camunda.zeebe.db.ZeebeTransaction` and no rewiring
   of RocksDB's column families. On the workshop branch only RocksDB internals used it; the cache
   has its own typed `WriteBehindColumnFamily` (values = `DbValue` objects, keys = `RawKey` bytes)
   and `WriteBehindTransaction implements ZeebeDbTransaction`. PR 1 = module split + `DbIterator`,
   `RawDbAccess`, `RawAccessibleDb`, `RocksDbRawAccess`/`RocksDbIterator` + `DbNullKey` -> `impl`.
8. **Subagents:** use Sonnet for simpler tasks (mechanical ports, test moves, build fixes,
   formatting); keep the default model for concurrency-sensitive store work and reviews.
10. **`count()` transaction leak (camunda/camunda#68932) is fixed in a separate PR by Carlo.**
    Every subagent brief must say: do NOT touch `TransactionalColumnFamily.count()`/
    `countEqualPrefix()` and do NOT add `shouldRollBackFailedTransactionAfterCount`. If a stack
    PR turns out to depend on the fix, report it; we then add that fix PR to the stack (as a base)
    instead of duplicating it. **Done (2026-11-12):** camunda/camunda#69231 (branch
    `claude/eager-keller-t5jcx5`, `c38016b0ae1`) is now the stack base under PR 1. GitHub refuses
    to change #69232's base (native stack), so its diff vs main also shows the fix commit until
    #69231 merges; the PR body says so.
11. **Builds (2026-11-12):** subagents may build/test with Gradle (`./gradlew`, see the
    `gradle-run-build` skill) instead of Maven, and format with `./gradlew spotlessApply` (Java,
    markdown, sortPom). Gradle Spotless does NOT add license headers: new files need
    `./mvnw license:format` on their module (or a copied header). One Maven `-Dquickly` build of
    zb-db before finishing so pom wiring and dependency analysis are checked.
12. **PR 2b + order (2026-11-12):** `cs/wb-2b-engine-load-runner` ports the workshop
    `EngineLoadMain` (engine-only load runner, test sources, Gradle `engineLoad` task). It must
    never run by default (no test naming, task not wired to check/test/build, no CI change).
    Stack order: fix -> PR 1 -> PR 2 -> PR 2b -> PR 3 (PR 3 rebases onto 2b).
9. Commits authored as Carlo Sana <1188147+entangled90@users.noreply.github.com>, no Claude
   co-author line.

## Planned PR stack

1. **Module split + API** (`zeebe-db-api`, raw access SPI `RawDbAccess`/`DbIterator`/
   `RawAccessibleDb`/byte-level `ZeebeTransaction`, RocksDB impl of it). Contract test module
   against RocksDB. No behavior change.
2. **Write-behind store** (final Batches design, Caffeine cache, evict-on-delete, flush delay,
   metrics), generic over `Backend`, with `RawBackend` over `RawDbAccess`. No key index.
   Contract + differential tests.
3. **Key index with KEEP_LOWEST / KEEP_HIGHEST windows** (KeyIndex, IndexSource, HandoverSource).
- Detailed cut plan + risks R1-R5: `WRITE_BEHIND_PR2_PR3_PLAN.md`.
- Not in scope: enabling the cache in broker/engine, JDBC/RDBMS parts.

## Status

- [x] Baseline: main CI green, `./mvnw install -Dquickly -T1C` green on `a0761b3ef` (2026-11-12)
- [x] PR 1 pushed: `cs/wb-1-zb-db-api` on #69231 fix `c38016b0ae1`, draft PR camunda/camunda#69232
  - `661bcae9419` refactor: split zeebe-db into an API module and the RocksDB implementation
  - `c9df0d16497` refactor: move DbNullKey to the API module
  - `c509645bafa` feat: expose the committed RocksDB state through a raw access SPI
  - `9e70c8ace44` test: run the ZeebeDb behavior tests as contracts in zeebe-db-tests
    (fix's regression test now lives in `DbTransactionContractTest`)
  - Layout: `zeebe-db-parent` (pom), `zeebe-db-api`, `zeebe-db` (RocksDB, dir `rocksdb`, Gradle
    `:zeebe-db`), `zeebe-db-tests`. TransactionListener NOT ported. DbKeyInstanceFactory ported
    (unused yet, tested).
  - Outside zb-db: parent/pom.xml dependencyManagement; settings.gradle.kts; ci.yml unit-test
    module list (+`:zeebe-db-api`, `:zeebe-db-tests`); broker, debug-cli, microbenchmarks
    declare zeebe-db-api; **engine, stream-platform, backup: zeebe-db -> test scope** (their main
    code only uses API classes; dependency analysis demanded it). Consumers of the engine artifact
    no longer get the RocksDB jar transitively. Mention in PR description.
  - Validated: zb-db full verify without -Dquickly (462 + 111 tests), randomized profile, full repo
    install -Dquickly, downstream dependency analysis, Gradle compile of zb-db projects.
  - Found on main, NOT fixed (behavior change, separate fix PR?): `count()`/`countEqualPrefix()`
    outside a transaction leaves the context's transaction open (tracked in camunda/camunda#68932); a later runInTransaction neither
    commits nor rolls back, so a failed insert stays visible. Workshop branch had a fix + test
    `shouldRollBackFailedTransactionAfterCount`.
- [x] PR 2/3 cut plan done; R1 decided (encoded bytes)
- [x] PR 2 pushed: `cs/wb-2-write-behind-store` on PR 1 `9e70c8ace44`, draft PR camunda/camunda#69234
  - 12 commits ..`a55cbe190c5` (pre-restack tip was `1b77e84a04a`): module, DbEnumValue#getEnumType (additive), held
    values (R1), store + flusher, ZeebeDb, tests, contract tests, R1 test, R2/R3/R4 fixes.
  - Deviations: `Backend<K, V, T>` (3 type params); lambda-free column family (begin/commitIfBegun);
    metrics via agrona Long2ObjectHashMap; cache weight = bytes + 200 overhead.
  - `RocksDbVsWriteBehindDifferentialTest` committed as the last PR 2 commit (`a55cbe190c5`),
    300 tries green. NOTE: differential/property tests are tagged `randomized` and are skipped
    unless Gradle gets `-PincludeRandomTests` (Maven: `-Pinclude-random-tests`).
  - `c3da3c4ed3a` perf: stop waking the write-behind writer on every commit (2026-11-13).
    Agrona ManyToOneConcurrentLinkedQueue; writer parks until the oldest commit is due
    (idle park = max(flushDelay, 1ms)); only threshold/snapshot/close unpark it. Delay 0
    now means "within 1ms". Profile: commit->unpark samples 198 -> 4.
    PR 2b/3 rebased on it: PR 2b `d95d6ce61d0`, PR 3 `c6ba067f66b`.
  - Follow-ups: seek copies dirty range into a TreeMap; put does a full decode, miss decodes twice;
    weight ignores decoded heap size.
- [x] PR 2b pushed: `cs/wb-2b-engine-load-runner` on PR 2 `a55cbe190c5`, draft PR camunda/camunda#69247
  - `ded4ea9e60c` test: let engine tests run on the write-behind DB (env toggles, test dep)
  - `b5aa611f5b7` test: add a plain-main engine load runner (EngineLoadMain, TestEngine helpers,
    Gradle `engineLoad`; env forwarded via providers for the configuration cache)
  - Verified not run by default (Gradle dry runs, Maven 0 tests). Smoke 5000 instances:
    rocksdb 383/s, write-behind 433/s.
- [x] PR 3 pushed: `cs/wb-3-key-index-windows` on PR 2b `b5aa611f5b7`, draft PR camunda/camunda#69254
  - 8 commits `dcf31e358a2`..`41bb24bb7d2` (backend group range refactor, KeyIndexPolicy +
    config, absent-key lookups, indexed scans, windows past bound, tests, contract variants,
    3 differential tests DISABLE/KEEP_LOWEST/KEEP_HIGHEST).
  - Index stamps last-changing commit seq; point reads trust it only if not newer than their
    read seq (deterministic interleaving test).
  - New test dep `junit-jupiter-params` in writebehind pom (Maven only failed; Gradle adds it).
  - Follow-ups: refill on processing thread (bounded maxKeys/4); IndexSource gives up after 4
    storage reads; ~100-150 B/key.
  - `810b71f8d25` test: let the engine load runner use the write-behind key index.
    `ZEEBE_DB_KEY_INDEX` (unset/default = workshop 23-CF set, `none`, or `CF[:POLICY],...`),
    `ZEEBE_DB_KEY_INDEX_MAX_KEYS`; Gradle `-PkeyIndex`/`-PkeyIndexMaxKeys`. Write-behind only.
  - First run (4 CPU, 50k instances, inst/s): rocksdb 428/452, wb-none 467/520,
    wb-default 612/657. Prefill+revisit: 412 / 420 / 483. Key index: storage iterators
    1.6M -> ~620, 2.3M absent lookups answered from index, 0 disables/switches/handovers.
    Write-behind cuts RocksDB bytes written ~6x.
  - Open questions from the run: RocksDB get count inflated by flusher `keyMayExist` per
    delete; PROCESS_INSTANCE_KEY_BY_DEFINITION_KEY and CROSS_PARTITION_MESSAGE_START_HOLDER_ORIGIN
    still read storage; JOB_DEADLINES lookups above the low window; much higher block-cache
    misses with write-behind under revisit (unverified hypothesis: cold reads only).
  - `c7498c74312` test: let engine tests override RocksDB column family options
    (`ZEEBE_DB_ROCKSDB_CF_OPTIONS="name=value;..."`, test scope only).
  - Memtable whole-key bloom (`memtable_whole_key_filtering=true`), paired runs, inst/s
    off/on: 50k: rocksdb 458/505, wb-none 524/499, wb-default 651/669. Prefill+revisit:
    rocksdb 426/419, wb-none 423/440, wb-default (24 CF) 542/573. Cuts get time 20-40%
    at equal get count; throughput change within noise. Not proposed for production.
  - `2eebe385e6e` test: JOB_ACTIVATABLE_BY_PRIORITY:KEEP_LOWEST added to the default set
    (24 CFs). It caused ~500 of the 606 remaining storage seeks (tombstone scans, puts
    and deletes land in separate flush batches). 50k wb-default: seeks 606 -> 100,
    skipped keys 14.6M -> 0, seek time 2691 -> 2, 651 -> 694 inst/s.
  - Remaining: flush delay defaults to 0 in engine tests (no coalescing, ~700k real
    deletes); PR 4 candidate: in-memory Bloom filter over all CFs (design open).
  - Flush delay sweep with the non-blocking flusher (50k + prefill + revisit, wb-default,
    inst/s; old flusher delay 0 = 522, 5s = 501): 0 -> 569 (563 profiled), 100ms -> 591,
    1s -> 537. Flushes 207k (old 0) / 115k / 1.4k / 156; filter misses 113 / 12k / 17k / 30k.

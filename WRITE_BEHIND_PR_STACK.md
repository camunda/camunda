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
    instead of duplicating it.
11. **Builds (2026-11-12):** subagents may build/test with Gradle (`./gradlew`, see the
    `gradle-run-build` skill) instead of Maven, and format with `./gradlew spotlessApply` (Java,
    markdown, sortPom). Gradle Spotless does NOT add license headers: new files need
    `./mvnw license:format` on their module (or a copied header). One Maven `-Dquickly` build of
    zb-db before finishing so pom wiring and dependency analysis are checked.
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
- [x] PR 1 pushed: `cs/wb-1-zb-db-api` on main `a0761b3ef`, draft PR camunda/camunda#69232
  - `4cc471f9543` refactor: split zeebe-db into an API module and the RocksDB implementation
  - `8ba50341492` refactor: move DbNullKey to the API module
  - `9c1e3f4d5fd` feat: expose the committed RocksDB state through a raw access SPI
  - `b2121d1b6a0` test: run the ZeebeDb behavior tests as contracts in zeebe-db-tests
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
- [ ] PR 2: subagent running on `cs/wb-2-write-behind-store` (based on PR 1, started 2026-11-12)
- [ ] PR 3

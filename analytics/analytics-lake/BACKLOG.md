# analytics-lake backlog

Deferred follow-ups from the 2026-07-22 review fix batch (see the commit "fix: review findings —
partition-safe file window, commit locking, frontier carry-forward, identity sort keys"). Each entry
states what's deferred, why, what should trigger picking it up, and a rough fix sketch.

## 1. Concurrent multi-partition + compactor race coverage beyond the smoke test

**What**: `LakeCompactorConcurrencyTest` (added in Fix 3) proves the commit-lock fix doesn't
regress under *some* concurrent interleaving — two committer threads plus a compaction loop,
bounded iterations, no real Parquet I/O. It is a smoke test, not a stress test: it doesn't force
the data-file rewrite path (needs > `DATA_FILE_COMPACTION_THRESHOLD` real files), doesn't inject
timing to land a commit mid-rewrite, and runs a fixed, small iteration count.

**Why deferred**: writing real Parquet files for every descriptor to cross the compaction
threshold, plus deliberately racing commit timing, is a meaningfully bigger test (and slower) than
a PoC-scale smoke test justifies before the underlying lock design has had any real usage.

**Trigger**: before this PoC graduates past single-node-demo scale, or if any lock-related bug
report ever surfaces in practice.

**Fix sketch**: extend the concurrency test (or add a new one) that writes real per-descriptor
Parquet files (reusing `IcebergParquetEncoderFactory`/`LocalFileSink`) so the data-file rewrite path
actually triggers under contention, and interleave a few `Thread.yield()`/`CountDownLatch` barriers
timed to land mid-rewrite.

## 2. End-to-end backpressure test through real wiring

**What**: `SinkPipelineBackpressureTest` and friends exercise backpressure at the unit level
(fake gate, fake sink); the translator's own retry loop under a genuinely full ring, wired through
real `SinkPipeline`s end to end, is not covered by an integration-level test.

**Why deferred**: the unit-level coverage already pins the contract (`begin()` returning false,
retry-without-advancing); a full end-to-end version mostly re-proves the same thing at higher cost
and flakiness risk (real timing, real threads).

**Trigger**: any backpressure-related bug that unit tests failed to catch, or before backpressure
tuning (ring sizing, gate thresholds) becomes a supported knob for real deployments.

**Fix sketch**: extend `SinkIntegrationTest` with a small-ring configuration that is guaranteed to
fill, feeding faster than the flush thread can drain, and assert the translator's retry loop
eventually converges with zero dropped rows.

## 3. Hardcoded byte-estimate / avg-bytes-per-row config knobs; binary-arena exhaustion kills the pipeline

**What**: `FileWindow`'s `BYTES_PER_ROW_ESTIMATE` (200) and `LakePocApp.VARS_JSON_AVG_BYTES_PER_ROW`
(512) are hardcoded constants, not configurable. If a process's `vars_json` payload exceeds the
budgeted arena size, `HeapBinaryColumn#set` throws `IllegalStateException` and (per `FlushLoop`'s
failure handling) that terminates the whole pipeline for that partition permanently.

**Why deferred**: no real workload has exercised this limit yet; picking good defaults without
data would be guessing, and a config surface for numbers nobody has needed to tune yet is
premature.

**Trigger**: the first time a real (or realistic-load-test) workload hits the arena-exhaustion
`IllegalStateException`, or before this PoC takes on any workload with unusually large instance
variable payloads.

**Fix sketch**: expose both as `SinkConfig`/`LakeConfig` fields with the current values as
defaults; separately, consider a degrade-to-flush path (finish the current file early and open a
fresh one with a clean arena) instead of a hard failure — needs design, since it changes file
boundaries mid-window.

## 4. `RocksDbTranslatorState.variablesOf` allocates a `LinkedHashMap` + strings per completed instance

**What**: draining an instance's variables into the row-append path allocates a fresh map and
per-entry strings, once per completed instance (see Fix 4's own doc note on this being a budgeted,
completion-rate-bounded exception, not a record-rate one — but still real garbage).

**Why deferred**: bounded by completion rate already, and the engine's own key-only/garbage-free
drain pattern (referenced as the fix) is a bigger lift than this PoC's variable-payload-serialization
path currently justifies.

**Trigger**: profiling shows this drain as a meaningful GC contributor at realistic completion
rates.

**Fix sketch**: port the engine's key-only iteration + garbage-free value read pattern instead of
materializing a `Map<String, String>` per completed instance.

## 5. Rung 1.5 encoder — drive parquet-java column writers directly

**What**: `BatchRowView`'s flyweight `Record` cursor still boxes every plain `LONG`/`INT` value
through `Record.get(int, Class)`'s `Object` return (see B5's javadoc addendum — ~5 MB Eden per
16k-row flush, measured).

**Why deferred**: rung 1 (iceberg-parquet's generic writer via `BatchRowView`) is correct and
simple; rung 1.5 (driving parquet-java's column writers directly, no `Record` indirection) removes
the boxing but is a materially larger, riskier implementation.

**Trigger**: flush-thread allocation shows up materially in profiles at realistic throughput —
this is flush-thread garbage, not poll-thread, so it is not urgent until profiling says otherwise.

**Fix sketch**: implement a `BatchEncoder` that drives `parquet-java`'s typed column writers
(`LongColumnWriter`, etc.) directly off a `SortedRun`'s primitive accessors, behind the same
`BatchEncoder`/`BatchEncoder.Factory` interface so `DayRouter`/`FileWindow`/`FlushLoop` need no
changes.

## 6. Declare Iceberg `SortOrder` table metadata + `DataFiles.withSortOrder`

**What**: the raw tables' Iceberg `Table` metadata carries no declared `SortOrder`, even though
every data file this sink writes is, in fact, sorted by the schema's sort key (see
`RawTableSchemas`/`SegmentSorter`). External engines (Spark, Trino, DuckDB) get no free hint that
files are pre-sorted.

**Why deferred**: no external engine consumes these tables yet at this PoC stage; declaring sort
order metadata without a consumer to benefit from it is speculative.

**Trigger**: the L2 merge implementation (multi-file, sort-order-aware merge) — at that point the
sink's own compaction/merge logic can also start relying on the declared order, not just external
readers.

**Fix sketch**: call `UpdateSortOrder` once at table creation (mirroring the partition-spec
declaration in `IcebergLakeWriter#tableOrCreate`), and pass `withSortOrder(...)` on every
`DataFiles.builder(...)` call in `DirectCommitSink`/`IcebergLakeWriter`/`LakeCompactor`.

## 7. Interner adversarial-collision audit

**What**: `Interner`'s dictionary-encoding hash/equality path has not been audited against
adversarial input (e.g. many distinct strings engineered to collide) beyond ordinary correctness
tests.

**Why deferred**: today's dictionary-valued columns are all closed, small-cardinality, engine-owned
strings (process ids, element ids, states) — not user-controlled input — so there is no realistic
adversarial input surface yet.

**Trigger**: any feature that lets a user-defined value flow into a dictionary-encoded column
(e.g. a user-defined dataset/report column backed by `STRING_DICT`).

**Fix sketch**: a targeted test with strings chosen to collide under the interner's hash function,
verifying correctness (not just performance) holds under collision-heavy input.

## 8. Ring capacity power-of-two mask

**What**: `ColumnarSegmentRing`'s index arithmetic could use a power-of-two capacity + bitmask
instead of modulo, a minor cosmetic/performance cleanup.

**Why deferred**: purely cosmetic at current scale; not worth a standalone change.

**Trigger**: never on its own — fold into the next change that already touches
`ColumnarSegmentRing`'s indexing.

**Fix sketch**: require `ringSegments` be a power of two (or round up internally) and replace `%`
with `& (capacity - 1)`.

## 9. Radix sort evaluation for `SegmentSorter`

**What**: `SegmentSorter` uses a median-of-three quicksort; a radix sort (keying off the now
identity-only sort columns) was floated as a possible throughput win.

**Why deferred**: Fix 5's identity-only sort keys (`(process_id, instance_key)` /
`(process_id, instance_key, element_key)`) make the sorter's equal-keys worst case unreachable
(see `RawTableSchemas`' updated javadoc), which both improves the quicksort's own worst-case
behavior and shrinks the expected win from switching algorithms — the case for radix sort is
weaker post-Fix-5 than it was when first floated.

**Trigger**: measure quicksort's actual cost at realistic segment sizes/throughput first; only
pursue radix sort if profiling shows sorting as a bottleneck even with the equal-keys pathology
gone.

**Fix sketch**: N/A until the measurement above justifies it — do not implement speculatively.

## 10. Swap hadoop-common+exclusions for the shaded hadoop-client-api artifact
- **What**: replace `org.apache.hadoop:hadoop-common` (with its long exclusion list) by
  `org.apache.hadoop:hadoop-client-api` — the purpose-built shaded artifact with no transitive
  graph, carrying `Configuration` and friends. Likely sufficient alone for iceberg-parquet's
  settings-bag usage; verify the encoder test suite + a smoke pass.
- **Why deferred**: the exclusion approach is empirically green (61/61 + review); swapping mid-smoke
  churns the classpath for no functional gain today.
- **Trigger**: next pom-touching change, or the first exclusion-related runtime surprise.
- **Fix sketch**: dependency swap + delete exclusions; if something misses an impl class, add
  hadoop-client-runtime (also shaded) rather than reverting to exclusions. Long-term this whole
  block dies when iceberg-parquet adopts ParquetConfiguration (watch release notes).

11. **Multi-writer-safe data file names.** File names are `<seq>.parquet` with the sequence
    clock-seeded per process — unique only under the current single-process deployment. Two app
    instances (consumer group scale-out) write the same tables from different machines: clock skew
    and partition-ownership handover both break clock-seeded uniqueness. Before multi-node, add a
    per-writer component to the name (short random suffix or fenced writer id, the standard
    Iceberg-writer approach). See IcebergParquetEncoderFactory's naming javadoc.

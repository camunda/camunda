# Analytics Lake (PoC)

A learning proof of concept: a small translator that reads Zeebe records off the Event Bridge,
folds open process/element state in RocksDB, and lands two finished-row tables — `instances` and
`activities` — as a local Iceberg lakehouse. Parquet files are written by an embedded DuckDB
connection; iceberg-core commits them to the table (catalog: `JdbcCatalog` over a local H2 file
database). See `src/main/java/io/camunda/analytics/lake/write/IcebergLakeWriter.java` for the
heavily-commented walkthrough of how the two pieces fit together and exactly which iceberg-core and
DuckDB APIs are involved.

This is a PoC: unpartitioned tables, one file per flush, no retention, no authentication on the
H2/DuckDB side. A periodic compaction pass (see "Compaction" below) keeps the one-file-per-flush
pattern from accumulating indefinitely, but it is not a substitute for retention. None of the rest
is an oversight — see the javadoc on `IcebergLakeWriter` and `LakeConfig` for what's deliberately
deferred and why.

## Running it against a local stack

You need a running Event Bridge gateway with a `zeebe-records` topic being fed by a Zeebe exporter
(the same stack the rest of `analytics/` runs against — see `analytics/run-stack-demo.sh`).

### System properties

| Property                 | Default            | Meaning                                             |
|---------------------------|--------------------|------------------------------------------------------|
| `lake.contactPoint`       | `http://localhost:8080` | Event Bridge gateway base URL (needs the scheme)  |
| `lake.topic`              | `zeebe-records`    | Topic carrying Zeebe records                          |
| `lake.group`              | `lake-poc`         | This translator's own consumer group                  |
| `lake.dir`                | `./data/lake`      | Iceberg warehouse directory (catalog DB + data files) |
| `lake.flushRows`          | `5000`             | Flush the buffer at this many buffered records        |
| `lake.flushIntervalMs`    | `2000`             | Flush at least this often while rows are buffered     |
| `lake.stateDumpIntervalMs`| `30000`            | Dump the open translator state to Parquet at least this often; `0` disables it |
| `lake.compactIntervalMs`  | `300000`           | Run a compaction pass (see "Compaction" below) at least this often; `0` disables it |
| `lake.uiPort`             | `8091`             | Port for the embedded demo UI (see "Demo UI" below); `0` disables it |
| `lake.bpmnDir`            | _(none)_           | Explicit directory to scan for `.bpmn` files for the `/process-map` page (see "Process map" below); unset means "use the default resolution order" |

The RocksDB translator state directory is not independently configurable in this PoC; it's always
`./data/lake-state` (relative to the JVM's working directory), a sibling of the warehouse
directory's default.

### A required JVM flag

RocksDB access (via Agrona's `UnsafeBuffer`, used by the `event-bridge-streaming` state library
`RocksDbTranslatorState` builds on) needs the JVM's internal `Unsafe` class opened up:

```
--add-opens java.base/jdk.internal.misc=ALL-UNNAMED
```

Without it you'll see `IllegalAccessError`/module-access failures out of `org.agrona.UnsafeApi`
before the app gets anywhere near the Event Bridge. This repo's `.mvn/jvm.config` does not
currently include this flag, so it has to be supplied explicitly for either run method below.

### Option 1 — `java -cp` (recommended; most direct control over JVM flags)

```bash
# from the repo root, after `./mvnw install -pl analytics/analytics-lake -am -Dquickly`
./mvnw -pl analytics/analytics-lake dependency:build-classpath -Dmdep.outputFile=/tmp/lake-cp.txt -q

java --add-opens java.base/jdk.internal.misc=ALL-UNNAMED \
  -cp "analytics/analytics-lake/target/classes:$(cat /tmp/lake-cp.txt)" \
  -Dlake.contactPoint=http://localhost:8080 \
  -Dlake.topic=zeebe-records \
  -Dlake.group=lake-poc \
  -Dlake.dir=./data/lake \
  io.camunda.analytics.lake.LakePocApp
```

### Option 2 — `exec:java`

The `exec-maven-plugin` is available (managed centrally, no extra `pom.xml` config needed for
this module). Because `exec:java` runs the target class **inside Maven's own JVM** rather than
forking a new process, the `--add-opens` flag above has to reach that JVM via `MAVEN_OPTS`, not
via `-D` system properties:

```bash
MAVEN_OPTS="--add-opens java.base/jdk.internal.misc=ALL-UNNAMED" \
./mvnw -pl analytics/analytics-lake exec:java \
  -Dexec.mainClass=io.camunda.analytics.lake.LakePocApp \
  -Dexec.classpathScope=runtime \
  -Dlake.contactPoint=http://localhost:8080 \
  -Dlake.dir=./data/lake
```

Stop either with `Ctrl-C`: the shutdown hook flushes any buffered rows, then closes the RocksDB
state, the Iceberg writer (DuckDB connection + catalog), and the Event Bridge client/consumer.

## Where files land

Given the default `lake.dir=./data/lake`:

```
data/
├── lake/
│   ├── catalog.mv.db                        # H2 file database backing JdbcCatalog
│   └── lake/                                # the "lake" namespace
│       ├── instances/
│       │   ├── metadata/                    # Iceberg table metadata JSON, manifests, manifest-lists
│       │   └── data/
│       │       └── p<partition>-<from>-<to>.parquet   # one file per flush with buffered rows
│       └── activities/
│           ├── metadata/
│           └── data/
│               └── p<partition>-<from>-<to>.parquet
└── lake-state/                              # RocksDB: open (unfinished) instances/elements/variables
    └── _snapshot/                            # periodic Parquet dump of the open state (see below)
```

`<partition>` is the Event Bridge source partition the flush was for; `<from>`/`<to>` are the
(inclusive) source-topic offset range folded into that file — informational only, not read back by
anything.

## Inspecting live state

Alongside the finished-row lake tables, the translator periodically dumps its *open* (unfinished)
RocksDB state — `lake-state/_snapshot/open_instances.parquet` and
`lake-state/_snapshot/open_elements.parquet` — controlled by `lake.stateDumpIntervalMs` (default
`30000`, `0` disables it). An `offsets.json` alongside them stamps the per-partition offset the
dump is consistent with. This is a live, queryable view of "what's running right now":

```sql
SELECT process_id, count(*)
FROM read_parquet('data/lake-state/_snapshot/open_instances.parquet')
GROUP BY ALL;
```

## Compaction

The writer's one-file-per-flush pattern means small Parquet files (and small manifests, and
snapshots) accumulate over time. `io.camunda.analytics.lake.write.LakeCompactor` runs a periodic
compaction pass, on the same poll-loop thread as the flush/state-dump checks (never concurrently
with a commit), assembled from three plain iceberg-core primitives plus one DuckDB rewrite:

1. **Data-file compaction** — once a table's live data-file count exceeds a threshold (20), DuckDB
   reads every live file back with `read_parquet([...])`, sorts it (`process_id, start_ms` for
   `instances`; `process_id, instance_key, start_ms` for `activities`), and writes one new
   compacted Parquet file. The row count is verified against the files it replaces before iceberg
   registers the swap via `Table#newRewrite()` — a mismatch aborts just that table's pass (logged,
   not thrown).
2. **Manifest consolidation** — `Table#rewriteManifests()` clusters everything into a single
   manifest, so the many small manifests one-file-per-flush produces don't pile up indefinitely.
3. **Snapshot expiry** — `Table#expireSnapshots()` drops all but the last 3 snapshots, which also
   physically deletes the manifests, manifest lists, and now-unreferenced data files (including
   ones the rewrite just replaced) through the table's `FileIO` — this is why `LocalFileIO` has to
   implement `deleteFile`, not just read/write.

This is the assembled-from-primitives equivalent of a managed lakehouse's periodic `CHECKPOINT`:
nothing here is a single iceberg-core call, but the same three effects (rewrite small files, merge
manifests, drop old snapshots) are what a managed table format does automatically under the hood.
Table properties `write.metadata.delete-after-commit.enabled=true` and
`write.metadata.previous-versions-max=5` are set on construction (retroactively, on
already-existing tables too) so old `metadata.json` files — which `expireSnapshots` does not
remove — get cleaned up as well.

Controlled by `lake.compactIntervalMs` (default `300000`, `0` disables it). A crash between the
DuckDB rewrite and the iceberg commit that registers it leaves an orphaned, unreferenced compacted
file on disk — harmless (nothing reads it), and not swept automatically; a future orphan-file scan
is the natural follow-up.

## Demo UI

While the app is running, an embedded `io.camunda.analytics.lake.ui.LakeUiServer` serves a
minimal, self-contained web UI at `http://localhost:8091` (the `lake.uiPort` system property
controls the port; `0` disables it). It's built on nothing but the JDK's own
`com.sun.net.httpserver` — no new dependency — and owns its own read-only embedded DuckDB
connection, entirely separate from the writer's, so browsing the lake can never contend with or
block the translator's own flush path.

The page shows six canned query tiles:

- **Instances per process** — count, average and p95 duration per process/version.
- **Activities per element** — count and average duration per BPMN element.
- **Top 10 variants** — each instance's ordered element-id sequence, grouped and counted.
- **Bottleneck edges** — average gap between consecutive elements in the same instance.
- **Currently running per process** — from the periodic open-state snapshot (`open_instances`),
  not the finished-row tables.
- **Recent instances** — the last 20 finished instances by end time.

Plus a free-form SQL box that runs against the same `/api/query` endpoint. All queries run over
seven views (`instances`, `activities`, `open_instances`, `open_elements`, and the three gold-table
views `transitions`, `instance_kpis`, `element_bits` — see `GoldTables`) (re)created lazily on each
request — before the translator has flushed anything (or before the first compaction pass has
derived the gold tables), hitting a tile just returns a friendly "no data yet" error instead of a
server crash. Results are capped at 500 rows with a ~15s query timeout.

The `instances`/`activities`/`transitions`/`instance_kpis`/`element_bits` views are
**snapshot-consistent**, not a directory glob: the UI opens its own independent Iceberg catalog
handle (never the writer's own `Table`/catalog instances — see `LakeUiServer`'s javadoc for why
sharing those would be unsafe) and builds each view from `read_parquet([...])` over the exact file
list the table's *current snapshot* reports via `newScan().planFiles()`. This is what makes the UI
immune to the double-counting caveat described below for hand-written `read_parquet` globs:
compaction-retained old files (kept on disk by the 3-snapshot retention policy) and any orphaned
crash litter are invisible to a snapshot, so they never make it into the UI's view, even though
they're still physically present under `data/`. This matters just as much for the gold tables as for
the raw ones: `GoldTables#recompute()` wholesale-replaces each gold table's contents on every pass
(see that class's javadoc), so without the snapshot-consistent view a naive glob would double-count
every row after a second recompute. `open_instances`/`open_elements` stay plain single-file reads —
`StateSnapshotDumper` atomically replaces those files, so there is no Iceberg table (and no
compaction-retained old copy) to worry about there.

### Dashboard tiles from the gold tables

Three of the dashboard's tiles are derived from the gold tables rather than the raw `instances`/
`activities` tables, and render an empty-state caption (not an error) when the gold views exist but
have no rows yet:

- **Straight-through rate** — share of instances with `rework_count = 0`, overall and per process
  (from `instance_kpis`).
- **Bottleneck transitions** — top 8 directly-follows edges by average gap
  (`total_gap_ms / n`, excluding the synthetic `__START__`/`__END__` edges), with an optional
  process filter (from `transitions`).
- **Rework hotspots** — per-process share of instances with `rework_count > 0`, with average
  activity count for reworked vs. clean instances in the tooltip (from `instance_kpis`).

## Process map

`/process-map` renders a BPMN heatmap: node badges (execution count + average duration, from
`activities`) and edge badges/coloring (`n` + average gap, from `transitions`) overlaid on the
process's own BPMN diagram via [bpmn-js](https://github.com/bpmn-io/bpmn-js) (loaded from a pinned
CDN version — if that fails to load, e.g. offline, the page degrades to plain node/edge data tables
instead of a broken diagram; neither the data explorer nor the dashboard are affected either way).

BPMN XML is discovered from disk, not from a running engine's deployment — see
`io.camunda.analytics.lake.ui.BpmnCatalog`'s javadoc for the exact directory resolution order (an
explicit `lake.bpmnDir` override first; otherwise `/tmp/eb-demo`, this repo's `analytics/`
directory, and two further PoC-pragmatic fallbacks, in that order). Only processes whose BPMN
`<bpmn:process id="...">` matches a process id actually present in the data appear in the picker; if
none match, the page shows a hint listing the data's process ids and every directory that was
scanned.

The page's killer feature is its conformance cohort filter: "only instances that skipped/visited
element X", a dropdown populated from `element_bits` and applied as a bitmask predicate
(`elements_seen & (1 << bit)`) on `instance_kpis`, joined via `instance_key` into `activities` so
node badges recompute for exactly that cohort. The edge layer is **not** cohort-filtered — `
transitions` is already pre-aggregated across every instance, so the page marks it "all instances"
rather than silently mixing cohort-filtered nodes with all-instance edges. See
`io.camunda.analytics.lake.ui.ProcessMapService`'s javadoc for the query logic in full.

## Demo queries

These run against the Parquet files with a local DuckDB CLI (`duckdb`) — no server, no cluster.
Two ways to point DuckDB at the data:

- **Iceberg-aware** (reads the catalog's current snapshot, so it reflects exactly what's
  committed — no half-written or superseded files):
  ```sql
  INSTALL iceberg;
  LOAD iceberg;
  SELECT * FROM iceberg_scan('data/lake/lake/instances') LIMIT 5;
  ```
  The path is the table's own directory (the one containing `metadata/` and `data/`), not the
  warehouse root. Iceberg's own location tracking in this PoC uses `file:` URIs internally (see
  `LocalFileIO`'s javadoc) — if `iceberg_scan` on a bare path is finicky in your DuckDB build, the
  `read_parquet` fallback below always works and needs no extension.

- **Plain Parquet glob** (reads every data file directly — this can double-count rows once
  compaction is enabled, since a superseded file isn't physically removed until a later
  compaction pass's `expireSnapshots` call runs, and any orphaned crash litter is picked up too;
  prefer `iceberg_scan`/the catalog's current snapshot for anything that needs to be correct, not
  just quick):
  ```sql
  SELECT * FROM read_parquet('data/lake/lake/instances/data/*.parquet') LIMIT 5;
  ```
  This is exactly the bug the embedded demo UI (see "Demo UI" above) does *not* have: its
  `instances`/`activities` views are built from the table's current-snapshot file list, not a
  directory glob, so browsing through the UI is always double-count-free. The caveat here only
  applies to hand-written queries against the raw files, like the ones in this section.

All the snippets below use the `read_parquet` glob form; swap in `iceberg_scan(...)` on the table
directory if you prefer. **`start_ms`/`end_ms`/`duration_ms`/`instance_start_ms` are epoch
milliseconds, not a timestamp type** (see `IcebergLakeWriter`'s schema javadoc for why) — always
divide by 1000 before `to_timestamp(...)`.

The `activities` table's `instance_start_ms` column carries the owning process instance's start
time — the "family date" a future retention pass will use to partition and drop an instance and all
of its activities together atomically.

### 1. Completed instance count per process per day

```sql
SELECT
  process_id,
  version,
  date_trunc('day', to_timestamp(end_ms / 1000)) AS day,
  count(*) AS completed_count
FROM read_parquet('data/lake/lake/instances/data/*.parquet')
WHERE state = 'COMPLETED'
GROUP BY 1, 2, 3
ORDER BY 3, 1;
```

### 2. Process variants (ordered element-id sequence per instance)

```sql
SELECT
  process_id,
  version,
  string_agg(element_id, ' -> ' ORDER BY start_ms) AS variant,
  count(DISTINCT instance_key) AS instance_count
FROM read_parquet('data/lake/lake/activities/data/*.parquet')
GROUP BY 1, 2, 3
ORDER BY instance_count DESC;
```

### 3. Bottleneck edges (average gap between consecutive elements)

```sql
WITH ordered AS (
  SELECT
    instance_key,
    element_id,
    start_ms,
    end_ms,
    lead(element_id) OVER (PARTITION BY instance_key ORDER BY start_ms) AS next_element_id,
    lead(start_ms)   OVER (PARTITION BY instance_key ORDER BY start_ms) AS next_start_ms
  FROM read_parquet('data/lake/lake/activities/data/*.parquet')
)
SELECT
  element_id AS from_element,
  next_element_id AS to_element,
  count(*) AS transitions,
  avg(next_start_ms - end_ms) AS avg_gap_ms
FROM ordered
WHERE next_element_id IS NOT NULL
GROUP BY 1, 2
ORDER BY avg_gap_ms DESC;
```

### 4. Instances with element X but not element Y (anti-join)

```sql
WITH activities AS (
  SELECT * FROM read_parquet('data/lake/lake/activities/data/*.parquet')
)
SELECT DISTINCT x.instance_key
FROM activities x
WHERE x.element_id = 'X'
  AND NOT EXISTS (
    SELECT 1 FROM activities y
    WHERE y.instance_key = x.instance_key
      AND y.element_id = 'Y'
  );
```

### 5. Duration spread with exemplar witnesses (arg_min/arg_max)

Per-process min/median/p95/max duration, plus the actual instance `key` that produced the min and
the max — `arg_min`/`arg_max` return the row's other column at the position where the aggregated
expression is smallest/largest, so these two columns are concrete, look-up-able exemplars, not just
numbers:

```sql
SELECT
  process_id,
  count(*) AS n,
  quantile_cont(duration_ms, 0.5) AS median_ms,
  quantile_cont(duration_ms, 0.95) AS p95_ms,
  min(duration_ms) AS min_ms,
  arg_min(key, duration_ms) AS min_key,
  max(duration_ms) AS max_ms,
  arg_max(key, duration_ms) AS max_key
FROM read_parquet('data/lake/lake/instances/data/*.parquet')
GROUP BY process_id
ORDER BY p95_ms DESC;
```

### 6. Duration percentiles over time with exemplars

Same idea as #5, but bucketed by completion time instead of grouped by process — this is the query
behind the dashboard's "Duration over time" tile (bucket width and window are substituted
client-side from the tile's time-range toggle; `<process filter>` is only appended when a specific
process is selected):

```sql
SELECT
  time_bucket(INTERVAL '1 minute', to_timestamp(end_ms / 1000)) AS bucket,
  count(*) AS n,
  quantile_cont(duration_ms, 0.5) AS median_ms,
  quantile_cont(duration_ms, 0.95) AS p95_ms,
  min(duration_ms) AS min_ms,
  arg_min(key, duration_ms) AS min_key,
  max(duration_ms) AS max_ms,
  arg_max(key, duration_ms) AS max_key
FROM read_parquet('data/lake/lake/instances/data/*.parquet')
WHERE end_ms > (epoch(now()) - 900) * 1000
GROUP BY bucket
ORDER BY bucket;
```

A bucket with zero completions is simply absent from the result — there's no row to zero-fill, and
no percentile/min/max of an empty set — which is why the dashboard tile renders those as gaps in its
lines and band rather than fabricating a "0ms duration" data point.

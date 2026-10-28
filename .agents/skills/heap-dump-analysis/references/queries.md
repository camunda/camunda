# Writing queries for headless MAT

What bit us in headless MAT on 8.9 broker dumps, and how to get numbers out of the CSVs.

## MAT quirks we hit

- **One throwing expression kills the whole report.** MAT evaluates CSV columns lazily while
  it writes the file. An exception in any row aborts the pack (rc != 0). Rerun the suspect
  queries alone, each in its own pack.
- **A zero-row query writes no CSV.** A broken query writes none either, with rc 0 and
  nothing in the log; only the report HTML says `Problem reported`. `run_mat.sh` prints that
  count as `problems=N`.
- **No `$` in string literals** (`No unflagged parameters`). Use `.` in the regex instead of
  `\$`. A `$` in a class name (`LocalCache$Segment`) is fine.
- **`FROM OBJECTS` needs parentheses** around an expression: `FROM OBJECTS (x.field) r`.
- **Indexing a subquery needs `eval`**: `eval((SELECT ...))[0].v`.
- **`dominatorof(x)` can be null**: `classof(dominatorof(x))` then throws. Select
  `dominatorof(x).@objectId`.
- **500 rows** unless the section sets `<param key="limit" value="10000000" />`.
- **Names are cut to 20 characters** in page and zip names: a query named
  `unreachable_histogram` gives `unreachable_histogra3.csv`. Keep query names unique in
  their first 20 characters, and give each pack its own `filename_suffix`.
- **Numbers are quoted with commas** (`"2,099,546,568"`, with `run_mat.sh`'s English
  locale). Use `gawk` with `FPAT` and strip everything but digits.

## Reading the heap

- **Partition of an engine state:** follow the inbound reference from `ProcessingDbState`,
  which has `partitionId` (`engine_states`). Not the dominator: shared objects have none.
  Some `Db*State` aren't held by a `ProcessingDbState` and show an empty partition: 63 of
  180 on zeebe-13.
- **Guava caches:** `LocalCache` -> `segments[]`. Each segment has its own `count` and cap
  (`maxSegmentWeight`); compare each count with its cap. On zeebe-13, 84 of the 1167
  `drg_entries` rows had no `Segment` as dominator, and 2 were still loading (empty
  `retained`). To count entries per segment, use only rows whose `segId` matches a
  `cache_segments` row.
- **Threads:** MAT writes the stacks to `<stem>.threads` next to the dump. Never print its
  lines; count threads per pattern with `scripts/count_threads.sh <stem>.threads <pattern>...`.
  We used `ReplayStateMachine`, `DecisionRequirementsCreatedApplier`,
  `DmnScalaDecisionEngine.parse` and `OutOfMemoryError.<init>`.
- **Names change between versions.** Before you run a pack, check that each class and field
  it queries exists in the source at the customer's release tag. For example,
  `DbDecisionState` must still have a `drgCache` field.

## Getting numbers out

Write your own one-liners that print only aggregates. What you need to know about the CSVs:

- **Location:** `raw/reports/<suffix>/<query><N>.csv`, where MAT adds the number `<N>`.
  Match it with `<query>[0-9]*.csv`.
- **Format:** the first line is the header, and each row ends with a comma. Numbers are
  quoted with commas (see the quirks above).
- **Columns** follow the SELECT order. `histogram` has class, objects, shallow, retained.
- **Reachable heap:** the sum of the shallow column in `histogram`.
- **Loaded DRG cache entries:** sum `cnt` in `cache_segments` over the rows whose `mapId`
  is a `mapId` in `decision_states`. Other Guava caches in the broker have segments too. If
  `decision_states` has no `mapId`, the field names changed in this version.
- `Segment.count` counts loaded entries only. Rows of `drg_entries` also include entries
  that are still loading; their `retained` is empty.

## Truncated dumps

4 of our 10 dumps were truncated. MAT logs it only on the first parse, as
`Expected to read another N bytes, but only M`. `run_mat.sh` prints that count as
`truncated=N`.

A query that reads a field pointing into the missing part throws
`illegal object reference` and kills the report. To read the last hop safely, take the
field's raw address and look it up among the parent's outbound references. A missing object
then gives an empty value. `queries/raw_drg_ids_pack.xml` reads the DRG id this way, to
hash it ([privacy.md](privacy.md)).

**Ad-hoc packs:** copy the header of `queries/starter_pack.xml` with your own
`filename_suffix`, and pass the file to `run_mat.sh` after the dump.

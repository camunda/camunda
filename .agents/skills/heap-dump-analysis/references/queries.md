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
- **Threads:** MAT writes the stacks to `<stem>.threads` next to the dump. Count threads per
  pattern, and never print the lines:
  ```bash
  awk -v p='ReplayStateMachine' '/^Thread 0x/ { t++ } $0 ~ p && !seen[t]++ { n++ } END { print n+0 }' <stem>.threads
  ```
  We used `ReplayStateMachine`, `DecisionRequirementsCreatedApplier`,
  `DmnScalaDecisionEngine.parse` and `OutOfMemoryError.<init>`.
- **Names change between versions.** Check the names the pack uses on the customer's tag:
  `git show 8.9.23:zeebe/engine/src/main/java/io/camunda/zeebe/engine/state/deployment/DbDecisionState.java | grep -n drgCache`

## Getting numbers out

CSVs are in `raw/reports/<suffix>/<query><N>.csv`. The first line is the header; each row
ends with a comma. Columns follow the SELECT order; `histogram` has class, objects,
shallow, retained.

```bash
R=$OUT_DIR/<name>/raw/reports/starter
F='([^,]*)|("[^"]*")'
# Reachable heap in bytes: sum of shallow sizes in the live histogram
gawk -v FPAT="$F" 'NR>1 { gsub(/[^0-9]/, "", $3); s += $3 } END { printf "%d\n", s }' "$R"/histogram[0-9]*.csv
# Rows of a query, for example DeployedDrg objects
gawk 'NR>1 && NF { n++ } END { print n+0 }' "$R"/deployed_drgs[0-9]*.csv
# Loaded entries of the DRG caches: Segment.count of the caches DbDecisionState points to
gawk -v FPAT="$F" 'function id(x) { gsub(/[^0-9]/, "", x); return x }
  FNR==1 { f++; next }
  f==1 && id($3) != "" { map[id($3)] = 1; n++; next }
  f==2 && id($2) in map { s += id($3) }
  END { if (!n) { print "no mapId in decision_states: check the field names" > "/dev/stderr"; exit 1 }
        print s+0 }' "$R"/decision_states[0-9]*.csv "$R"/cache_segments[0-9]*.csv
```

`Segment.count` counts loaded entries only. Rows of `drg_entries` also include entries that
are still loading; their `retained` is empty.

## Truncated dumps

4 of our 10 dumps were truncated. MAT logs it only on the first parse, as
`Expected to read another N bytes, but only M`:

```bash
grep -c 'Expected to read' raw/first_parse.log
grep -oE 'Expected to read another [0-9,]+ bytes, but only [0-9,]+' raw/first_parse.log |
  tr -d , | awk '{ s += $5 - $9 } END { print s+0 }'     # bytes missing
```

A query that reads a field pointing into the missing part throws
`illegal object reference` and kills the report. To read the last hop safely, take the
field's raw address and look it up among the parent's outbound references. A missing object
then gives an empty value. Our `raw_drg_ids` query read the DRG id this way, to hash it
([privacy.md](privacy.md)):

```
SELECT eval((SELECT toString(s) AS v FROM OBJECTS (SELECT OBJECTS o FROM OBJECTS (SELECT OBJECTS outbounds(r) FROM OBJECTS (d.parsedDecisionRequirements) r) o) s WHERE s.@objectAddress = d.parsedDecisionRequirements.getField(\"decisionRequirementsId\").getValue().getObjectAddress()))[0].v AS drgId FROM io.camunda.zeebe.engine.state.deployment.DeployedDrg d
```

**Ad-hoc packs:** copy the header of `queries/starter_pack.xml` with your own
`filename_suffix`, and pass the file to `run_mat.sh` after the dump.

# 01 — The pipeline: how an event becomes a chart

## The one-sentence version

The engine turns the orchestration cluster's event stream into small, pre-aggregated tables
(**cubes**) at ingest time, so every dashboard question is answered by reading a handful of
rows — never by scanning instances.

## The stages

```
Zeebe brokers ──► event bridge ──► STAGE 1 ──► facts topic ──► STAGE 2 ──► serving store ──► dashboard
(process events)  (replicated      (base                      (aggregation)  (H2/Postgres/      (REST + React)
                   log)             projection)                               Elasticsearch/
                                                                              OpenSearch)
```

**Stage 1 — from events to facts.** Zeebe emits low-level records (element activated, variable
created, incident raised, …). Stage 1 folds them into a small per-instance state (RocksDB) and,
at meaningful moments, emits a **fact** — a self-contained business event:

```
fact: PROCESS_INSTANCE / COMPLETED
├─ bpmnProcessId: claim-process        who
├─ startTime, endTime, durationMs      when / how long
├─ hadIncident, completedNormally      how it went
├─ variantHash, variantElements        which path it took
├─ value, valueDelta                   what it was worth
└─ var.* (lazily resolved)             its variables, on demand
```

Why the intermediate state: a completion record alone doesn't know when the instance started,
which variables it had, or which elements it executed. Stage 1's durable state (a heap
write-cache over RocksDB, committed with every cut — ch. 06 §4) is what lets one fact carry the
whole story.

**Stage 2 — from facts to cubes.** Every dataset declaration subscribes to a fact type and folds
matching facts into its cells. A cell is one `(dataset, dimension values, time window)` bucket
holding one accumulator per meter:

```
facts:  claim COMPLETED 4.2s │ claim COMPLETED 48s │ order COMPLETED 3.1s │ …
                 │                     │                     │
                 ▼                     ▼                     ▼
cube "process-duration", window 09:41–09:42
┌───────────────┬──────────────────────────────────────────┐
│ claim-process │ count=2, duration-sketch{4.2s, 48s}, …   │   ◄── ONE row for
│ order-process │ count=1, duration-sketch{3.1s}, …        │       any number of
└───────────────┴──────────────────────────────────────────┘       instances
```

## Why this stays fast forever

The core trade of the whole design:

```
                    storage & query cost grows with…
───────────────────────────────────────────────────────────────
raw instance store       instances                (millions/day)   classic approach,
                                                                   hurts at volume
cube store               dimensions × windows     (thousands)      this engine
```

A query is "read the cells in the range, merge them, do arithmetic" — O(cells), independent of
instance count. The price is that questions must be *declared* (a dataset exists before its data);
chapter 05 describes how that limitation is being dissolved.

## Event time, watermarks, grace — when is a window "done"?

Facts carry the time the business event **happened** (event time), not when it was processed.
Windows close only when the engine is confident nothing older will still arrive:

```
event time ────────────────────────────────────────────►
              window A          window B         "now" per source
─────────┬────────────────┬────────────────┬──────┰─────
         │ ✔ finalized    │ waiting for    │ open ┃
         │   & released   │ grace to pass  │      ┃ watermark = min over ALL
         │                │                │      ┃ sources of their newest
                                                  ┃ event time (a partition
released when: watermark − grace > window end     ┃ that lags holds everyone
                                                  ┃ back — correctness first)
```

- **Watermark** = minimum of the per-source clocks, so a slow partition can't cause data loss.
- **Grace** (lateness budget) = how long a closed window keeps accepting stragglers. Facts arriving
  after grace are **counted and alarmed** (`late-drop` metric), never silently discarded.
- The watermark clock itself is persisted inside the commit cut, so a restart resumes with the
  exact same notion of time.

Practical consequence visible in every chart: the newest ~grace minutes of any series are still
"maturing" — the UI clamps reads to the newest *releasable* boundary rather than painting a
misleading flat tail.

## Exactly-once (effectively-once) — why numbers survive crashes

Every count in every cube must be right even when machines die mid-stream. Three mechanisms:

```
1. FRAMES CARRY ORIGIN     each fact remembers the source-log position it came from
2. DEDUP IS PERSISTED      Stage 2 remembers, per source, the highest position already
                           folded — a replayed fact at-or-below it is skipped
3. THE ATOMIC CUT          state + consumed offsets + dedup watermarks + the event-time
                           clock commit in ONE RocksDB transaction; recovery reopens the
                           cut and replays the log from exactly there
```

The invariant, in one line: **a fact is folded exactly once into every cell, no matter how many
times the log is replayed.** Everything downstream (meter math, chapter 02) is designed to be
safe under "replay from the last cut" — which is why merge-order-independence matters so much
there.

Writes to the serving store are fenced too: each row write carries a `(epoch, offset)` version,
so a zombie writer from before a failover cannot clobber newer data (on Elasticsearch this maps
to `external_gte` versioning; on RDBMS to a fenced upsert).

## Snapshots — "how many are running right now?"

Flow metrics (started/ended per window) can't answer point-in-time questions ("in flight at
09:30?") without summing all history. For LEVEL-style meters, the pipeline additionally samples
the running total periodically:

```
level over time:      ╱╲    ╱─╲       snapshot series = the level's value
                   ──╱  ╲──╱   ╲──    materialized every N minutes, released
snapshots:          ●    ●    ●       only once the watermark proves no
                                      earlier delta can still arrive
```

A snapshot read carries the last value forward between samples (a step chart), and clamps at
`now − grace` — beyond that, no snapshot can exist yet.

## Where things run

Everything above is one Spring application (the analytics webapp) embedding both stages plus the
serving reads; the event bridge is a separate replicated-log cluster consuming a Zeebe exporter.
Serving storage is pluggable — H2/Postgres via MyBatis, Elasticsearch, OpenSearch — behind one
SPI; all meter math is backend-neutral because accumulators travel as opaque bytes and are merged
in the reading JVM.

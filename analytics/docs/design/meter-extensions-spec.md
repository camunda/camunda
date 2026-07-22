# Meter extensions — drill-down, set algebra, and read-time composition

Status: **proposal (not implemented).** Distilled from a comparative engine study (Celonis PQL
engine, Arroyo, ClickHouse, Honeycomb, Druid/DataSketches) on 2026-07-17. Terminology follows
`../glossary.md`; the write-path context is `../staged-aggregation-design.md`.

## Motivation

Every meter today answers *how many* or *how long* per cell. Four recurring questions fall outside
that envelope, and each maps to a well-understood accumulator or read-path pattern in a neighboring
system:

| Question the pipeline cannot answer today            | Extension                          | Prior art                          |
|-------------------------------------------------------|------------------------------------|------------------------------------|
| "Show me example instances behind this cell"          | Exemplar meter (bottom-k sample)   | Prometheus/OTel exemplars, ClickHouse `SAMPLE` |
| "Which instance was the slowest?"                      | Extremum witness (argmin/argmax)   | ClickHouse `argMax`                |
| "How many of the *same* instances did both A and B?"   | Reach meter (Theta sketch)         | Druid + DataSketches set algebra   |
| "Last 24 h, rolling" without a sliding-window state    | Read-time bin composition          | Arroyo sliding-window bins         |

None of these touch the write-path architecture. Each is a new accumulator type (or a read-path
operator) slotting into the existing seams: `AggregateFunction` for the fold,
`SegmentSealingAggregation` partials for the shuffle, `STREAM_MERGE` for the read. The merge
algebra stays decomposable and deterministic — the two invariants every accumulator must keep:

- **Mergeable partials.** Stage 1 seals per-segment partials; Stage 2 merges them; the read path
  may merge cells again (tier roll-up, scatter reads). `merge(a, b)` must be associative and
  commutative.
- **Deterministic replay.** A sealed delta is a pure function of its segment's positions. Any
  accumulator with sampling or tie-breaking must derive its choices from the data (keys, source
  coordinates), never from RNG state, wall clock, or arrival order.

## Background: where a meter's data flows

```
fact ──▶ CubeAggregationProcessor (gate) ──▶ SegmentSealingAggregation
                                                  │  (window, grain key) → ACC   per open segment
                                             seal │
                                                  ▼
                                     CellDelta ──▶ shuffle ──▶ CubeMergeProcessor ──▶ serving cell
                                                                                          │
                                              read: DIRECT / PUSH_DOWN (additive, SQL)    │
                                                    STREAM_MERGE (blob, app-side merge) ◀─┘
```

All four extensions below are `STREAM_MERGE`-shaped on the read side (their values are not
SQL-reducible), except where noted.

---

## 1. Exemplar meter — a deterministic sample of contributing instances

**Answers:** "show me K real `processInstanceKey`s behind this cell."

The accumulator is a **bottom-k sample by hash priority**: keep the K instance keys whose
`hash64(key)` values are smallest.

```
ACC = { (hash, instanceKey) × ≤K }        fold:  insert (hash64(k), k); trim to K smallest
                                          merge: set-union, trim to K smallest
```

Why bottom-k-by-hash instead of a classic random reservoir:

- **Deterministic.** The same set of contributing instances always yields the same sample,
  regardless of arrival order or replay — no RNG, so sealed deltas stay reproducible.
- **Mergeable without bias.** Union-then-trim of two bottom-k sets is exactly the bottom-k of the
  union. A random reservoir needs weighted-merge bookkeeping and is order-sensitive.
- **Uniform over distinct instances.** A well-mixed hash makes "smallest K hashes" a uniform sample
  of the distinct keys (the same argument that makes the Theta sketch below work — the exemplar
  accumulator is literally a Theta sample that also stores the key next to the hash).

Read side: the cell's exemplar list resolves against Operate / the instance table dataset — every
dashboard number becomes a click target. Sizing: K × ~16 B (hash + key) per cell; K = 8–32 covers
the UI use case.

Note the bias to document in the UI: the sample is uniform over *distinct contributing instances*,
not weighted by the measure. The exemplars behind a p99 duration cell are typical members of the
cell, not the slowest ones — pair with the witness meter (§2) when "the worst one" is the ask.

## 2. Extremum witness — argmin / argmax

**Answers:** "*which* instance was the min/max?"

Today: `ExtremumAccumulator(count, extremum)`. Extension:

```
ACC = (count, extremum, witnessKey)

fold  (v, k):  if v beats extremum → (count+1, v, k)
               if v ties extremum  → keep the SMALLER key      ← deterministic tie-break
merge (a, b):  winner's witness travels; on tie, smaller key
```

The tie-break makes the merge order-independent, preserving replay determinism. One read-side
consequence: the plain min/max meters push down to SQL `MIN`/`MAX` (`PushdownSpec`); a witness
variant has no portable SQL form, so it reads `DIRECT` or `STREAM_MERGE` — it is a *separate meter
kind*, not a change to the existing extremum.

## 3. Reach meter — Theta sketches and "how many of the same ones"

**Answers:** overlap questions between any two cells — funnels, drop-offs, compliance gaps.

### Why HLL cannot do this

The existing distinct meter (`HllSketchValue`) stores per-bucket extremal statistics (leading-zero
counts). Two HLLs can union (register-wise max) but retain no item identities — there is nothing to
*match* between them. A Theta sketch instead retains a **canonical sample of the item hashes
themselves**, and samples of the same key universe are comparable: union, intersection, and
difference (`AnotB`) are all defined. That is the entire trade: Theta pays more bytes to keep
identities.

### How a Theta sketch works

Hash every item to a uniform value in [0, 1). Keep the k smallest hashes; call the retention
cutoff θ. Density does the counting: k hashes packed below θ ⇒ ≈ k/θ distinct items overall.

```
all hashes of distinct instance keys, uniform on [0,1):
  |••••••••••••••••••••|•    •   •    •  •     •   •        |
  0                    θ                                    1
   └── keep these k ──┘└── discard; θ remembers the density ┘
  estimate ≈ k / θ
```

Below k distinct items nothing is discarded — **the sketch is exact**. Many per-(element, window,
grain) cells will sit under a k of 4096 and carry exact reach sets.

### Worked example — approval compliance ("maverick buying")

Week 28 of the order process. Two cells, each with a reach sketch over `processInstanceKey`:

```
instance   Approve?  Ship?    hash(key)
PI-101       ✓         —        0.72        The SAME key always hashes to the
PI-102       ✓         —        0.11        SAME value, in whichever cell's
PI-103       ✓         ✓        0.35        sketch it lands — this is the
PI-104       ✓         ✓        0.89        load-bearing fact.
PI-105       ✓         ✓        0.04
PI-106       ✓         ✓        0.51
PI-111       —         ✓        0.27        ← shipped, never approved
```

Fold time — the cells fill as facts arrive:

```
cell (Approve, wk28):  A = { 0.04  0.11  0.35  0.51  0.72  0.89 }     |A| = 6
cell (Ship,    wk28):  S = { 0.04  0.27  0.35  0.51  0.89 }           |S| = 5
```

Read time — match hashes across the two cells:

```
              0.04   0.11   0.27   0.35   0.51   0.72   0.89
sketch A       ●      ●      ·      ●      ●      ●      ●
sketch S       ●      ·      ●      ●      ●      ·      ●
             ─────  ─────  ─────  ─────  ─────  ─────  ─────
A ∩ S          ✓                    ✓      ✓             ✓    = 4  approved AND shipped
A AnotB S             ✓                           ✓           = 2  stuck after approval
S AnotB A                    ✓                                = 1  shipped WITHOUT approval ⚠
```

Distinct counts alone (6 and 5) suggest "1 dropped" — the truth is 2 dropped and 1 skipped
approval; the two errors masked each other. The compliance number (1) is invisible to any
combination of plain counts.

At scale the sketches trim, and the arithmetic still holds. Suppose both trimmed to θ = 0.75
(PI-104 at 0.89 is dropped from *both* — same hash, same cutoff, so trimming is consistent):

```
A (θ=0.75) = { 0.04 0.11 0.35 0.51 0.72 }      S (θ=0.75) = { 0.04 0.27 0.35 0.51 }
A ∩ S on samples = { 0.04, 0.35, 0.51 } = 3 matches
scale by kept fraction:  3 / 0.75 = 4.0   ✓ (true: 4)
```

### Spec

- **Meter kind `REACH(keyField)`** — typically `processInstanceKey`; with a root-instance dimension
  it can sketch the root key, making funnels span call-activity chains.
- **Fold:** Theta update sketch, `update(key)`. **Partials:** at seal, compact to the
  *ordered, trimmed-to-k canonical form* so a replayed segment serializes byte-identically
  (raw QuickSelect sketches can be arrival-order-sensitive in their over-retention; canonical
  compaction removes that).
- **Merge:** Theta `Union` — associative, commutative. Stage 2 and tier roll-ups unchanged in shape.
- **Read:** new planner operation, *set expressions over cells*: `∩`, `∪`, `AnotB` evaluated
  app-side over fetched blobs. Results are ephemeral — **only raw reach sketches are stored**;
  chaining set ops compounds error, so derived sketches are never written back.
- **Error:** union ≈ 1/√k (~1.6 % at k = 4096). Intersection error grows as the overlap shrinks
  relative to the inputs — exactly the compliance case — so results surface with the library's
  bounds ("≈300 ± 40") and are treated as detectors, not ledgers.
- **Sizing:** ≤ 8 B × k + header ≈ 32 KB per cell at k = 4096 (vs ~1.5 KB HLL). Opt-in meter kind
  next to HLL distinct, not a replacement.
- **Canonical hashing is load-bearing:** every reach sketch must hash the same field with the same
  function (the DataSketches default is stable), or samples stop being comparable.

Read-time expressions this unlocks, all from existing-shaped cells:

- **Funnel:** `reach(Created) → ∩ reach(Approved) → ∩ reach(Shipped)` per window — real drop-off
  stages, not three unrelated counts.
- **Diagnosis:** `reach(hadIncident) ∩ reach(variant = V)` — do incidents concentrate on a path?
- **Long runners:** `reach(Created, June) ∩ reach(Completed, July)` from monthly cells alone.

### The composition rule

A Theta result says *how many of the same ones* but stores only hashes — it cannot name instances
(0.27 does not invert to PI-111). The exemplar meter (§1) names them. The intended UX pairing:
**Theta detects and counts the overlap; exemplars hand the user K real keys from the offending
cell to click into.**

## 4. Rolling windows — read-time bin composition

**Answers:** "last 24 h" KPI tiles — a window anchored to *now*, without any sliding-window state.

All stored windows are **tumbling**: fixed calendar buckets (`[09:00–10:00]`, `[10:00–11:00]`, …)
that never move. A **rolling** tile ("volume in the last 24 h") is a window anchored to the current
moment — it covers a different range every hour, so no pre-aggregated cell for it can exist. The
classic write-path answer (Flink-style sliding windows: every fact folds into every overlapping
window, 24 state updates per fact for a 24 h/1 h slide) is a state explosion we deliberately do
not have.

The observation: a rolling window is never arbitrary — hour resolution serves a "last 24 h" tile —
and **24 consecutive 1 h tier cells are exactly the last 24 hours, in 24 pieces**. Merging cells is
what every meter already does (the Stage-2 combiner algebra), so rolling needs no new state, no new
meter, and no write-path change. It is a read-path rule:

```
1h tier cells:  … [08] [09] [10] [11] … [07] [08] [09]
                     └──────── last 24 cells ─────────┘
rolling(24h) = fetch the last 24 tier cells, merge them          ← the entire feature
```

Optional shortcut for frequent refreshes: consecutive answers overlap in 23 of 24 cells, so
**invertible** accumulators (count, sum, avg as (n, Σ), stddev as (n, Σ, Σ²)) can update the
previous total by subtracting the cell that fell out and adding the one that entered:

```
at 10:00:  [10ʸ] [11ʸ] … [08] [09]        total = T
at 11:00:        [11ʸ] … [08] [09] [10]   total = T − cell[10ʸ] + cell[10]
```

**Non-invertible** accumulators (min/max, HLL, Theta, histograms) cannot subtract — if the maximum
lived in the hour that just expired, the remaining maximum is unknowable without the other bins —
so they simply re-merge the window's cells, which is bounded and cheap (24 merges). One feature,
one optional shortcut; not two mechanisms.

Implementation-wise this is a *window composition operator* in the planner. It pairs naturally
with the stable-frontier clamp: the rolling edge is always frontier-clamped, so a rolling tile
never includes a half-merged leading bin.

---

## Model boundaries (stated, not folklore)

- **Retraction.** Only invertible accumulators can subtract. Fact *corrections* are therefore not
  representable for sketch/extremum/exemplar cells — the pipeline's answer to correction remains
  append-only dedup plus, in the limit, replay/rebuild. This is the same boundary ClickHouse
  (`AggregatingMergeTree`) and Druid live with; documenting it as an invariant prevents accidental
  "just emit a compensating fact" designs against non-invertible meters.
- **Heatmaps need no new meter.** `HistogramAggregateFunction` per time bucket already is a
  duration heatmap (the Honeycomb signature view); the gap is a read/UI surface, not state.
- **Determinism checklist for any future accumulator:** order-independent fold, canonical
  serialized form at seal, data-derived tie-breaks. (§1 and §2 exist in their exact shapes because
  of this checklist.)

## Implementation seams

| Extension        | New accumulator | MeterCatalog | Wire/blob codec | Read strategy       | Planner change |
|------------------|-----------------|--------------|-----------------|---------------------|----------------|
| Exemplar         | yes (bottom-k)  | new kind     | yes             | STREAM_MERGE        | none           |
| Extremum witness | extend record   | new kind     | yes (+key)      | DIRECT/STREAM_MERGE | none           |
| Reach (Theta)    | yes (Theta)     | new kind     | yes (canonical) | STREAM_MERGE        | set-expression op |
| Rolling          | none            | none         | none            | n/a                 | window composition op |

Suggested order: **extremum witness** (smallest, exercises the new-meter-kind path end to end),
then **exemplar**, then **reach**, then **rolling** when a rolling tile is requested.

## Open questions

1. Exemplar K and reach k as dataset-declaration parameters vs global defaults.
2. Whether the reach meter's set expressions belong in `ReportQuery` (declared combinations) or as
   an ad-hoc query surface.
3. Witness/exemplar keys in the serving store: raw `long` keys vs dictionary-coded — ties into the
   fact-schema/dictionary proposal.
4. Whether `REACH` on the *root* instance key should be a distinct declaration
   (`REACH(rootProcessInstanceKey)`) or a modifier, pending the root-instance dimension.

## Sources

- Celonis PQL engine: Vogelgesang et al., *Celonis PQL: A Query Language for Process Mining*
  (Springer 2022) — query-time computation over columnar data; result caching.
- Arroyo engineering blog — sliding windows as bin add/subtract.
- Apache DataSketches — Theta sketch set operations (`Union`, `Intersection`, `AnotB`), error
  bounds; library already pinned in this repo for HLL/quantiles.
- ClickHouse `argMax` / `AggregatingMergeTree`; Prometheus & OpenTelemetry exemplars; Honeycomb
  heatmaps.

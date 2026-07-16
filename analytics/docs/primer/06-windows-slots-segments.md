# 06 — Inside Stage 2: windows, slots, and segments

Three internal layouts make the aggregation engine deterministic, replayable, and cheap. They
answer, respectively: *where in time does a fact land?* (windows), *where inside a cell does each
number live?* (meter slots), and *how does exactly-once shuffle work without a transactional
producer?* (segments).

---

## 1. Windows: slicing EVENT TIME

A windowing strategy is three numbers — size, grace, and the assignment rule:

```
   windowStart(t) = t − (t mod size)        every event time maps to exactly one window

   event time ──────────────────────────────────────────►
        │ 09:41:00     │ 09:42:00     │ 09:43:00     │
        │  window A    │  window B    │  window C    │        size  = 1 minute
        └──────────────┴──────────────┴──────────────┘        grace = allowed lateness

   window A finalizes when   watermark > A.end + grace
```

Grace is *part of the window definition*, not a query parameter — a window owns its lateness
budget, and the aggregation finalizes/evicts the window's cells exactly once, when the event-time
watermark proves nothing older can still arrive (chapter 01).

**Tiers** are just several window strategies over the same facts: a cube declaring 1m and 1h
windows folds every fact into one cell per tier. Reads pick the coarsest tier that fits the
requested range; the commutative-monoid property (chapter 02) guarantees both tiers answer
identically — the hourly cell IS the merge of its sixty minute cells.

Determinism note: the window a fact lands in is a pure function of its event time. Replays,
reorderings, and repartitionings cannot move a fact to a different window.

---

## 2. Slots: the composite cell (one writer, one row, many meters)

A cube cell's value is a **composite accumulator** — one slot per declared meter, framed
back-to-back:

```
cell key:    (datasetId, dimension values)  ++  windowStart
cell value:  ┌ slotCount ┬ len ┬ slot 0 bytes ┬ len ┬ slot 1 bytes ┬ … ┐
             │     5     │  8  │ COUNT acc    │ 24  │ STDDEV acc   │   │
             └───────────┴─────┴──────────────┴─────┴──────────────┴───┘
                           activated             duration_p (KLL sketch) …
```

Why slots instead of one stream per meter (the design decision behind ADR 0009):

```
BEFORE (meter = its own stream)              AFTER (meter = a slot)
one serving row had N writers                one row, ONE writer
→ torn rows, per-column fencing,             → one shuffle key = the row's ownership
  N× dedup streams, N× gating                  prefix, one fence, one dedup, one gate
```

The shuffle key is the cell's *ownership prefix* `(datasetId, dimensionKey)` — time lives inside
the owner, never in the routing key — so exactly one Stage-2 owner ever writes a given serving
row, and a whole row is always internally consistent (its meters saw the same facts).

Slot framing is length-separated and tolerant on decode: unknown trailing slots are preserved,
which is the evolution seam for adding meters to an existing cube (still gated by the
fresh-store bootstrap rule, chapter 03).

---

## 3. Segments: slicing SOURCE POSITION (the exactly-once shuffle unit)

Windows slice event time; **segments slice the source log's position space** — the same idea on
the other axis:

```
   source partition's positions ──────────────────────────────────────►
        │ 0 … 99        │ 100 … 199     │ 200 … 299     │
        │  segment 0    │  segment 1    │  segment 2    │      stride = 100 positions
        └───────────────┴───────────────┴───────────────┘      (the segmentStride config)

   segment(position) = ⌊ position / stride ⌋          pure function — replay-stable
```

**How Stage 1 → Stage 2 stays effectively-once without a transactional producer:**

```
STAGE 1 (per source partition)
fold the open segment's records into an in-memory (window, key) → accumulator buffer
     │
     │  a record crosses into the next segment
     ▼
SEAL: emit each cell's delta (folded from empty over JUST this segment),
      stamped with the coordinate (sourcePartition, segmentIndex)
     │
     ▼
STAGE 2 (per cell owner)
merge each delta ONCE — the persisted dedup remembers, per source partition,
which segments are already merged; a replayed delta is skipped by coordinate
```

The proof rests on one sentence from the windows section, applied to positions: **a sealed delta
is a pure function of its segment's positions**, so re-folding after a crash reproduces the
*identical* delta — and "merge each coordinate once" is then sufficient for correctness, no
matter how many times Stage 1 replays or re-emits. (The open, not-yet-sealed segment's partial
buffer is part of Stage 1's atomic cut, so a crash resumes folding mid-segment without loss.)

**What the stride tunes** (demo: 100):

```
smaller stride                          larger stride
─ deltas emitted sooner (latency)       ─ fewer, fatter deltas (throughput)
─ more coordinates to dedup             ─ cheaper dedup bookkeeping
─ finer replay granularity              ─ more re-folding after a crash
```

It's a batching knob, not a correctness knob — every value of the stride yields the same final
cells, by the same argument as always: same fact multiset, monoid merges, dedup by coordinate.

---

## The symmetry worth putting on one slide

```
                   WINDOWS                      SEGMENTS
axis               event time                   source position
assignment         t → ⌊t/size⌋                 pos → ⌊pos/stride⌋
unit is closed by  the watermark + grace        the next record crossing the boundary
what it enables    "when" queries, tiering      replay-stable deltas, origin dedup
both are           pure functions of the fact — arrival order and timing can
                   never change where anything lands, which is the root of
                   every determinism claim in this primer
```


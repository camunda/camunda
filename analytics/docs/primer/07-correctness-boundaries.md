# 07 — Correctness boundaries: determinism, idempotence, equivalence

Every guarantee in this system reduces to three properties, each placed at a specific boundary.
This chapter names the boundaries, the mechanic that enforces each property there, and what
breaks when the property is violated — the "why can we trust the numbers" chapter.

## The three properties, in one breath

```
   DETERMINISTIC    same input        →  same output          enables REPLAY
                    f(log) = f(log), always — so recomputing after a crash is safe

   IDEMPOTENT       applying twice    =  applying once        enables RETRY
                    f(f(x)) = f(x) — so "did my write land?" never needs an answer

   EQUIVALENT       two representations answer identically    enables SUBSTITUTION
   (isomorphic)     merged tiers ≡ fine cells, decoded ≡ encoded, replayed ≡ original,
                    any backend ≡ any other — so parts are interchangeable
```

They compose into the system's core promise: **crashes, retries, replays, reorderings, and
backend swaps cannot change any number.** Determinism makes re-doing safe, idempotence makes
over-doing safe, equivalence makes substituting safe.

---

## The boundary map

```
Zeebe ──①──► event bridge ──②──► STAGE 1 ──③──► facts ──④──► STAGE 2 ──⑤──► serving ──⑥──► REST/UI
       append                fold; state         append          fold           write          read
```

### ① Exporter → event-bridge log: *at-least-once, by design*

The exporter retries on failure, so a record can be appended twice. This boundary is
deliberately NOT idempotent — making a distributed append idempotent is expensive, and it's
cheaper to make every consumer immune instead. The record's **source position** (monotonic per
partition) is the immunity token everything downstream uses.

### ② Log → Stage-1 fold: *deterministic fold + positional dedup*

```
mechanic 1: the pre-fold skip     a record at-or-below the committed position
                                  watermark is discarded before touching state
mechanic 2: pure folds            appliers use ONLY the record + current state —
                                  no wall clock, no randomness, no JVM hashCode
mechanic 3: the atomic cut        state + consumed position + dedup watermarks +
                                  the event-time clock commit in ONE transaction
```

Together: recovery = reopen the cut, replay the log from the committed position; determinism
guarantees the replayed folds rebuild byte-identical state. This is the **log ↔ state
equivalence**: the state IS a fold of the log, and any prefix of the log has exactly one state.
Violated symptom: duplicate facts, phantom instances, drifting LEVEL gauges after every restart.

### ③ Stage 1 → facts topic: *deterministic derivation, at-least-once append*

Facts may be appended twice (crash between append and cut). The immunity token here is richer:
Stage 1 pre-aggregates into **sealed segment deltas** (chapter 06), and a sealed delta is a pure
function of its segment's positions — replaying reproduces the *identical* delta. Determinism
mechanics that make this true:

```
window assignment    t → ⌊t/size⌋          pure function of event time
segment assignment   pos → ⌊pos/stride⌋    pure function of position
variant hash         FNV+avalanche, XOR    no seed but the process id; byte-level,
                                           JVM-independent, order-insensitive
value stamp          read ONCE at activation, materialized — never re-read
```

### ④ Facts → Stage-2 fold: *coordinate idempotence + order equivalence*

```
mechanic 1: coordinate dedup      Stage 2 persists, per source partition, which
                                  segment coordinates are already merged; a
                                  replayed delta is skipped — merge-once
mechanic 2: the monoid            every meter's merge is commutative + associative
                                  (chapter 02), so ANY arrival order and ANY
                                  grouping of the same deltas yields the same cell
```

Mechanic 2 is the **order equivalence**: parallel partitions, interleavings, and repartitionings
are all just permutations/parenthesizations — provably invisible. Some accumulators are even
natively idempotent (HLL registers, MIN/MAX) — defense in depth beneath the dedup.

### ⑤ Stage 2 → serving store: *idempotent, fenced writes*

The write may be retried, and after a failover a **zombie** (the old owner, not yet aware it
lost ownership) may still write. Two mechanics:

```
idempotence   the row write is a full-row upsert keyed by the cell identity —
              writing the same finalized row twice is a no-op by content
fencing       every write carries a WriteVersion (epoch, offset) packed so that
              newer ≥ older numerically; the store rejects anything older
              (Elasticsearch: external_gte versioning; RDBMS: fenced upsert)
```

Single-writer ownership (the composite-cell design, chapter 06) is what makes a *simple* fence
sufficient: exactly one owner per row means "newer than what's there" is a total order, not a
merge problem. TABLE datasets are idempotent one level up: every end fact of a variant upserts
the *identical* dictionary row, so replays and races are harmless by content equality.

### ⑥ Serving → REST/UI: *equivalence of reads, idempotence for free*

Reads are naturally idempotent; the load-bearing property here is equivalence:

```
tier equivalence      an hourly cell ≡ the merge of its 60 minute cells
                      (monoid corollary) — reads pick tiers freely
backend equivalence   accumulators travel as opaque bytes and are merged by the
                      SAME code in the reading JVM — H2, Postgres, Elasticsearch
                      and OpenSearch must return identical numbers (pinned by
                      parity integration tests per backend)
codec equivalence     decode(encode(acc)) ≡ acc, with TOLERANT decode: unknown
                      trailing meter slots are preserved, so old readers and new
                      cells coexist (the evolution seam)
```

---

## Where DETERMINISM is deliberately *not* required

Honesty about the edges — three places tolerate nondeterminism, each contained by design:

```
wall-clock reads    "now − grace" clamps, aging-WIP ages — read-time presentation
                    only; never folded into state
rolling thresholds  (roadmap, ch. 05) per-variant p95 for strata: order-dependent
                    but replay-stable per partition; each kept row records the
                    threshold it was judged against, so results stay explainable
sketch internals    KLL/HLL estimates vary within their error bounds across merge
                    orders — the CONTRACT (rank/count error) is what's guaranteed,
                    not the bit pattern
```

---

## The audit table (the slide)

```
boundary              property          mechanic                        broken symptom
─────────────────────────────────────────────────────────────────────────────────────────
exporter → log        (none — retried)  source position as token        n/a by design
log → stage-1 state   deterministic     pure folds, pre-fold skip,      state drift after
                                        atomic cut                      every restart
stage-1 → facts       deterministic     sealed deltas = pure fn of      unmergeable dupes
                                        segment positions
facts → stage-2       idempotent +      coordinate dedup + monoid       double counting,
                      order-equivalent  merges                          order-dependent cells
stage-2 → serving     idempotent +      full-row upsert + (epoch,       zombie clobbers,
                      fenced            offset) version fence           torn rows
serving → UI          equivalent        tier/backend/codec parity       backend-dependent or
                                                                        range-dependent numbers
provisioning/DDL      idempotent        create-if-absent (topics,       duplicate datasets,
                                        datasets, bootstrap-if-empty)   startup crashes
```

The test strategy mirrors the table: replay tests pin ②/③ (fold twice → same state/hash),
dedup and restart tests pin ④ (a cut mid-stream, reopen, no double counts), fencing tests pin ⑤
(stale-version write rejected), and per-backend parity ITs pin ⑥. When something new is added,
the first review question is always the same: *which boundary does it sit on, and which of the
three properties does that boundary demand?*

# Process KPI extensions — derived facts from the base projection

Status: **proposal (not implemented).** Companion to `meter-extensions-spec.md` (new accumulator
types); this document covers new *measurements* — facts and fields the base projection could
derive so that the existing generic meters answer process-mining questions. Distilled from the same
comparative study (Celonis PQL engine primarily) on 2026-07-17. Terminology follows
`../glossary.md`.

## The pattern

The transferable Celonis lesson is not an engine technique but a modeling habit: **process
semantics become columns at ingest, and everything downstream stays generic.** Their variant is a
derived column on the case table; their conformance result is a derived column on the activity
table; their flow graph is a derived successor relation. Analysis is then plain filter/group-by.

Our architecture is a better factory for such columns than their batch ETL: the base projection
(`apply → derive → evict`) already folds every record against live entity rows, and datasets
already consume whatever facts the derivers emit. Every extension below is therefore the same
move — *a small entity-row addition + a new fact or field* — with zero new aggregation machinery
(or one declared meter reusing existing accumulators).

| Extension                    | New fact / field              | KPI it unlocks                          | Consumed by (existing) |
|------------------------------|-------------------------------|------------------------------------------|------------------------|
| §1 Transition facts          | `FactType.TRANSITION`         | Flow map, bottleneck (waiting time)      | count, avg, percentile |
| §2 Span facts                | `FactType.SPAN` (declared)    | Milestone-to-milestone SLA durations     | avg, percentile, histogram |
| §3 Occurrence / rework       | fields on ELEMENT facts       | Rework rate per activity                 | count, ratio           |
| §4 Conformance dimension     | field on PROCESS_INSTANCE     | Conformance rate, violation split        | count, ratio           |
| §5 Instance table enrichment | columns on PROJECTED dataset  | Drill-down hub ("the case table")        | table reads            |
| §6 Root-instance dimension   | fields on all facts           | End-to-end journeys across call chains   | every meter, REACH     |

---

## 1. Transition facts — the flow map and the bottleneck map

**KPIs:** edge frequency (the process-explorer graph) and **edge waiting time** (where time
accumulates *between* activities — the classic bottleneck view; element duration only shows time
*inside* activities).

**New fact:** `FactType.TRANSITION` with fields `fromElementId`, `toElementId`, `gapMs`
(source end → target start), plus the usual instance dimensions.

```
                 Create Order
              ┌──────┴──────┐
        2 100 │             │ 400                     edge width  = count meter
     avg 1.2h │             │ avg 0.3h                edge label  = avg/p99 gap meter
              ▼             ▼
           Approve  ◀── Change Price
        3 (of them)      400 · avg 2.6h   ← the bottleneck is BETWEEN activities,
              │ 2 500 · avg 4.1h ⚠           invisible to element-duration KPIs
              ▼
           Ship Goods
```

A dataset declares grain `(fromElementId, toElementId)` with count/avg/percentile meters over
`gapMs` — the two Celonis signature views drop out of existing machinery.

**Derivation, and why ours beats theirs:** Celonis *infers* the successor relation from timestamp
order within a case; interleaved parallel branches then produce spurious edges (a known
process-mining artifact they cannot fix — the log has no more truth in it). Zeebe emits actual
`SEQUENCE_FLOW_TAKEN` records: our edges are engine truth, not temporal inference. The record
names both endpoints; the entity rows hold the timestamps for `gapMs` (source element's `end` is
already on `ElementEntity`; target start arrives with the target's activation). Gateway branch
distribution already walks part of this path — `TRANSITION` generalizes it from gateway-local to
every edge.

**Notes:** emission point is the target's activation (both endpoints then known). Multi-instance
bodies and boundary events need an explicit rule for which "edge" they produce (open question).

## 2. Span facts — milestone-to-milestone durations

**KPI:** "time from *Create Order* to *Ship Goods*, p95, per region" — the SLA measurement between
two declared milestones. Today the meter set covers the two ends of the spectrum only: single
element duration and whole-instance duration. (This is Celonis
`CALC_THROUGHPUT(FIRST_OCCURRENCE['A'] TO LAST_OCCURRENCE['B'])`.)

```
instance ─────────────────────────────────────────────────────▶ time
   Create ──▶ Check ──▶ Approve ──▶ Pack ──▶ Ship ──▶ Invoice
   │◀──────────────── span(Create → Ship) ──────────▶│
                                    │◀─ span(Pack→Ship)─▶│
```

**New concept required — declaration-provisioned derivation.** Unlike §1/§3 (derived
unconditionally), span pairs are user-declared: the deriver must know *which* `(A, B)` pairs to
track. Dataset declarations already reach the engine (provisioning, activation); this extends
their vocabulary from "how to aggregate facts" to "which facts to derive." That is the one
genuinely new seam in this document.

**Derivation:** the instance's root entity row tracks, per declared milestone, first/last
occurrence timestamps. Emission point follows the declared semantics: `FIRST[A] → FIRST[B]` can
emit at B's first completion; anything involving `LAST[...]` is only final at instance completion —
emit there. A `SPAN` fact carries `spanId`, `durationMs`, instance dimensions; existing
avg/percentile/histogram meters do the rest.

**Notes:** state cost is bounded by declared milestones per definition (not by elements). Spans
whose A never occurs emit nothing; spans open at instance termination emit nothing (or a
`completed=false` variant — open question).

## 3. Occurrence index and rework dimension

**KPI:** rework rate per activity ("Approve is re-executed in 18 % of instances"), rework count
distributions. The variant signature already *bucketizes* loop counts (`1 / 2–3 / 4+`) into the
variant identity; this dimension answers the follow-up: *which* activity reworks, at what rate —
meterable, filterable, trendable.

**New fields on ELEMENT facts:** `occurrenceIndex` (1st, 2nd, … activation of this element in this
scope) and `isRework` (`occurrenceIndex > 1`).

```
ELEMENT facts for one instance:      derived fields:
  Approve   (1st activation)    →    occurrenceIndex=1  isRework=false
  Approve   (2nd activation)    →    occurrenceIndex=2  isRework=true
  Ship      (1st activation)    →    occurrenceIndex=1  isRework=false

rework rate(Approve) = RATIO(count where isRework / count)      ← existing meters
```

**Derivation:** the projection already maintains per-element activation counts per scope (the
variant signature is built from them at completion); stamping the running count onto each ELEMENT
fact reads the same state at activation time. No change to `VariantSignature` — the two compose.

Cheapest extension in this document; also the natural first exercise of the "derived field" path.

## 4. Conformance dimension — the unfair advantage

Celonis must *reconstruct* the process model from the log; we deploy the BPMN. Conformance can be
a derived dimension instead of a product module.

**Phase 1 (cheap):** instance-level flag. A dataset (or definition-level config) declares the
reference — an allowed set of variant hashes, or "the happy path of version N". At instance
completion the deriver stamps `conformant = variantHash ∈ allowedSet` onto the PROCESS_INSTANCE
fact. Conformance rate = one RATIO meter; nonconformant share per region/version = plain group-by.

**Phase 2 (larger, deliberately deferred):** element-level violation codes (skipped mandatory
activity, undeclared repetition, wrong order) require walking the model graph during derivation —
Celonis' integer-coded `CONFORMANCE` operator equivalent. Worth a separate spec once phase 1 has
users.

## 5. Instance table enrichment — the "case table" as drill-down hub

Celonis' analytical center of gravity is the case table: one row per instance, carrying every
derived attribute, so any aggregate view can expand into the instances behind it. Our PROJECTED
(raw) dataset kind is the same shape — the proposal is to make an enriched instance table the
*standard* drill-down target:

```
instance table row:
  processInstanceKey | definition | version | state | cycleTimeMs | hadIncident
  | variantHash | reworkCount | conformant | rootProcessInstanceKey | var.* (declared)
```

This is where the meter extensions land when a user clicks: exemplar keys (meter spec §1) and
extremum witnesses (§2) resolve against this table; a Theta overlap ("≈300 skipped approval")
hands its exemplars to this table to become a concrete worklist.

**Known hazard:** the completeElement/markIncident row-rebuild path has previously dropped
uncarried entity fields; every column added here must be carried through those rebuilds (regression
tests per column, not per feature).

## 6. Root-instance dimension — end-to-end journeys

**Problem:** call activities split one business journey across process instances; each instance is
its own "case", so no dataset can see the journey. This is the BPMN analog of the limitation that
drove Celonis to build object-centric process mining — and we can solve it with one field, because
the engine already knows the parentage.

```
order-process  PI-500  ──call──▶  payment-process  PI-611  ──call──▶  fraud-check  PI-702
   root = 500                        root = 500                          root = 500
```

**Derivation:** stamp `rootProcessInstanceKey` (and `rootProcessId`) on the scope row at instance
creation — carried from the parent record's parentage or resolved once via the parent's row — and
emit both on every fact as optional dimensions.

**What it unlocks:** any existing dataset can group end-to-end (cycle time of the *journey*, not
the fragment); `REACH(rootProcessInstanceKey)` (meter spec §3) makes funnels and overlap questions
span call chains; the instance table (§5) gains a "show the whole journey" pivot.

---

## Dependencies and interactions

| Extension    | Entity-row state added                  | Interacts with                            |
|--------------|------------------------------------------|-------------------------------------------|
| §1 Transition| none beyond existing timestamps          | gateway branch distribution (generalizes) |
| §2 Span      | per-declared-milestone first/last stamps | declaration-provisioned derivation (new)  |
| §3 Rework    | none (reads existing activation counts)  | variant signature (composes, no change)   |
| §4 Conformance| none (reads variant hash at completion) | §3 counts for phase 2                     |
| §5 Table     | none (columns from §1–§4, §6)            | exemplar/witness/Theta click-through      |
| §6 Root dim  | root key on scope row                    | REACH meter, §5                           |

A declared **fact schema** (field catalog per `FactType`) is the quiet enabler for all of the
above: every new field/fact lands as a catalog entry that dataset declarations validate against at
provisioning time, instead of extending the untyped field bag. Recommended to land first or
together with the first extension.

## Suggested order

1. **§3 rework fields** — smallest, exercises the derived-field path, immediate KPI.
2. **§6 root dimension** — one field, large product leverage, prerequisite for journey-level REACH.
3. **§1 transition facts** — highest-recognition visualization in the category; first new FactType.
4. **§5 instance table enrichment** — turns the accumulated fields into the drill-down hub.
5. **§2 span facts** — waits on the declaration-provisioned derivation seam.
6. **§4 conformance** — phase 1 anytime after §3; phase 2 as its own spec.

## Open questions

1. Transition facts: edge semantics for boundary events, event subprocesses, and multi-instance
   bodies; whether `TRANSITION` volume (≈ one fact per sequence flow taken) warrants its own
   dataset-level sampling or stays unconditional.
2. Span facts: emission for never-completed spans (`completed=false` fact vs silence); interaction
   with instance termination/cancellation.
3. Root dimension: depth cap or full ancestry (root only vs parent chain); tenant boundaries.
4. Conformance phase 1: where the allowed-variant set lives (dataset declaration vs definition
   metadata) and how it versions.
5. Whether §1/§3 fields are emitted unconditionally or gated by any-dataset-references-them (the
   fact-schema catalog would make the gating declarative).

## Sources

- Vogelgesang et al., *Celonis PQL: A Query Language for Process Mining* (Springer 2022) — derived
  variant/conformance columns, SOURCE/TARGET edge operators, case-table-centric model.
- Celonis docs — object-centric process mining motivation (the 1:N / single-case limitation §6
  addresses natively).
- `meter-extensions-spec.md` — the accumulator counterparts (exemplars, witness, REACH) these
  measurements feed.

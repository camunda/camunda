# What the analytics pipeline writes to its changelogs, and why

- Status: design document for the analytics application specifically. The general machinery —
  cuts, markers, compaction, the recovery ladder — is explained in
  [event-bridge/docs/changelog-failover-and-compaction.md](../../event-bridge/docs/changelog-failover-and-compaction.md);
  this document answers the narrower question: *for our two analytics stages, which rows
  actually land on the changelog, which deliberately never do, and why.*
- Snapshot date: 2026-07-16. Status notes at the end mark the two pieces still on branches.

## 1. The map: two stages, two changelogs

```
   Zeebe records            Stage 1 (projection)          facts topic          Stage 2 (aggregation)        serving store
   ──────────────►  ┌──────────────────────────┐  ──────────────────►  ┌──────────────────────────┐  ────────────────►
                    │ state: open instances,   │   sealed segments     │ state: cube cells         │   dashboard rows
                    │ variables, incidents,    │   (pre-aggregates)    │ (running totals per       │
                    │ open segment buffer,     │                       │  dataset/dimensions/      │
                    │ dedup watermarks         │                       │  window), dedup marks     │
                    └────────────┬─────────────┘                       └────────────┬─────────────┘
                                 │ per cut: the frozen delta                        │ per cut: the frozen delta
                                 ▼                                                  ▼
                    analytics-stage1-changelog                         analytics-stage2-changelog
                    (compacted topic, one partition                    (compacted topic, one partition
                     per source partition)                              per facts partition)
```

Each shard owns one changelog partition and is its only writer. Everything below follows from a
single principle — **the mirror rule**:

> A row goes on the changelog **iff the frozen cut persists it locally.** The changelog records
> are read off the *same frozen structures* the local RocksDB transaction writes — one object,
> two destinations — so the changelog is, by construction, exactly the state a standby must hold
> to be byte-identical with the active. Never more, never less.

That rule is why this document can be an inventory of local state: to know what's on the
changelog, list what the cut persists.

## 2. Stage 1: the projection shard

Stage 1 folds raw Zeebe records into the *base projection* — the "what is currently true"
picture of running processes — and periodically seals pre-aggregated segments toward Stage 2.
Its cut persists, and therefore its changelog carries, four groups of rows plus the marker.
Every record key is wrapped in the envelope `cfTag(1 byte) + storeKey`, so one changelog carries
all of them:

```
   cfTag  column family            one row means…
   ─────  ──────────────────────   ─────────────────────────────────────────────
    ▪     ELEMENT_ENTITY           "this element instance (flow node) is
                                    currently active in this process instance"
    ▪     VARIABLE_SCOPES          "this variable scope exists, under this
                                    parent" — the scope tree of live instances
    ▪     VARIABLE_ENTRIES         "variable `amount` in that scope currently
                                    holds 4200"
    ▪     INCIDENT_ENTITY          "this incident is currently open"
    ▪     VARIANT_ELEMENTS         "this instance has executed this element" —
                                    the executed-path (variant) bookkeeping
    ▪     OPEN_SEGMENT             partial pre-aggregates of the segment being
                                    filled right now (not yet sealed)
    ▪     ZEEBE_APPLIED_POSITION   "records from Zeebe partition 3 are folded
                                    up to position 8_114_204" — the pre-fold
                                    dedup watermark
          (reserved 4-byte key)    THE MARKER: "this cut ends; the source is
                                    processed up to offset X"
```

### 2.1 Why each group must be there

**The projection rows (the first five)** are *the* reason the changelog exists at all. A process
instance can run for months; its scopes, variables, and open elements are live state whose
*source records are older than any retention window*. This state is unreconstructable from the
source — if a shard dies and its successor cannot get these rows as bytes, they are gone. This
is the "unbounded-age state" of the general document, in the flesh.

**The open segment buffer** is bounded and young (at most one segment's worth of folding), but
losing it on failover would lose the partial pre-aggregates since the last seal — the successor
would either double-count (if it refolds records already reflected there) or under-count (if it
skips them). Carrying it makes promotion seamless: the successor continues filling the same
segment mid-stride.

**The dedup watermarks** are the effectively-once guarantee itself. They record how far each
Zeebe partition's records have been folded, and they commit *atomically with the state they
protect* (same cut, same transaction, same changelog). Separate them from the state and a
failover can replay source records into state that already contains them — silent double
counting. On the changelog they are just rows like any other; the atomicity comes from the cut.

**The marker** carries the cut's source offset X and is strictly the cut's last record — the
resume authority and the torn-cut boundary (general doc, §6).

### 2.2 A worked cut

Suppose one cut interval (say 500 ms) sees: process instance `P1` starts, sets variable
`amount=4200`, and its task `T` becomes active; meanwhile instance `P0` (running since last
week) completes; and instance `P2` starts *and* completes entirely within the interval.

```
   what the fold did to local state:            what lands on the changelog:

   P1: scope created, amount=4200,              put  VARIABLE_SCOPES    (P1-root)
       element T active                         put  VARIABLE_ENTRIES   (P1, amount)
                                                put  ELEMENT_ENTITY     (P1, T)
                                                put  VARIANT_ELEMENTS   (P1, …)

   P0: completed → its scopes, variables,       tombstone VARIABLE_SCOPES  (P0-root)
       open elements cleared                    tombstone VARIABLE_ENTRIES (P0, …)
       (they WERE persisted by older cuts)      tombstone ELEMENT_ENTITY   (P0, …)

   P2: created AND cleared within the           ─ nothing. ABSORBED. ─
       interval — never persisted               (see §2.3)

   open segment gained P0's and P2's            put  OPEN_SEGMENT (changed cells
   completion facts                                   only — unchanged cells from
                                                      earlier folds are NOT re-sent)

   Zeebe partition 3 advanced                   put  ZEEBE_APPLIED_POSITION (p3)
                                                      (only because it MOVED)

   the cut closes at source offset 4650         MARKER {X=4650}   ◄── always last
```

A standby applying this cut (atomically, on the marker) ends up byte-identical with the active:
P1 live, P0 gone, P2 never heard of, the open segment mid-fill, dedup at the right positions.

### 2.3 What Stage 1 deliberately never sends

- **Born-and-died state (absorption).** `P2` above: its scope rows were created and deleted
  between two barriers, so they never entered the frozen delta — *structurally*, not by a
  filter: the caching store annihilates the put+delete pair before the freeze ever runs, and a
  delete is only ever emitted for a row some completed cut actually wrote. Under load this is
  the difference between a changelog proportional to *state churn* and one proportional to
  *event volume* — short-lived instances (the common case!) cost the changelog nothing.
- **Unchanged rows.** A variable set last week and untouched since is not re-sent by every cut.
  Its row from the cut that last changed it remains valid; compaction keeps exactly that row.
  (Same rule now applies to watermarks: only *moved* ones are re-persisted — see status notes.)
- **Sealed segments.** The moment a segment seals, it is published to the **facts topic** — a
  durable, replicated topic in its own right. Putting it on the changelog too would store it
  twice; the facts topic *is* its durability. The changelog only carries the not-yet-sealed
  buffer.
- **The Zeebe records themselves, or any "what happened" event.** The changelog is state
  replication — "what is", never "what happened" (the normative contract from ADR 0009). Anyone
  needing events reads the source. Example of what this rules out: you cannot reconstruct "how
  many times did variable `amount` change" from the changelog — only its latest value survives.
  That is by design, and it is what lets compaction bound the changelog by live keyspace.

## 3. Stage 2: the aggregation shard

Stage 2 consumes sealed segments from the facts topic and merges them into **cube cells** —
running totals per (dataset, dimension key, time window) — evicting and emitting cells to the
serving store as their windows finalize. Its changelog:

```
   cfTag  what                     one row means…
   ─────  ──────────────────────   ─────────────────────────────────────────────
    ▪     cube cells               "cube cell (disputes, region=EU, window
          (grouped cell store)      14:00–15:00) currently totals 17" — the
                                    running aggregate, keyed by its OWNERSHIP
                                    PREFIX (datasetId, dimKey), so every cell
                                    has exactly one writing shard
    ▪     open-segment cells       partial state of the segment currently being
                                    merged (same role as Stage 1's buffer)
    ▪     shuffle dedup marks      "from source partition 2, stream S, segments
                                    are merged through (segment 41, chunk 3)" —
                                    the effectively-once guard across the
                                    shuffle hop
          (reserved key)           THE MARKER {X = facts-topic offset}
```

### 3.1 Why, and the worked cut

The cube cells are running aggregates: re-deriving one means re-reading *every* sealed segment
that ever contributed to it — and the facts topic, like any source, is retention-bounded. So the
cells are unbounded-age state exactly like Stage 1's scopes, just one level up. The dedup marks
are the same argument as §2.1: they must move atomically with the cells they protect, or replay
double-merges.

```
   a cut in which: the EU dispute count for the 14:00 window grew by 3;
   the 13:00 window FINALIZED (emitted to serving, evicted from state);
   and a cell for a brand-new key was created and its window closed,
   all within one interval:

   changelog:  put        (disputes, EU, 14:00) = 17      ← changed cell, new total
               tombstone  (disputes, EU, 13:00)           ← evicted AND previously
                                                            persisted → delete
               ─ nothing for the born-and-died cell ─     ← absorbed (never persisted)
               put        dedup (p2, S) = (seg 41, ch 3)  ← moved marks only
               MARKER {X}
```

### 3.2 What Stage 2 deliberately never sends

- **Serving rows.** Finalized windows are *published* to the serving store at the cut (that is
  the pipeline's output); the changelog is not a second serving feed and carries only the
  tombstone that removes the finalized cell from *state*. A standby does not need finalized
  windows — they are already where they belong.
- **The sealed segments it consumed** — those live in the facts topic; Stage 2's changelog
  never re-transports its input.
- Everything in §2.3's list, for the same reasons: absorption, unchanged cells, no events.

## 4. The intuition for size

Per cut, the changelog costs O(changed keys this interval) — coalesced, absorbed, delta-only.
Over time, compaction bounds the total at O(live keyspace):

```
   Stage-1 changelog steady-state  ≈  rows for OPEN instances (scopes, variables,
                                      elements, incidents, variants)
                                      + one open-segment buffer
                                      + one watermark row per Zeebe partition
                                      + one marker

   Stage-2 changelog steady-state  ≈  cells for OPEN windows per (dataset, dim)
                                      + one open-segment buffer
                                      + one dedup row per (partition, stream)
                                      + one marker

   COMPLETED instances and FINALIZED windows cost nothing: their tombstones die
   after the grace window, and then the keys have vanished entirely.
```

That is the whole promise in one picture: the changelog's size tracks *what is currently true*,
not *how much has ever happened* — which is also exactly what a failover needs to copy.

## 5. How a standby consumes all this (one paragraph)

The standby tails the changelog, buffers each cut until its marker, then applies the whole cut
in one local transaction — cfTag routes each row to its column family, tombstones delete, the
marker's {X, P} land in the same batch. Because of the mirror rule, the result is byte-identical
with the active's store (pinned by the byte-equivalence test). On promotion it resumes the
source at X+1 and continues folding — the projection rows make months-old instances simply *be
there*, the open buffers make the current segment continue mid-stride, and the dedup rows make
the replayed tail fold exactly once. Every record type in this inventory exists because one of
those three sentences needs it.

## 6. Status notes (2026-07-16)

- The Stage-1 and Stage-2 changelog write paths described here are merged.
- "Only *moved* watermarks are re-persisted" for Stage 1 is on branch
  `roman/eb-stage1-watermark-delta` (verified, merge pending); until it merges, Stage 1
  re-persists every watermark each cut — harmless (compaction absorbs), just not delta-true.
- The standby/applier consuming these changelogs is on branch `roman/eb-standby-failover`
  (verified, merge pending); the runtime wiring of role flips is the follow-up after it.

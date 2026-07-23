# ADR 0001 — Scope of materialized tables and richer joins

- Status: Accepted
- Date: 2026-07-03
- Scope: `event-bridge/event-bridge-streaming`

## Context

The streaming library now offers, for joining and enrichment:

- a **stream–table lookup join** (`StreamTableJoin`): enrich each stream record with a keyed lookup
  against a table held in a state store (inner and left-outer);
- a **processor graph** (`ProcessorTopology`) with `getStateStore`/`forward`/`schedule`;
- **bounded, checkpoint-coalesced state stores** (`StoreBuilder` + `CachingKeyValueStore`).

A fuller join/table toolkit is conceivable, and was captured as a backlog item:

1. **Materialized tables as a first-class, queryable view** — expose a materialized dataset (e.g. a
   rollup's output) as a readable/joinable table, including a change log with tombstones.
2. **Declared joins with enforced co-partitioning** — reject a join whose two inputs are not
   partitioned the same way, so a lookup never misses because the counterpart lives on another
   member.
3. **Table–table joins** — maintain a joined view that re-emits when *either* side changes.
4. **Stream–stream windowed joins** — correlate two streams within a time window, which requires
   windowed buffered state on *both* sides.

The question this ADR settles: how much of that to build now.

## Decision

Ship only the stream–table lookup join (already done). **Defer items 1–4 as a future epic**, to be
implemented only when a concrete use case in the analytics domain requires one of them.

Additionally: **do not introduce a per-store change log.** Recovery in this pipeline is deliberately
change-log-free — the source log *is* the recovery log: state advances as one atomic cut with the
consumed offsets (produce-before-commit), and a crash replays the source from the last committed
offset. A "table as a change-log topic with tombstones" (item 1) therefore does not fit the current
model and is out of scope; a queryable table view, if later needed, would be a read-through over the
already-durable store, not a new change-log.

## Rationale

- **The domain's joins are enrichment lookups**, which item 0 (the shipped lookup join) already
  covers: derive a key from a fact, read reference/prior state, emit the enriched fact. The Kappa
  pre-aggregation pipeline (project → aggregate rollups) has no current need for table–table or
  windowed stream–stream correlation.
- **Cost is high and unamortized.** Items 2–4 add co-partition enforcement, dual-sided windowed
  state with retention, and re-emit-on-change semantics — substantial machinery with no consumer
  today. Building them now would be speculative (YAGNI).
- **One genuine capability gap** is the stream–stream windowed join: it is the only item that cannot
  already be approximated by a lookup join against a materialized store. It is the first candidate to
  revisit if a correlation use case appears.
- **Consistency with the recovery model.** Adding change logs would duplicate the source log and
  contradict the change-log-free design, splitting recovery across two mechanisms.

## Consequences

- The join/table surface stays small: one lookup join plus the store/processor primitives.
- Cross-member state transfer on rebalance remains a separate, known concern, to be solved by
  snapshotting a member's durable state rather than by change-log replay (out of scope here).
- If a windowed-correlation or continuously-maintained-join use case arises, revisit with a
  follow-up ADR covering: the dual windowed-state model and its retention/grace, co-partition
  enforcement at topology-build time, and re-emit semantics. Prefer expressing new joins as
  processors on the existing `ProcessorTopology` rather than a parallel join runtime.

## Revisit triggers

- A dashboard/query requirement to read a materialized rollup by key at low latency (→ a
  read-through queryable table view, still change-log-free).
- A domain requirement to correlate two independent streams within a time window (→ stream–stream
  windowed join; the genuine gap).


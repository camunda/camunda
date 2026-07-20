# ADR 0009 — State changelogs on compacted topics

- Status: Accepted (2026-07-16) — supersedes the state-transport core of ADR 0006 (decisions 2,
  3, 6 there). The supersession was decided with the failover-first re-scoping of decision 4
  below; [ADR 0006](0006-standby-tasks-warm-failover.md)'s Status line records the split between
  its superseded and retained content.
- Date: 2026-07-15
- Scope: `event-bridge-streaming` commit path, analytics shards, `event-bridge-consumer-groups`
  roles, disaster/bootstrap recovery
- Builds on: [ADR 0005](0005-asynchronous-checkpointing.md) /
  [0007](0007-single-durability-concept.md) / [0008](0008-every-commit-is-a-cut.md) (every commit
  is an atomic cut with a frozen delta), and the cut-delta trueness fixes (`e6bde7eae44`: deletes
  only for cells a completed cut wrote; `510d8be7584`: the freeze serializes only cells folded
  into since the last completed cut — this amends 0005's description of the freeze as
  serializing the whole open buffer)
- Depends on: [event-bridge ADR 0001](../../../docs/adr/0001-compacted-topics-and-log-compaction.md)
  (compacted topics)
- Supersedes: [ADR 0006](0006-standby-tasks-warm-failover.md)'s source-fed passive replicas and
  snapshot store (decisions 2, 3, 6 and the retention contract there). 0006's protocol surface —
  per-partition roles in the assignment, anti-affine standby placement, readiness reporting,
  ready-only promotion, progressive warming — carries over unchanged onto the new transport.

## Context

ADR 0006 solved fast failover with *source-fed* standbys: every passive replica re-fetches and
re-folds the source, and an object-store snapshot subsystem covers bootstrap beyond the retained
window. That design rests on a contractual invariant — two folds of the same topic must produce
byte-identical output — and buys its warm standby at the price of duplicate fetch + fold per
replica, a snapshot store as a deployment dependency, and a standing coupling between snapshot
cadence and source retention ("snapshot age vs. retained window" as a first-class safety metric).

Since then the commit path has consolidated (ADRs 0007/0008): all durable effects of a partition
happen in one place, the commit cut, whose frozen delta is now — after the trueness fixes — an
exact statement of what changed since the last completed cut: no deletes for keys never persisted
(born-and-died cells are absorbed), no re-puts of unchanged cells.

That makes a state changelog nearly free on the consumer side: **the changelog record set is the
cut delta the shard already produces.** The coalescing a changelog wants (latest value per key per
interval, short-lived keys absorbed) is exactly what the cut protocol already computes. For
comparison, Kafka Streams obtains its changelog from cache-flush-at-commit on the processing
thread: its coalescing is pressure-leaky (cache eviction emits mid-interval per-key records) and
it has no born-and-died absorption; without caching it degrades to a record per put. Our cut
gives the stronger form structurally — under memory pressure the runtime takes an *early cut*,
never stray records.

## Decision

Every shard replicates its state through a **changelog: a partition of a compacted topic (event-
bridge ADR 0001) that carries each cut's delta as keyed records**. The changelog is the outer
truth; local RocksDB is a restart accelerator.

1. **Publication is part of the cut.** At each commit cut, after publishing frames/serving rows,
   the shard produces the frozen delta to its own changelog partition: one keyed record per
   changed cell, one tombstone per delete, plus one **offset-marker record** (a reserved key per
   shard) carrying the cut's source offset X — pipelined, one await.
2. **Ordering: changelog ack strictly before the local commit.** Only after the changelog append
   is acknowledged does the shard run its local transaction {delta, source offset X, changelog
   position P}, and only then the chained source `commitOffset(X)`. A crash between ack and local
   commit leaves keyed duplicates in the changelog; last-write-wins compaction absorbs them. The
   reverse order would let local state claim durability the outer truth does not have.
3. **The changelog is state replication, never an event feed** (normative consumer contract).
   It says *what is*, not *what happened*: born-and-died keys never appear at all, and
   mid-interval values coalesce away. Anything needing business events reads a source topic.
4. **Fencing is deferred behind failover — a conscious re-scoping (2026-07-16).** Broker-enforced
   producer-epoch fencing (this decision's original form: the broker rejects appends from a
   superseded epoch) is postponed until after the failover milestone. The interim guards, both
   load-bearing and therefore contractual: (a) source offset commits remain coordinator-epoch
   fenced, and (b) the cut chain ends with that commit and **a rejected offset commit halts the
   shard immediately** — no retry, no continue — so a deposed zombie discovers its deposition at
   the end of its next cut and the blast radius is bounded to **one zombie cut per failover**,
   whose content is a deterministic fold of source records the successor also folds. The
   residual mixed-marker window (zombie cut interleaving a rebuild) is accepted as bounded and
   converging — the same deferral shape 0006 used for its serving-sink fence. Epoch *stamping*
   of changelog batches is deferred with it (the wire format exists, unmerged); when enforcement
   lands, unstamped history is simply outside the fence's memory — no rewrite, no migration.
   The broker-side design (per-producer epoch map, engine-pattern replay recovery, the
   key-disjointness contract that makes transactions unnecessary) is settled in principle and
   parked with its seven open questions for a dedicated fencing ADR — the full problem statement
   and design space are documented in
   [event-bridge/docs/multi-producer-compacted-topics.md](../../../docs/multi-producer-compacted-topics.md).
5. **Disaster rebuild** (no usable local state anywhere): consume the compacted changelog from the
   start — O(live keyspace), not O(history) — apply bytes into the store, read the latest
   offset-marker, resume the source at X+1. No determinism requirement, no coupling to source
   retention, no snapshot store.
6. **A standby is a changelog follower, and it applies whole cuts only.** It tails its
   partitions' changelogs and applies bytes to its own store — no source fetch, no fold, no
   staged output to discard. The apply is **cut-atomic**: records are buffered until their cut's
   offset-marker arrives (the marker is the last record of every cut), then applied as one unit —
   a writer crashing mid-publish leaves a torn tail beyond the last marker, and cut-atomic apply
   makes it invisible instead of half-applied (half-applied cells without their dedup watermark
   would double-merge on post-promotion replay). Readiness = changelog lag. Promotion = drain to
   the last marker, flip active with epoch E+1, resume the source at X+1. An empty joiner warms
   by running the same apply loop from the changelog start — off the availability-critical path.
   A restarting member with an intact disk resumes from its local cut's changelog position P
   instead of from the start.
7. **From 0006, the protocol surface is retained** (roles in the assignment, anti-affinity,
   readiness-triggered assignor runs, ready-only promotion, warming caps); **not built** are the
   snapshot subsystem (checkpoint-diff upload, restore, pruning, snapshot-age monitoring) and the
   determinism contract test as a failover prerequisite — determinism is demoted from
   load-bearing invariant to an ordinary property of the fold.

## Consequences

- **The 0006 trade is consciously reversed.** 0006 rejected changelogs as "a second replicated log
  and write amplification to recover something the snapshot store + source tail already
  determine." We now pay that log — one delta per shard per cut interval — to buy: byte-apply
  standbys (no duplicate fold compute, no determinism contract), retention decoupling (no
  snapshot-age safety metric), a bootstrap bounded by keyspace, and a recovery story with no
  second storage system. Today's state is tiny (136 KB nominal, tens of MB under backpressure),
  so the cost is negligible now and grows with cardinality — if keyspaces ever make changelog
  rebuild too slow, the remedy is snapshotting *of the changelog topic itself* on the broker
  side, not a return to source-fed replicas.
- Broker compaction (event-bridge ADR 0001) becomes a hard dependency of the streaming runtime's
  recovery story, including tombstone grace ≥ the longest plausible rebuild duration.
- The cut pipeline gains one stage (produce + ack) between publish and persist; commit latency
  now includes a changelog round-trip. Cut cadence and changelog partition placement are the
  tuning knobs.
- Local persistence becomes a pure optimization (0006 reached the same property via snapshots):
  members can run on ephemeral disks; an intact disk only shortens catch-up.
- ADR 0006 remains the reference for the assignment-protocol design; its Status must note this
  supersession (see both Status lines; its implementation had not started).

## Considered and rejected

- **Source-fed standbys + snapshot store (ADR 0006 as written).** Works, and its protocol design
  is retained — but every standby duplicates fetch and fold, correctness of promotion rests on
  contractual byte-determinism of the seal path, and bootstrap depends on a second storage system
  whose freshness must be monitored against source retention. The changelog replaces all three
  with byte application.
- **Snapshot shipping without a changelog.** Covers bootstrap but not warm standby (nothing to
  tail between snapshots); failover would wait on a snapshot download.
- **Fold-ahead or active-active replicas.** Rejected in 0006; nothing here changes that analysis.
- **Changelog ack after local commit.** Simpler pipelining, but a crash window where local state
  and committed source offsets outrun the outer truth — the changelog could permanently miss
  rows that serving already exposed.

## Follow-up work items

1. Changelog cut stage: produce delta + offset-marker, ack-before-persist ordering, changelog
   position in the shard transaction.
2. Epoch fencing on the changelog producer path (reuse coordinator-epoch validation).
3. Rebuild + intact-disk restore paths; retire the retained-window replay guard.
4. Standby-on-changelog implementation over 0006's protocol surface — supersession decided
   (decision 4 as amended), under implementation (`roman/eb-standby-failover`).
5. Docs: stamp 0006's Status line with the supersession split (done); consumer-contract note
   ("state, not events")
   on the topic/API docs.


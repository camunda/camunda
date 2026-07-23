# ADR 0002 — Rebalance handoff for sharded partition state

- Status: Accepted (design; phased implementation)
- Date: 2026-07-03
- Scope: `event-bridge-streaming` runtime, `event-bridge-client` consumer, analytics shards

## Context

State is now sharded per source partition: each partition is an owning `Task`
(`ProjectionShard` / `AggregationShard`) with its **own** RocksDB directory, and
the runtime resumes it from the shard's own committed offset. This is
member-local state — it lives on the node that owns the partition and does not
travel.

That is correct while a partition stays on one member, but not across a
**rebalance**. When partition *P* moves from member A to member B:

- A has committed *P*'s state (in A's RocksDB) and offset *O* to the broker.
- B is assigned *P*, opens its **own** (empty) RocksDB for *P*, and
  `restore()` returns `NO_OFFSET`. The consumer group resumes *P* from the
  broker's committed offset *O*.
- B therefore starts folding from *O* with **empty** state — every instance and
  window before *O* is missing. The projection/aggregate for *P* is now wrong
  until it happens to be rebuilt.

So sharded analytics is correct today only while assignments are stable. Two
facts shape the fix:

- **The assignor is already sticky** — it retains each member's previous
  partitions, so partitions move only on a membership change (a member dies or
  is added), not on every rebalance. Churn is already minimized.
- **The client exposes no rebalance lifecycle hooks** — `Consumer` has
  `joinGroup`/`leaveGroup`/`sendHeartbeat`/`seek`/`poll`/`commitOffset`, but no
  "partitions revoked" / "partitions assigned" callback. Any handoff needs those
  hooks added first.

## Decision

Make sharding correct under rebalance in phases, cheapest-first. Do **not**
attempt a big-bang snapshot-transfer implementation.

1. **Phase 0 — constrain (now).** Document that sharded state is member-local
   and correct only under stable assignment. Rely on the sticky assignor; a
   forced move currently yields a cold, incorrect shard until rebuilt. Acceptable
   for a single stable member per partition (today's deployment) and while the
   source is one partition.
2. **Phase 1 — rebuild on assign (correctness without transfer).** Add
   `Consumer` rebalance hooks (`onPartitionsAssigned`/`onPartitionsRevoked`). On
   being assigned a partition with no local state, the runtime seeks that
   partition to the source start and replays to rebuild the shard, ignoring the
   broker's committed offset until caught up. Correct and self-contained; cost is
   a full (retention-bounded) replay per moved partition. Requires a
   seek-to-beginning capability on the client.
3. **Phase 2 — snapshot handoff (fast moves).** On revoke, snapshot the
   partition's RocksDB to shared storage; on assign, download and restore it,
   then resume from its committed offset. Turns an O(history) replay into an
   O(state) transfer. Requires: shared snapshot storage, a
   snapshot/restore of a partition's column families, and coordination with the
   rebalance protocol (revoke must finish the snapshot before the partition is
   reassigned).

## Rationale

- **Phase 1 before Phase 2:** replay-to-rebuild makes rebalance *correct* with
  far less machinery than snapshot transfer, and it is the fallback Phase 2 needs
  anyway (a snapshot may be missing/corrupt). Ship correctness first, speed
  second.
- **Snapshots, not the low-watermark.** Rebuild/transfer must anchor on a
  consistent per-partition snapshot, not a shared low-watermark offset — the
  shards are independent and a global watermark would over- or under-replay.
- **Client hooks are the prerequisite.** Both phases need revoke/assign
  callbacks; that is the first concrete work item and belongs to the client, not
  the runtime.

## Consequences

- Until Phase 1 lands, run one stable member per partition (or accept cold
  rebuilds on the rare forced move). This is fine for the current single-source-
  partition deployment.
- Phase 1 adds a rebuild path in the runtime and a seek-to-start + rebalance
  listener on the client.
- Phase 2 introduces an external snapshot store and a partition
  snapshot/restore format — a substantial, separately-scoped effort with its own
  ADR when undertaken.

## Follow-up work items

- Client: add rebalance lifecycle callbacks and seek-to-beginning.
- Runtime: on assign-without-state, drive a bounded replay-to-rebuild for the
  shard.
- Later: partition snapshot/restore + shared snapshot storage (Phase 2).

> **Update (2026-07-08):** Phase 1 (replay-to-rebuild on assign-without-state) is implemented in
> `SourceLoop.materialize`, but it is sound only while the source's retained window still covers
> the partition's full relevant history — retention compacts independent of consumer progress, so
> long-lived in-flight state can outlive the log. Warm failover and the retention-safe snapshot
> bootstrap (Phase 2, now required rather than optional) are specified in
> [ADR 0006 — standby tasks](0006-standby-tasks-warm-failover.md).


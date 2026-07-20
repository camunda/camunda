# ADR 0006 — Standby tasks: warm failover and snapshot-based bootstrap

- Status: Proposed as written; partially superseded. The state-transport core — decisions 2, 3
  and 6 (source-fed passive replicas, frontier-following, the snapshot store) and the
  snapshot-based bootstrap in the title — is **superseded by
  [ADR 0009](0009-state-changelog-on-compacted-topics.md)**, per the failover-first re-scoping
  recorded in 0009's amended decision 4 (2026-07-16). The assignment-protocol surface —
  decision 1 plus the readiness/promotion/warming machinery of decisions 4, 5 and 7 — is
  **retained** and implemented by the standby-on-changelog work.
- Date: 2026-07-08
- Scope: `event-bridge-streaming` runtime, `event-bridge-client` consumer,
  `event-bridge-consumer-groups` coordinator/assignor, analytics shards
- Builds on: ADR 0002 (rebalance handoff), ADR 0004 (actor-per-partition), ADR 0005
  (asynchronous checkpointing), analytics ADR 0007 (stable source coordinates)

## Context

ADR 0002 Phase 1 is implemented: when a partition is assigned to a member with no local state, the
runtime seeks to the source start and rebuilds the shard by replay
(`SourceLoop.materialize`). That is slow — recovery is O(retained history) — but it is also **not
generally correct**, because the source is retention-bounded: `LogRetentionCompactor` keeps the
most recent `maxRecords` per data partition and compacts older segments **independent of consumer
progress** (deliberately, so a slow consumer can never pin the log). Stage-1 state includes open
process instances whose starts can lie arbitrarily far in the past; once retention has compacted
past the oldest in-flight record, a from-scratch replay rebuilds a shard that is **silently
missing** every instance older than the retained window. So replay-from-start is a valid bootstrap
only while the retained window still covers the partition's full relevant history — it cannot be
the failover or (re)bootstrap mechanism.

The requirement is a **fast failover procedure** with two faces: (1) an empty joiner (new node,
lost disk) must become a working group member without a stop-the-world restore, and (2) a warm
standby must take over a dead active's partitions in roughly one commit interval. State size must
be treated as unbounded — restore cost is O(state), so the design must keep restore off the
availability-critical path rather than assume it is cheap.

Four properties of the current system make this achievable, and they are the load-bearing
invariants of this decision:

1. **The fold is deterministic and effectively-once.** Source topics are Raft-replicated; the
   pre-fold origin-coordinate watermark (analytics ADR 0007) makes state a pure function of topic
   content. Consequently, **(latest snapshot + source-log tail) is an authoritative,
   node-independent representation of every shard** — local RocksDB is a materialized cache of
   that pair. This is the disaggregated-state mental model without a remote read path.
2. **Shuffle output is deterministic too.** Segment boundaries are a fixed stride over source
   position (`Segments`), sealed deltas fold from empty over exactly one segment
   (`SegmentSealingAggregation`), and chunking is deterministic (`EnvelopePublisher`). The reduce
   side drops re-emits per `(sourcePartition, streamId)` by `(segment, chunk)` watermark
   (`SegmentDedup`). Two members folding the same offsets publish **byte-identical** envelopes.
3. **Emission is a single choke point.** Since ADR 0005, all outward effects happen at the commit
   barrier: `CommitCut.publish()` and `consumer.commitOffset(...)` (`PartitionCommitter`). Making
   a task passive means skipping exactly those two calls.
4. **The epoch fence blocks commits, not fetches** (`CoordinationValidator`) — a passive member
   can fetch and fold freely without protocol changes.

## Decision

Add **standby tasks** — passive replicas of a partition's shard kept warm by running the same fold
from the same source — plus a **snapshot store** that makes any shard restorable regardless of
retention. Fast failover is delivered exclusively by warm standbys; an empty joiner is a *warming
standby* that earns active work progressively; a blocking restore exists only when every copy of a
partition is lost at once.

1. **Per-partition roles in the assignment protocol.** A group gets a `standby.replicas` count
   (default 0 — today's behavior). The assignment maps each partition to one **active** member and
   up to N **standby** members on distinct members, extending the three partition-only shapes:
   `MemberAssignment` (wire record), `PartitionAssignment` (assignor output), and
   `PartitionAssignmentContext` (assignor input). Balanced anti-affine placement yields the
   crossed-pair topology (2 partitions, 2 members: each active for one, standby for the other —
   one death makes the survivor active for both).
2. **A standby task is the same `Task`, gated passive at the barrier.** It owns its own RocksDB
   shard, folds every record, and takes local atomic cuts exactly as an active does — but at the
   commit barrier it skips `cut.publish()` and `commitOffset(...)`: state persists durably,
   nothing is emitted, nothing advances the group offset. Staged output is **discarded** at each
   cut.
3. **Standbys follow the committed frontier.** A passive shard folds only up to the group's
   committed offset for its partition (already delivered on every heartbeat as
   `committedOffsets`), never beyond. Everything it folded, the active already published *and*
   committed, so discarding staged output is safe by determinism (invariant 2), and the standby
   trails the active by about one commit interval.
4. **Promotion is a role flip; only a converged standby is promotable; handover is per partition.**
   Each heartbeat reports per-standby-partition **readiness** (converged to the frontier or not).
   On the active's session expiry the assignor promotes a *ready* standby: the runtime flips the
   existing shard — publishing and committing on, resume from the local cut. Re-folded records the
   dead active already published are reconciled by `SegmentDedup` and idempotent sink upserts —
   the existing crash-replay argument, across members. A cold member is **never** promoted; if no
   standby is ready, the partition waits (correctness over availability). Readiness changes
   trigger a (debounced) assignor run, so role migration is progressive, shard by shard.
5. **An empty joiner is a warming standby — same lifecycle, second entry point.** A joining member
   with no usable local state is assigned standby roles (capped, so it does not restore everything
   at once); existing actives are untouched (the assignor is already sticky). Per partition it
   restores from the snapshot store, tail-replays to the frontier, then follows it (decision 3),
   reporting readiness as each shard converges; the assignor hands over active roles progressively
   to rebalance the group. Restore is engineered for unbounded state: parallel file downloads,
   **resumable at file granularity**, throttled against co-located active shards. A member
   returning with an intact disk runs the same lifecycle, choosing its starting point by
   freshness: compare the local cut's offset against the newest manifest's offset and continue
   from whichever is higher — local state is usable only while the retained window still reaches
   its offset; otherwise download. Time-to-contribution is bounded by the smallest shard, never by node-total state.
6. **Snapshot store — composed from existing modules, not built.** The active snapshots each owned
   shard at a committed cut: a RocksDB checkpoint (hardlink, near-free) whose upload is
   **incremental at SST-file granularity** (SSTs are immutable; upload only files the store lacks
   — no engine fork needed), committed by writing a small **manifest** {live file set, consumed
   offset, dedup watermarks, member epoch} with a conditional put, so a demoted zombie's manifest
   write fails atomically. Transport and lifecycle reuse the monorepo: `zeebe/backup-stores`
   (S3/GCS/Azure/filesystem clients + testkit) and `zeebe/snapshot` (checksummed
   transient→persisted install, restorable stores). Pruning keeps the last verified manifest until
   a newer one is verified; unreferenced files are GC'd by manifest reference. The **retention
   contract** follows: the retained window must cover `snapshot interval + slack` — long-lived
   in-flight state is carried by snapshots, not retention. **Snapshot age vs. retained window is a
   first-class safety metric** (the invariant erodes silently if uploads stall).
7. **The only blocking restore is the all-copies-lost case** (active and all standbys die
   together, or first bootstrap). Its cost is managed by `standby.replicas` and placement (makes
   it an N-failure event), snapshot cadence (shrinks the tail replay, which competes with live
   fold throughput), and download parallelism (object-store restore is bandwidth-bound when reads
   parallelize across files).
8. **Fencing.** Epoch/ownership validation already rejects the zombie's offset commits; the
   manifest CAS (decision 6) fences its state durability. The serving sink stays unfenced in v1: a
   zombie can upsert stale full-values until its session times out, after which the promoted
   active's replay overwrites them — bounded, self-healing. The strict fix (epoch guard in the
   sink upsert) is deferred.

## Considered and rejected

- **Changelog-fed standbys** (state-change topic, compacted): a second replicated log and write
  amplification to recover something the snapshot store + source tail already determine.
- **Replay-from-start as bootstrap** (ADR 0002 Phase 1 as originally scoped): unsound once
  retention compacts past long-lived in-flight state. Retained only behind a guard: legal iff the
  partition's full history is within the retained window.
- **Disaggregated remote read path** (ForSt/Hummock-style: state files authoritative on object
  storage, local disk a lazy read-through cache): the only design where an empty joiner works
  immediately at any state size — but it requires a forked/custom storage engine plus an
  asynchronous state-access runtime to hide remote latency, a multi-year engineering program we
  will not build. **Adopt-not-build escape hatch**: the manifest + immutable-SST layout chosen in
  decision 6 is exactly the substrate such an engine consumes; revisit (e.g. ForSt via its Java
  bindings, or a caching filesystem under stock RocksDB) if restore SLAs in the all-copies-lost
  case ever become binding.
- **Tiered log storage** (offload old EB log segments to object storage; the log becomes logically
  infinite): fixes bootstrap *correctness* from the other side and is the only option that extends
  the new-dataset backfill horizon, but replay stays O(history) — it complements snapshots rather
  than replacing them. Not chosen now; noted as future broker work with independent value.
- **Peer-to-peer snapshot transfer** (promoted active streams state to the warming standby):
  members are gateway clients with no member-to-member channel, and a store also survives the
  everyone-died case.
- **Fold-ahead standbys** (passive shard runs to the log head): would hold sealed segments the
  active has not yet published — discard loses data on promotion, so they would need durable
  retention plus a gap protocol. Frontier-following makes discard trivially safe.
- **Active-active emission**: the sink flip-flops between two "latest" values while members
  diverge; window finalization becomes ambiguous.

## Consequences

- Protocol change in three places plus heartbeat additions (role per assignment entry, readiness
  per standby partition); group-level `standby.replicas`; readiness-triggered assignor runs.
- A shared snapshot store becomes a deployment dependency, but its client/lifecycle code is reused
  from `zeebe/backup-stores` and `zeebe/snapshot`; the new code is the checkpoint-diff-upload task
  and the restore path.
- Each standby partition duplicates fetch traffic and fold compute — the deliberate price of
  takeover-in-one-commit-interval without a changelog.
- **Determinism becomes contractual.** Invariant 2 is what makes promotion safe; it needs a test
  that two independent folds of the same topic produce identical envelope sequences, so a future
  change (e.g. hash-order iteration in the seal path) cannot silently break failover.
- **Local persistence becomes a performance choice, not a correctness requirement.** Once every
  shard is re-derivable from (snapshot + tail), members can run on ephemeral disks — a pod
  reschedule is absorbed by standby promotion while the new pod warms as a joiner. Persistent
  volumes remain worthwhile for cheap rolling restarts (intact-disk fast path) but are no longer
  mandatory.
- Cross-member failover needs real integration tests — none exist today (single-runtime rebalance
  is covered by `StreamRuntimeRebalanceTest`; multi-member promotion and progressive joiner
  handover are not).

## Follow-up work items

1. Protocol + assignor: roles in `MemberAssignment`/`PartitionAssignment`/context,
   `standby.replicas`, anti-affine standby placement, ready-only promotion, warming caps,
   readiness in the heartbeat, readiness-triggered (probing) assignor runs.
2. Client: role in the rebalance listener delta; surface the committed frontier and readiness
   reporting.
3. Runtime: passive gate in `PartitionActor`/`PartitionCommitter` (skip publish + commit, keep
   persist), frontier throttle, role-flip transitions, single standby lifecycle with
   restore/warm-local entry points; guard the existing replay-from-start path behind the
   retained-window check.
4. Snapshot subsystem: RocksDB checkpoint at a committed cut; SST-level incremental upload;
   manifest with offset/watermarks/epoch + conditional put; parallel resumable throttled restore;
   pruning/GC; snapshot-age-vs-retention monitoring. Reuse `zeebe/backup-stores` +
   `zeebe/snapshot`. Start with full-checkpoint uploads (Zeebe-style, pure reuse); SST-file-name
   dedup is a later bolt-on that changes nothing about manifest, restore, or GC.
5. Tests: determinism contract test (two folds → identical envelopes); multi-member failover IT
   (active dies → ready standby promoted → sink converges; cold member never promoted); joiner IT
   (empty member warms and receives actives progressively).
6. Deferred: sink-side epoch fence; disaggregated read path (adopt-not-build) and tiered EB log
   segments as independent future tracks.

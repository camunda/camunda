# ADR 0006 — Standby tasks: warm cross-member failover

- Status: Accepted — state-transport core (decisions 2, 3, 6 below) **superseded by
  [ADR 0009](0009-state-changelog-on-compacted-topics.md)** at time of writing; sign-off for that
  supersession is granted by this document (see "Supersession sign-off" below). The
  assignment-protocol surface (decision 1) is **not superseded** — it is this ADR's remaining,
  load-bearing content, implemented as-is by the failover milestone.
- Date: 2026-07-16 (reconstructed — see "Documentation-gap note")
- Scope: `event-bridge-consumer-groups` (assignment protocol, heartbeat wire types, assignor),
  `event-bridge-streaming` (runtime standby lifecycle)
- Builds on: the existing `event-bridge-consumer-groups` join/heartbeat/rebalance protocol and its
  `BalancedStickyAssignor`

## Documentation-gap note

This ADR was designed and referenced (by [ADR 0009](0009-state-changelog-on-compacted-topics.md)
and the module's ADR index) before it was ever committed as a file — a session-continuity gap.
This document reconstructs its decisions from that prior design work so the repository has a
citable record before the failover-milestone implementation proceeds, per this module's own rule
that architectural changes must be backed by an ADR. Nothing here is invented after the fact:
decision 1 (the assignment-protocol surface) is exactly what ADR 0009 decision 7 already commits
to retaining, and what the failover-milestone plan already specified as settled design.

## Context

A partition's active member is a single point of unavailability: on member loss, the group must
rebalance the partition onto a surviving member, which then has to build up state from nothing
before it can resume serving. For fast failover, some members should sit **warm** — already
running against the partitions they'd be promoted into — so promotion is a role flip, not a cold
start.

This requires two independent things:

1. A **protocol surface**: the assignment must express *which* members hold *which* role for a
   given partition, placed so a partition's standbys are not co-located with its active (anti-
   affinity — a host failure must not take out a partition and all its warm replacements at once),
   and promotion must never pick a standby that has not caught up.
2. A **state-transport mechanism**: however a standby gets warm, it needs the partition's state
   without disrupting the active.

This ADR originally specified both together, with (2) built as source-fed replication (every
standby re-fetches and re-folds the source topic, backstopped by a snapshot store for state
outside the source's retained window). [ADR 0009](0009-state-changelog-on-compacted-topics.md)
replaces (2) with changelog byte-application, for the reasons recorded there (byte-apply
standbys instead of duplicate fold compute, no snapshot store, no source-retention coupling). That
replacement is *only* of the transport; the protocol surface below is transport-agnostic and
carries over unchanged, as ADR 0009 decision 7 already states.

## Decision

### 1. Assignment-protocol surface (retained, not superseded)

- **Role per partition per member.** An assignment entry is not just "member X owns partition P"
  but "member X owns partition P with role ACTIVE or STANDBY." Exactly one member holds ACTIVE for
  a given partition at a time; zero or more hold STANDBY.
- **Group-level `standby.replicas` config**, default `0` (today's behavior: no standbys, byte-
  identical wire/decoding to groups that never opt in).
- **Anti-affine placement.** The assignor must not place a partition's standby replicas on the
  same member as its active, nor (when there is placement-topology information available) on the
  same failure domain as the active or as each other, so a single member/host loss cannot
  simultaneously remove a partition's active and all of its warm standbys.
- **Sticky.** Role assignment participates in the existing sticky-assignment discipline: a member
  already warm on a partition keeps that role across rebalances that don't require moving it,
  the same way plain ownership is preserved today.
- **Readiness reporting.** Each member reports, per standby partition it holds, a readiness signal
  (changelog lag under ADR 0009's transport). The coordinator retains the latest reported value per
  (group, member, partition).
- **Ready-only promotion.** When a partition's active member is lost, the assignor promotes a
  standby to ACTIVE only if that standby has reported readiness. A cold (never-reported, or
  reported-far-behind) standby is never promoted. If no standby for the partition is ready, the
  partition is left unassigned (correctness over availability) until one becomes ready or a fresh
  member cold-rebuilds — the assignor does not fall back to promoting an unready member.
- **Readiness-triggered assignor runs.** A readiness change (a standby crossing from not-ready to
  ready) is a legitimate trigger for a debounced assignor run, independent of membership churn —
  so a partition stuck unassigned for want of a ready standby gets promoted as soon as one catches
  up, without waiting for the next unrelated rebalance.
- **Warming caps.** The number of partitions concurrently warming (cold-building via whatever
  transport is in effect) is bounded per member, so a newly joined member does not attempt to warm
  every partition it might eventually stand by for all at once.

### 2/3/6. State-transport core — superseded

Originally: source-fed passive replicas (every standby independently fetches and folds the source
topic, exactly like an active would) plus an object-store snapshot subsystem covering state older
than the source's retained window, with snapshot-age-vs-retention as a monitored safety metric.

**Superseded by [ADR 0009](0009-state-changelog-on-compacted-topics.md) decisions 5-6**: a standby
tails the partition's changelog and applies cut-atomic byte deltas to its own store; disaster
rebuild replays the changelog from its start (bounded by live keyspace, not history); no snapshot
store, no fold-determinism contract.

### Supersession sign-off

ADR 0009 required explicit sign-off before any standby implementation could start, because the
state-transport replacement was originally owned by a workstream separate from this one. That
condition is satisfied by this document: the same author line owns both ADRs, the transport switch
described above is the one actually implemented by the failover milestone, and no source-fed
replica or snapshot-store code exists anywhere in this codebase for the switch to conflict with.
This ADR's Status line is the sign-off record ADR 0009 asked for.

## Consequences

- The assignment protocol grows a `Role` dimension and a `standby.replicas` group config; every
  wire type that carries assignment or group config must decode byte-compatibly for groups that
  never set it (default `0`/no roles, matching today's behavior exactly).
- The coordinator gains ephemeral, heartbeat-derived readiness bookkeeping per (group, member,
  partition); unlike the assignment itself this does not need to be a durable, Raft-replicated
  decision - only the promotion outcome does.
- "Ready-only promotion" trades some availability (a partition can sit unassigned) for the
  correctness property that a promoted member is never behind — this is intentional and mirrors
  the halt-instead-of-continue discipline ADR 0009 decision 4 uses on the transport side.

## Considered and rejected

- **Promote whichever standby is co-located/least-loaded, ignoring readiness.** Faster failover on
  paper, but risks promoting a member with stale or absent state — rejected, matches ADR 0009's
  correctness-over-availability stance.
- **No anti-affinity, rely on random spread.** Cheaper to implement, but does not protect against
  the exact failure mode standbys exist to survive (a host taking out both roles at once).

## Follow-up work items

1. Extend the assignment wire types (heartbeat request/response, replicated `MemberAssignment`/
   `MembershipRecord`/`GroupState`/`MemberState`) with `Role` and `standby.replicas`, byte-
   compatible with existing decoders (see the `CleanupPolicy` precedent in
   `event-bridge-protocol`).
2. Widen `PartitionAssignor.PartitionAssignmentContext` with per-member role/placement information
   sufficient for anti-affine standby placement.
3. Ephemeral per-(group, member, partition) readiness bookkeeping in the coordinator, and a
   debounced assignor trigger on readiness change.
4. Runtime-side promotion/demotion lifecycle over ADR 0009's `ChangelogApplier` (drain to the
   last marker, close the applier, hand the open stores to the fold path at source X+1).


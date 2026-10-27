# Model partition-group changes as a dependency graph

**DRI**: Panagiotis Goutis

**Status**: Proposed

**Purpose**: Defines how a partition-group configuration change's operations are ordered, and why
that model is scoped to a single partition group rather than the whole plan.

**Audience**: Contributors working on `zeebe/dynamic-config` change execution, and anyone adding a
new adopter (a transformer producing a `PartitionGroupPhase`).

## Context

A partition-group change plan (`PartitionGroupPhase`) ran its operations as one strict FIFO queue:
one at a time, in submission order, even when two operations touched disjoint state and had no
reason to wait on each other (e.g. two different brokers' mode changes, or two different
partitions' restores on the same broker). Every new concurrency axis considered for this — broker,
partition, group — would have meant nesting a new container level with its own
`Sequential | Parallel` marker and its own eligibility tier, one per axis (see "Alternatives").

## Decision

**D1. Model a change as operations with declared dependencies, not nested containers.**
`OperationGraph` holds a set of operations plus, per operation, the `OperationId`s it
`dependsOn`. Two operations with no dependency path between them may run at the same time — that
is the whole concurrency model. Adding a concurrency axis costs nothing structurally: it is fewer
edges, not a new container type. `PartitionGroupPhase` now always carries one `OperationGraph` per
group; `DependencyChangePlan` tracks progress against it (which `OperationId`s are complete) the
same way `ClusterChangePlan` already tracks progress against a queue, and a new
`GraphScopeReconciler` drives it by starting every operation `runnableFor` the local member, in
place of the old reconciler's one-at-a-time contract. How many of those a broker starts at once is
capped, which is a runtime policy knob and not a correctness bound: the graph says what *may* run
together, and the cap says how much of that one broker chooses to take on. A transformer with
nothing to parallelize gets the old behaviour for free via `OperationGraph.sequential(...)`, which
chains every operation behind its predecessor.

**D2. Correctness of the edges is the graph author's responsibility; it is not validated.**
A missing edge is not rejected, it is executed. The rule that makes today's adopters safe — the
broker applying an operation must be the only writer of the entry it writes — is documented on
`OperationGraph`, not enforced, and two operation shapes that violate it
(`MemberRemoveOperation`, `PartitionForceReconfigureOperation`) are named there as out of scope for
concurrent execution until write-set validation exists or the merge rule changes.

**D3. Every sub-configuration runs a graph; phase sequencing stays.**
`GlobalConfiguration` runs a `DependencyChangePlan` too, so nothing executes the queue model. The
gain is not parallelism — a cluster-wide change has none to declare — but that one execution model
means one plan lifecycle, one merge path and one driver, instead of a class of bug where a check
written for one model silently does the wrong thing for the other.

Phase boundaries are *not* collapsed into the graph. A boundary is expressible as "every operation
in phase B depends on every operation in phase A", but such a graph spans sub-configurations, and
progress lives in each sub-configuration's own pending change — which is also its unit of merge and
of versioning. Collapsing therefore means moving progress up to the plan, and with it the merge
semantics, the driver's staleness anchor, phase advancement and the dry-run: its own change, with
its own ADR. Its one prerequisite is done — a graph node names the partition group it targets, so a
graph spanning sub-configurations is expressible.

`ClusterChangePlan` survives as data, not as an executor: it is the shape the pre-8.10
`ClusterTopology` message carries, which the single-group `ClusterConfiguration` is encoded as for a
broker without the graph model. That configuration is scheduled for removal on its own timeline, and
the queue model goes with it.

## Alternatives considered

- **Nested containers, keyed by member then partition** (a companion proposal considered
  alongside this one). Reaches the same broker- and partition-parallelism by
  nesting `Sequential | Parallel` steps. Rejected: each concurrency axis needs its own container
  level and its own eligibility tier — `AwaitModeChangeApplier` writing several partitions of its
  own member qualifies under one tier and not the other, a distinction with no meaning outside the
  encoding — and a barrier between two container levels over-synchronises, forcing every operation
  in the first to finish before any in the second starts even when only same-broker pairs are
  actually related.
- **Collapse `PhasedChangePlan` into the graph now.** Rejected for this change (see D3); tracked as
  a follow-up once `PartitionGroupOperation` carries a `groupId()` and the wire migration it needs
  has its own investigation.

## Consequences

- `PhasedChangePlan.Phase` stays `GlobalPhase | PartitionGroupPhase`, and `ChangePlan` is a sealed
  interface over `ClusterChangePlan` and `DependencyChangePlan`. Every sub-configuration's pending
  change is a `DependencyChangePlan`; the interface exists so the legacy projection and the
  reporting views can take either, not so an execution path can decide which model it is running.
  `GlobalPhase` still carries a flat operation list, converted to a sequential graph on activation,
  so a cluster-wide change cannot yet *declare* parallelism even though the driver would run it.
- One driver serves every scope. `GraphScopeReconciler` takes a `Scope` supplying what differs —
  where the plan lives, which applier runs an operation, how a completion is recorded — and the
  queue-shaped `ScopeReconciler` is gone. The global graph is sequential and so only ever offers one
  operation, but it runs through the same machinery deliberately: a driver that assumed one would
  need rewriting the first time a cluster-wide change declares two independent operations, and two
  drivers for one execution model is how the subtle parts — re-entrancy, per-operation backoff,
  reclaiming state from a cancelled plan — drift apart.
- Every partition-group transformer emits a graph. Those with no parallelism to declare call
  `OperationGraph.sequential(...)` and execute exactly as they did as queues, including
  `PhysicalTenantProvisioningInitializer`, which starts a change directly on a group it has just
  created rather than through a phase.
- The legacy single-group view carries whichever model the sub-configuration it projects is
  running, so a consumer reading it in-process sees the real change. It is rendered as a queue only
  where the wire demands one — `ClusterChangePlan.flatten`, when the legacy `ClusterTopology`
  message is encoded — and that rendering is lossy by nature of the queue model, not by defect: it
  cannot express that several operations are running at once. It is also not a reporting view:
  `toLegacyDefault()` is dual-written into the legacy gossip field, so on a mixed-version cluster
  the synthetic plan version minted there is a live input to an old broker's
  `ClusterChangePlan#merge`, and that broker executes the flattened queue head-first — which is why
  the flattening orders operations by dependency rather than by operation id.
- Wire format: `PartitionGroupConfiguration`'s and `GlobalConfiguration`'s pending-change fields
  keep their tags and change type from `ClusterChangePlan` to `DependencyChangePlan`, and
  `PlannedOperation`'s operation becomes a `oneof` over both operation kinds so a graph can carry
  cluster-wide operations — field 2 keeps its tag and type, so graphs written before that still
  decode. Because either kind can now travel in either scope's graph, the decoder validates the kind
  against the enclosing sub-configuration rather than letting it fail later at the driver's cast. That is a breaking change, taken deliberately
  because the graph model has not been released, so no persisted or gossiped configuration carries
  the old type on that tag. Compatibility *within* this change lives one level up instead, in
  `PhasedChangePlanPhase`: the pre-merge `PartitionGroupParallelPhase` is kept as a decode-only arm,
  so a configuration written before the queue phase and the graph phase became one type still
  decodes — a flat operation list is exactly a sequential graph.
- Every additional concurrency axis a future adopter wants is a change to that adopter's own edges,
  not a change to `OperationGraph`, `DependencyChangePlan`, or `GraphScopeReconciler`.

## Source

- [Issue #60302](https://github.com/camunda/camunda/issues/60302)
- Reviewed by Deepthi Devaki Akkoorath, DRI of [ADR-0001](0001-multi-partition-group-cluster-configuration.md)


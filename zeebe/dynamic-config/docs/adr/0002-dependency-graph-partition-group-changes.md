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

**D3. Scope the graph to one partition group's plan; leave phase sequencing and the single-group
queue model untouched.**
`PartitionGroupOperation` carries `memberId()` and, for some operations, `partitionId()`, but no
`groupId()` — a group's identity exists only as `PartitionGroupPhase`'s map key. Because of that,
collapsing `PhasedChangePlan`'s phase boundaries into the graph itself (a phase boundary is
expressible as "every operation in phase B depends on every operation in phase A") is possible in
principle but needs two things this change does not do: adding `groupId()` to every
`PartitionGroupOperation`, and a wire migration from the phase-shaped format to a flat one that has
not been investigated. Doing that here would mean designing and shipping an unrelated data-model
change and an unscoped wire migration alongside the execution model change. `ClusterChangePlan` —
the queue model — is left as it is for the same reason, in the two places it still backs: the
single-group `ClusterConfiguration`, which is scheduled for removal on its own timeline, and
`GlobalConfiguration`, whose operations are cluster-wide broker lifecycle steps taken one at a time
by construction. Neither needs graph semantics: the first because it is going, the second because
its operations have nothing to depend on.

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

- `PhasedChangePlan.Phase` stays `GlobalPhase | PartitionGroupPhase`, and `ChangePlan` becomes a
  sealed interface over `ClusterChangePlan` and `DependencyChangePlan`. The two never meet in one
  place: a partition group's pending change is always a `DependencyChangePlan`, the global
  configuration's is always a `ClusterChangePlan`. The interface exists so the legacy projection and
  the reporting views can take either — not so a group's execution path can decide which model it is
  running. Carrying both on a group would have meant two plan lifecycles, two merge paths and two
  reconcilers per group, plus a class of bug where a check written for one silently does the wrong
  thing for the other. The group-scoped queue reconciler is gone with it, and `ScopeReconciler` now
  serves the global configuration only.
- Every partition-group transformer emits a graph. Those with no parallelism to declare call
  `OperationGraph.sequential(...)` and execute exactly as they did as queues, including
  `PhysicalTenantProvisioningInitializer`, which starts a change directly on a group it has just
  created rather than through a phase.
- The legacy single-group view renders a graph as a queue via
  `CurrentClusterConfiguration.toQueueProjection`, which is lossy — it cannot express that several
  operations are running at once — by nature of the queue model it renders into, not a defect in the
  projection. It is also not purely a reporting view: `toLegacyDefault()` is dual-written into the
  legacy gossip field, so on a mixed-version cluster the synthetic plan version minted here is a
  live input to an old broker's `ClusterChangePlan#merge`, and that broker would execute the
  flattened queue sequentially.
- Wire format: `PartitionGroupConfiguration`'s pending-change field keeps its tag and changes type
  from `ClusterChangePlan` to `DependencyChangePlan`. That is a breaking change, taken deliberately
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


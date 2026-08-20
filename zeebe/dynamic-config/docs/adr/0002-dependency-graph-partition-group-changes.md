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
place of the old reconciler's one-at-a-time contract. A transformer with nothing to parallelize
gets the old behaviour for free via `OperationGraph.sequential(...)`, which chains every operation
behind its predecessor.

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
the queue model still backing the single-group `ClusterConfiguration`, which is scheduled for
removal on its own timeline — is left as it is for the same reason: it does not need graph
semantics before it goes.

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
  sealed interface over `ClusterChangePlan` and `DependencyChangePlan` so
  `CurrentClusterConfiguration`'s phase-completion and scope-reconciliation logic can dispatch on
  which one a group is running.
- A consumer that needs one uniform view across both models (REST change view, restore status,
  metrics) renders a graph as a queue via `CurrentClusterConfiguration.toQueueProjection`, which is
  lossy — it cannot show that several operations are running at once — by nature of the queue
  model it renders into, not a defect in the projection.
- The wire format gains a `oneof` between the flat operation list and the graph so existing
  `PartitionGroupConfiguration` messages still decode; no adopter writes a graph until it migrates,
  so there is nothing to migrate from on the write side.
- Every additional concurrency axis a future adopter wants is a change to that adopter's own edges,
  not a change to `OperationGraph`, `DependencyChangePlan`, or `GraphScopeReconciler`.

## Source

- [Issue #60302](https://github.com/camunda/camunda/issues/60302)
- Reviewed by Deepthi Devaki Akkoorath, DRI of [ADR-0001](0001-multi-partition-group-cluster-configuration.md)


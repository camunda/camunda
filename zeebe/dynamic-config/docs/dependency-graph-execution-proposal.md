# Solution proposal: declared operation dependencies in dynamic cluster configuration

**Author**: Panagiotis Goutis
**Reviewers**: Deepthi Devaki Akkoorath
**Status**: Draft — for discussion
**Issue**: [#60302](https://github.com/camunda/camunda/issues/60302)
**Alternative to**: [parallel member execution proposal](./parallel-member-execution-proposal.md),
which solves the same problem by nesting containers instead

> **As implemented, this proposal's write-set safety net does not exist.** The "Eligibility" section
> below and the `state/` implementation note under "Implementation notes" describe a disjoint-write-set
> check that was built, then removed: it could not protect the orderings that mattered most (a
> pre-restore before a restore writes no configuration state at all, so the check saw it as safely
> concurrent regardless of any edge), and none of the operations the two current adopters — restore
> and mode change — declare need it, since both name every edge by hand. `WriteSet` and `WriteSets`
> are gone from `state/`; `OperationGraph.of` validates only that the graph is acyclic and that every
> `dependsOn` id exists. Ordering correctness is the transformer author's alone — see
> `OperationGraph`'s class javadoc, which documents the two traps this check used to catch
> automatically. Left the sections below as they were reasoned through, since the write-set idea and
> why it falls short of a real safety net is itself the point of "Why write-set analysis alone cannot
> replace declared edges" — but nothing past this note is current behavior.

## Summary

Model a configuration change as **operations with declared dependencies** rather than as a sequence
of containers. Concurrency becomes the *absence of an edge* instead of the *presence of a container*,
which makes broker-parallel, partition-parallel and group-parallel execution the same thing, and
makes any future axis free.

## Why a second proposal

The companion proposal reaches broker parallelism by nesting: a plan holds steps, a step is
sequential or parallel, a parallel step is keyed by member, and a member's entry is keyed by
partition. It works, but the cost is structural and recurring:

|   Axis added    |         What it costs in the nesting model         |
|-----------------|----------------------------------------------------|
| broker          | a container level, a `Sequential\|Parallel` marker |
| partition       | another container level, a second eligibility tier |
| anything future | another level, another marker, another tier        |

The two eligibility tiers are the clearest symptom. "Member-scoped" and "partition-scoped" only exist
as separate concepts because the container tells you *how* operations run rather than *why* they must
be ordered. `AwaitModeChangeApplier` writes several partitions of its own member, so it qualifies
under one tier and not the other — a distinction with no meaning outside the encoding.

The nesting also **over-synchronises**. A barrier between the pre-restore container and the restore
container forces every pre-restore in the group to finish before any restore begins, even though a
broker's restore of partition 1 depends only on its own pre-restore of partition 1.

## The model

```java
public record DependencyChangePlan(
    long id,
    Status status,
    Instant startedAt,
    SortedMap<OperationId, PlannedOperation> operations,   // immutable for the plan's lifetime
    SortedMap<OperationId, Instant> completed) {           // grow-only

  public record PlannedOperation(
      ClusterConfigurationChangeOperation operation,
      SortedSet<OperationId> dependsOn) { }

  /** Stable, comparable, assigned once when the coordinator builds the plan. */
  public record OperationId(int value) implements Comparable<OperationId> { }
}
```

Everything else is derived, never stored:

```java
boolean isComplete(OperationId id)          // completed.containsKey(id)
boolean isRunnable(OperationId id)          // not complete, and every dependsOn is complete
List<PlannedOperation> runnableFor(MemberId m)
boolean hasPendingChanges()                 // completed.size() < operations.size()
List<Operation> pendingOperations()         // not completed, in stable order
List<CompletedOperation> completedOperations()
```

### Execution

A broker asks its sub-configuration for `runnableFor(localMemberId)` and applies what it finds,
recording each completion as it lands. There is no index to advance, no barrier to release, and no
notion of a current step.

**The degree of concurrency is a runtime policy, not a property of the plan.** A broker may cap how
many runnable operations it starts at once. This matters: ship the model with the cap at 1 and
behaviour is identical to today, then raise it after measurement — without touching a single plan.
In the nesting model, concurrency is baked into the plan's shape, so changing your mind means
changing what transformers emit.

### Merge

`operations` is immutable and identical across copies of the same plan. `completed` is grow-only, and
each entry has exactly one writer — the broker that owns the operation (`operation.memberId()`).
Merge is therefore a flat union with earliest-timestamp-wins on collision:

```java
merge(other) = new DependencyChangePlan(id, status, startedAt, operations,
                                        union(completed, other.completed))
```

Commutative, associative and idempotent by construction. This is the same shape as the existing
per-member merges in `GlobalConfiguration.members` and `PartitionGroupConfiguration.members`, and it
is flatter than the nesting model's `Map<MemberId, Map<PartitionId, List<Instant>>>`.

No plan-level version is needed. The sub-configuration version continues to move only at plan
boundaries.

### Completion

The plan is complete when every operation is complete. The end-of-plan work (clear `pendingChanges`,
set `lastChange`, drop `LEFT`/partition-less members, bump the sub-configuration version) is a pure
function of converged state, so any broker computing it agrees — provided the completion timestamp is
derived from `max(completed.values())` and never read from the wall clock. Two brokers observing
completion a moment apart would otherwise stamp different `completedAt` values on `lastChange`, which
the sub-configuration merges do not reconcile at equal versions, so they would never converge.

## Eligibility: one rule, no tiers

> Two operations may lack a dependency edge between them only if their **write sets are disjoint**.

That single sentence replaces the member-scoped/partition-scoped tiers. A write set is:

```java
record WriteSet(
    Set<MemberId> members,     // whose entries this operation writes — may be empty, may be several
    OptionalInt partitionId,   // present when the write is confined to one partition of those members
    boolean subConfigFields)   // routingState, incarnationNumber, availability, distributor config
```

Two write sets are disjoint iff neither touches `subConfigFields`, and either their member sets are
disjoint, or both carry a `partitionId` and the two differ.

**The write set is not derivable from `operation.memberId()`** — that field is the *acting* broker,
which is not always what gets written. Two operations prove it:

- `MemberRemoveOperation(memberId, memberToRemove)` dispatches to
  `MemberLeaveApplier(op.memberToRemove(), …)`, so it writes a *different* member's entry than the one
  applying it.
- `PartitionForceReconfigureOperation` loops `group.members().keySet()` and calls `updateMember` for
  every one of them, so its write set is the whole group.

`writeSet(operation)` therefore has to be a real switch over the sealed hierarchy — sitting next to
the switch in `ConfigurationChangeAppliersImpl` that picks the applier, so the two stay visibly in
sync. A wrong entry there is a silent correctness bug, which is an argument for deriving it from the
applier where possible rather than restating it.

Checked at plan construction (every unordered pair) and again during the coordinator's existing
dry-run simulation, before anything is persisted or gossiped.

### Why write-set analysis alone cannot replace declared edges

Tempting shortcut: keep flat ordered lists and simply run any two operations concurrently when their
write sets are disjoint — zero transformer migration.

**It is unsafe, and more comprehensively than one example suggests.**

`PartitionReassignmentOperationsGenerator#bootstrapPartition` emits `PartitionBootstrap(primary, p)`
followed by `PartitionJoin(other, p)` for every other member. Those have *disjoint* write sets — each
writes only its own member's entry — but the join cannot precede the bootstrap, because the Raft
partition has to exist before anyone joins it.

Worse, **six of the twenty-five operations write nothing to the configuration at all.** Their
appliers return `UnaryOperator.identity()` from both `init` and `apply`; the entire effect is a
side effect elsewhere in the broker:

|           Operation            |                   Real effect                    |
|--------------------------------|--------------------------------------------------|
| `PreScalingOperation`          | engine-side preparation before partitions arrive |
| `PostScalingOperation`         | engine-side completion after they have           |
| `DeleteHistoryOperation`       | deletes history                                  |
| `AwaitRelocationCompletion`    | waits for relocation                             |
| `PartitionPreRestoreOperation` | drops local disk data                            |
| `PartitionRestoreOperation`    | restores a partition from backup                 |

For these, write-set analysis says "disjoint from everything" and would happily run `PreScaling`
concurrently with — or after — the scale-up it is supposed to precede. There is nothing in the
configuration to analyse.

So edges must be declared. Write-set disjointness stays as an additional automatic check that catches
a *state* race, but it cannot catch an *effect* race, and a quarter of the operations only have the
latter.

## Coverage: all 25 operations

Every operation in `GlobalChangeOperation` and `PartitionGroupOperation`, with the write set read
from its applier rather than inferred from the record. Nothing here is inexpressible; the column that
matters is how much of the ordering the write set can protect.

|                  Operation                  |                    Writes                    | Ordering protected by write set? |
|---------------------------------------------|----------------------------------------------|----------------------------------|
| `MemberJoinOperation`                       | own member entry                             | yes                              |
| `MemberLeaveOperation`                      | own member entry                             | yes                              |
| `MemberRemoveOperation`                     | **`memberToRemove`'s entry**                 | yes, once `writeSet` is right    |
| `UpdatePartitionDistributorConfigOperation` | global distributor config                    | yes — conflicts with all         |
| `PreScalingOperation`                       | **nothing**                                  | **no — declared edge required**  |
| `PostScalingOperation`                      | **nothing**                                  | **no — declared edge required**  |
| `UpdateRoutingState`                        | group routing state                          | yes — conflicts within group     |
| `UpdateIncarnationNumberOperation`          | group incarnation number                     | yes — conflicts within group     |
| `StartPartitionScaleUp`                     | group routing state                          | yes — conflicts within group     |
| `AwaitRedistributionCompletion`             | group routing state                          | yes — conflicts within group     |
| `AwaitRelocationCompletion`                 | **nothing**                                  | **no — declared edge required**  |
| `DeleteHistoryOperation`                    | **nothing**                                  | **no — declared edge required**  |
| `ModeChangeOperation`                       | own member entry                             | yes                              |
| `AwaitModeChangeOperation`                  | own member entry, several partitions         | yes                              |
| `ExportingStateChangeOperation`             | own member entry, several partitions         | yes                              |
| `PartitionJoinOperation`                    | own member, one partition                    | partly — see bootstrap below     |
| `PartitionLeaveOperation`                   | own member, one partition                    | yes                              |
| `PartitionReconfigurePriorityOperation`     | own member, one partition                    | yes                              |
| `PartitionDisableExporterOperation`         | own member, one partition                    | yes                              |
| `PartitionDeleteExporterOperation`          | own member, one partition                    | yes                              |
| `PartitionEnableExporterOperation`          | own member, one partition                    | yes                              |
| `PartitionBootstrapOperation`               | own member, one partition                    | partly — the join depends on it  |
| `PartitionForceReconfigureOperation`        | **every member of the group**, one partition | yes — conflicts with all         |
| `PartitionPreRestoreOperation`              | **nothing**                                  | **no — declared edge required**  |
| `PartitionRestoreOperation`                 | **nothing**                                  | **no — declared edge required**  |

Two conclusions:

1. **No operation is inexpressible.** `MemberRemove` and `PartitionForceReconfigure` need a write set
   richer than "my own entry", which the record above provides; `ForceReconfigure` conflicting with
   its whole group is correct, since it is a recovery operation that should serialise.
2. **Six operations have no configuration write at all**, so declared edges are not a nicety for
   them — they are the *only* thing ordering them. That is the strongest argument in this proposal:
   an encoding that derives ordering from structure or from state analysis cannot express a quarter of
   the existing operation set correctly.

## Restore, worked

Cluster-wide restore, per partition group:

```
preRestore(m, p)      depends on ∅
restore(m, p)         depends on { preRestore(m, p) }
modeChange(m)         depends on { restore(m, p) : p ∈ partitions(m) }
awaitModeChange(m)    depends on { modeChange(m) }
updateIncarnation     depends on { awaitModeChange(m) : m ∈ members }
```

Five lines, no markers, no containers. All three axes fall out:

- **per broker** — no edges between different members' operations
- **per partition** — no edges between different partitions of the same member
- **per group** — each group has its own plan, driven concurrently (existing behaviour, unchanged)

And it is **strictly more parallel than the barrier model**: broker 0 begins restoring partition 1 as
soon as its own pre-restore finishes, while broker 1 is still pre-restoring partition 2. The barrier
version made every pre-restore in the group wait for every other.

> **Question this surfaces for the restore owner.** The current plan is phase-major — all
> pre-restores, then all restores. Is that load-bearing (must every partition be wiped before any is
> restored, so a mid-way failure cannot leave mixed generations) or incidental? If load-bearing, it is
> one extra edge set and should be declared. Today nobody has had to say, because list order hid the
> question. Making it explicit is the point.

### Other adopters

- **`bootstrapPartition`** — `join(m, p)` depends on `bootstrap(primary, p)`; the joins have no edges
  among themselves and currently run one at a time.
- **`ModeChangeRequestTransformer`** — `awaitModeChange(m)` depends on `modeChange(m)`, nothing else.
  `2N` round trips become two levels of a graph.

## Scope

### Minimal — recommended

Only the sub-configuration plan changes. `PartitionGroupConfiguration.pendingChanges` and
`GlobalConfiguration.pendingChanges` hold a `DependencyChangePlan` instead of a `ClusterChangePlan`;
phases keep sequencing across sub-configurations; ADR-0001's per-group independence for merge,
failure and retry is untouched.

`ClusterChangePlan` is **not modified** — it stays with the single-group `ClusterConfiguration`,
which is scheduled for removal. (It is referenced from 12 files; the ones outside the state package
read it through the single-group projection and keep working.)

### Larger — later, if wanted

A phase boundary is expressible as edges: every operation in phase *B* depends on every operation in
phase *A*. So `PhasedChangePlan` could eventually collapse into the same model, leaving exactly one
sequencing concept in the system. Worth stating as the direction; not worth bundling.

## Implementation notes

### `state/`

- `DependencyChangePlan` + `PlannedOperation` + `OperationId` as above.
- ~~`WriteSet` and `writeSet(ClusterConfigurationChangeOperation)`.~~ Built, then removed — see the
  note at the top of this document.
- Plan construction validates: no cycles (topological sort) and every `dependsOn` id exists. ~~and
  every unordered pair without a path between them has disjoint write sets~~ — removed along with
  `WriteSet`; a missing edge between two operations that may run concurrently is not rejected.
- `pendingChangesFor(MemberId)` becomes `runnableFor(MemberId)` and returns a *collection*. It must
  be a genuine per-member lookup — today both sub-configurations implement it as "if the plan has
  pending changes for this member, return `nextPendingOperation()`", which is only sound while one
  broker is ever eligible. The moment several are, that hands a broker another broker's operation.

### `ClusterConfigurationManagerImpl`

- `onGoingGroupOperation`, `shouldRetryGroup` and `groupBackoffRetry` are keyed by `groupId` today
  (one in-flight operation per broker per group). They become keyed by `(groupId, OperationId)`.
- The apply loop starts every runnable operation for the local member, up to the concurrency cap.
- **Do not hoist the configuration read out of the loop.**
  `applyPartitionGroupConfigurationChangeOperation` captures
  `persistedCurrentConfiguration.getConfiguration()` at the top and uses it for the `init` write.
  That is safe today only because each call re-enters and re-reads, and
  `updateLocalCurrentConfiguration` persists synchronously within the turn. Reading once and looping
  reintroduces a lost update on the init transform.
- Completion detection must run on gossip rounds that change nothing.
  `onGossipReceivedCurrent` short-circuits on an unchanged merge; if end-of-plan work only runs on the
  changed branch, a plan whose final persist failed is never retried once the cluster converges. Gossip
  syncs every `DEFAULT_SYNC_DELAY` (10s), so running the check on every round suffices and needs no
  timer.
- Advancement triggered from a merge must be dispatched through `updateMultiConfiguration`, not
  applied inline — the update path is re-entrant, and `maybeAdvancePhase` is only safe because it
  posts through `executor.run`.

### `changes/`

- The eligibility check moves into `ConfigurationChangeCoordinatorImpl`'s existing simulation.
- `PartitionGroupConfigurationChangeApplier` and `GlobalConfigurationChangeApplier` should state that
  the sub-configuration is **not** stable across `apply()` — peers' results arrive by gossip
  mid-apply, and only the applier's own write set is stable. Neither interface says this today.

### Serialization

A flat repeated field of `(id, operation, dependsOn[])` plus a repeated `(id, completedAt)`. Simpler
proto than nested maps. Sorted collections must be re-sorted and de-duplicated on decode, or the
decoded record is semantically equal but fails `equals`, which breaks the manager's no-op
short-circuit.

### Transformer migration

A helper preserves today's behaviour exactly, one line per transformer:

```java
// each operation depends on the previous — identical to the current flat ordered list
DependencyChangePlan.sequential(op1, op2, op3, …)
```

Every existing transformer converts mechanically and behaves as before. Adopters then relax edges
where they know it is safe. This is the property the nesting model cannot offer: there, each new axis
is a structural change to the plans themselves.

Transformers to audit when relaxing: `PartitionReassignRequestTransformer`,
`ClusterScaleRequestTransformer`, `ForceScaleDownRequestTransformer` and the zone transformers all
carry ordering that is currently implicit in list order.

## What is harder than the nesting model

Stated plainly, because these are the reasons to say no.

1. **Transformers must think about dependencies.** Today ordering is free — you write a list. The
   `sequential(…)` helper makes migration behaviour-preserving, but any transformer that *wants*
   parallelism has to reason about what genuinely depends on what. That is more thought, and the
   `bootstrap`/`join` case shows the reasoning is not always about configuration state.
2. **Diagnosing a stuck plan is less eyeballable.** "Step 3 of 7" is easier to read than a graph. The
   change view needs to answer *which operations are incomplete and what each is waiting on*, or
   operators lose ground. This is a real ops requirement, not a nicety.
3. **Cycle and conflict validation is new code** with no counterpart today — the nesting model gets
   acyclicity for free from its structure.
4. **Deterministic display order.** Topological order is not unique; the change view must tiebreak on
   `OperationId` so two brokers render identically.
5. **It is a larger conceptual step for review** than adding a marker to an existing phase type.

## What gets easier

1. Any future axis of concurrency is free — no new level, marker or tier.
2. One eligibility rule instead of two tiers.
3. Flatter merge than the nesting model, with the same convergence argument.
4. Concurrency degree becomes a tunable runtime policy rather than plan shape, which is how the
   "does concurrent per-partition restore actually help?" question gets answered by measurement
   instead of by guessing before writing the plans.
5. Implicit ordering becomes explicit, surfacing real questions (restore's phase-major ordering)
   instead of letting list order hide them.

## Risks

- **Under-declared edges are silent**, and more so than originally scoped here: with `WriteSet`
  removed (see the note at the top of this document), nothing checks a missing edge at all any more,
  not even the same-member-writes case this section originally carved out as the one thing write-set
  analysis *could* catch. A transformer that forgets an edge produces a plan that is accepted and
  races — silently, since two operations writing the same entry both succeed and the sub-configuration
  merge just keeps one by version. Mitigation is unchanged: the `sequential(…)` default (nothing is
  parallel unless someone deliberately made it so), plus tests pinning each adopter's edges by hand.
- **Concurrent recovery entry is a behavioural change**, not just a faster one — brokers transition
  together rather than one at a time. Same caveat as the companion proposal; the mode-change
  transformer should stay sequential until the recovery owner reviews it.
- **Mixed-version clusters.** A broker predating this work reads the single-group gossip projection,
  which cannot represent concurrent progress. No new compatibility logic is proposed for a layer
  scheduled for deletion; treated as a release-scoping question, as in the companion proposal.
- **A member that disappears mid-plan stalls it.** Its operations never complete. Validate at
  construction that a plan does not both target a member and remove it; `cancelChange` stays the
  escape hatch.

## Comparison

|                            |    Nesting (companion proposal)     |   Declared dependencies (this)    |
|----------------------------|-------------------------------------|-----------------------------------|
| Broker parallelism         | parallel step keyed by member       | absence of an edge                |
| Partition parallelism      | second container level, second tier | absence of an edge                |
| Next axis                  | another level + marker + tier       | free                              |
| Eligibility rule           | two tiers                           | one rule                          |
| Completion state           | nested maps of prefixes             | flat map of ids                   |
| Restore parallelism        | barriers between phases             | strictly more — no false barriers |
| Concurrency degree         | baked into plan shape               | runtime policy                    |
| Transformer migration      | per-axis structural change          | one line, then opt in             |
| Ordering correctness       | implicit in containers              | declared, and cycle-checked       |
| Diagnosing a stuck plan    | step index — easy                   | needs blocked-on reporting        |
| Conceptual step for review | small                               | larger                            |

## Open questions

1. Is a declared partial order the right primitive here, or is it more machinery than the problem
   warrants? This is the decision; everything else follows.
2. Is restore's phase-major ordering load-bearing or incidental?
3. What should the default concurrency cap be, and is it per broker, per sub-configuration, or per
   operation kind?
4. Does the change view need to show the dependency structure, or is "incomplete, waiting on X"
   enough for operators?
5. Should `PhasedChangePlan` eventually collapse into this, or stay as the cross-sub-configuration
   sequencer indefinitely?

## References

- [Companion proposal: parallel member execution](./parallel-member-execution-proposal.md)
- [ADR-0001: Extend cluster configuration to support multiple partition groups](./adr/0001-multi-partition-group-cluster-configuration.md)
- [Withdrawn ADR-0002](./adr/0002-parallel-per-broker-change-operations.md)
- [Review thread](https://github.com/camunda/camunda/pull/60403)


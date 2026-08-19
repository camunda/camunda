# Solution proposal: parallel member execution in dynamic cluster configuration

**Author**: Panagiotis Goutis
**Reviewers**: Deepthi Devaki Akkoorath
**Status**: Draft — for discussion
**Issue**: [#60302](https://github.com/camunda/camunda/issues/60302)
**Replaces**: draft ADR-0002, withdrawn after review
([PR #60403](https://github.com/camunda/camunda/pull/60403))
**Alternative**: [declared operation dependencies](./dependency-graph-execution-proposal.md) — same
problem, modelled as a partial order over operations rather than as nested containers. Read both
before deciding; the two differ in what a *future* axis of concurrency costs, not in what this one
achieves.

## Problem

A configuration change is a queue of operations executed strictly one broker at a time
(`ClusterChangePlan#hasPendingChangesFor` returns true only for the head operation's member). The
shape *"run this on every broker, then continue once all of them are done"* has no representation,
so it is written as a sequential run of per-member operations and costs one round trip per broker.

Restore is the clearest case. Per partition group:

```
for each broker, for each of its partitions:  PartitionPreRestore
for each broker, for each of its partitions:  PartitionRestore
for each broker:                              ModeChange(PROCESSING)
for each broker:                              AwaitModeChange(PROCESSING)
once:                                         UpdateIncarnationNumber
```

With `N` brokers and `P` partitions each: `2·N·P + 2·N + 1` serialized round trips. Every broker
involved is out of processing mode for the partitions being restored, so nothing about restoring
broker A's copy requires broker B's to be finished first. The serialization buys nothing.

Two other shapes want the same thing:

- `ModeChangeRequestTransformer` emits `N` mode changes then `N` verifications — `2N` round trips for
  one cluster-wide transition.
- `PartitionReassignmentOperationsGenerator#bootstrapPartition` emits one `PartitionBootstrap`
  followed by a `PartitionJoin` per remaining member. The joins are independent of each other and
  only depend on the bootstrap.

## What already exists

ADR-0001 introduced two axes of parallelism:

1. **Across sub-configurations.** `GlobalConfiguration` and each `PartitionGroupConfiguration` own an
   independent change plan; `ClusterConfigurationManagerImpl` drives them concurrently with per-group
   in-flight and retry state.
2. **Across partition groups within a phase.** `PartitionGroupParallelPhase` activates operations into
   several named groups at once.

Neither helps *within* one sub-configuration, which is where the `N` factor lives.

---

## Decisions taken

These are settled and not re-opened by the option choice below. Each was either forced by review or
established while prototyping.

### DC-1. `ClusterChangePlan` is not modified; the current model gets its own plan type

`ClusterChangePlan` is shared with the single-group `ClusterConfiguration` and was only reused by the
current model because nothing about it had changed. Changing it now would couple new functionality to
a path scheduled for removal. It stays exactly as it is, serving the single-group model, and the
current model gets a new type.

**Cost, stated plainly:** `ClusterChangePlan` is referenced from 12 files. The ones outside the state
package — `RestoreStatus`, `dist/ClusterApiUtils`, `gateway-rest/RecoveryController`,
`TopologyMetrics`, `restore-standalone/RestoreManager` — read it through the single-group projection
and keep working unchanged, but anything that wants to *see* concurrent progress has to read the new
type. See the shared implementation notes.

### DC-2. Members are keyed, not listed: `Map<MemberId, List<Operation>>`

`PartitionGroupParallelPhase` is already `Map<groupId, List<Operation>>` — parallel across keys,
sequential within one key. The member axis takes the identical shape one level down. A `List` of
operations implies sequencing, which is exactly what we are trying not to say.

This is **not a second concurrency mechanism**: it is the existing one — *a map from an independent
key to a sequential queue* — applied to a second key type.

### DC-3. Progress is a grow-only completed prefix, merged per member

```java
record MemberQueue(
    List<ClusterConfigurationChangeOperation> operations,  // immutable for the plan's lifetime
    List<Instant> completedAt)                             // a prefix; its size is the completed count
```

A member's next operation is `operations.get(completedAt.size())`; it is done when the sizes match.
Only the owning broker writes its own entry, so merge is *take the longer prefix* (element-wise
earliest timestamp on equal length, so a re-stamp after restart still converges).

This is the same per-entry merge `GlobalConfiguration.members` and
`PartitionGroupConfiguration.members` already use. **No new CRDT join is introduced at plan level.**

### DC-4. No plan-level version, and no explicit barrier-release action

Because progress is a monotone prefix per member, "which round is active" and "is the plan complete"
are *derived* from the queues rather than stored:

- `currentRound()` = the first round whose members are not all drained.
- `hasPendingChanges()` = some round is not drained.

There is nothing to increment, nothing to release, and no question of who releases it or whether the
release is idempotent. This removes an entire class of problem the withdrawn design had to solve with
a coordinator-free release routine.

### DC-5. Concurrent operations must be member-scoped, checked before the plan is persisted

An operation may only run concurrently with other members' operations if it **writes nothing but its
own member's entry**. Members run at the same time and learn each other's results by gossip; the
members map merges entry by entry, so writes confined to distinct entries converge and writes to
anything else race. Full rule and enforcement below.

### DC-6. Completion timestamps are derived, never read from the wall clock at completion time

Once several brokers can observe a plan complete, two doing so a moment apart would stamp different
`completedAt` values on `lastChange` — and the sub-configuration merges do not reconcile `lastChange`
at equal versions, so the two would never converge and the cluster would report two different
completion times for one change, permanently. Derive it from the latest recorded operation
completion. (Found while prototyping; it fails silently.)

### DC-7. Restore is the first and only adopter

`ModeChangeRequestTransformer` is a natural second adopter and its appliers qualify, but brokers
entering recovery together rather than one at a time is a transient-behaviour change, not a pure
latency win, and wants the recovery owner's review separately.

### DC-8. No new compatibility logic is added for the single-group model

A broker predating this work reads the single-group gossip projection, which cannot represent
concurrent member progress. That projection is written exactly as it is today. Teaching it to
represent concurrency would mean growing a layer that is scheduled for deletion, and it could not be
made correct anyway — the single-group merge has no join and loses one of two concurrent completions
by construction. Treated as a release-scoping question instead; see Risks.

### DC-9. Naming mirrors the group axis

`MemberParallelPhase` / `MemberParallelChangePlan`, alongside `PartitionGroupParallelPhase`. The names
should read as the group-parallel concept applied to members.

---

## Option space

The only genuinely open question is **where cross-member ordering lives**. Per-member queues cannot
express "A bootstraps, then B and C join" inside a single map, and that ordering is real — Raft
requires the partition to exist before others join it.

Six options were considered. A and B are live; C is a variant worth keeping on the table; D, E and F
are rejected with reasons.

|                         | Barrier mechanisms  |  Cross-member ordering  |  Coordinator needed  |   Blocked on   |
|-------------------------|---------------------|-------------------------|----------------------|----------------|
| **A** rounds in plan    | 2 (rounds + phases) | rounds, inside the plan | no                   | nothing        |
| **B** ordering = phases | 1 (phases)          | phases                  | yes, unless D7 lands | ADR-0001 D7    |
| **C** markers on state  | 1 (phases)          | phases                  | yes, unless D7 lands | ADR-0001 D7    |
| D barrier in plan       | 2                   | steps                   | no                   | — *(rejected)* |
| E coordinator acks      | 1                   | acks                    | yes, hard dependency | — *(rejected)* |
| F plan per member       | 2                   | not expressible         | no                   | — *(rejected)* |

### Option A — rounds inside the sub-configuration plan

The plan is a sequence of rounds; each round is a `Map<MemberId, List<Operation>>`. A round is
complete when every member in it has drained its queue, and the active round is derived (DC-4).

- Cross-member ordering stays where it is today, so no existing transformer changes shape.
- Costs a second barrier concept: a round is a barrier, and a phase is also a barrier.
- Depends on nothing else landing first.

### Option B — cross-member ordering becomes phases

No round concept. A sub-configuration plan is one `Map<MemberId, List<Operation>>`, and anything
needing cross-member ordering becomes consecutive phases. One barrier mechanism in the system.

- **Blocked on ADR-0001 D7 (coordinator-free phase advancement).** Phase advancement is
  coordinator-only today, so under B every ordering point becomes a coordinator round trip: a
  sequential `N`-operation plan turns into `N` phases and gains a coordinator liveness dependency it
  does not have. That is a regression for existing sequential plans unless D7 lands first — and
  ADR-0001 already names D7 as the wanted follow-up.

### Option C — per-member progress markers on member state

Like B, but the queue lives in the phase (read-only) and progress is recorded on `BrokerState` /
`BrokerPartitionState`, riding the existing per-member version merge with no plan-side progress at
all. The purest reuse of existing machinery.

Not chosen now because it puts plan bookkeeping into member state — a type every consumer of the
configuration reads — and the REST change view still needs plan-side data to render, so the plan
cannot stay entirely ignorant of progress. Worth revisiting if B is chosen, since B and C differ only
in where the counter lives.

### Option D — a barrier primitive inside `ClusterChangePlan` *(rejected on review)*

The withdrawn ADR-0002. A plan becomes a list of steps, each step holding at most one operation per
broker, with a grow-only completion set and a CRDT join on the plan's equal-version merge branch.

Rejected because it overloads `ClusterChangePlan` (DC-1) and adds a second concurrency mechanism
alongside phases. Its one-operation-per-broker limit also forced restore into `P` lockstepped rounds
in which no broker could start its `k+1`-th partition until every broker had finished its `k`-th —
a limitation DC-2's per-member lists remove outright.

### Option E — coordinator collects acknowledgement RPCs *(rejected)*

Brokers ack completion to the coordinator, which advances when all acks are in. Rejected: acks are
not persisted, so a coordinator failover loses the barrier with no way to reconstruct who was done,
and it adds a second control path beside gossip for state that gossip already carries.

### Option F — one plan queue per broker per sub-configuration *(rejected)*

`Map<MemberId, ClusterChangePlan>`. Rejected: it discards cross-member ordering entirely rather than
relocating it, multiplies plan state by cluster size, and a barrier still has to be expressed on top.

### Recommendation

**Option B is the better end state; Option A is what is buildable without prerequisites.**

A round in Option A has exactly a phase's shape, so if D7 lands the migration is mechanical — each
round becomes a phase and the round concept is deleted. My suggestion is to size D7 before deciding:
if it is small, go straight to B and leave the system with one barrier concept; if it is not, take A
and converge later.

---

## Implementation notes — Option A

### Data model (`state/`)

```java
public record MemberParallelChangePlan(
    long id,
    Status status,
    Instant startedAt,
    List<ParallelRound> rounds) {          // operations immutable; completedAt prefixes grow

  public record ParallelRound(SortedMap<MemberId, MemberQueue> perMember) { }

  public record MemberQueue(
      List<ClusterConfigurationChangeOperation> operations,
      List<Instant> completedAt) { }

  // derived, never stored:
  Optional<ParallelRound> currentRound();                       // first round not fully drained
  boolean hasPendingChanges();
  Optional<Operation> pendingOperationFor(MemberId);            // current round only
  List<Operation> pendingOperations();                          // flat, for the change view
  List<CompletedOperation> completedOperations();               // positional order, not arrival order
  MemberParallelChangePlan completeFor(MemberId);               // appends one timestamp
  MemberParallelChangePlan merge(MemberParallelChangePlan);     // round-wise, member-wise, longer prefix wins
}
```

`completedOperations()` must order by position in the plan, never by arrival, or two brokers rendering
the same converged state produce different lists.

### Sub-configurations

- `GlobalConfiguration.pendingChanges` and `PartitionGroupConfiguration.pendingChanges` change type.
- `pendingChangesFor(MemberId)` becomes a genuine per-member lookup (see shared note SN-1).
- `advanceConfigurationChange` takes the acting `MemberId` — it does not today, and must not infer it
  from the head of a queue.
- End-of-plan work (clear `pendingChanges`, set `lastChange`, drop `LEFT`/partition-less members, bump
  the sub-configuration version) fires when the last round drains. It is a pure function of converged
  state, so several brokers computing it concurrently agree — provided DC-6 holds.

### Phases

`PartitionGroupParallelPhase` carries rounds: `Map<String, List<Map<MemberId, List<Operation>>>>`.
Keep a derived flat view so the REST change view, `ClusterModeChangeMapper` and `flattenPhases` are
untouched. Build phases through named factories — the unmarked spelling should mean the current
one-broker-at-a-time behaviour, and fanning out should be spelled explicitly at the call site.

### Manager

`ClusterConfigurationManagerImpl` applies the local member's next operation per sub-configuration as
it does now; the only change is that the operation comes from a per-member lookup. **No release
routine is needed** (DC-4) — draining the last round makes `hasPendingChanges()` false, which the
existing phase-completion check already tests.

### Serialization

New proto messages for the plan; `ClusterChangePlan`'s message is untouched. `toLegacy` must project
the new plan into a `ClusterChangePlan` for the single-group gossip field — flatten to the sequential
ordering it degrades to, and do not attempt to represent concurrency (DC-8).

### Estimated scope

~10 files in `dynamic-config` plus the proto and the restore transformers. No changes outside the
module: existing consumers read the single-group projection.

---

## Implementation notes — Option B

### Prerequisite: ADR-0001 D7

Phase advancement must stop being coordinator-only. The groundwork is favourable —
`PhasedChangePlan` pre-computes all phases, they are immutable, and `currentPhaseIndex` is monotone,
so advancement is already an idempotent pure function of converged state. What needs checking before
committing to B:

- `maybeAdvancePhase` is gated on `isLocalMemberCoordinator()`; removing the gate is the easy part.
- `activateNextPhase` writes into sub-configuration `pendingChanges`. Two brokers doing it
  concurrently must produce identical results — they should, since the phase is a read-only template,
  but it needs asserting.
- `completePlan` evicts from a bounded history and advances `nextId`. Neither is obviously idempotent
  under concurrent execution; this is the part most likely to need rework.

**If this prerequisite is not taken, Option B should not be.**

### Data model (`state/`)

Same as A minus the rounds:

```java
public record MemberParallelChangePlan(
    long id,
    Status status,
    Instant startedAt,
    SortedMap<MemberId, MemberQueue> perMember) { }
```

Everything else in DC-3 and DC-4 is unchanged; the plan is complete when every member is drained.

### Phases

`PartitionGroupParallelPhase` changes from `Map<String, List<Operation>>` to
`Map<String, Map<MemberId, List<Operation>>>`. There is then exactly one phase type per
sub-configuration kind, expressing both axes, and no separate member-parallel phase is needed —
which is the strongest form of "reuse the Phases concept".

### Transformer migration — the real cost of B

Every transformer that relies on cross-member ordering inside one flat list must be split into
phases. Concretely:

- `PartitionReassignmentOperationsGenerator#bootstrapPartition` becomes two phases:
  `{primary: [bootstrap]}` then `{each other member: [join]}`. This is also a latency win — the joins
  currently run one at a time.
- `PartitionReassignRequestTransformer`, `ClusterScaleRequestTransformer`,
  `ForceScaleDownRequestTransformer` and the zone transformers need auditing for ordering assumptions
  that are currently implicit in list order and would become silent parallelism.

That audit is the main risk in B: an ordering that is currently accidental-but-correct becomes
concurrent, and nothing fails loudly.

### Estimated scope

Option A's scope, plus the D7 work, plus the transformer audit and migration. Larger, but ends with
one barrier concept instead of two and no round machinery to maintain.

---

## Implementation notes — shared

These apply to whichever option is taken. All were found while prototyping the withdrawn design, and
each fails quietly.

**SN-1. `pendingChangesFor(MemberId)` must be a per-member lookup, not a head-of-queue read.** Both
sub-configurations implement it today as "if the plan has pending changes *for* this member, return
`nextPendingOperation()`" — sound only while exactly one broker is ever eligible. The moment several
are, the second half stops matching the first and a broker is handed *another broker's* operation to
apply. It compiles, it type-checks, and the applier starts work on behalf of a peer.

**SN-2. Advancement triggered from a merge must be dispatched, not applied inline.**
`updateLocalCurrentConfiguration` → advance → update → `updateLocalCurrentConfiguration` is
re-entrant; the existing `maybeAdvancePhase` is only safe because `updateMultiConfiguration` posts
through `executor.run`. Anything new on that path must do the same.

**SN-3. Progress driven by merges must also run when a merge changes nothing.**
`onGossipReceivedCurrent` short-circuits on an unchanged merge. If completion detection only runs on
the changed branch, work whose persist failed is never retried once the cluster converges, and the
plan sits complete-but-unadvanced with nothing to perturb it. Gossip syncs periodically
(`DEFAULT_SYNC_DELAY`, 10s), so running the check on every round suffices and needs no timer.

**SN-4. Repeated proto fields lose set and sort semantics.** Sorted collections encode as repeated
fields; decode must re-sort and de-duplicate, or the decoded record is semantically equal but fails
`equals` — which breaks the manager's no-op short-circuit and `PhasedChangePlan#merge`'s
same-id-different-phases guard.

**SN-5. The applier contract is weaker than it reads.**
`PartitionGroupConfigurationChangeApplier` and `GlobalConfigurationChangeApplier` say nothing about
stability during `apply()`. Once members run concurrently the sub-configuration *does* change
mid-apply as peers' results arrive by gossip; only the applier's own member entry is stable. State
this on both interfaces before someone writes an applier that reads group state during `apply()`.

**SN-6. A member that disappears mid-plan stalls it.** Completion requires every targeted member to
finish. Validate at plan construction that a plan does not both target a member and remove it. Do
*not* treat members absent from `GlobalConfiguration.members()` as vacuously complete — that would
complete plans on transient absence. `cancelChange` stays the operator escape hatch.

**SN-7. Metrics.** `topologyMetrics.observeOperation` is created per operation; several may now be
live per sub-configuration. Verify `TopologyManagerMetrics` holds no single-slot assumption.

---

## Eligibility rule (DC-5 in full)

An operation may share a parallel unit only if its applier writes nothing outside its own member's
entry. Reading is unrestricted — `GlobalConfiguration`, other members' state, anything. Writing
`routingState`, `incarnationNumber`, `availability`, the sub-configuration version, or another
member's entry disqualifies it. `addMember` is fine: it writes one entry, exactly as `updateMember`
does, and the members map merges entry by entry.

Ineligible today: `UpdateRoutingStateApplier`, `UpdateIncarnationNumberApplier`,
`UpdatePartitionDistributorConfigApplier`, `DeleteHistoryApplier`, `StartPartitionScaleUpApplier`,
`PreScalingApplier`, `PostScalingApplier`, and `PartitionForceReconfigureApplier` (it rewrites other
members' partition maps).

The current model has no interface expressing this — every applier implements
`PartitionGroupConfigurationChangeApplier` or `GlobalConfigurationChangeApplier` directly and calls
`updateMember` by hand. The legacy `ConfigurationChangeAppliers.MemberOperationApplier` enforces it
structurally but is not on this path.

**Proposal:** add `MemberScopedGroupApplier` with a `memberId()` accessor, and have the coordinator
refuse a plan whose concurrent operations do not all resolve to one — checked during the existing
dry-run simulation, before anything is persisted or gossiped. Two details learned from prototyping:
the applier's `memberId()` must be asserted equal to the operation's (the marker promises "one
member's entry", not "*this* member's entry"), and the simulation can additionally verify the
*observed* write set by diffing the sub-configuration before and after each operation, which catches
a mis-marked applier rather than trusting the declaration.

Open: whether to make this structural instead — an applier base class that can only express a write
to its own member — at the cost of not fitting the `addMember` case.

## First adopter: restore

```
per member m:  [ PreRestore(m, p) for each p in partitions(m) ]
               ─── barrier ───
per member m:  [ Restore(m, p, backups) for each p in partitions(m) ]
               ─── barrier ───
per member m:  [ ModeChange(m, PROCESSING), AwaitModeChange(m, PROCESSING) ]
               ─── barrier ───
once:          UpdateIncarnationNumber        // group-level write, so it runs alone
```

Four barriers regardless of `N` and `P`, with each broker's own work proceeding at its own pace
inside them. Three brokers holding three partitions each: **25 serialized round trips today, 4
barriers**. Uneven partition counts cost nothing extra, since each member drains its own queue.

`AwaitModeChange` is retained — the barrier subsumes its sequencing role but not its verification
role, which is what confirms each member's partitions came up in the target mode.

## Risks

- **Concurrent recovery entry changes transient cluster behaviour.** Today there is a window where
  some brokers are still processing; after adoption they transition together. Intended, but
  behavioural, not merely faster — hence DC-7 deferring the mode-change transformer.
- **Mixed-version clusters.** Per DC-8, a broker predating this work cannot observe concurrent
  progress. Restore requires the cluster to be in recovery, so the exposure is a restore issued
  during a rolling upgrade — narrow, but real. The clean answer is to refuse concurrent execution
  until every member supports it, which needs version negotiation the module does not have. Proposed
  to be handled with the retirement of the ADR-0001 D4 dual-write path.
- **Option B only: silent parallelism from implicit ordering.** See the transformer migration note.

## Open questions

1. **Option A or B** — is coordinator-free phase advancement (ADR-0001 D7) worth sizing now? This is
   the decision the rest depends on.
2. Should a member's unit be a *list* of operations or a single operation with ordering always one
   level up? This is DC-2's granularity, and question 3 decides it.
3. Restore is I/O bound. Does running several partition restores concurrently on one broker actually
   help, or does it saturate the same disk? If it does not, per-member lists earn nothing over
   per-member single operations for this adopter.
4. Should the eligibility rule be structural rather than declared-and-checked, accepting that
   `addMember`-style appliers then cannot express it?
5. Does `RestoreStatus` / the REST change view need to *show* per-member concurrency, or is the
   flattened view sufficient? This decides how far the new plan type has to reach outside the module.

## References

- [ADR-0001: Extend cluster configuration to support multiple partition groups](./adr/0001-multi-partition-group-cluster-configuration.md)
- [Withdrawn ADR-0002](./adr/0002-parallel-per-broker-change-operations.md)
- [Review thread](https://github.com/camunda/camunda/pull/60403)
- A prototype of Option D exists on this branch, unpushed. Its property tests establishing merge
  convergence transfer to A and B; its data model does not.


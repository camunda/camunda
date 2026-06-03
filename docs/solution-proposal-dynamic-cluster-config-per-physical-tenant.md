# Dynamic Cluster Configuration for Physical Tenants

## 1. Requirements

### Functional

- Each physical tenant owns a dedicated Raft partition group with partitions numbered 1..N,
  stored in isolated data directories.
- Each physical tenant must have an **independent change plan**: scaling, exporter operations,
  and partition lifecycle changes on tenant A must not block or be blocked by tenant B.
- **Per-tenant partition state** must be tracked: replica lifecycle (JOINING → ACTIVE → LEAVING →
  LEFT), exporter enable/disable state, per-partition dynamic config.
- **Different partition counts per tenant** must be representable (may be required in 8.10).
- **All cluster members must be visible** in the configuration regardless of which tenants they
  are assigned to. A broker joining the cluster before partition assignment, or finishing a leave,
  must not disappear from the configuration.
- Concurrent read-only operations across tenants must always be served.

### Non-functional

- The `withGroupName()` workaround in `PartitionDistribution` must be eliminated.
- Existing operations (scale, exporter changes) on the `default` tenant must continue to work
  throughout any incremental migration.
- Rolling restart safety: old brokers gossiping `ClusterTopology` and new brokers gossiping the
  new format must coexist during the upgrade window.

---

## 2. Solution Alternatives

### Background: the naming collision

`MemberState.partitions` is `SortedMap<Integer, PartitionState>`. Every partition group numbers
its partitions 1..N. Partition group `default` partition 1 and partition group `groupA` partition
1 are different Raft replicas with the same integer key — a flat map per member cannot represent
both.

`ClusterConfiguration` also conflates two concerns: **cluster membership** (broker lifecycle,
JOINING → ACTIVE → LEAVING → LEFT) and **partition assignment** (which partitions each broker
hosts). With multiple partition groups, a broker might be in the cluster but not yet assigned to
any partition group — making it invisible in a pure per-group config.

### Option A — `PartitionGroupClusterConfiguration` wrapper with dedicated cluster membership

```
PartitionGroupClusterConfiguration
  clusterMembership: ClusterConfiguration       // ALL brokers; partitions always empty
  partitionGroupConfigs: Map<groupId, ClusterConfiguration>
  pendingPlan: Optional<PartitionGroupChangePlan>
```

Each partition group has a complete, independent `ClusterConfiguration`. A dedicated
`clusterMembership` section tracks all brokers regardless of partition group assignment.
Cluster-spanning operations (member join/leave) go through a phased `PartitionGroupChangePlan`;
pure single-group operations (exporter changes) go directly into the target group's
`pendingChanges`.

### Option A' — Same wrapper; `default` partition group is the membership authority

Same outer type but no dedicated `clusterMembership` field. The `default` partition group's
`ClusterConfiguration` always contains all cluster members. Non-default partition group configs
contain only members that host partitions in that group.

### Option B — Additive field `Map<String, PartitionGroupConfig>` in `ClusterConfiguration`

Keep `ClusterConfiguration` as-is (representing all members and the `default` partition group's
partitions). Add a new optional field for non-default partition groups:

```
ClusterConfiguration
  ...existing fields (default partition group)...
  partitionGroups: Map<groupId, PartitionGroupConfig>   // excludes "default"

PartitionGroupConfig
  version, memberPartitions, routingState, pendingChanges, lastChange
```

### Option C — Compound partition key `(groupId, partitionId)` throughout

Replace `int partitionId` with `GroupPartitionId(groupId, partitionId)` everywhere in the
data model. A single `ClusterConfiguration` holds all partition groups in one structure.

### Option D — Fully federated: one `ClusterConfiguration` per partition group, independent gossip

Each partition group has its own `ClusterConfigurationManager`, gossip topic, persistence file,
and initializer. A separate membership-only manager tracks all brokers.

---

### Comparison

| Criterion | A (wrapper + dedicated membership) | A' (wrapper + default authority) | B (additive field) | C (compound key) | D (federation) |
|---|---|---|---|---|---|
| All members always visible | ✅ explicit field | ✅ via "default" | ✅ existing members map | ✅ | ✅ |
| Independent change plans | ✅ | ✅ | ⚠️ requires duplication | ⚠️ needs plan redesign | ✅ |
| Different partition counts | ✅ | ✅ | ⚠️ asymmetric | ✅ | ✅ |
| Symmetric group model | ✅ | ❌ default is special | ❌ | ✅ | ✅ |
| Removes `withGroupName()` | ✅ | ✅ | ❌ | ✅ | ✅ |
| Proto migration risk | Medium | Medium | Low | High | None |
| Blast radius | Wide | Wide | Narrow | Widest | Moderate |
| Stuck operation blast radius | Per-group isolated | Per-group isolated | Per-group isolated | Cluster-wide | Per-group isolated |
| Long-term viable | ✅ | ⚠️ special-casing leaks | ❌ needs migration | ❌ plan redesign needed | ⚠️ N× overhead |

**Option B** cannot cleanly represent different partition counts for the default partition group
and requires a migration to Option A before they are needed. **Option C** cannot provide
independent change plans without redesigning the sequential plan execution model — operations for
different partition groups are interleaved in one global queue; a stuck operation blocks all
partition groups. **Option D** multiplies actor/thread overhead by the number of partition groups,
introduces bootstrap ordering problems, and its aggregated state converges to Option A's data
model anyway.

**Option A'** is simpler to migrate to but creates permanent special-casing: every call site
that needs "all cluster members" must know to look in the `default` partition group's config.
This leaks into coordinators, REST handlers, and initializers. It also breaks if a broker is not
assigned to the `default` partition group.

**Option A is the recommended approach.**

---

## 3. Chosen Solution: Option A

### 3.1 Data model

```java
record PartitionGroupClusterConfiguration(
    ClusterConfiguration clusterMembership,                // ALL brokers; MemberState.partitions always empty
    Map<String, ClusterConfiguration> partitionGroupConfigs, // one per partition group ID
    Optional<PartitionGroupChangePlan> pendingPlan           // only for cluster-spanning operations
)
```

**`clusterMembership`** holds every broker in the cluster with its lifecycle state. The
`partitions` map in each `MemberState` here is always empty. `clusterId` lives here. Member
lifecycle operations (`MemberJoinOperation`, `MemberLeaveOperation`, `MemberRemoveOperation`)
apply exclusively here.

**`partitionGroupConfigs`** holds one `ClusterConfiguration` per partition group. Each only
includes members that host partitions in that group. `MemberState.state` here means the broker's
participation state in that group's Raft replicas — the same JOINING/ACTIVE/LEAVING/LEFT enum,
scoped to partition-group membership rather than cluster membership. `incarnationNumber` is
tracked per partition group here, since each group has its own Raft history. Partition change
plans, routing state, exporter state, and history-management operations
(`UpdateIncarnationNumberOperation`, `DeleteHistoryOperation`) live here.

**`pendingPlan`** records the phased execution plan for operations that span cluster membership
and partition groups. It is present only when such a cross-phase operation is in progress. Pure
single-group operations do not use it.

### 3.2 Two kinds of operations

The distinction between the two kinds determines whether `pendingPlan` is used.

**Single-group operations** (exporter disable/enable/delete, per-group routing update,
per-group partition reconfiguration, incarnation number update, history deletion) do not require
cluster membership changes. The coordinator writes their operations directly into
`partitionGroupConfigs[groupId].pendingChanges`. No `PartitionGroupChangePlan` is created.

**Cluster-spanning operations** (scale-up, scale-down, broker replacement — any operation that
requires `MemberJoinOperation` or `MemberLeaveOperation`) must sequence membership changes before
partition changes. The coordinator generates a `PartitionGroupChangePlan` with explicit phases.

The "one plan at a time" constraint is **per-partition-group**, not global:

- The coordinator rejects a new request for group A if `partitionGroupConfigs["groupA"].hasPendingChanges()`.
- Two requests for different partition groups A and B can be in flight simultaneously.
- A cluster-spanning request additionally requires `pendingPlan` to be absent and all partition
  groups it will touch in any future phase to have no pending changes.

### 3.3 Phased change plan

```java
record PartitionGroupChangePlan(
    long id,
    Instant startedAt,
    int currentPhaseIndex,   // advances as phases complete; indexes into `phases`
    List<Phase> phases       // all phases pre-computed at plan creation, including future ones
)

sealed interface Phase {
    // Operations applied to clusterMembership
    record ClusterMembershipPhase(
        List<ClusterConfigurationChangeOperation> operations
    ) implements Phase {}

    // Operations applied to one or more partition group configs, executed in parallel across groups
    record PartitionGroupParallelPhase(
        Map<String, List<ClusterConfigurationChangeOperation>> operationsPerGroup
    ) implements Phase {}
}
```

All phases are pre-computed and stored in `pendingPlan.phases` at plan creation. This is
required for coordinator restart recovery: if the coordinator restarts mid-plan, it reads
`currentPhaseIndex` and the stored phases to determine where to resume.

When a phase is activated, the coordinator copies that phase's operations into the appropriate
sub-configs' `pendingChanges` in a single atomic update of `PartitionGroupClusterConfiguration`.
Future phases remain in `pendingPlan.phases` but their operations are not yet in sub-configs.

### 3.4 Operation dispatch

| Operation | Kind | Sub-config target |
|---|---|---|
| `MemberJoinOperation`, `MemberLeaveOperation`, `MemberRemoveOperation` | cluster-spanning | `clusterMembership` |
| `PreScalingOperation`, `PostScalingOperation` | cluster-spanning | `clusterMembership` |
| `UpdateIncarnationNumberOperation`, `DeleteHistoryOperation` | **single-group** | `partitionGroupConfigs[groupId]` |
| `PartitionBootstrapOperation`, `PartitionJoinOperation`, `PartitionLeaveOperation` | cluster-spanning | `partitionGroupConfigs[groupId]` |
| `StartPartitionScaleUp`, `AwaitRedistributionCompletion`, `AwaitRelocationCompletion` | cluster-spanning | `partitionGroupConfigs[groupId]` |
| `PartitionDisableExporterOperation`, `PartitionEnableExporterOperation`, `PartitionDeleteExporterOperation` | **single-group** | `partitionGroupConfigs[groupId]` |
| `UpdateRoutingState` (per-group) | **single-group** | `partitionGroupConfigs[groupId]` |

### 3.5 How a broker executes its operations

Each broker's `ClusterConfigurationManagerImpl` checks two sources on every config update:

```
1. clusterMembership.pendingChangesFor(localMemberId)
   → execute the membership operation (one at a time)
   → call updateClusterMembership(c -> c.advanceConfigurationChange(...))

2. for each groupId in partitionGroupConfigs:
       partitionGroupConfigs[groupId].pendingChangesFor(localMemberId)
       → hand to PartitionManagerImpl[groupId] (non-blocking per group)
       → call updatePartitionGroupConfig(groupId, c -> c.advanceConfigurationChange(...))
```

Operations across different partition groups execute concurrently: each group has its own
`PartitionManagerImpl` actor. Within a single partition group the existing sequential
`ClusterChangePlan` execution model is preserved unchanged.

### 3.6 Phase advancement

The coordinator is a `ClusterConfigurationUpdateListener`. On every config update it checks
whether the active phase has finished:

```java
void onClusterConfigurationUpdated(PartitionGroupClusterConfiguration config) {
    if (!isCoordinator(config)) return;
    if (config.pendingPlan().isEmpty()) return;

    final var plan = config.pendingPlan().get();
    final var currentPhase = plan.phases().get(plan.currentPhaseIndex());

    final boolean phaseComplete = switch (currentPhase) {
        case ClusterMembershipPhase ignored ->
            !config.clusterMembership().hasPendingChanges();
        case PartitionGroupParallelPhase p ->
            p.operationsPerGroup().keySet().stream()
                .allMatch(id -> !config.partitionGroupConfigs().get(id).hasPendingChanges());
    };

    if (!phaseComplete) return;

    if (plan.currentPhaseIndex() + 1 >= plan.phases().size()) {
        configManager.updateConfiguration(c -> c.completePlan());
    } else {
        configManager.updateConfiguration(c -> c.activateNextPhase());
    }
}
```

`isCoordinator()` reads from `clusterMembership.members` (lowest member ID among ACTIVE members).

### 3.7 Merge semantics

#### How existing `ClusterConfiguration` sub-configs merge

`ClusterConfiguration` uses a two-level versioning scheme:

- **Config version** (`ClusterConfiguration.version`): incremented at exactly two points — when
  the coordinator calls `startConfigurationChange()` (plan start) and when the last operation in a
  plan completes (plan end). Between those two events all operation completions keep the config
  version constant and advance only `ClusterChangePlan.version`.
- **Member version** (`MemberState.version`): incremented by each member when it advances its own
  state. Only the member itself ever writes its own `MemberState`.

`ClusterConfiguration.merge()` uses the config version as a fast path: if versions differ, the
higher version wins wholesale. If equal (concurrent writes during plan execution), it merges
field-by-field:

- **Members**: union of keys, each overlapping `MemberState` merged by its own version (highest
  wins).
- **`pendingChanges`** (`ClusterChangePlan`): higher plan-internal version wins; no conflict
  detection — safe because only one broker per operation advances the plan.
- **`routingState`**: higher version wins; same version + different content throws — protected by
  coordinator exclusivity.
- **`incarnationNumber`**: `Math.max()`.
- **`lastChange`**: taken from `this`. Safe because `lastChange` is written only on plan
  completion, which bumps the config version — `lastChange` disagreements only occur at different
  config versions and are resolved by the fast path. **Invariant to preserve: never write
  `lastChange` without also bumping the config version.**

Each group's `ClusterConfiguration` in `partitionGroupConfigs` applies this same logic
independently.

#### `PartitionGroupClusterConfiguration.merge()`

`PartitionGroupClusterConfiguration` has **no outer version**. Every gossip reconciliation is a
full field-by-field merge. The structure behaves as a CRDT: the result is the component-wise
maximum of all received state.

```java
PartitionGroupClusterConfiguration merge(PartitionGroupClusterConfiguration other) {
    final var mergedMembership = clusterMembership.merge(other.clusterMembership);

    // Union of keys: a group present in only one side is adopted without conflict.
    final var mergedGroups = new HashMap<>(other.partitionGroupConfigs);
    partitionGroupConfigs.forEach((groupId, config) ->
        mergedGroups.merge(groupId, config, ClusterConfiguration::merge));

    final var mergedPlan = mergePlan(pendingPlan, other.pendingPlan);
    return new PartitionGroupClusterConfiguration(
        mergedMembership, Map.copyOf(mergedGroups), mergedPlan);
}
```

**Union semantics for new partition groups**: a group key present in one side but absent in the
other is adopted directly. This handles partition group provisioning — early gossip carries the
new group entry, stale gossip does not. Once a group key appears it is never removed by a merge (deletion could be delivered in a later iteration.).

#### `PartitionGroupChangePlan.merge()`

```java
Optional<PartitionGroupChangePlan> mergePlan(
    Optional<PartitionGroupChangePlan> a, Optional<PartitionGroupChangePlan> b) {
    if (a.isEmpty()) return b;
    if (b.isEmpty()) return a;
    return Optional.of(a.get().merge(b.get()));
}

PartitionGroupChangePlan merge(PartitionGroupChangePlan other) {
    if (id == other.id) {
        // Same plan advancing through phases — take the furthest-advanced index.
        return currentPhaseIndex >= other.currentPhaseIndex ? this : other;
    }
    // Different IDs: a new coordinator issued a replacement plan.
    // Take the more advanced to avoid regressing execution.
    if (currentPhaseIndex != other.currentPhaseIndex) {
        return currentPhaseIndex > other.currentPhaseIndex ? this : other;
    }
    // Equal phase index across different IDs: take the higher plan ID.
    // The coordinator assigns IDs monotonically, so higher ID = more recent decision.
    return id > other.id ? this : other;
}
```

**Why "take the more advanced plan" is safe across different IDs**: the coordinator only generates
a new plan when `pendingPlan` is absent (§3.2). In the race window where a new coordinator starts
a fresh plan (at phase 0) before converging gossip, the existing plan is at phase ≥ 0. Taking the
plan with the higher `currentPhaseIndex` prevents the cluster from being driven backward.
Idempotent phase activation (see below) means that if the existing plan's phase operations are
re-written on top of already-executing sub-configs, `ClusterChangePlan::merge` takes the higher
plan-internal version and the re-write is silently discarded.

**Required invariant**: plan IDs must be monotonically increasing across coordinator restarts. The
coordinator must derive the next plan ID from the last ID seen in the persisted or gossiped
`PartitionGroupClusterConfiguration`, not from a local counter that resets on restart.

#### Phases as read-only templates

`pendingPlan.phases` is a **read-only roadmap**. The coordinator writes it once at plan creation
and never modifies the phase entries afterward — only `currentPhaseIndex` advances. When a phase
is activated, its operations are **copied** into the appropriate sub-config:
`clusterMembership.pendingChanges` for a `ClusterMembershipPhase`, or
`partitionGroupConfigs[groupId].pendingChanges` for each group in a
`PartitionGroupParallelPhase`. From that point execution is driven entirely by the sub-configs.
Brokers poll sub-config `pendingChanges`; the coordinator checks sub-config
`hasPendingChanges()` for phase completion. Neither reads from `pendingPlan.phases` at runtime.

This is why `PartitionGroupChangePlan.merge()` can safely select one plan wholesale: the phases
in the losing plan are discarded, but the in-progress execution state is not in those phase
objects — it is in the sub-configs, which merge independently via their own
`ClusterConfiguration.merge()`. Taking the winning plan's future phases gives the coordinator the
correct roadmap for what comes next.

After a phase is activated, its entry in `pendingPlan.phases` becomes advisory. A newly elected
coordinator that has only partial gossip can determine what is currently executing by inspecting
the sub-configs directly (`hasPendingChanges()` on each). The stored future phases are only
needed to know what the coordinator must activate next — they carry no execution state of their
own.

#### Phase activation idempotency

When multiple brokers simultaneously activate Phase N+1 (Idea A, §4) or a coordinator
re-activates a phase after restart:

1. Both read `pendingPlan.phases[N+1]` — identical content (pre-stored at plan creation).
2. Both call `ClusterChangePlan.init(phases[N+1].operations)`, producing a fresh plan at the
   same initial plan-internal version with identical content.
3. Both write to the same sub-config, both producing `sub-config.version = V+1`.

Merge at same sub-config version: `ClusterChangePlan::merge` takes the higher plan-internal
version — both are at the initial version with equal content, so either is correct. No
`RoutingState` conflict arises because routing state is a separate field untouched by phase
activation. ✓

If Phase N+1 is already in progress when a re-activation arrives (plan-internal version has
advanced to K+M), `ClusterChangePlan::merge` takes K+M > K and the re-activation is silently
discarded. ✓

**Critical invariant**: idempotency requires that `phases[N+1].operations` is deterministic —
all brokers derive the same op list from the same pre-stored data. Phases are generated once by
the coordinator at plan creation and stored verbatim. They must never be recalculated from
observed sub-config state.

---

### 3.8 Example: scale-up — add broker B5, remove B1

**Generated `PartitionGroupChangePlan`:**

```
Phase 0 — ClusterMembershipPhase
    MemberJoinOperation(B5)

Phase 1 — PartitionGroupParallelPhase
    "default": [PartitionBootstrapOp(B5, p=1), PartitionLeaveOp(B1, p=1), UpdateRoutingState]
    "groupA":  [PartitionBootstrapOp(B5, p=1), PartitionLeaveOp(B1, p=1), UpdateRoutingState]

Phase 2 — ClusterMembershipPhase
    MemberLeaveOperation(B1)
```

**Execution:**
1. Coordinator activates Phase 0: `clusterMembership.pendingChanges = [MemberJoinOperation(B5)]`.
2. B5 executes the join. `clusterMembership.pendingChanges` clears.
3. Coordinator detects Phase 0 complete; activates Phase 1 atomically:
   both `partitionGroupConfigs["default"].pendingChanges` and `partitionGroupConfigs["groupA"].pendingChanges`
   are populated in a single config update.
4. B5's `PartitionManagerImpl["default"]` and `PartitionManagerImpl["groupA"]` bootstrap
   partition 1 concurrently (independent Raft groups, independent actors).
5. B1 concurrently handles `PartitionLeaveOp` in both groups once B5's bootstrap completes in
   each (sequential within-plan ordering enforces this per group).
6. Coordinator detects both partition groups in Phase 1 have no pending changes; activates Phase 2.
7. B1 executes `MemberLeaveOperation`. Plan complete.

### 3.9 Example: concurrent exporter changes on separate partition groups

**Request 1:** disable `es-exporter` on groupA — in progress.

```
partitionGroupConfigs["groupA"].pendingChanges = ClusterChangePlan([
    PartitionDisableExporterOp(B1, p=1, "es-exporter"),
    PartitionDisableExporterOp(B2, p=2, "es-exporter")
])
// pendingPlan absent
```

**Request 2 arrives while Request 1 is in progress:** disable `es-exporter` on groupB.

Coordinator checks: `partitionGroupConfigs["groupB"].hasPendingChanges() == false` ✓

```
partitionGroupConfigs["groupB"].pendingChanges = ClusterChangePlan([
    PartitionDisableExporterOp(B1, p=1, "es-exporter"),
    PartitionDisableExporterOp(B2, p=2, "es-exporter")
])
// pendingPlan still absent
```

Both plans execute fully concurrently:

```
B1's PartitionManagerImpl["groupA"]  ← DisableExporterOp(p=1) for groupA's Raft group
B1's PartitionManagerImpl["groupB"]  ← DisableExporterOp(p=1) for groupB's Raft group
B2's PartitionManagerImpl["groupA"]  ← DisableExporterOp(p=2) for groupA's Raft group
B2's PartitionManagerImpl["groupB"]  ← DisableExporterOp(p=2) for groupB's Raft group
```

No cross-group coordination. Each broker's per-group actors are independent.

### 3.10 Proto

```protobuf
message PartitionGroupClusterTopology {
  ClusterTopology clusterMembership = 1;           // all members; PartitionState maps always empty
  map<string, ClusterTopology> partitionGroups = 2; // keyed by partition group ID
  PartitionGroupChangePlan pendingPlan = 3;
}

message PartitionGroupChangePlan {
  int64 id = 1;
  google.protobuf.Timestamp startedAt = 2;
  int32 currentPhaseIndex = 3;
  repeated Phase phases = 4;
  CompletedChange lastChange = 5;
}

message Phase {
  oneof phase_type {
    ClusterMembershipPhase       membershipPhase    = 1;
    PartitionGroupParallelPhase  partitionGroupPhase = 2;
  }
}

message ClusterMembershipPhase {
  repeated TopologyChangeOperation operations = 1;
}

message PartitionGroupParallelPhase {
  map<string, PartitionGroupOperationList> operationsPerGroup = 1;
}

message PartitionGroupOperationList {
  repeated TopologyChangeOperation operations = 1;
}

message GossipState {
  PartitionGroupClusterTopology clusterTopology = 1;  // replaces bare ClusterTopology
}
```

Migration: on reading an old-format file (bare `ClusterTopology`), wrap it as:
- `clusterMembership` = all members with `partitions` cleared
- `partitionGroups["default"]` = original config unchanged
- `pendingPlan` = absent

### 3.11 Pros and cons

**Pros**

- Per-partition-group change plans, routing state, and exporter state are fully independent.
- `ClusterConfiguration` is reused verbatim per partition group — all existing plan, gossip
  merge, and operation logic applies without modification.
- Different partition counts per group: each group's `partitionCount()` is independent.
- All brokers are always visible via `clusterMembership` regardless of group assignment.
- `withGroupName()` is removed entirely.
- Concurrent single-group operations (exporter changes, per-group reconfiguration) work
  without global coordination.

**Cons**

- Wide blast radius: every consumer of `ClusterConfigurationService` must be updated or shielded
  behind a compatibility accessor.
- `MemberState.state` carries different semantics in `clusterMembership` (broker lifecycle) vs
  `partitionGroupConfigs` (Raft-group participation lifecycle) — same type, different context.
- Phase advancement for cluster-spanning operations depends on coordinator availability at each
  phase boundary (see §4 for mitigation ideas).
- Rolling restart requires the gossip handler to accept both old and new wire formats during the
  upgrade window.
- Operations that span cluster membership and partition groups (broker join+assign) require the
  coordinator to generate correctly sequenced phases; request transformers must enforce this.

### 3.12 Incremental delivery

Each increment leaves the system in a working state. The `default` partition group's scale and
exporter operations work throughout.

| # | What | `default` group behavior |
|---|---|---|
| 1 | `PartitionGroupClusterConfiguration` + `PartitionGroupChangePlan` data model; proto; serialization; migration | Unchanged |
| 2 | Gossip and persistence carry `PartitionGroupClusterTopology`; compat accessors for existing consumers | Unchanged |
| 3 | Non-default partition group entries seeded from static config; `PartitionDistribution` derived per-group; `withGroupName()` removed | Unchanged |
| 4 | Non-default group `PartitionManagerImpl` instances register executors and update `partitionGroupConfigs[id]` on state changes | Unchanged |
| 5 | Coordinator generates `PartitionGroupChangePlan` for default-group-only operations; phase advancement wired | Identical result, new mechanics; verified by existing tests |
| 6 | Scale/exporter operations generate ops for all partition groups; `PartitionGroupParallelPhase` spans all group configs | Multi-group enabled |
| 7 | REST topology API exposes per-group state | API surface |

---

## 4. Removing the Coordinator Bottleneck at Phase Boundaries

### The problem

In the current design, the coordinator must detect that Phase N is complete and write Phase N+1's
operations into sub-configs. If the coordinator is unavailable at that moment, the operation
stalls even though individual brokers are healthy and ready. This is a regression: previously the
coordinator was only a liveness concern when *starting* an operation, not while executing it.

### Idea A — Any ACTIVE broker can advance phases

Phase N+1's operations are pre-computed and stored in `pendingPlan.phases[N+1]` at plan creation.
Advancing a phase is a deterministic, idempotent read-then-write: read `phases[N+1]`, copy
operations into sub-configs. Any broker that observes (via gossip) that the current phase is
complete can perform this write.

If multiple brokers race to activate the next phase simultaneously, they write identical
operations (same data from the same stored phase). Gossip version-based merge resolves the race;
the result is correct regardless of which broker wins.

**Effect:** the coordinator is required only to *initiate* a new change (generate phases from a
request). Phase transitions require no specific broker — this restores the original liveness
property.

### Idea B — The broker completing the last operation in a phase activates the next

When a broker completes the last operation of Phase N — detected by `hasPendingChanges()` becoming
false on `clusterMembership` or all partition groups in a `PartitionGroupParallelPhase` — it
activates Phase N+1 as part of the same `advanceConfigurationChange()` call, in one atomic config
update.

No separate "phase watcher" loop is needed. Phase transitions are a natural extension of the
operation-completion flow already present in each broker.

**Race condition:** two brokers from different partition groups might both see "all groups
complete" and both try to activate Phase N+1. As in Idea A, idempotent write + gossip version
merge resolves this safely.

### Idea C — Coordinator re-election + watchdog (minimal change)

The coordinator is already defined as the lowest-member-ID ACTIVE broker. If broker 0 dies,
broker 1 becomes coordinator. The gap is that broker 1 only advances a phase when its
`ClusterConfigurationUpdateListener` fires — but no callback fires if all brokers are waiting
for a coordinator that isn't there.

Fixes:
1. On detecting it has become coordinator (membership change event or broker startup), check
   whether `pendingPlan` has a complete current phase and advance it immediately.
2. A periodic watchdog on the coordinator (e.g., every gossip sync interval) checks for stuck plans.

**Effect:** availability gap shrinks to one gossip round after the new coordinator detects its
role. The coordinator is still the sole phase advancer; the window of unavailability is bounded
rather than eliminated.

### Idea D — System partition as the phase state machine (future)

Store `PartitionGroupChangePlan` in the Raft-replicated system partition. The system partition
leader (elected by Raft, not the coordinator role) manages phase transitions. Raft election
provides automatic failover without relying on gossip convergence.

This requires the system partition to be a committed dependency and introduces bootstrap
ordering constraints. It is the right long-term architecture but not immediately applicable.

### Recommendation

**Ideas A and B together** eliminate the coordinator as a liveness dependency for phase
transitions, restoring the original property of the single-phase model. They are complementary:
Idea B handles the common case (the completing broker advances immediately); Idea A handles the
fallback (any broker can advance if the completing broker is slow to gossip). Both rely on the
same core insight: since all phase operations are pre-stored, advancement is idempotent and safe
for any broker to perform.

**Idea C** is the minimal-risk fallback if Ideas A/B introduce too much complexity in the initial
implementation — implement C first, migrate to A/B in a follow-up.
# Dynamic Cluster Configuration — Multi-Partition-Group Spec

Module paths:
- `dynamic-config` → `zeebe/dynamic-config/src/main/java/io/camunda/zeebe/dynamic/config/`
- `broker` → `zeebe/broker/src/main/java/io/camunda/zeebe/broker/`

---

## Requirements

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

## 1. Data model

```java
record CurrentClusterConfiguration(
    long version,                                      // always INITIAL_VERSION; reserved for future root-level merge
    GlobalConfiguration globalConfiguration,               // ALL brokers + cluster-level config
    Map<String, PartitionGroupConfiguration> partitionGroups, // one per partition group ID
    Optional<PhasedChangePlan> pendingPlan             // only for cluster-spanning operations
)
```

**`globalConfiguration`** — type `GlobalConfiguration`
- Holds every broker with its lifecycle state (`JOINING → ACTIVE → LEAVING → LEFT`).
- Each member is a `BrokerState`: only `State state`, `long version`, `Instant lastUpdated` —
no partition assignments.
- All cluster-level config lives here: `clusterId`, `partitionDistributorConfig`.
- Target for: `MemberJoinOperation`, `MemberLeaveOperation`, `MemberRemoveOperation`,
`PreScalingOperation`, `PostScalingOperation`, `UpdatePartitionDistributorConfigOperation`.

**`partitionGroups`** — `Map<String, PartitionGroupConfiguration>`
- One `PartitionGroupConfiguration` per partition group, keyed by group ID (e.g. `"default"`,
`"tenantA"`).
- Each entry includes only the members hosting partitions in that group.
- Each member is a `MemberPartitionState`: only `SortedMap<Integer, PartitionState> partitions`,
`long version`, `Instant lastUpdated` — no lifecycle `State`.
- `incarnationNumber` and `recovery` tracked per group (each group has its own independent Raft history).
- Partition change plans, routing state, exporter state, and history-management operations live here.
- Target for: `UpdateIncarnationNumberOperation`, `DeleteHistoryOperation`,
`PartitionBootstrapOperation`, `PartitionJoinOperation`, `PartitionLeaveOperation`,
`StartPartitionScaleUp`, `AwaitRedistributionCompletion`, `AwaitRelocationCompletion`,
`PartitionDisableExporterOperation`, `PartitionEnableExporterOperation`,
`PartitionDeleteExporterOperation`, `UpdateRoutingState`.

**`pendingPlan`** — `Optional<PhasedChangePlan>`
- Present only during cluster-spanning operations (those requiring member join/leave).
- Absent for single-group operations (exporter changes, per-group routing updates, etc.).

### `GlobalConfiguration` fields

```java
record GlobalConfiguration(
    long version,
    SortedMap<MemberId, BrokerState> members,  // lifecycle state only, no partition assignments
    Optional<CompletedChange> lastChange,
    Optional<ClusterChangePlan> pendingChanges,
    Optional<String> clusterId,
    Optional<PartitionDistributorConfig> partitionDistributorConfig
)

record BrokerState(
    MemberState.State state,   // JOINING, ACTIVE, LEAVING, LEFT
    long version,
    Instant lastUpdated
)
```

### `PartitionGroupConfiguration` fields

```java
record PartitionGroupConfiguration(
    long version,
    SortedMap<MemberId, MemberPartitionState> members,  // partition assignments only, no lifecycle State
    Optional<CompletedChange> lastChange,
    Optional<ClusterChangePlan> pendingChanges,
    Optional<RoutingState> routingState,
    long incarnationNumber,
    boolean recovery
)

record MemberPartitionState(
    SortedMap<Integer, PartitionState> partitions,
    long version,
    Instant lastUpdated
)
```

---

## 2. Phased change plan

```java
record PhasedChangePlan(
    long id,
    Instant startedAt,
    int currentPhaseIndex,   // advances as phases complete; indexes into `phases`
    List<Phase> phases,      // all phases pre-computed at plan creation
    CompletedChange lastChange
)

sealed interface Phase {
    record ClusterMembershipPhase(
        List<ClusterConfigurationChangeOperation> operations
    ) implements Phase {}

    record PartitionGroupParallelPhase(
        Map<String, List<ClusterConfigurationChangeOperation>> operationsPerGroup
    ) implements Phase {}
}
```

- All phases are pre-computed at plan creation and stored in `phases`.
- Only `currentPhaseIndex` advances; `phases` entries are never modified after creation.
- When a phase is activated its operations are copied into the appropriate sub-config's
  `pendingChanges`. From that point execution is driven by the sub-configs alone.
- `pendingPlan.phases` are read-only templates; they carry no execution state.
- **Plan ID invariant**: IDs must be monotonically increasing across coordinator restarts.
  Derive the next ID from the last ID seen in the persisted/gossiped state.

---

## 3. Operation dispatch

|                                                  Operation                                                  |       Kind       |           Target           |
|-------------------------------------------------------------------------------------------------------------|------------------|----------------------------|
| `MemberJoinOperation`, `MemberLeaveOperation`, `MemberRemoveOperation`                                      | cluster-spanning | `globalConfiguration`        |
| `PreScalingOperation`, `PostScalingOperation`                                                               | cluster-spanning | `globalConfiguration`        |
| `PartitionBootstrapOperation`, `PartitionJoinOperation`, `PartitionLeaveOperation`                          | cluster-spanning | `partitionGroups[groupId]` |
| `StartPartitionScaleUp`, `AwaitRedistributionCompletion`, `AwaitRelocationCompletion`                       | cluster-spanning | `partitionGroups[groupId]` |
| `UpdateIncarnationNumberOperation`, `DeleteHistoryOperation`                                                | single-group     | `partitionGroups[groupId]` |
| `PartitionDisableExporterOperation`, `PartitionEnableExporterOperation`, `PartitionDeleteExporterOperation` | single-group     | `partitionGroups[groupId]` |
| `UpdateRoutingState`                                                                                        | single-group     | `partitionGroups[groupId]` |

**Single-group operations** write their ops directly into `partitionGroups[groupId].pendingChanges`. No `PhasedChangePlan` is created. The concurrency constraint is per-group: reject a new request for group A only if `partitionGroups["groupA"].hasPendingChanges()` is true.

**Cluster-spanning operations** require a `PhasedChangePlan`. Additionally require: `pendingPlan` is absent and all groups the plan will touch have no pending changes.

---

## 4. How a broker executes its operations

On every config update `ClusterConfigurationManagerImpl` checks two sources:

```
1. globalConfiguration.pendingChangesFor(localMemberId)
   → execute membership operation (one at a time)
   → call updateGlobalConfiguration(c -> c.advanceConfigurationChange(...))

2. for each groupId in partitionGroups:
       partitionGroups[groupId].pendingChangesFor(localMemberId)
       → hand to PartitionManagerImpl[groupId] (non-blocking per group)
       → call updatePartitionGroupConfig(groupId, c -> c.advanceConfigurationChange(...))
```

Operations across different partition groups execute concurrently via independent
`PartitionManagerImpl` actors. Within a single group, sequential execution is preserved.

Non-default groups use `PartitionGroupConfigurationChangeAppliers` (not `ConfigurationChangeAppliers`).
The applier interface:

```java
@FunctionalInterface
interface PartitionGroupConfigurationChangeAppliers {
    PartitionGroupOperationApplier getApplier(ClusterConfigurationChangeOperation operation);

    interface PartitionGroupOperationApplier {
        MemberId memberId();
        Either<Exception, UnaryOperator<PartitionGroupConfiguration>> init(
            CurrentClusterConfiguration wrapper, String groupId);
        ActorFuture<UnaryOperator<PartitionGroupConfiguration>> apply();
    }
}
```

---

## 5. Coordinator — phase advancement

The coordinator is the ACTIVE broker with the lowest member ID, determined from
`globalConfiguration.members`.

```java
void onClusterConfigurationUpdated(CurrentClusterConfiguration config) {
    if (!isCoordinator(config)) return;
    if (config.pendingPlan().isEmpty()) return;

    final var plan = config.pendingPlan().get();
    final var currentPhase = plan.phases().get(plan.currentPhaseIndex());

    final boolean phaseComplete = switch (currentPhase) {
        case ClusterMembershipPhase ignored ->
            !config.globalConfiguration().hasPendingChanges();
        case PartitionGroupParallelPhase p ->
            p.operationsPerGroup().keySet().stream()
                .allMatch(id -> !config.partitionGroups().get(id).hasPendingChanges());
    };

    if (!phaseComplete) return;

    if (plan.currentPhaseIndex() + 1 >= plan.phases().size()) {
        configManager.updateConfiguration(c -> c.completePlan());
    } else {
        configManager.updateConfiguration(c -> c.activateNextPhase());
    }
}
```

On restart, the coordinator immediately checks the current `pendingPlan` and
advances if the current phase is already complete.

---

## 6. Merge semantics

### `CurrentClusterConfiguration.merge()`

`version` is always `INITIAL_VERSION` — it is never incremented. The current merge is always
field-by-field CRDT. The version field is reserved so that a future evolution can switch to a
root-level version-wins strategy without changing the wire format.

```java
CurrentClusterConfiguration merge(CurrentClusterConfiguration other) {
    final var mergedGlobal = globalConfiguration.merge(other.globalConfiguration);

    // Union of keys: group present in only one side is adopted without conflict.
    final var mergedGroups = new HashMap<>(other.partitionGroups);
    partitionGroups.forEach((groupId, config) ->
        mergedGroups.merge(groupId, config, PartitionGroupConfiguration::merge));

    final var mergedPlan = mergePlan(pendingPlan, other.pendingPlan);
    // version always stays INITIAL_VERSION; not used in merge decisions.
    return new CurrentClusterConfiguration(
        INITIAL_VERSION, mergedGlobal, Map.copyOf(mergedGroups), mergedPlan);
}
```

A group key present in one side but absent in the other is adopted directly. **Group keys are
never removed by a merge.** (Deletion of tenants must be handled in a later iteration.)

### `GlobalConfiguration.merge()`

`BrokerState.version` is incremented on every mutation of the record — any lifecycle transition
(`toJoining()`, `toActive()`, `toLeaving()`, `toLeft()`). Follows the same pattern as
`MemberState.version` in the existing code. Only the member itself ever writes its own `BrokerState`.

- If versions differ: higher version wins wholesale.
- If equal: members merged element-wise by `BrokerState.version`; `ClusterChangePlan` by plan
  version.
- `clusterId`: non-empty value wins over empty.
- `partitionDistributorConfig`: non-empty wins over absent; if both present, `this` wins
  (coordinator is the sole writer).
- `lastChange` invariant: never write `lastChange` without bumping the membership version.

### `PhasedChangePlan.merge()`

```java
Optional<PhasedChangePlan> mergePlan(
    Optional<PhasedChangePlan> a, Optional<PhasedChangePlan> b) {
    if (a.isEmpty()) return b;
    if (b.isEmpty()) return a;
    return Optional.of(a.get().merge(b.get()));
}

PhasedChangePlan merge(PhasedChangePlan other) {
    if (id == other.id) {
        return currentPhaseIndex >= other.currentPhaseIndex ? this : other;
    }
    if (currentPhaseIndex != other.currentPhaseIndex) {
        return currentPhaseIndex > other.currentPhaseIndex ? this : other;
    }
    return id > other.id ? this : other;
}
```

### `PartitionGroupConfiguration` sub-config merge

`MemberPartitionState.version` is incremented on every mutation of the record — any partition
operation (`addPartition()`, `removePartition()`, `updatePartition()`). Follows the same pattern
as `MemberState.version` in the existing code. Only the member itself ever writes its own
`MemberPartitionState`.

Each group's `PartitionGroupConfiguration` merges using version-based semantics:
- If versions differ: higher version wins wholesale.
- If equal: members merged element-wise by `MemberPartitionState.version`; `ClusterChangePlan` by
plan version; `RoutingState` by routing version; `incarnationNumber` by `Math.max()`; `recovery`
field: OR semantics (once set, stays set).
- `lastChange` invariant: never write `lastChange` without bumping the config version.

### Phase activation idempotency

When multiple brokers simultaneously activate Phase N+1:
1. Both read `pendingPlan.phases[N+1]` — identical content.
2. Both produce `ClusterChangePlan.init(phases[N+1].operations)` at the same initial version.
3. Both write to the same sub-config at version V+1 with identical content.

The merge resolves correctly: `ClusterChangePlan::merge` takes higher plan-internal version
(both are equal → either is fine). If Phase N+1 is already in progress (plan version at K+M),
the re-activation is silently discarded by the merge. This requires that phase operations are
deterministic and never recalculated from sub-config state.

---

## 7. Proto

Add to `zeebe/dynamic-config/src/main/resources/proto/topology.proto`:

```protobuf
// Dedicated broker lifecycle state for GlobalConfiguration.
// Does not carry partition assignments (contrast: legacy MemberState has both).
message BrokerState {
  State state = 1;
  int64 version = 2;
  google.protobuf.Timestamp lastUpdated = 3;
}

// Dedicated partition assignment state for PartitionGroupConfiguration.
// Does not carry a lifecycle State (contrast: legacy MemberState has both).
message MemberPartitionState {
  map<int32, PartitionState> partitions = 1;
  int64 version = 2;
  google.protobuf.Timestamp lastUpdated = 3;
}

// Encodes GlobalConfiguration: broker lifecycle state and cluster-level config.
message GlobalConfiguration {
  int64 version = 1;
  map<string, BrokerState> members = 2;
  CompletedChange lastChange = 3;
  ClusterChangePlan pendingChanges = 4;
  optional string clusterId = 5;
  PartitionDistributorConfig partitionDistributorConfig = 6;
}

// Encodes PartitionGroupConfiguration: per-group partition assignment state.
message PartitionGroupConfiguration {
  int64 version = 1;
  map<string, MemberPartitionState> members = 2;
  CompletedChange lastChange = 3;
  ClusterChangePlan pendingChanges = 4;
  RoutingState routingState = 5;
  int64 incarnationNumber = 6;
  bool recovery = 7;
}

message CurrentClusterConfiguration {
  GlobalConfiguration globalConfiguration = 1;
  map<string, PartitionGroupConfiguration> partitionGroups = 2;
  PhasedChangePlan pendingPlan = 3;
  int64 version = 5; // always INITIAL_VERSION; reserved for future root-level version-based merge
}

message PhasedChangePlan {
  int64 id = 1;
  google.protobuf.Timestamp startedAt = 2;
  int32 currentPhaseIndex = 3;
  repeated PhasedChangePlanPhase phases = 4;
  CompletedChange lastChange = 5;
}

message PhasedChangePlanPhase {
  oneof phase_type {
    ClusterMembershipPhase membershipPhase = 1;
    PartitionGroupParallelPhase partitionPhase = 2;
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
```

Existing messages used unchanged: `ClusterChangePlan`, `CompletedChange`, `RoutingState`,
`PartitionState`, `TopologyChangeOperation` (and all operation sub-messages), `State` enum,
`PartitionDistributorConfig` (reused directly in `GlobalConfiguration` field 6).
`ClusterTopology` and `MemberState` are kept for the legacy `GossipState` field 1 migration path
and must not be removed.

`GossipState` adds a new field for `CurrentClusterConfiguration`; field 1 is kept unchanged for
backwards compatibility:

```protobuf
message GossipState {
  ClusterTopology clusterTopology = 1;                              // kept; old brokers read/write this
  CurrentClusterConfiguration currentClusterConfiguration = 2;  // new; upgraded brokers write this
}
```

**Migration** — on decoding a `GossipState`, check field 2 first:
- If `currentClusterConfiguration` (field 2) is present → decode directly.
- If absent (old broker or pre-upgrade snapshot) → read `clusterTopology` (field 1) and migrate:
- `globalConfiguration` = all members as `BrokerState` (state extracted, partitions dropped);
`clusterId` preserved; `partitionDistributorConfig` taken from `ClusterTopology.partitionDistributor`
directly; `recovery` absent (no `recovery` field in `ClusterTopology` proto)
- `partitionGroups["default"]` = original members as `MemberPartitionState` (partitions
extracted, state dropped); `routingState` and `incarnationNumber` preserved; `recovery` defaults
to `false`
- `version` = `INITIAL_VERSION`
- `pendingPlan` = absent

---

## 8. Scale-up example

Goal: add broker B5, remove B1; two partition groups (`default`, `groupA`).

Generated `PhasedChangePlan`:

```
Phase 0 — ClusterMembershipPhase
    MemberJoinOperation(B5)

Phase 1 — PartitionGroupParallelPhase
    "default": [PartitionBootstrapOp(B5, p=1), PartitionLeaveOp(B1, p=1), UpdateRoutingState]
    "groupA":  [PartitionBootstrapOp(B5, p=1), PartitionLeaveOp(B1, p=1), UpdateRoutingState]

Phase 2 — ClusterMembershipPhase
    MemberLeaveOperation(B1)
```

Execution:
1. Coordinator activates Phase 0 → `globalConfiguration.pendingChanges = [MemberJoinOperation(B5)]`.
2. B5 executes join → `globalConfiguration.pendingChanges` clears.
3. Coordinator detects Phase 0 complete → activates Phase 1 atomically for both groups.
4. `PartitionManagerImpl["default"]` and `PartitionManagerImpl["groupA"]` on B5 bootstrap
partition 1 concurrently.
5. B1 handles `PartitionLeaveOp` in both groups concurrently once B5's bootstrap completes.
6. Coordinator detects Phase 1 complete → activates Phase 2.
7. B1 executes `MemberLeaveOperation` → plan complete.

---

## 9. Concurrent single-group operations example

Two independent exporter-disable requests on different groups proceed without coordination:

```
partitionGroups["groupA"].pendingChanges = ClusterChangePlan([
    PartitionDisableExporterOp(B1, p=1), PartitionDisableExporterOp(B2, p=2)
])
// Request 2 accepted because partitionGroups["groupB"].hasPendingChanges() == false
partitionGroups["groupB"].pendingChanges = ClusterChangePlan([
    PartitionDisableExporterOp(B1, p=1), PartitionDisableExporterOp(B2, p=2)
])
// pendingPlan absent throughout
```

All four operations run concurrently via independent `PartitionManagerImpl` actors.

---

## 10. Implementation task breakdown

Issues 1–6 are implemented on branch `dd-poc-cluster-config-per-physical-tenants`.

### Issue 1 — Add proto messages for `CurrentClusterConfiguration`

**Description**
Add ten new proto messages to `topology.proto` (see §7): `BrokerState`, `MemberPartitionState`,
`GlobalConfiguration`, `PartitionGroupConfiguration`, `CurrentClusterConfiguration`,
`PhasedChangePlan`, `PhasedChangePlanPhase`, `ClusterMembershipPhase`,
`PartitionGroupParallelPhase`, `PartitionGroupOperationList`. `GossipState` is not changed yet.
Purely additive; no existing message is modified or removed.

**Implementation idea**
Append the messages listed in §7 to
`zeebe/dynamic-config/src/main/resources/proto/topology.proto`. Do not modify any existing
message. Regenerate the Java protobuf sources.

**Acceptance criteria**
- Proto compiles; generated Java classes are committed.
- No existing proto message is modified.
- Module builds with no other changes.

**Depends on** — none

---

### Issue 2 — Introduce `PhasedChangePlan` record with `merge()`

**Description**
Create `PhasedChangePlan.java` in `dynamic-config/.../state/` as specified in §2.
Includes the `Phase` sealed interface with `ClusterMembershipPhase` and
`PartitionGroupParallelPhase` nested records. Implements `merge()` per §6. No proto
serialisation or wiring; subsequent issues build on this type.

**Implementation idea**
- All fields immutable; use `List.copyOf()` and `Collectors.toUnmodifiableMap()` in compact
constructors.
- `merge()` implements the three cases from §6: same ID (higher phase index), different ID
(higher phase index), different ID equal phase (higher plan ID).
- Add `withNextPhase()`, `hasNextPhase()`, `currentPhase()` helpers.

**Acceptance criteria**
- Unit tests for all three `merge()` cases, `currentPhase()`, and `hasNextPhase()`.
- All collections are defensively copied.
- No existing class is modified.

**Depends on** — none

---

### Issue 3 — Introduce `CurrentClusterConfiguration` record with `merge()`

**Description**
Create `CurrentClusterConfiguration.java` in `dynamic-config/.../state/` as specified in
§1. Fields: `GlobalConfiguration globalConfiguration`, `Map<String, PartitionGroupConfiguration>
partitionGroups`, `Optional<PhasedChangePlan> pendingPlan`. Implements `merge()` per §6.
Includes `ofDefault(ClusterConfiguration)` migration factory. No proto, no wiring; state-transition
methods added in Issue 4.

Also create `GlobalConfiguration.java`, `BrokerState.java`, `PartitionGroupConfiguration.java`, and
`MemberPartitionState.java` with their respective fields and merge semantics (see §1 and §6).

**Implementation idea**
- Compact constructors: defensively copy all collections.
- `CurrentClusterConfiguration.merge()`: field-by-field delegating to
`GlobalConfiguration.merge()` and `PartitionGroupConfiguration.merge()` per group, union semantics
for group keys.
- `ofDefault(config)`: wraps each member as a `BrokerState` (lifecycle state extracted) for
`GlobalConfiguration`; wraps each member as a `MemberPartitionState` (partition assignments
extracted) for `PartitionGroupConfiguration`, placed in `partitionGroups["default"]`.
- Add `updatePartitionGroupConfig(groupId, updater)` and `updateGlobalConfiguration(updater)`.
- Add `withDerivedGlobalConfiguration()`: rebuilds `globalConfiguration` as a pure function of
  `partitionGroups["default"]` (state + version extracted from each member entry; partitions
  dropped). Returns a new `CurrentClusterConfiguration` with the updated membership. Used
  by the manager in the transitional period (Issues 10–11) before phased dispatch takes over.
  Does nothing (returns `this`) if `partitionGroups` has no `"default"` entry yet.
- `PartitionGroupConfiguration.advance()` removes members whose `partitions` map is **empty**
  after plan completion (replaces the legacy `state() == State.LEFT` filter). Empty partitions
  after `PartitionLeaveApplier` removes the last partition is the structural equivalent of LEFT
  in the new model.

**Acceptance criteria**
- Unit tests: concurrent membership merge at same version; group-key union; overlapping group
merge; plan merge delegation; `ofDefault()` correctness.
- `withDerivedGlobalConfiguration()` test: after updating a member in the default group, calling
  `withDerivedGlobalConfiguration()` produces a `globalConfiguration` that matches the updated state;
  version matches the default group's version.
- `advance()` test: member with empty `partitions` is removed after advance; member with at
  least one partition is kept.
- `GlobalConfiguration` and `PartitionGroupConfiguration` have their own unit tests for `merge()`.
- No existing class is modified.

**Depends on** — Issue 2

---

### Issue 4 — Add `initPlan()`, `activateNextPhase()`, `completePlan()`

**Description**
Add the state-transition methods to `CurrentClusterConfiguration` that the coordinator
uses to drive the phased plan:
- `initPlan(plan)` — sets `pendingPlan` at `currentPhaseIndex=0`; activates Phase 0 into sub-configs.
- `activateNextPhase()` — increments index; activates Phase N+1.
- `completePlan()` — clears `pendingPlan`.

Activation copies phase operations into sub-configs: for `ClusterMembershipPhase` calls
`GlobalConfiguration.startConfigurationChange()`; for `PartitionGroupParallelPhase` calls
`PartitionGroupConfiguration.startConfigurationChange()` on each targeted group.

**Implementation idea**

```java
private CurrentClusterConfiguration applyPhase(PhasedChangePlan plan) {
    return switch (plan.currentPhase()) {
        case ClusterMembershipPhase p -> {
            final var updated = p.operations().isEmpty() ? globalConfiguration
                : globalConfiguration.startConfigurationChange(p.operations());
            yield new CurrentClusterConfiguration(updated, partitionGroups, Optional.of(plan));
        }
        case PartitionGroupParallelPhase p -> {
            final var updatedGroups = new HashMap<>(partitionGroups);
            p.operationsPerGroup().forEach((groupId, ops) -> {
                if (!ops.isEmpty()) updatedGroups.compute(groupId, (id, cfg) -> {
                    if (cfg == null) throw new IllegalStateException("Unknown group: " + id);
                    return cfg.startConfigurationChange(ops);
                });
            });
            yield new CurrentClusterConfiguration(globalConfiguration, updatedGroups, Optional.of(plan));
        }
    };
}
```

**Acceptance criteria**
- Unit tests for each method; overrun guard for `activateNextPhase()` on last phase;
`ClusterMembershipPhase` activates only `globalConfiguration`; `PartitionGroupParallelPhase`
activates only named groups; `globalConfiguration` unchanged when activating a partition phase.

**Depends on** — Issues 2, 3

---

### Issue 5 — Serialiser: `CurrentClusterConfiguration` ↔ proto + migration

**Description**
Add encode/decode methods to `ProtoBufSerializer` for `CurrentClusterConfiguration`.
Each Java type maps directly to its dedicated proto message (see §7). No temporary
`ClusterConfiguration` intermediary; no reuse of `ClusterTopology` for the new types.

**Gossip migration** — two separate paths: encoding (send) and decoding (receive):

*Send (dual-write):* new brokers populate BOTH fields of `GossipState`:
- Field 2 (`currentClusterConfiguration`): the full `CurrentClusterConfiguration` using
  the new dedicated messages.
- Field 1 (`clusterTopology`): a legacy `ClusterTopology` derived from `partitionGroups["default"]`
  and `globalConfiguration`, using the existing `encodeClusterTopology()` path. This keeps old
  brokers' view of this node current during the rolling upgrade window. If field 1 were left unset,
  old brokers would freeze their last-known state for this node and never see subsequent lifecycle
  or partition changes — stalling any old-broker coordinator.

*Receive:* when decoding a `GossipState`:
- If field 2 (`currentClusterConfiguration`) is present → decode directly as
  `CurrentClusterConfiguration`.
- If absent (message from an old broker) → read `clusterTopology` (field 1) and migrate via
  `ofDefault()`:
  - Each `MemberState` entry → `BrokerState` (state extracted, partitions dropped) for membership
  - Each `MemberState` entry → `MemberPartitionState` (partitions extracted, state dropped) for
    the default group

**Persistence migration — header version bump:**
`PersistedClusterConfiguration` uses a fixed-size header that already contains a version field.
Use this version to identify the body format:
- Header version 1 (current / legacy): body is raw `ClusterTopology` bytes — read via
  `decodeClusterTopology()` and migrate via `ofDefault()`.
- Header version 2 (new): body is raw `CurrentClusterConfiguration` bytes — read via
  `decodeCurrentClusterConfiguration()`.

When writing the new format, bump the header version to 2 and encode the body with
`encodeCurrentClusterConfiguration()`. On first boot after an upgrade the broker reads
header version 1, migrates via `ofDefault()`, and immediately writes back header version 2 with
the migrated content.

**Implementation idea**
Add to `ProtoBufSerializer`:
- `encodeCurrentClusterConfiguration(CurrentClusterConfiguration)` → `byte[]`
  Encodes to `CurrentClusterConfiguration` proto using the new dedicated messages.
- `encodeGossipState(CurrentClusterConfiguration)` → `byte[]`
  Dual-write: builds a `GossipState` with field 1 derived from the default group (legacy path) and
  field 2 from the full wrapper (new path).
- `decodeGossipState(byte[])` → `CurrentClusterConfiguration`
  Checks field 2 first; if absent migrates from field 1 via `ofDefault()`.
- `decodeCurrentClusterConfiguration(byte[])` → `CurrentClusterConfiguration`
  Decodes raw `CurrentClusterConfiguration` bytes (no `GossipState` envelope); used by
  persistence (header version 2 path).
- `encodeGlobalConfiguration(GlobalConfiguration)` → `GlobalConfiguration` proto — maps
  `BrokerState` per member directly.
- `decodeGlobalConfiguration(proto.GlobalConfiguration)` → Java `GlobalConfiguration`
- `encodePartitionGroupConfiguration(PartitionGroupConfiguration)` → `PartitionGroupConfiguration`
  proto — maps `MemberPartitionState` per member directly.
- `decodePartitionGroupConfiguration(proto.PartitionGroupConfiguration)` → Java
  `PartitionGroupConfiguration`
- Internal `encodePhasedChangePlan` / `decodePhasedChangePlan` helpers.

Do not modify any existing serialiser method.

**Acceptance criteria**
- Round-trip test with two groups.
- Migration test (gossip): decode a raw `ClusterTopology`-based `GossipState` (field 2 absent);
  default group content preserved; `globalConfiguration` members have correct `State`.
- Migration test (persistence): decode a legacy header-v1 file; verify migrated content; verify
  that the rewritten file has header version 2 and round-trips as `CurrentClusterConfiguration`.
- Dual-write test: encode a `CurrentClusterConfiguration` via `encodeGossipState()`; decode
  with old code path (`decode(byte[])`) and confirm `clusterTopology` is non-empty and matches
  the default group.
- Round-trip test for `PhasedChangePlan` with both phase types.

**Depends on** — Issues 1, 3

---

### Issue 6 — Concurrent operation application in `ClusterConfigurationManagerImpl`

**Description**
Extend `ClusterConfigurationManagerImpl` to apply operations for multiple partition groups
concurrently. Add in-memory state for non-default groups (persistence comes in Issue 10).

Add fields:

```java
private final Map<String, PartitionGroupConfigurationChangeAppliers> partitionGroupAppliers = new HashMap<>();
private final Map<String, PartitionGroupConfiguration> nonDefaultGroupConfigs = new HashMap<>();
private final Map<String, Boolean> onGoingGroupOperations = new HashMap<>();
private final Map<String, Boolean> shouldRetryGroup = new HashMap<>();
```

Add package-private methods:
- `registerPartitionGroupAppliers(String groupId, PartitionGroupConfigurationChangeAppliers)`
- `removePartitionGroupAppliers(String groupId)`
- `setPartitionGroupConfig(String groupId, PartitionGroupConfiguration config)`
- `updatePartitionGroupConfig(String groupId, UnaryOperator<PartitionGroupConfiguration> updater)`
- `getPartitionGroupConfig(String groupId)` (`@VisibleForTesting`)

Extend `applyConfigurationChangeOperation`: after the default group, iterate
`nonDefaultGroupConfigs.forEach(this::applyGroupConfigurationChangeOperation)`.

`applyGroupConfigurationChangeOperation` mirrors the default group's apply/init/retry logic
using per-group state maps. Default and non-default appliers must be **different instances**
(each closes over a different update callback).

**Applier `init()` signature:** `PartitionGroupOperationApplier.init()` receives
`(CurrentClusterConfiguration wrapper, String groupId)` — the full wrapper, not just the
group's config. Appliers that guard on the broker being ACTIVE (e.g., `PartitionJoinApplier`,
`PartitionBootstrapApplier`) look up the lifecycle state via
`wrapper.globalConfiguration().members().get(memberId)`. The return type
(`UnaryOperator<PartitionGroupConfiguration>`) is unchanged. The dispatch site must pass the full
wrapper to every `init()` call.

**`PartitionGroupConfiguration.advance()` member cleanup:** After plan completion, `advance()`
removes member entries whose `partitions` map is empty (contrast: the default-group path removes
members with `state() == State.LEFT`). `PartitionLeaveApplier` removes partitions; once all
partitions are gone the member entry is cleaned up on the next `advance()`. This is safe because
`PartitionBootstrapApplier.init()` always writes at least one JOINING partition entry, so the
partitions map is never empty between init and completion.

**Acceptance criteria**
- Tests: op applied for non-default group; no op without appliers; two groups' `apply()` futures
pending simultaneously (concurrent dispatch); isolation between groups;
`updatePartitionGroupConfig` triggers dispatch.
- Test: `PartitionJoinApplier` (or equivalent) correctly rejects init when broker is not ACTIVE
  in `globalConfiguration` (non-default group path).
- Test: member with no remaining partitions is removed after `advance()` in a non-default group.
- All existing manager tests pass.

**Depends on** — Issues 2, 3

---

### Issue 7 — Register per-group appliers from non-default `PartitionManagerImpl` instances

**Description**
Wire each non-default `PartitionManagerImpl` to call
`registerPartitionGroupAppliers(groupId, appliers)` at startup and
`removePartitionGroupAppliers(groupId)` on shutdown.

The default group keeps using the existing `registerTopologyChangeAppliers(appliers)`. Only
non-default groups use the new `PartitionGroupConfigurationChangeAppliers` API.

Non-default group appliers must use `updatePartitionGroupConfig(partitionGroup, updater)` as
their update callback instead of `updateClusterConfiguration(updater)`.

**Implementation idea**

In `PartitionManagerImpl.java`:
1. Locate where the default group builds and registers its `ConfigurationChangeAppliers`.
2. For non-default groups (`!partitionGroup.equals(DEFAULT_GROUP_NAME)`):
- Build a `PartitionGroupConfigurationChangeAppliers` instance wrapping the existing per-operation
applier constructors with `updatePartitionGroupConfig` as the update callback.
- Call `registerPartitionGroupAppliers(partitionGroup, appliers)` instead of
`registerTopologyChangeAppliers(appliers)`.
3. On shutdown: call `removePartitionGroupAppliers(partitionGroup)` for non-default groups.

The dispatch site in `applyGroupConfigurationChangeOperation()` must pass the full
`CurrentClusterConfiguration` wrapper and the `groupId` string to every
`operationApplier.init()` call (per the updated interface; see §4 and Issue 6). The wrapper is
available from the manager's internal state after Issue 10; in Issue 7 (pre-Issue 10), pass a
synthesised wrapper containing at least the `globalConfiguration` derived from the default group
and the current group config.

**Acceptance criteria**
- Non-default `PartitionManagerImpl` registers appliers on startup and unregisters on shutdown.
- Simulated JOINING → ACTIVE transition in non-default group: `getPartitionGroupConfig(groupId)`
reflects the state change.
- Default group behaviour and existing tests unchanged.

**Depends on** — Issue 6

---

### Issue 8 — Seed non-default group configs at broker startup

**Description**
At broker startup, seed an initial `PartitionGroupConfiguration` for each configured non-default
physical tenant ID via `setPartitionGroupConfig(groupId, config)`. Also run
`ExporterStateInitializer` per non-default group.

**Implementation idea**

In `StaticInitializer` (or whichever `ClusterConfigurationInitializer` is wired at startup):
1. After constructing the default group's `ClusterConfiguration`, retrieve all configured
non-default tenant IDs from `BrokerCfg`.
2. For each, seed:

```java
PartitionGroupConfiguration seed = PartitionGroupConfiguration.init();
for (var entry : defaultConfig.members().entrySet()) {
    seed = seed.addMember(entry.getKey(), entry.getValue());
}
manager.setPartitionGroupConfig(groupId, seed);
```

3. Extend `ExporterStateInitializer` to iterate all groups and call
   `updatePartitionGroupConfig(groupId, ...)` for each non-default group.

The seed must match the default group's members and partition assignments exactly.

**Acceptance criteria**
- After startup with `default` + `tenantA`, `getPartitionGroupConfig("tenantA")` returns a
non-null `PartitionGroupConfiguration` with identical members and partition assignments to the
default group.
- `ExporterStateInitializer` marks exporters initialised in each group.
- Default group startup behaviour unchanged.

**Depends on** — Issues 6, 7

---

### Issue 9 — Derive `PartitionDistribution` per group; remove `PartitionDistribution.withGroupName()`

**Description**
Replace `getPartitionDistribution().withGroupName(groupId)` with a proper per-group derivation.
Add `getPartitionDistribution(String groupId)` to `DynamicClusterConfigurationService`.
Delete `PartitionDistribution.withGroupName()` and `clonePartitionMetadataWithGroup()`.

**Implementation idea**

In `DynamicClusterConfigurationService`:

```java
public PartitionDistribution getPartitionDistribution(String groupId) {
    if (DEFAULT_GROUP_NAME.equals(groupId)) return getPartitionDistribution();
    final PartitionGroupConfiguration groupConfig = manager.getPartitionGroupConfig(groupId);
    if (groupConfig == null) throw new IllegalStateException("Unknown partition group: " + groupId);
    return deriveFrom(groupConfig);
}
```

Extract the derivation logic into a static `deriveFrom(PartitionGroupConfiguration)` helper
shared by both paths.

Update all non-default `PartitionManagerImpl` callers to use `getPartitionDistribution(partitionGroup)`.

Verify no references remain: `grep -r "withGroupName" zeebe/`

**Acceptance criteria**
- `withGroupName()` and `clonePartitionMetadataWithGroup()` deleted; no references remain.
- `getPartitionDistribution("default")` equals `getPartitionDistribution()`.
- Non-default partitions start up correctly.
- Integration test verifies non-default partition startup with the new path.

**Depends on** — Issues 7, 8

---

### Issue 10 — Migrate manager state to `CurrentClusterConfiguration`; extend persistence

**Description**
Migrate `ClusterConfigurationManagerImpl` and `PersistedClusterConfiguration` to store
`CurrentClusterConfiguration` instead of bare `ClusterConfiguration`. Remove the
in-memory `nonDefaultGroupConfigs` map from Issue 6; all group state lives in the wrapper.

Existing public APIs (`getClusterConfiguration()`, `updateClusterConfiguration()`) continue to
work for the default group via compat accessors — do not break the public API.

For now, member ops (`MemberJoinOperation` etc.) still update the default group's config (not
`globalConfiguration` directly). `globalConfiguration` is kept current by calling
`withDerivedGlobalConfiguration()` (added in Issue 3) before every persist and gossip.

**Transitional invariant (Issues 10–11):** `globalConfiguration` is a pure, synchronous function of
`partitionGroups["default"]`, recomputed at every `updateLocalConfiguration()` call. Specifically:
`globalConfiguration.members` = members from the default group with lifecycle state extracted;
`globalConfiguration.version` = the default group's version. This invariant is enforced by always
calling `withDerivedGlobalConfiguration()` before writing; it is never computed at read time.

**Cutover (Issue 12):** Once `PhasedChangePlan` dispatch is active, member ops are routed through
`ClusterMembershipPhase` and write directly to `globalConfiguration`. At that point `withDerivedGlobalConfiguration()`
must NOT be called — `globalConfiguration` is the source of truth. Add a `// TODO Issue 12: remove
withDerivedGlobalConfiguration() once phased dispatch is live` comment at the call site.

Add `ActorFuture<CurrentClusterConfiguration> getMultiTenantConfiguration()` for the
coordinator (used in Issues 12, 13).

**Implementation idea**

1. Update `PersistedClusterConfiguration` to write header version 2 and encode the body with
   `encodeCurrentClusterConfiguration` (from Issue 5). On read, branch on the header
   version: version 1 → `decodeClusterTopology()` + `ofDefault()` migration; version 2 →
   `decodeCurrentClusterConfiguration()`. First-boot migration is automatic.
2. In `ClusterConfigurationManagerImpl`:
   - Remove `nonDefaultGroupConfigs` field.
   - Store the wrapper internally; all group reads/writes go through `wrapper.partitionGroups()`.
   - `updatePartitionGroupConfig(groupId, updater)` → `wrapper.updatePartitionGroupConfig(groupId, updater)` → persist.
   - `getClusterConfiguration()` → `wrapper.partitionGroups().get("default")`.
   - `updateClusterConfiguration(updater)` → extract default group, apply updater, put back, persist.
3. In `updateLocalConfiguration()`, call `wrapper.withDerivedGlobalConfiguration()` before every
   `persist()` and `gossip()` call. This is the only call site; `globalConfiguration` is never
   derived at read time. Leave a TODO comment referencing Issue 12 (the cutover point).

**Acceptance criteria**
- Old-format persistence file migrates transparently on first boot.
- Non-default group configs survive broker restart.
- `nonDefaultGroupConfigs` field removed.
- All existing `ClusterConfigurationManagerImpl` and `PersistedClusterConfiguration` tests pass.
- `getMultiTenantConfiguration()` returns the full wrapper.
- Transitional invariant test: after `updateClusterConfiguration()` changes a member's lifecycle
  state (e.g., JOINING → ACTIVE), `getMultiTenantConfiguration().globalConfiguration().members()`
  reflects the updated state without an additional explicit write.

**Depends on** — Issues 5, 6, 7, 8

---

### Issue 11 — `ClusterConfigurationGossiper` carries `CurrentClusterConfiguration`

**Description**
Migrate `ClusterConfigurationGossiper` to gossip `CurrentClusterConfiguration` via the new
`GossipState.currentClusterConfiguration` field (field 2). After this issue, non-default group
state is visible cluster-wide.

Rolling-restart safety: new brokers dual-write both fields of `GossipState` — field 1 carries a
legacy `ClusterTopology` derived from the default group, field 2 carries the full
`CurrentClusterConfiguration`. Old brokers read only field 1 and continue to receive live
updates from new nodes. New brokers read field 2 when present (new broker sender) and fall back
to field 1 migration when absent (old broker sender). No feature gate is required.

**Implementation idea**

Relevant files:
- `dynamic-config/.../gossip/ClusterConfigurationGossiper.java`
- `dynamic-config/.../gossip/ClusterConfigurationGossipState.java`

Steps:
1. Change gossiper internal state to hold `CurrentClusterConfiguration`.
2. On send: call `encodeGossipState(CurrentClusterConfiguration)` from Issue 5, which
dual-writes field 1 (default-group legacy view) and field 2 (full wrapper). Both fields are
always populated; old brokers see field 1, new brokers prefer field 2.
3. On receipt: call `decodeGossipState(byte[])` from Issue 5 — if field 2 is present, decode as
`CurrentClusterConfiguration`; if absent, decode field 1 and migrate via `ofDefault()`.
Merge using `CurrentClusterConfiguration.merge()` (from Issue 3).
4. `ClusterConfigurationUpdateListener` callbacks: existing listeners receive `ClusterConfiguration`
(extract default group from `wrapper.partitionGroups().get("default")`). Add a new listener
shape receiving `CurrentClusterConfiguration` for the coordinator.

**Acceptance criteria**
- Two-broker test (both on new code): non-default group state propagates via gossip.
- Dual-write test: gossip message from a new broker decoded by the old code path produces a
  non-empty `ClusterTopology` matching the default group's current state.
- Rolling-restart test: default group state converges between old and new brokers (old broker
  sees live updates from new brokers via field 1).
- All existing gossip integration tests pass.
- Rolling-restart strategy documented in PR.

**Depends on** — Issues 5, 10

---

### Issue 12 — Coordinator generates `PhasedChangePlan` for default-group-only operations

**Description**
Migrate `ConfigurationChangeCoordinatorImpl` from generating flat `ClusterChangePlan` to phased
`PhasedChangePlan`. **Scope: default group only.** Behaviour is identical to the current
flat plan; this validates the phase mechanism. Existing integration tests must pass unchanged.

**Implementation idea**

Phase-splitting rules (operation list produced by request transformers is unchanged):
- `MemberJoinOperation`, `MemberLeaveOperation`, `MemberRemoveOperation`,
`PreScalingOperation`, `PostScalingOperation` → `ClusterMembershipPhase`
- All other operations → `PartitionGroupParallelPhase` with `{"default": [ops]}`
- Ordering: join-membership phases before partition phases; leave-membership phases after.

`isCoordinator()` reads from `wrapper.globalConfiguration().members()`.

Start the plan via `manager.updateMultiConfig(c -> c.initPlan(plan))`. `initPlan` (Issue 4)
writes Phase 0 ops into the appropriate sub-config's `pendingChanges`. Brokers execute from
sub-config as before.

**Acceptance criteria**
- All existing coordinator integration tests pass without modification.
- Scale-up completes end-to-end with correct phase sequencing.
- Unit test verifies phase-splitting for a representative scale-up operation list.

**Depends on** — Issues 4, 10

---

### Issue 13 — Wire phase advancement in the coordinator

**Description**
Implement the `ClusterConfigurationUpdateListener` callback in `ConfigurationChangeCoordinatorImpl`
that detects phase completion and calls `activateNextPhase()` or `completePlan()`.

On coordinator re-election or restart, immediately check the persisted `pendingPlan` and advance
if the current phase is already complete.

**Implementation idea**

Register a listener receiving `CurrentClusterConfiguration` (from Issue 11). On each callback:

```java
void onClusterConfigurationUpdated(CurrentClusterConfiguration config) {
    if (!isCoordinator(config)) return;
    if (config.pendingPlan().isEmpty()) return;

    final var plan = config.pendingPlan().get();
    final var currentPhase = plan.phases().get(plan.currentPhaseIndex());

    final boolean complete = switch (currentPhase) {
        case ClusterMembershipPhase ignored -> !config.globalConfiguration().hasPendingChanges();
        case PartitionGroupParallelPhase p ->
            p.operationsPerGroup().keySet().stream()
                .allMatch(id -> !config.partitionGroups().get(id).hasPendingChanges());
    };
    if (!complete) return;

    if (plan.currentPhaseIndex() + 1 >= plan.phases().size()) {
        manager.updateMultiConfig(CurrentClusterConfiguration::completePlan);
    } else {
        manager.updateMultiConfig(CurrentClusterConfiguration::activateNextPhase);
    }
}
```

The callback is idempotent — concurrent firings produce the same result (see §6 phase activation
idempotency). The actor's serial execution semantics prevent overlapping `updateMultiConfig` calls.

**Acceptance criteria**
- Scale-up completes end-to-end: Phase 0 (join) → Phase 1 (partitions) → Phase 2 (leave) → done.
- Restart recovery: coordinator restarted during Phase 1; plan completes after new coordinator.
- All existing coordinator integration tests pass.

**Depends on** — Issues 11, 12

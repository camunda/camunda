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
record PartitionGroupClusterConfiguration(
    ClusterConfiguration clusterMembership,                  // ALL brokers; MemberState.partitions always empty
    Map<String, ClusterConfiguration> partitionGroupConfigs, // one per partition group ID
    Optional<PartitionGroupChangePlan> pendingPlan           // only for cluster-spanning operations
)
```

**`clusterMembership`**
- Holds every broker with its lifecycle state (`JOINING → ACTIVE → LEAVING → LEFT`).
- `MemberState.partitions` is always empty here.
- `clusterId` lives here.
- Target for: `MemberJoinOperation`, `MemberLeaveOperation`, `MemberRemoveOperation`,
  `PreScalingOperation`, `PostScalingOperation`.

**`partitionGroupConfigs`**
- One `ClusterConfiguration` per partition group, keyed by group ID (e.g. `"default"`, `"tenantA"`).
- Each entry includes only the members hosting partitions in that group.
- `MemberState.state` here means Raft-group participation, not cluster lifecycle.
- `incarnationNumber` tracked per group (each group has its own Raft history).
- Partition change plans, routing state, exporter state, and history-management operations live here.
- Target for: `UpdateIncarnationNumberOperation`, `DeleteHistoryOperation`,
  `PartitionBootstrapOperation`, `PartitionJoinOperation`, `PartitionLeaveOperation`,
  `StartPartitionScaleUp`, `AwaitRedistributionCompletion`, `AwaitRelocationCompletion`,
  `PartitionDisableExporterOperation`, `PartitionEnableExporterOperation`,
  `PartitionDeleteExporterOperation`, `UpdateRoutingState`.

**`pendingPlan`**
- Present only during cluster-spanning operations (those requiring member join/leave).
- Absent for single-group operations (exporter changes, per-group routing updates, etc.).

---

## 2. Phased change plan

```java
record PartitionGroupChangePlan(
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

| Operation | Kind | Target |
|---|---|---|
| `MemberJoinOperation`, `MemberLeaveOperation`, `MemberRemoveOperation` | cluster-spanning | `clusterMembership` |
| `PreScalingOperation`, `PostScalingOperation` | cluster-spanning | `clusterMembership` |
| `PartitionBootstrapOperation`, `PartitionJoinOperation`, `PartitionLeaveOperation` | cluster-spanning | `partitionGroupConfigs[groupId]` |
| `StartPartitionScaleUp`, `AwaitRedistributionCompletion`, `AwaitRelocationCompletion` | cluster-spanning | `partitionGroupConfigs[groupId]` |
| `UpdateIncarnationNumberOperation`, `DeleteHistoryOperation` | single-group | `partitionGroupConfigs[groupId]` |
| `PartitionDisableExporterOperation`, `PartitionEnableExporterOperation`, `PartitionDeleteExporterOperation` | single-group | `partitionGroupConfigs[groupId]` |
| `UpdateRoutingState` | single-group | `partitionGroupConfigs[groupId]` |

**Single-group operations** write their ops directly into `partitionGroupConfigs[groupId].pendingChanges`. No `PartitionGroupChangePlan` is created. The concurrency constraint is per-group: reject a new request for group A only if `partitionGroupConfigs["groupA"].hasPendingChanges()` is true.

**Cluster-spanning operations** require a `PartitionGroupChangePlan`. Additionally require: `pendingPlan` is absent and all groups the plan will touch have no pending changes.

---

## 4. How a broker executes its operations

On every config update `ClusterConfigurationManagerImpl` checks two sources:

```
1. clusterMembership.pendingChangesFor(localMemberId)
   → execute membership operation (one at a time)
   → call updateClusterMembership(c -> c.advanceConfigurationChange(...))

2. for each groupId in partitionGroupConfigs:
       partitionGroupConfigs[groupId].pendingChangesFor(localMemberId)
       → hand to PartitionManagerImpl[groupId] (non-blocking per group)
       → call updatePartitionGroupConfig(groupId, c -> c.advanceConfigurationChange(...))
```

Operations across different partition groups execute concurrently via independent
`PartitionManagerImpl` actors. Within a single group, sequential execution is preserved.

---

## 5. Coordinator — phase advancement

The coordinator is the ACTIVE broker with the lowest member ID, determined from
`clusterMembership.members`.

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

On restart, the coordinator immediately checks the current `pendingPlan` and
advances if the current phase is already complete.

---

## 6. Merge semantics

### `PartitionGroupClusterConfiguration.merge()`

No outer version — always field-by-field CRDT merge:

```java
PartitionGroupClusterConfiguration merge(PartitionGroupClusterConfiguration other) {
    final var mergedMembership = clusterMembership.merge(other.clusterMembership);

    // Union of keys: group present in only one side is adopted without conflict.
    final var mergedGroups = new HashMap<>(other.partitionGroupConfigs);
    partitionGroupConfigs.forEach((groupId, config) ->
        mergedGroups.merge(groupId, config, ClusterConfiguration::merge));

    final var mergedPlan = mergePlan(pendingPlan, other.pendingPlan);
    return new PartitionGroupClusterConfiguration(
        mergedMembership, Map.copyOf(mergedGroups), mergedPlan);
}
```

A group key present in one side but absent in the other is adopted directly. **Group keys are
never removed by a merge.** (Deletion of tenants must be handled in a later iteration.)

### `PartitionGroupChangePlan.merge()`

```java
Optional<PartitionGroupChangePlan> mergePlan(
    Optional<PartitionGroupChangePlan> a, Optional<PartitionGroupChangePlan> b) {
    if (a.isEmpty()) return b;
    if (b.isEmpty()) return a;
    return Optional.of(a.get().merge(b.get()));
}

PartitionGroupChangePlan merge(PartitionGroupChangePlan other) {
    if (id == other.id) {
        return currentPhaseIndex >= other.currentPhaseIndex ? this : other;
    }
    if (currentPhaseIndex != other.currentPhaseIndex) {
        return currentPhaseIndex > other.currentPhaseIndex ? this : other;
    }
    return id > other.id ? this : other;
}
```

### `ClusterConfiguration` sub-config merge (existing, unchanged)

Each group's `ClusterConfiguration` merges via the existing `ClusterConfiguration.merge()`:
- If versions differ: higher version wins wholesale.
- If equal: members merged element-wise by `MemberState.version`; `ClusterChangePlan` by plan
  version; `RoutingState` by routing version; `incarnationNumber` by `Math.max()`.
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
message PartitionGroupClusterTopology {
  ClusterTopology clusterMembership = 1;
  map<string, ClusterTopology> partitionGroups = 2;
  PartitionGroupChangePlan pendingPlan = 3;
}

message PartitionGroupChangePlan {
  int64 id = 1;
  google.protobuf.Timestamp startedAt = 2;
  int32 currentPhaseIndex = 3;
  repeated PartitionGroupPhase phases = 4;
  CompletedChange lastChange = 5;
}

message PartitionGroupPhase {
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

`GossipState` changes to carry `PartitionGroupClusterTopology`:
```protobuf
message GossipState {
  PartitionGroupClusterTopology clusterTopology = 1;
}
```

**Migration** — on reading old-format bytes (bare `ClusterTopology`):
- `clusterMembership` = all members from original, `partitions` cleared
- `partitionGroups["default"]` = original config unchanged
- `pendingPlan` = absent

---

## 8. Scale-up example

Goal: add broker B5, remove B1; two partition groups (`default`, `groupA`).

Generated `PartitionGroupChangePlan`:
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
1. Coordinator activates Phase 0 → `clusterMembership.pendingChanges = [MemberJoinOperation(B5)]`.
2. B5 executes join → `clusterMembership.pendingChanges` clears.
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
partitionGroupConfigs["groupA"].pendingChanges = ClusterChangePlan([
    PartitionDisableExporterOp(B1, p=1), PartitionDisableExporterOp(B2, p=2)
])
// Request 2 accepted because groupB.hasPendingChanges() == false
partitionGroupConfigs["groupB"].pendingChanges = ClusterChangePlan([
    PartitionDisableExporterOp(B1, p=1), PartitionDisableExporterOp(B2, p=2)
])
// pendingPlan absent throughout
```

All four operations run concurrently via independent `PartitionManagerImpl` actors.

---

## 10. Implementation task breakdown

Issues 1–6 are implemented on branch `dd-poc-cluster-config-per-physical-tenants`.

### Issue 1 — Add proto messages for `PartitionGroupClusterTopology`

**Description**
Add seven new proto messages to `topology.proto` (see §7). `GossipState` is not changed yet.
Purely additive; no existing message is modified.

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

### Issue 2 — Introduce `PartitionGroupChangePlan` record with `merge()`

**Description**
Create `PartitionGroupChangePlan.java` in `dynamic-config/.../state/` as specified in §2.
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

### Issue 3 — Introduce `PartitionGroupClusterConfiguration` record with `merge()`

**Description**
Create `PartitionGroupClusterConfiguration.java` in `dynamic-config/.../state/` as specified in
§1. Implements `merge()` per §6. Includes `ofDefault(ClusterConfiguration)` migration factory.
No proto, no wiring; state-transition methods added in Issue 4.

**Implementation idea**
- Compact constructor: `partitionGroupConfigs = ImmutableMap.copyOf(partitionGroupConfigs)`.
- `merge()`: field-by-field delegating to `clusterMembership.merge()` and
  `ClusterConfiguration::merge` per group, union semantics for group keys.
- `ofDefault(config)`: copies all members with `MemberState.partitions` cleared into
  `clusterMembership`; original config goes into `partitionGroupConfigs["default"]`.
- Add `updatePartitionGroupConfig(groupId, updater)` and `updateClusterMembership(updater)`.

**Acceptance criteria**
- Unit tests: concurrent membership merge at same version; group-key union; overlapping group
  merge; plan merge delegation; `ofDefault()` correctness.
- No existing class is modified.

**Depends on** — Issue 2

---

### Issue 4 — Add `initPlan()`, `activateNextPhase()`, `completePlan()`

**Description**
Add the state-transition methods to `PartitionGroupClusterConfiguration` that the coordinator
uses to drive the phased plan:
- `initPlan(plan)` — sets `pendingPlan` at `currentPhaseIndex=0`; activates Phase 0 into sub-configs.
- `activateNextPhase()` — increments index; activates Phase N+1.
- `completePlan()` — clears `pendingPlan`.

Activation copies phase operations into sub-configs by calling
`ClusterConfiguration.startConfigurationChange()` on each targeted sub-config.

**Implementation idea**

```java
private PartitionGroupClusterConfiguration applyPhase(PartitionGroupChangePlan plan) {
    return switch (plan.currentPhase()) {
        case ClusterMembershipPhase p -> {
            final var updated = p.operations().isEmpty() ? clusterMembership
                : clusterMembership.startConfigurationChange(p.operations());
            yield new PartitionGroupClusterConfiguration(updated, partitionGroupConfigs, Optional.of(plan));
        }
        case PartitionGroupParallelPhase p -> {
            final var updatedGroups = new HashMap<>(partitionGroupConfigs);
            p.operationsPerGroup().forEach((groupId, ops) -> {
                if (!ops.isEmpty()) updatedGroups.compute(groupId, (id, cfg) -> {
                    if (cfg == null) throw new IllegalStateException("Unknown group: " + id);
                    return cfg.startConfigurationChange(ops);
                });
            });
            yield new PartitionGroupClusterConfiguration(clusterMembership, updatedGroups, Optional.of(plan));
        }
    };
}
```

**Acceptance criteria**
- Unit tests for each method; overrun guard for `activateNextPhase()` on last phase;
  `ClusterMembershipPhase` activates only `clusterMembership`; `PartitionGroupParallelPhase`
  activates only named groups.

**Depends on** — Issues 2, 3

---

### Issue 5 — Serialiser: `PartitionGroupClusterConfiguration` ↔ proto + migration

**Description**
Add encode/decode methods to `ProtoBufSerializer` for `PartitionGroupClusterConfiguration`.
Include first-boot migration: on decoding legacy `ClusterTopology` bytes, wrap via `ofDefault()`.

Migration detection: legacy bytes parsed as `PartitionGroupClusterTopology` produce
`clusterMembership.members` with non-empty `partitions` maps (signature of old format). New
format always has empty `partitions` in `clusterMembership`.

**Implementation idea**
Add to `ProtoBufSerializer`:
- `encodePartitionGroupClusterConfiguration(PartitionGroupClusterConfiguration)` → `byte[]`
- `decodePartitionGroupClusterConfiguration(byte[])` → `PartitionGroupClusterConfiguration`
  (with migration check)
- Internal `toProto` / `fromProto` helpers delegating to existing sub-config converters.

Do not modify any existing serialiser method.

**Acceptance criteria**
- Round-trip test with two groups.
- Migration test: encode old `ClusterConfiguration`, decode with new method; default group
  content preserved; `clusterMembership` has same members with empty partitions.
- Round-trip test for `PartitionGroupChangePlan` with both phase types.

**Depends on** — Issues 1, 3

---

### Issue 6 — Concurrent operation application in `ClusterConfigurationManagerImpl`

**Description**
Extend `ClusterConfigurationManagerImpl` to apply operations for multiple partition groups
concurrently. Add in-memory state for non-default groups (persistence comes in Issue 10).

Add fields:
```java
private final Map<String, ConfigurationChangeAppliers> partitionGroupAppliers = new HashMap<>();
private final Map<String, ClusterConfiguration> nonDefaultGroupConfigs = new HashMap<>();
private final Map<String, Boolean> onGoingGroupOperations = new HashMap<>();
private final Map<String, Boolean> shouldRetryGroup = new HashMap<>();
```

Add package-private methods:
- `registerPartitionGroupAppliers(String groupId, ConfigurationChangeAppliers)`
- `removePartitionGroupAppliers(String groupId)`
- `setPartitionGroupConfig(String groupId, ClusterConfiguration config)`
- `updatePartitionGroupConfig(String groupId, UnaryOperator<ClusterConfiguration> updater)`
- `getPartitionGroupConfig(String groupId)` (`@VisibleForTesting`)

Extend `applyConfigurationChangeOperation`: after the default group, iterate
`nonDefaultGroupConfigs.forEach(this::applyGroupConfigurationChangeOperation)`.

`applyGroupConfigurationChangeOperation` mirrors the default group's apply/init/retry logic
using per-group state maps. Default and non-default `ConfigurationChangeAppliers` must be
**different instances** (each closes over a different update callback).

**Acceptance criteria**
- Tests: op applied for non-default group; no op without appliers; two groups' `apply()` futures
  pending simultaneously (concurrent dispatch); isolation between groups; `updatePartitionGroupConfig` triggers dispatch.
- All existing manager tests pass.

**Depends on** — Issues 2, 3

---

### Issue 7 — Register per-group appliers from non-default `PartitionManagerImpl` instances

**Description**
Wire each non-default `PartitionManagerImpl` to call `registerPartitionGroupAppliers(groupId, appliers)`
at startup and `removePartitionGroupAppliers(groupId)` on shutdown.

The default group keeps using the existing `registerTopologyChangeAppliers(appliers)`. Only
non-default groups use the new API.

Non-default group appliers must use `updatePartitionGroupConfig(partitionGroup, updater)` as
their update callback instead of `updateClusterConfiguration(updater)`.

**Implementation idea**

In `PartitionManagerImpl.java`:
1. Locate where the default group builds and registers its `ConfigurationChangeAppliers`.
2. For non-default groups (`!partitionGroup.equals(DEFAULT_GROUP_NAME)`):
   - Build a separate `ConfigurationChangeAppliers` instance with `updatePartitionGroupConfig`
     as its update callback.
   - Call `registerPartitionGroupAppliers(partitionGroup, appliers)` instead of
     `registerTopologyChangeAppliers(appliers)`.
3. On shutdown: call `removePartitionGroupAppliers(partitionGroup)` for non-default groups.

**Acceptance criteria**
- Non-default `PartitionManagerImpl` registers appliers on startup and unregisters on shutdown.
- Simulated JOINING → ACTIVE transition in non-default group: `getPartitionGroupConfig(groupId)`
  reflects the state change.
- Default group behaviour and existing tests unchanged.

**Depends on** — Issue 6

---

### Issue 8 — Seed non-default group configs at broker startup

**Description**
At broker startup, seed an initial `ClusterConfiguration` for each configured non-default
physical tenant ID via `setPartitionGroupConfig(groupId, config)`. The seeded config must have
the same members and partition assignments as the default group. Also run `ExporterStateInitializer`
per non-default group.

**Implementation idea**

In `StaticInitializer` (or whichever `ClusterConfigurationInitializer` is wired at startup):
1. After constructing the default group's `ClusterConfiguration`, retrieve all configured
   non-default tenant IDs from `BrokerCfg`.
2. For each, seed:
   ```java
   ClusterConfiguration seed = ClusterConfiguration.init();
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
  non-null config with identical members and partition assignments to the default group.
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
    final ClusterConfiguration groupConfig = manager.getPartitionGroupConfig(groupId);
    if (groupConfig == null) throw new IllegalStateException("Unknown partition group: " + groupId);
    return deriveFrom(groupConfig);
}
```

Extract the `ClusterConfiguration → PartitionDistribution` derivation logic into a static
`deriveFrom(ClusterConfiguration)` helper shared by both paths.

Update all non-default `PartitionManagerImpl` callers to use `getPartitionDistribution(partitionGroup)`.

Verify no references remain: `grep -r "withGroupName" zeebe/`

**Acceptance criteria**
- `withGroupName()` and `clonePartitionMetadataWithGroup()` deleted; no references remain.
- `getPartitionDistribution("default")` equals `getPartitionDistribution()`.
- Non-default partitions start up correctly.
- Integration test verifies non-default partition startup with the new path.

**Depends on** — Issues 7, 8

---

### Issue 10 — Migrate manager state to `PartitionGroupClusterConfiguration`; extend persistence

**Description**
Migrate `ClusterConfigurationManagerImpl` and `PersistedClusterConfiguration` to store
`PartitionGroupClusterConfiguration` instead of bare `ClusterConfiguration`. Remove the
in-memory `nonDefaultGroupConfigs` map from Issue 6; all group state lives in the wrapper.

Existing public APIs (`getClusterConfiguration()`, `updateClusterConfiguration()`) continue to
work for the default group via compat accessors — do not break the public API.

For now, member ops (`MemberJoinOperation` etc.) still update the default group's config (not
`clusterMembership` directly). Populate `clusterMembership` at write time as a copy of the
default group's members with empty partitions.

Add `ActorFuture<PartitionGroupClusterConfiguration> getMultiTenantConfiguration()` for the
coordinator (used in Issues 12, 13).

**Implementation idea**

1. Update `PersistedClusterConfiguration` to use `encodePartitionGroupClusterConfiguration` /
   `decodePartitionGroupClusterConfiguration` (from Issue 5). First-boot migration is automatic.
2. In `ClusterConfigurationManagerImpl`:
   - Remove `nonDefaultGroupConfigs` field.
   - Store the wrapper internally; all group reads/writes go through `wrapper.partitionGroupConfigs()`.
   - `updatePartitionGroupConfig(groupId, updater)` → `wrapper.updatePartitionGroupConfig(groupId, updater)` → persist.
   - `getClusterConfiguration()` → `wrapper.partitionGroupConfigs().get("default")`.
   - `updateClusterConfiguration(updater)` → extract default group, apply updater, put back, persist.
3. At persist time, populate `clusterMembership` from default group's members with empty partitions.

**Acceptance criteria**
- Old-format persistence file migrates transparently on first boot.
- Non-default group configs survive broker restart.
- `nonDefaultGroupConfigs` field removed.
- All existing `ClusterConfigurationManagerImpl` and `PersistedClusterConfiguration` tests pass.
- `getMultiTenantConfiguration()` returns the full wrapper.

**Depends on** — Issues 5, 6, 7, 8

---

### Issue 11 — `ClusterConfigurationGossiper` carries `PartitionGroupClusterTopology`

**Description**
Migrate `ClusterConfigurationGossiper` to gossip `PartitionGroupClusterTopology`. After this
issue, non-default group state is visible cluster-wide.

Rolling-restart strategy: use **option (c) — hard upgrade gate**. Emit the new gossip format
only after a cluster-wide feature flag is set (once all brokers are upgraded). Until the gate is
set, gossip the old format; non-default group state remains local-only.

**Implementation idea**

Relevant files:
- `dynamic-config/.../gossip/ClusterConfigurationGossiper.java`
- `dynamic-config/.../gossip/ClusterConfigurationGossipState.java`

Steps:
1. Change gossiper internal state to hold `PartitionGroupClusterConfiguration`.
2. Gossip messages encode/decode `PartitionGroupClusterTopology` (using Issue 5 serialiser).
3. On receipt, decode (migration handled by serialiser) and merge using
   `PartitionGroupClusterConfiguration.merge()` (from Issue 3).
4. Gate new-format emission on the cluster-wide feature flag.
5. `ClusterConfigurationUpdateListener` callbacks: existing listeners receive `ClusterConfiguration`
   (extract default group). Add a new listener shape receiving `PartitionGroupClusterConfiguration`
   for the coordinator.

**Acceptance criteria**
- Two-broker test (both on new code): non-default group state propagates via gossip.
- Rolling-restart test: default group state converges between old and new brokers.
- All existing gossip integration tests pass.
- Rolling-restart strategy documented in PR.

**Depends on** — Issues 5, 10

---

### Issue 12 — Coordinator generates `PartitionGroupChangePlan` for default-group-only operations

**Description**
Migrate `ConfigurationChangeCoordinatorImpl` from generating flat `ClusterChangePlan` to phased
`PartitionGroupChangePlan`. **Scope: default group only.** Behaviour is identical to the current
flat plan; this validates the phase mechanism. Existing integration tests must pass unchanged.

**Implementation idea**

Phase-splitting rules (operation list produced by request transformers is unchanged):
- `MemberJoinOperation`, `MemberLeaveOperation`, `MemberRemoveOperation`,
  `PreScalingOperation`, `PostScalingOperation` → `ClusterMembershipPhase`
- All other operations → `PartitionGroupParallelPhase` with `{"default": [ops]}`
- Ordering: join-membership phases before partition phases; leave-membership phases after.

`isCoordinator()` reads from `wrapper.clusterMembership().members()`.

Start the plan via `manager.updateMultiConfig(c -> c.initPlan(plan))`. `initPlan` (Issue 4)
writes Phase 0 ops into sub-config `pendingChanges`. Brokers execute from sub-config as before.

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

Register a listener receiving `PartitionGroupClusterConfiguration` (from Issue 11). On each callback:

```java
void onClusterConfigurationUpdated(PartitionGroupClusterConfiguration config) {
    if (!isCoordinator(config)) return;
    if (config.pendingPlan().isEmpty()) return;

    final var plan = config.pendingPlan().get();
    final var currentPhase = plan.phases().get(plan.currentPhaseIndex());

    final boolean complete = switch (currentPhase) {
        case ClusterMembershipPhase ignored -> !config.clusterMembership().hasPendingChanges();
        case PartitionGroupParallelPhase p ->
            p.operationsPerGroup().keySet().stream()
                .allMatch(id -> !config.partitionGroupConfigs().get(id).hasPendingChanges());
    };
    if (!complete) return;

    if (plan.currentPhaseIndex() + 1 >= plan.phases().size()) {
        manager.updateMultiConfig(PartitionGroupClusterConfiguration::completePlan);
    } else {
        manager.updateMultiConfig(PartitionGroupClusterConfiguration::activateNextPhase);
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

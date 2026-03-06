```yaml
---
applyTo: "zeebe/dynamic-config/src/**"
---
```
# Dynamic Config Module — Cluster Topology Management

## Purpose

This module manages the dynamic cluster topology (configuration) of a Zeebe cluster at runtime. It tracks which brokers are members, which partitions each broker replicates, exporter states, routing state, and orchestrates topology changes (scaling, partition reassignment, exporter enable/disable) through a coordinator-based protocol with gossip-based dissemination.

## Architecture

The module has five layers, from outer to inner:

1. **API layer** (`api/`): Sealed `ClusterConfigurationManagementRequest` types and `*Transformer` classes that translate high-level requests (scale, add members, enable exporter) into ordered lists of `ClusterConfigurationChangeOperation`. `ClusterConfigurationManagementRequestsHandler` dispatches requests to the coordinator.
2. **Coordination layer** (`changes/ConfigurationChangeCoordinatorImpl`): Validates requests by simulating operations with noop executors, then atomically starts the change plan via `ClusterConfigurationManager.updateClusterConfiguration`. Only the broker with the lowest `MemberId` (typically `"0"`) acts as coordinator.
3. **Change execution layer** (`changes/`): `ConfigurationChangeAppliersImpl` maps each sealed `ClusterConfigurationChangeOperation` variant to a `ClusterOperationApplier` (or `MemberOperationApplier`). Each applier has two phases: `init()` (validate + mark transitional state) and `apply()` (execute actual change asynchronously). The `PartitionChangeExecutor`, `ClusterMembershipChangeExecutor`, `PartitionScalingChangeExecutor`, and `ClusterChangeExecutor` interfaces are callbacks to the broker's `PartitionManager`.
4. **State layer** (`state/`): Immutable records (`ClusterConfiguration`, `MemberState`, `PartitionState`, `ClusterChangePlan`, `RoutingState`, `DynamicPartitionConfig`, `ExporterState`) form the topology model. All mutations return new instances. `ClusterConfiguration.merge()` resolves concurrent gossip updates using version numbers.
5. **Gossip + persistence layer**: `ClusterConfigurationGossiper` disseminates topology via Atomix `ClusterCommunicationService` (sync + push gossip). `PersistedClusterConfiguration` persists topology to a checksummed file (`.topology.meta`) with CRC32C integrity checks.

### Data Flow

```
REST/gRPC API → ClusterConfigurationManagementRequestSender
  → (Atomix messaging) → ClusterConfigurationRequestServer
  → ClusterConfigurationManagementRequestsHandler
  → *Transformer.operations(currentConfig) → List<ClusterConfigurationChangeOperation>
  → ConfigurationChangeCoordinatorImpl.applyOperations()
    → simulate with Noop executors (validate)
    → ClusterConfigurationManager.updateClusterConfiguration() (adds ClusterChangePlan)
  → Gossip disseminates updated ClusterConfiguration
  → Each member's ClusterConfigurationManagerImpl.onGossipReceived()
    → merge → applyConfigurationChangeOperation() if pending op targets this member
    → ClusterOperationApplier.init() → ClusterOperationApplier.apply()
    → advanceConfigurationChange() → gossip updated state
```

## Key Abstractions

| Type | Role |
|------|------|
| `ClusterConfiguration` | Root immutable record: version, members map, pending/completed changes, routing state, cluster ID, incarnation number. Central to all operations. |
| `ClusterConfigurationChangeOperation` | Sealed interface with ~15 operation variants (member join/leave, partition join/leave/bootstrap, exporter enable/disable/delete, scaling, routing). |
| `ClusterChangePlan` | Tracks ordered pending + completed operations with version for merge resolution. |
| `ConfigurationChangeAppliers` | Functional interface mapping operations to `ClusterOperationApplier` instances. |
| `ClusterOperationApplier` | Two-phase applier: `init()` returns `Either<Exception, UnaryOperator<ClusterConfiguration>>`, `apply()` returns `ActorFuture<UnaryOperator<ClusterConfiguration>>`. |
| `MemberOperationApplier` | Specialization that scopes init/apply to `MemberState` transformers. |
| `ConfigurationChangeRequest` | Functional interface: `(ClusterConfiguration) → Either<Exception, List<Operation>>`. All `*Transformer` classes implement this. |
| `PersistedClusterConfiguration` | File-backed topology with versioned header + CRC32C checksum. |
| `ClusterConfigurationGossiper` | Push + pull gossip protocol using Atomix messaging topics. |

## Design Patterns

- **Immutable state with copy-on-write**: All `state/` records return new instances on mutation. `ClusterConfiguration.updateMember()` uses `UnaryOperator<MemberState>` for atomic member updates.
- **Sealed interfaces for exhaustive dispatch**: `ClusterConfigurationChangeOperation` is sealed → `ConfigurationChangeAppliersImpl.getApplier()` uses pattern matching `switch` to map every variant.
- **Two-phase operation appliers**: `init()` validates and marks transitional state (e.g., JOINING), `apply()` executes the actual operation asynchronously via `PartitionChangeExecutor`.
- **Chain-of-responsibility initialization**: `ClusterConfigurationInitializer` uses `orThen()`, `andThen()`, and `recover()` to chain: FileInitializer → SyncInitializer → StaticInitializer → ExporterStateInitializer → RoutingStateInitializer → ClusterIdInitializer.
- **Noop executors for validation**: `ConfigurationChangeCoordinatorImpl` simulates operations using `NoopPartitionChangeExecutor` etc. before applying real changes.
- **Coordinator pattern**: Lowest-numbered member ID acts as coordinator. Non-forced requests are rejected by non-coordinators.
- **Version-based merge**: `ClusterConfiguration.merge()`, `MemberState.merge()`, `ClusterChangePlan.merge()`, and `RoutingState.merge()` all resolve conflicts by selecting the higher version.

## Extension Points

### Adding a new topology change operation
1. Add a new record variant to `ClusterConfigurationChangeOperation` (sealed interface in `state/`).
2. Create a `*Applier` class in `changes/` implementing `ClusterOperationApplier` or `MemberOperationApplier`.
3. Add the new case to `ConfigurationChangeAppliersImpl.getApplier()` switch expression.
4. Create a `*Transformer` class in `api/` implementing `ConfigurationChangeRequest`.
5. Add a request record to `ClusterConfigurationManagementRequest` and handler method to `ClusterConfigurationManagementApi` / `ClusterConfigurationManagementRequestsHandler`.
6. Register the handler in `ClusterConfigurationRequestServer.start()` with a new topic in `ClusterConfigurationRequestTopics`.
7. Update `ProtoBufSerializer` and the `.proto` files in `src/main/resources/proto/` for serialization.

### Adding a new executor callback
Extend `PartitionChangeExecutor`, `ClusterChangeExecutor`, or `PartitionScalingChangeExecutor` with a new method. Add a corresponding `Noop*` implementation. The broker's `PartitionManager` provides the real implementation.

## Invariants

- Only the coordinator (lowest `MemberId`) can start configuration changes; non-forced requests from other members are rejected.
- Only one `ClusterChangePlan` can be active at a time. `startConfigurationChange()` throws if `hasPendingChanges()`.
- Only a member can modify its own `MemberState` — version-based merge guarantees consistency.
- Operations in a `ClusterChangePlan` execute strictly sequentially; the next starts only after the current completes.
- `MemberState` transitions are guarded: LEAVING → JOINING is illegal, LEFT → ACTIVE is illegal, etc.
- Appliers must be idempotent — they are retried after restarts with exponential backoff (10s–60s).
- `PersistedClusterConfiguration` writes use `DSYNC` for durability and CRC32C for integrity.

## Common Pitfalls

- Never mutate `ClusterConfiguration` or `MemberState` in place — all state records are immutable; always use the returned new instance.
- When adding an applier, always handle the idempotent restart case in `init()` (e.g., partition already in JOINING state).
- The `ProtoBufSerializer` is large (~900 lines); when adding new operations, update both encode and decode paths and the `.proto` schema.
- Transformer classes compose operations from other transformers (e.g., `ScaleRequestTransformer` chains `AddMembersTransformer` → `PartitionReassignRequestTransformer` → `RemoveMembersTransformer`). Ensure operation ordering is correct.
- All async work uses `ActorFuture` from Zeebe's actor model — do not use `CompletableFuture` or block threads.

## Testing

- Applier tests extend `AbstractApplierTest` which provides `runApplier()` to simulate init → apply → update.
- Transformer tests construct a `ClusterConfiguration`, call `operations()`, and assert the generated operation list.
- `ClusterConfigurationManagementIntegrationTest` tests the full request → coordinator → apply flow.
- Property-based tests use jqwik (`ProtoBufSerializerPropertyTest`, `PersistedClusterConfigurationRandomizedPropertyTest`).
- Custom AssertJ assertions: `ClusterConfigurationAssert`, `MemberStateAssert`, `PartitionStateAssert`, `RoutingStateAssert`.
- Run scoped: `./mvnw -pl zeebe/dynamic-config -am test -DskipITs -DskipChecks -Dtest=<TestClass> -T1C`

## Key Files

| File | Purpose |
|------|---------|
| `state/ClusterConfiguration.java` | Root topology record — merge, version, change plan lifecycle |
| `state/ClusterConfigurationChangeOperation.java` | Sealed interface defining all 15+ operation types |
| `changes/ConfigurationChangeAppliersImpl.java` | Maps every operation to its applier via pattern-match switch |
| `changes/ConfigurationChangeCoordinatorImpl.java` | Coordinator: validates, simulates, and starts changes |
| `ClusterConfigurationManagerImpl.java` | Core manager: init, gossip merge, sequential operation execution with retry |
| `ClusterConfigurationManagerService.java` | Service wiring: actors, gossiper, coordinator, request server |
| `ClusterConfigurationInitializer.java` | Chain-of-responsibility initializer (File → Sync → Static → modifiers) |
| `api/ClusterConfigurationManagementRequestsHandler.java` | Dispatches API requests to transformers + coordinator |
| `gossip/ClusterConfigurationGossiper.java` | Push + pull gossip via Atomix messaging |
| `PersistedClusterConfiguration.java` | File persistence with CRC32C checksums |
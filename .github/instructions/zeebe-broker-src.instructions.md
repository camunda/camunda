```yaml
---
applyTo: "zeebe/broker/src/**"
---
```
# Zeebe Broker Module

The broker module is the distributed runtime that manages Raft-replicated partitions, each running an event-sourced stream processor (the engine) and exporter pipeline. It orchestrates the full lifecycle of partitions—bootstrap, Raft role transitions (leader/follower/inactive), snapshotting, and shutdown—while exposing command, query, admin, backup, and snapshot transport APIs.

## Architecture

The broker has three distinct lifecycle layers, each with its own step-based startup/shutdown pattern:

1. **Broker startup** (`bootstrap/`): Global broker-level services started once via `BrokerStartupProcess`, which executes an ordered list of `StartupStep<BrokerStartupContext>` subclasses (e.g., `ClusterServicesStep`, `CommandApiServiceStep`, `PartitionManagerStep`). Each step extends `AbstractBrokerStartupStep` and implements `startupInternal`/`shutdownInternal`. Steps are wrapped with `BrokerStepMetricDecorator` for observability. See `BrokerStartupProcess.buildStartupSteps()` for the full ordered list.

2. **Partition startup** (`partitioning/startup/`): Per-partition one-time setup via `PartitionStartupStep` (extends `StartupStep<PartitionStartupContext>`). Handles Raft bootstrap/join, snapshot store creation, directory setup, and `ZeebePartition` actor construction. Steps defined in `partitioning/startup/steps/`.

3. **Partition transitions** (`system/partitions/impl/steps/`): Per-partition role transitions via `PartitionTransitionStep` interface. Called on every Raft role change (leader → follower, etc.). Each step implements `onNewRaftRole()` (immediate cancellation hook), `prepareTransition()` (teardown in reverse order), and `transitionTo()` (setup in forward order). Steps are assembled in `ZeebePartitionFactory.constructPartition()`.

### Key Components

- **`Broker`** (`Broker.java`): Entry point. Creates `BrokerStartupContextImpl`, submits `BrokerStartupActor` to the `ActorScheduler`, and delegates to `BrokerStartupProcess`.
- **`ZeebePartition`** (`system/partitions/ZeebePartition.java`): The central Actor per partition. Implements `RaftRoleChangeListener`, `HealthMonitorable`, `DiskSpaceUsageListener`, `SnapshotReplicationListener`. Coordinates transitions between leader/follower/inactive roles via `PartitionTransition`.
- **`PartitionTransitionImpl`** (`system/partitions/impl/PartitionTransitionImpl.java`): Manages transition sequencing—cancels in-flight transitions when a new role arrives, prepares previous steps in reverse, then executes forward.
- **`PartitionManagerImpl`** (`partitioning/PartitionManagerImpl.java`): Manages all partitions for a broker node. Implements `PartitionChangeExecutor` and `PartitionScalingChangeExecutor` for dynamic topology changes (join, leave, bootstrap, reconfigure, scale).
- **`ExporterDirector`** (`exporter/stream/ExporterDirector.java`): Actor that reads committed records from the log stream and distributes them to registered `Exporter` instances. Manages exporter positions, back-off retry, and pause/resume lifecycle.
- **`CommandApiServiceImpl`** (`transport/commandapi/CommandApiServiceImpl.java`): Registers command and query request handlers per partition on the server transport. Creates `LogStreamWriter` for leader partitions.
- **`SystemContext`** (`system/SystemContext.java`): Validates broker configuration, creates the `ActorScheduler`, and provides all external dependencies (Atomix cluster, backup stores, identity services).

## Data Flow

1. Gateway sends command → Atomix messaging → `CommandApiRequestHandler` on the partition leader
2. `CommandApiRequestHandler` writes to `LogStream` via `LogStreamWriter`
3. `StreamProcessor` (installed by `StreamProcessorTransitionStep`) reads COMMAND records from log → dispatches to `Engine` (from `zeebe/engine`)
4. Engine produces EVENT records → written atomically with RocksDB state
5. `ExporterDirector` (installed by `ExporterDirectorPartitionTransitionStep`) reads committed records → calls `Exporter.export()` on each registered exporter

## Extension Points

### Adding a new broker startup step
1. Create a class extending `AbstractBrokerStartupStep` in `bootstrap/`
2. Implement `startupInternal()` and `shutdownInternal()` using `BrokerStartupContext`
3. Add the step to the ordered list in `BrokerStartupProcess.buildStartupSteps()`
4. Steps execute in list order on startup, reverse order on shutdown

### Adding a new partition transition step
1. Implement `PartitionTransitionStep` in `system/partitions/impl/steps/`
2. Implement `onNewRaftRole()` (cancel/pause), `prepareTransition()` (teardown), `transitionTo()` (setup)
3. Register the step in `ZeebePartitionFactory.constructPartition()` in the correct order
4. Use `PartitionTransitionContext` to access/set partition components (ZeebeDb, LogStream, etc.)

### Adding a new transport API
1. Create request reader/writer/handler classes in `transport/<apiname>/`
2. Extend `AsyncApiRequestHandler` for actor-based request handling
3. Register the handler on the `ServerTransport` via a new startup or transition step

### Adding a new exporter
Exporters are loaded via `ExporterRepository` from `BrokerCfg.exporters`. Implement `io.camunda.zeebe.exporter.api.Exporter` and register via configuration. The broker validates exporters at startup by instantiating and calling `configure()`.

## Configuration

All broker configuration lives in `system/configuration/`. `BrokerCfg` is the root, aggregating:
- `ClusterCfg` (nodeId, partitionsCount, replicationFactor, clusterSize)
- `NetworkCfg` / `SocketBindingCfg` (command API, internal API addresses)
- `DataCfg` (data directory, log segment size, snapshot period)
- `ExporterCfg` / `ExportingCfg` (exporter class, args, distribution interval)
- `FlowControlCfg` / `LimitCfg` (backpressure algorithms: Vegas, AIMD, Gradient, fixed)
- `ExperimentalCfg` → `EngineCfg`, `RocksdbCfg`, `FeatureFlagsCfg`, `RaftCfg`
- `BackupCfg` (S3, GCS, Azure, filesystem backup store configs)

Each `*Cfg` class extends `ConfigurationEntry` and has an `init(BrokerCfg, brokerBase)` method.

## Logging

Use the centralized `Loggers` class constants, not `LoggerFactory` directly:
- `Loggers.SYSTEM_LOGGER` — broker lifecycle, partitions
- `Loggers.EXPORTER_LOGGER` — exporter pipeline
- `Loggers.TRANSPORT_LOGGER` — command/query/admin API
- `Loggers.CLUSTERING_LOGGER` — cluster topology
- `Loggers.getExporterLogger(exporterId)` — per-exporter logger

## Invariants

- All partition component access must go through the actor's concurrency control (`actor.run()`, `actor.call()`). Never access `ZeebePartition` state from outside its actor thread.
- Transition steps must handle cancellation: `onNewRaftRole()` is called immediately and may interrupt an in-flight transition. Always check for `Role.INACTIVE` to close resources.
- `prepareTransition()` closes resources from the previous role; `transitionTo()` opens them for the new role. Never skip cleanup in `prepareTransition()`.
- `ExporterDirector` position tracking must always advance monotonically. Never reset exporter positions except during snapshot recovery.
- `CommandApiServiceImpl` only registers handlers for leader partitions. Follower partitions do not accept commands.

## Common Pitfalls

- Forgetting to register/unregister a component with `ComponentHealthMonitor` during transition steps causes stale health reports.
- Not handling `RecoverablePartitionTransitionException` vs `UnrecoverableException` correctly in `onInstallFailure()` — recoverable errors step down from leader, unrecoverable errors mark the partition as dead.
- Modifying `BrokerStartupContext` without updating the `BrokerContextImpl` return values causes the `SpringBrokerBridge` to expose stale state.
- Adding transition steps in the wrong order in `ZeebePartitionFactory` — for example, `StreamProcessorTransitionStep` must come before `ExporterDirectorPartitionTransitionStep` since exporters depend on committed records from the stream processor.

## Testing

- Unit tests for transition steps use `TestPartitionTransitionContext` and verify step behavior per role.
- `PartitionTransitionTestArgumentProviders` provides parameterized role transition combinations.
- `RandomizedPartitionTransitionTest` uses property-based testing (jqwik) for transition correctness.
- `ExporterRule` provides a test harness for `ExporterDirector` with in-memory log streams.
- Integration tests use `EmbeddedBrokerRule` with `EmbeddedBrokerConfigurator` for full broker lifecycle.
- Run scoped: `./mvnw -pl zeebe/broker -am test -DskipITs -DskipChecks -Dtest=<TestClass> -T1C`

## Key Files

- `Broker.java` — broker entry point and `BrokerStartupActor`
- `bootstrap/BrokerStartupProcess.java` — ordered startup step execution
- `system/partitions/ZeebePartition.java` — per-partition actor with role transitions
- `system/partitions/impl/PartitionTransitionImpl.java` — transition sequencing and cancellation
- `partitioning/PartitionManagerImpl.java` — multi-partition lifecycle and dynamic topology
- `partitioning/startup/ZeebePartitionFactory.java` — partition construction with all transition steps
- `exporter/stream/ExporterDirector.java` — exporter pipeline actor
- `system/configuration/BrokerCfg.java` — root configuration class
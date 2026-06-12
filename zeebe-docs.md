# Zeebe — Comprehensive Developer Reference

> Version: 8.10.0-SNAPSHOT
> License: Camunda License 1.0
> Language: Java 21
> Build: Maven (multi-module)

---

## Table of Contents

1. [What Zeebe Is](#what-zeebe-is)
2. [Repository Structure — Module Map](#repository-structure--module-map)
3. [Core Architecture](#core-architecture)
   - [Event-Driven Record Processing](#event-driven-record-processing)
   - [Partitioning and Raft](#partitioning-and-raft)
   - [The Actor Scheduler](#the-actor-scheduler)
   - [State Management (RocksDB)](#state-management-rocksdb)
4. [The Engine Module — Deep Dive](#the-engine-module--deep-dive)
   - [RecordProcessor / Engine Entry Point](#recordprocessor--engine-entry-point)
   - [TypedRecordProcessor — How Processing Works](#typedrecordprocessor--how-processing-works)
   - [Writers — Output Side Effects](#writers--output-side-effects)
   - [EventApplier — State Mutation via Events](#eventapplier--state-mutation-via-events)
   - [BPMN Element Lifecycle](#bpmn-element-lifecycle)
   - [BpmnElementProcessor Interface](#bpmnelementprocessor-interface)
   - [BpmnBehaviors — Reusable Logic](#bpmnbehaviors--reusable-logic)
   - [ProcessingState — In-Memory / DB State](#processingstate--in-memory--db-state)
   - [EngineProcessors — Wiring It Together](#engineprocessors--wiring-it-together)
5. [The Broker Module](#the-broker-module)
   - [Startup Process](#startup-process)
   - [Partitions and ZeebePartition](#partitions-and-zeebepartition)
   - [Exporter Pipeline](#exporter-pipeline)
6. [Gateway Modules](#gateway-modules)
   - [REST Gateway (gateway-rest)](#rest-gateway-gateway-rest)
   - [gRPC Gateway (gateway-grpc)](#grpc-gateway-gateway-grpc)
7. [Protocol Layer](#protocol-layer)
   - [Records, ValueTypes, and Intents](#records-valuetypes-and-intents)
   - [SBE Protocol](#sbe-protocol)
   - [MsgPack Variables](#msgpack-variables)
8. [Cross-Cutting Patterns and Design Decisions](#cross-cutting-patterns-and-design-decisions)
   - [Command → Event Separation](#command--event-separation)
   - [Command Distribution (Cross-Partition)](#command-distribution-cross-partition)
   - [Versioned Event Appliers](#versioned-event-appliers)
   - [State Migrations](#state-migrations)
   - [Feature Flags](#feature-flags)
   - [Either Type for Error Handling](#either-type-for-error-handling)
   - [Banned Instance Pattern](#banned-instance-pattern)
   - [Authorization and Multi-Tenancy](#authorization-and-multi-tenancy)
9. [Testing Strategy](#testing-strategy)
   - [Unit Tests — Engine Layer](#unit-tests--engine-layer)
   - [EngineRule — The Core Test Harness](#enginerule--the-core-test-harness)
   - [RecordingExporter](#recordingexporter)
   - [Client Builders in Tests](#client-builders-in-tests)
   - [Integration Tests (QA)](#integration-tests-qa)
10. [Coding Guidelines and Style](#coding-guidelines-and-style)
    - [Formatting](#formatting)
    - [Checkstyle and SpotBugs](#checkstyle-and-spotbugs)
    - [Naming Conventions](#naming-conventions)
    - [Immutability and State Segregation](#immutability-and-state-segregation)
11. [How to Implement a New Feature — Step-by-Step Guide](#how-to-implement-a-new-feature--step-by-step-guide)

---

## What Zeebe Is

Zeebe is the process automation engine powering Camunda 8. It is a distributed, horizontally scalable, BPMN-based workflow engine built around an **event-sourced, append-only log architecture**. Key characteristics:

- **Event-sourced**: The log is the source of truth. All state is derived by replaying events.
- **Partitioned**: Work is split across partitions. Each partition has an independent append-only journal.
- **Raft-based replication**: Each partition replicates via Raft for fault tolerance.
- **Single-threaded per partition**: Within a partition, processing is strictly single-threaded using a custom actor scheduler. There is no shared mutable state between partitions.
- **Command-event protocol**: Clients and internal actors issue *commands*; the engine writes *events* to the log as the results.

---

## Repository Structure — Module Map

```
zeebe/
├── atomix/                  Raft consensus, transport, cluster membership
├── auth/                    Authentication/authorization shared code
├── backup/                  Backup abstraction and API
├── backup-stores/           Backup store implementations (S3, GCS, Azure, filesystem)
├── bpmn-model/              Java API for parsing/building BPMN XML models
├── broker/                  Broker server (orchestrates partitions, exporters, gateway)
├── broker-client/           Internal cluster client used by broker components
├── dmn/                     DMN evaluation engine
├── dynamic-config/          Dynamic broker configuration at runtime
├── dynamic-node-id-provider/Node ID management
├── engine/                  THE CORE: event stream processor and BPMN execution
├── exporter-api/            SPI for exporters (Exporter interface)
├── exporter-common/         Shared exporter utilities
├── exporter-filter/         Record filtering for exporters
├── exporter-test/           Test utilities for exporter implementors
├── exporters/
│   ├── camunda-exporter/    Primary Camunda Platform exporter
│   ├── elasticsearch-exporter/
│   ├── opensearch-exporter/
│   └── rdbms-exporter/
├── expression-language/     Expression evaluation (FEEL integration)
├── feel/                    FEEL language engine wrapper
├── gateway/                 Gateway orchestration and shared gateway logic
├── gateway-grpc/            gRPC server implementation
├── gateway-protocol/        gRPC .proto definitions
├── gateway-protocol-impl/   Generated gRPC stubs
├── gateway-rest/            REST API (Spring MVC controllers)
├── journal/                 Append-only log backed by filesystem
├── logstreams/              Log stream reading/writing
├── msgpack-core/            MessagePack encoding/decoding
├── msgpack-value/           MsgPack value types (DbLong, DbString, etc.)
├── protocol/                SBE schema definitions + generated Java types
├── protocol-asserts/        AssertJ extensions for protocol records
├── protocol-impl/           Protocol record implementations
├── protocol-jackson/        JSON (Jackson) mapping for protocol objects
├── protocol-test-util/      Test utilities for working with SBE records
├── qa/                      Integration/QA test suite
├── restore/                 Restoring state from backups
├── scheduler/               Custom actor scheduler (event loop per partition)
├── snapshot/                Snapshot abstraction (state checkpointing)
├── stream-platform/         Stream processing primitives (RecordProcessor, Writers, etc.)
├── test-util/               Shared test utilities (RecordingExporter, etc.)
├── transport/               Network transport abstraction
├── util/                    General utilities (Either, FeatureFlags, Loggers, etc.)
└── zb-db/                   RocksDB wrapper with typed column families
```

---

## Core Architecture

### Event-Driven Record Processing

The core loop in a single partition:

```
Client/Internal Command
    |
    v
[Log Stream / Journal] <-- append-only, Raft-replicated
    |
    v
[StreamProcessor]  <-- single-threaded, reads from log
    |
    v
[RecordProcessor.replay(event)]   -- during startup (replay mode)
[RecordProcessor.process(command)]-- during normal operation (processing mode)
    |
    +--> Writes follow-up events to log (via Writers.state())
    +--> Writes follow-up commands to log (via Writers.command())
    +--> Sends responses to clients (via Writers.response())
    +--> Schedules side effects (via Writers.sideEffect())
    |
    v
[EventApplier.applyState()]  -- called immediately when an event is written,
                                 mutates RocksDB state
```

The critical insight: **processors don't mutate state directly**. They write *events* via `Writers.state()`, which atomically applies the event to the state database AND appends it to the log. On recovery, only the events (not the commands) are replayed, and the state is reconstructed via `EventApplier`.

### Partitioning and Raft

- A Zeebe cluster has N partitions. Each partition has its own Raft group with a leader and followers.
- The broker leader of a partition processes all commands for that partition.
- Partitions are independent — they do not share state.
- Cross-partition operations (e.g., deploying a process definition to all partitions) use the **CommandDistribution** pattern (see below).
- Partition assignment uses consistent hashing by `processInstanceKey`.

### The Actor Scheduler

Zeebe uses a custom **actor model** (`zeebe/scheduler`) instead of Java threads directly. Key points:

- `Actor` is the base class for all concurrent components (ZeebePartition, StreamProcessor, exporters, etc.).
- Each actor runs in a single-threaded event loop. No actor shares mutable state with another.
- Actors communicate via `ActorFuture` (non-blocking async).
- Two thread groups: **CPU** (for processing) and **IO** (for disk/network).
- Actors have lifecycle hooks: `onActorStarting()`, `onActorStarted()`, `onActorClosing()`, `onActorClosed()`.
- The scheduler is based on work-stealing queues (`WorkStealingGroup`).

### State Management (RocksDB)

All persistent engine state lives in RocksDB, accessed via the `zb-db` module:

- `ZeebeDb` wraps RocksDB and provides typed `ColumnFamily<KeyType, ValueType>` instances.
- Column families are enumerated in `ZbColumnFamilies`.
- Keys implement `DbKey`, values implement `DbValue` — both use a direct-memory buffer format (similar to SBE) for zero-copy I/O.
- All writes happen within a **transaction** managed by the stream processor platform. On a processing error the transaction is rolled back.
- State access is split into two interfaces per sub-domain:
  - `immutable.XxxState` — read-only access (used in processors that shouldn't mutate)
  - `mutable.MutableXxxState` — write access (used in event appliers)

---

## The Engine Module — Deep Dive

The `engine` module is the most important in the codebase. It contains all BPMN execution logic, state machines, and event appliers.

### RecordProcessor / Engine Entry Point

`Engine` implements `RecordProcessor` (from `stream-platform`). It is the top-level processor registered with the stream processor:

```java
public class Engine implements RecordProcessor {
    void init(RecordProcessorContext ctx)    // wires up all processors and event appliers
    boolean accepts(ValueType valueType)     // only handles JOB..SCALE range
    void replay(TypedRecord event)           // delegates to EventApplier
    ProcessingResult process(...)            // dispatches to typed processors via RecordProcessorMap
    ProcessingResult onProcessingError(...)  // error handling, banning instances
}
```

Key wiring in `init()`:
1. Creates `EventAppliers` and registers all `TypedEventApplier` implementations.
2. Creates `Writers` (wraps `ProcessingResultBuilder`).
3. Calls `TypedRecordProcessorFactory.createProcessors()` → `EngineProcessors.createEngineProcessors()` which registers all `TypedRecordProcessor` instances by `(RecordType, ValueType, Intent)` triple.

### TypedRecordProcessor — How Processing Works

Every command handler implements `TypedRecordProcessor<T extends UnifiedRecordValue>`:

```java
public interface TypedRecordProcessor<T extends UnifiedRecordValue> {
    default void processRecord(TypedRecord<T> record) { ... }
    default void processRecord(TypedRecord<T> record, ProcessingSession session) { ... }
    default ProcessingError tryHandleError(TypedRecord<T> command, Throwable error) { return UNEXPECTED_ERROR; }
    default boolean shouldProcessResultsInSeparateBatches() { return false; }
}
```

- `processRecord` is the main handler. It uses `Writers` to produce output.
- `tryHandleError` allows a processor to signal `EXPECTED_ERROR` for anticipated failures (e.g., business rule violations), preventing instance banning.
- `shouldProcessResultsInSeparateBatches()` returning `true` isolates follow-up commands into their own batches for better error isolation.

Processors are registered in `RecordProcessorMap` keyed by `(RecordType, ValueType, intentValue)`.

### Writers — Output Side Effects

`Writers` is the central output mechanism:

```java
public final class Writers {
    TypedCommandWriter command()    // write follow-up commands to the log
    TypedRejectionWriter rejection() // write command rejection (with reason)
    StateWriter state()              // write event AND apply state change to DB
    TypedResponseWriter response()   // send response to client
    SideEffectWriter sideEffect()    // schedule a post-commit side effect
}
```

`StateWriter.appendFollowUpEvent(key, intent, value)` is the critical call — it both:
1. Queues the event for writing to the log.
2. **Immediately** calls the corresponding `TypedEventApplier` to update in-memory/RocksDB state within the current transaction.

This means the processor can rely on the state being updated immediately after writing an event, allowing chaining of multiple state mutations in a single processing step.

### EventApplier — State Mutation via Events

`EventApplier` (implemented by `EventAppliers`) is the central registry of all state mutations:

```java
public interface EventApplier {
    int getLatestVersion(Intent intent);
    void applyState(long key, Intent intent, RecordValue value, int recordVersion);
}
```

`EventAppliers` maps `Intent → version → TypedEventApplier`. Each `TypedEventApplier` implements:

```java
public interface TypedEventApplier<I extends Intent, V extends RecordValue> {
    void applyState(long key, V value);
}
```

There are ~213 applier classes in `engine/state/appliers/`, named `<Entity><State>Applier.java` (e.g., `ProcessInstanceElementActivatingApplier`, `JobCreatedApplier`).

**Registration pattern in `EventAppliers.registerEventAppliers(state)`:**
```java
// Example pattern:
register(ProcessInstanceIntent.ELEMENT_ACTIVATING, 1, new ProcessInstanceElementActivatingApplier(state));
register(UserTaskIntent.CREATING, 2, new UserTaskCreatingV2Applier(state));  // versioned!
```

### BPMN Element Lifecycle

Every BPMN element goes through a strict lifecycle defined in `ProcessInstanceLifecycle`:

```
ELEMENT_ACTIVATING --> ELEMENT_ACTIVATED --> ELEMENT_COMPLETING --> ELEMENT_COMPLETED --> SEQUENCE_FLOW_TAKEN --> ELEMENT_ACTIVATING (next)
       |                     |                     |
       v                     v                     v
ELEMENT_TERMINATING --> ELEMENT_TERMINATED
```

State transition rules are enforced by `ProcessInstanceStateTransitionGuard`. Any attempt to transition to an invalid state is rejected.

### BpmnElementProcessor Interface

Each BPMN element type has a corresponding processor implementing `BpmnElementProcessor<T>`:

```java
public interface BpmnElementProcessor<T extends ExecutableFlowElement> {
    Class<T> getType();

    Either<Failure, ?> onActivate(T element, BpmnElementContext context);    // element entering
    Either<Failure, ?> finalizeActivation(T element, BpmnElementContext context); // after start EL
    Either<Failure, ?> onComplete(T element, BpmnElementContext context);    // element leaving
    Either<Failure, ?> finalizeCompletion(T element, BpmnElementContext context); // after end EL
    TransitionOutcome onTerminate(T element, BpmnElementContext context);    // cancellation
    void finalizeTermination(T element, BpmnElementContext context);
}
```

Processors are registered in `BpmnElementProcessors` and dispatched by `BpmnStreamProcessor`.

Container-type elements (Process, SubProcess, MultiInstance, CallActivity, AdHocSubProcess) additionally implement `BpmnElementContainerProcessor`.

**Current element processors** (in `processing/bpmn/`):
- `container/`: ProcessProcessor, SubProcessProcessor, MultiInstanceBodyProcessor, CallActivityProcessor, AdHocSubProcessProcessor, EventSubProcessProcessor
- `event/`: StartEventProcessor, EndEventProcessor, IntermediateCatchEventProcessor, etc.
- `gateway/`: ExclusiveGatewayProcessor, ParallelGatewayProcessor, InclusiveGatewayProcessor, etc.
- `task/`: ServiceTaskProcessor, UserTaskProcessor, ReceiveTaskProcessor, etc.

### BpmnBehaviors — Reusable Logic

`BpmnBehaviors` is an interface aggregating all shared behavioral logic that element processors use:

```java
public interface BpmnBehaviors {
    ExpressionProcessor expressionProcessor();
    BpmnVariableMappingBehavior variableMappingBehavior();
    BpmnEventPublicationBehavior eventPublicationBehavior();
    BpmnEventSubscriptionBehavior eventSubscriptionBehavior();
    BpmnIncidentBehavior incidentBehavior();
    BpmnStateBehavior stateBehavior();
    BpmnStateTransitionBehavior stateTransitionBehavior(); // drives lifecycle transitions
    BpmnJobBehavior jobBehavior();
    BpmnUserTaskBehavior userTaskBehavior();
    BpmnDecisionBehavior bpmnDecisionBehavior();
    BpmnCompensationSubscriptionBehaviour compensationSubscriptionBehaviour();
    VariableBehavior variableBehavior();
    ElementActivationBehavior elementActivationBehavior();
    CatchEventBehavior catchEventBehavior();
    EventTriggerBehavior eventTriggerBehavior();
    // ... more
}
```

`BpmnBehaviorsImpl` provides the concrete implementations. This is passed via constructor injection into element processors — **not** accessed via a service locator.

**Key behaviors:**
- `BpmnStateTransitionBehavior`: drives transitions (activating → activated → completing → completed), handles the `transitionTo*` methods, triggers follow-up events.
- `BpmnJobBehavior`: creates and activates jobs for service tasks.
- `BpmnUserTaskBehavior`: creates user tasks and manages lifecycle.
- `BpmnIncidentBehavior`: raises/resolves incidents when processing fails.
- `BpmnVariableMappingBehavior`: applies input/output variable mappings via FEEL.
- `BpmnEventSubscriptionBehavior`: manages message/signal/timer subscriptions for intermediate events.

### ProcessingState — In-Memory / DB State

`ProcessingState` is the read interface to all engine state. It aggregates ~30 sub-states:

```java
public interface ProcessingState {
    ProcessState getProcessState();         // deployed process definitions
    JobState getJobState();                 // active jobs
    MessageState getMessageState();         // published messages
    ElementInstanceState getElementInstanceState(); // active BPMN element instances
    VariableState getVariableState();       // variables per scope
    IncidentState getIncidentState();       // active incidents
    TimerInstanceState getTimerState();     // scheduled timers
    DecisionState getDecisionState();       // deployed DMN decisions
    UserTaskState getUserTaskState();       // user task records
    AuthorizationState getAuthorizationState();
    RoutingState getRoutingState();         // partition routing info
    BatchOperationState getBatchOperationState();
    // ... ~20 more
}
```

`MutableProcessingState` extends `ProcessingState` with mutable versions of all sub-states. Event appliers receive `MutableProcessingState`; processors receive the read-only `ProcessingState`.

### EngineProcessors — Wiring It Together

`EngineProcessors.createEngineProcessors()` is the factory method that wires everything:

1. Creates shared behaviors (`BpmnBehaviorsImpl`, `AuthorizationCheckBehavior`, `DecisionBehavior`, etc.)
2. Calls per-domain setup methods:
   - `addDeploymentRelatedProcessorAndServices()`
   - `addMessageProcessors()`
   - `addProcessProcessors()` → creates `BpmnStreamProcessor`
   - `addDecisionProcessors()`
   - `JobEventProcessors.addJobProcessors()`
   - `addUserTaskProcessors()`
   - `addIncidentProcessors()`
   - Many more for signals, timers, scaling, batch operations, etc.
3. Returns `TypedRecordProcessors` containing the completed `RecordProcessorMap`

---

## The Broker Module

### Startup Process

The broker uses a **staged startup process** (`BrokerStartupProcess`) with ordered `StartupStep` implementations. Each step has a `startup()` and `shutdown()` method. Steps execute sequentially in order, and shutdown reverses the order:

```
1. ClusterServicesStep           — Raft, cluster membership
2. ClusterConfigurationManagerStep
3. DiskSpaceUsageMonitorStep
4. MonitoringServerStep          — Prometheus/metrics endpoint
5. ApiMessagingServiceStep
6. RequestIdGeneratorStep
7. GatewayBrokerTransportStep
8. CommandApiServiceStep         — handles incoming commands from gateway
9. EmbeddedGatewayServiceStep    — optional, if gateway.enable=true
10. JobStreamServiceStep
11. SnapshotApiServiceStep
12. PartitionManagerStep         — starts all partitions
13. BrokerAdminServiceStep
14. CheckpointSchedulerServiceStep
```

### Partitions and ZeebePartition

Each `Partition` is started via another `StartupProcess`:
```
1. MetricsStep
2. PartitionDirectoryStep
3. SnapshotStoreStep
4. RaftBootstrapStep             — joins/creates Raft group
5. ZeebePartitionStep            — starts ZeebePartition actor
6. PartitionRegistrationStep
```

`ZeebePartition` is the central Actor for a partition. It responds to Raft role changes (leader/follower/candidate) and orchestrates:
- Stream processor lifecycle (only runs on leader)
- Snapshot taking
- Exporter lifecycle
- Health reporting

`PartitionTransition` defines how the partition transitions between roles, with `PartitionTransitionStep` implementations for each component.

### Exporter Pipeline

Exporters are configured in `BrokerCfg` and loaded via `ExporterRepository`. The exporter SPI is:

```java
public interface Exporter {
    void configure(Context context);  // called at startup for validation
    void open(Controller controller);  // setup
    void close();                      // teardown
    void export(Record<?> record);     // called for each committed record
    void purge();                      // optional: delete all exported data
}
```

The `Controller` allows calling `updateLastExportedRecordPosition(position)` to acknowledge export. The log is only compacted up to the minimum acknowledged position across all exporters.

Exporters run per-partition. The `ExporterFilter` module allows configuring which record types each exporter receives.

---

## Gateway Modules

### REST Gateway (gateway-rest)

The REST gateway is a Spring Boot application using Spring MVC. Key patterns:

**Controllers** live in `gateway-rest/src/main/java/.../controller/`. Each controller:
- Is annotated with `@CamundaRestController` (which extends `@RestController`)
- Is annotated with `@RequestMapping("/v2/<resource>")`
- Uses `@CamundaGetMapping` / `@CamundaPostMapping` custom annotations
- Returns `CompletableFuture<ResponseEntity<Object>>` for async handling
- Delegates to a `*Services` class (e.g., `ProcessInstanceServices`)

**Pattern for command endpoints:**
```java
@CamundaPostMapping
public CompletableFuture<ResponseEntity<Object>> createProcessInstance(
    @RequestBody ProcessInstanceCreationInstruction request) {
  return RequestMapper.toCreateProcessInstance(request, multiTenancyCfg.isChecksEnabled())
      .fold(
          RestErrorMapper::mapProblemToCompletedResponse,  // Left: validation error
          this::createProcessInstance);                     // Right: execute
}
```

**Pattern for search/query endpoints:** annotated with `@RequiresSecondaryStorage`, delegate to search services backed by Elasticsearch/OpenSearch.

Error handling is centralized in `GlobalControllerExceptionHandler`.

### gRPC Gateway (gateway-grpc)

Implements the Zeebe gRPC protocol (`.proto` definitions in `gateway-protocol`). The gRPC gateway translates gRPC requests into broker commands via `BrokerClient` / `InterPartitionCommandSender`.

---

## Protocol Layer

### Records, ValueTypes, and Intents

Every message on the log is a **Record**:

```java
public interface Record<T extends RecordValue> {
    long getKey();
    int getPartitionId();
    RecordType getRecordType();   // COMMAND, EVENT, REJECTION
    ValueType getValueType();     // JOB, PROCESS_INSTANCE, USER_TASK, ...
    Intent getIntent();           // e.g. ProcessInstanceIntent.ELEMENT_ACTIVATING
    T getValue();
    long getPosition();           // position in the log
    long getTimestamp();
    int getRecordVersion();       // for versioned applier lookup
}
```

`ValueType` is an enum covering all entity types (JOB, DEPLOYMENT, PROCESS_INSTANCE, USER_TASK, MESSAGE, TIMER, INCIDENT, VARIABLE, DECISION, ...).

Each `ValueType` has a corresponding `Intent` enum (e.g., `JobIntent` with CREATE, CREATED, COMPLETE, COMPLETED, FAIL, FAILED, ...).

`Intent` values are categorized:
- **Commands** (e.g., `CREATE`, `COMPLETE`, `CANCEL`): issued by clients or internally
- **Events** (e.g., `CREATED`, `COMPLETED`, `CANCELED`): produced by the engine after processing a command
- **Rejections**: when a command cannot be processed

### SBE Protocol

The wire format between gateway and broker uses **Simple Binary Encoding (SBE)** defined in `gateway-protocol`. SBE gives fixed-size, zero-allocation message parsing.

Internal log records use a combination of SBE for metadata and **MsgPack** for variable-length data (variables, headers, etc.).

### MsgPack Variables

Process instance variables are stored and transmitted as MsgPack-encoded byte arrays:
- `msgpack-core`: low-level encoding/decoding
- `msgpack-value`: `DbString`, `DbLong`, `DbDirectBuffer`, etc. — typed wrappers for RocksDB storage

---

## Cross-Cutting Patterns and Design Decisions

### Command → Event Separation

**Never mutate state in response to a command directly.** The pattern is always:
1. Validate command (processor reads immutable state)
2. Write event via `Writers.state().appendFollowUpEvent(key, intent, value)`
3. The `TypedEventApplier` for that event mutates state

This ensures that on recovery, replaying events from the log produces identical state.

**Corollary**: Every new feature that changes state needs both a processor (handles the command) AND an event applier (mutates state in response to the event).

### Command Distribution (Cross-Partition)

When a command needs to be executed on multiple partitions (e.g., deploying a process to all partitions), Zeebe uses `CommandDistributionBehavior`:

1. Partition 1 (coordinator) writes `COMMAND_DISTRIBUTION.STARTED` event
2. Coordinator sends commands to all other partitions via `InterPartitionCommandSender`
3. Each partition processes the command and sends back an acknowledgment
4. When all partitions acknowledge, coordinator writes `COMMAND_DISTRIBUTION.FINISHED`
5. There is a `CommandRedistributionScheduler` for retry on failure

This pattern is used for: deployment, role creation, authorization, tenant management, scaling, etc.

### Versioned Event Appliers

When an event's data format changes, a **new versioned applier** is created rather than modifying the existing one:

- `UserTaskCreatedApplier` → `UserTaskCreatedV2Applier` → `UserTaskCreatedV3Applier`
- The `recordVersion` field in each record selects the correct applier
- Old appliers are kept to support replay of historical events during rolling upgrades
- `EventAppliers.getLatestVersion(intent)` returns the version a processor should write

This is critical for **zero-downtime rolling updates** — a new broker can replay old events with old appliers, and new events use new appliers.

### State Migrations

When the RocksDB schema changes between versions, a `MigrationTask` is added to `DbMigratorImpl.MIGRATION_TASKS`:

```java
public static final List<MigrationTask> MIGRATION_TASKS = List.of(
    new DecisionMigration(),                        // 8.2
    new MultiTenancyJobStateMigration(),            // 8.3
    new ColumnFamilyPrefixCorrectionMigration(),    // 8.5
    new OrderedCommandDistributionMigration(),       // 8.6
    // ...
);
```

Migrations run on broker startup before processing begins. They are organized by target version in sub-packages (`to_8_2/`, `to_8_3/`, etc.).

### Feature Flags

`FeatureFlags` is a plain Java class (not Spring beans) with boolean flags for experimental features:

```java
public final class FeatureFlags {
    private boolean yieldingDueDateChecker;
    private boolean enableActorMetrics;
    private boolean enableMessageTTLCheckerAsync;
    // ...

    public static FeatureFlags createDefault() { ... }
    public static FeatureFlags createDefaultForTests() { ... }  // may differ from production defaults
}
```

**To add a feature flag:**
1. Add a `private static final boolean` constant and a field to `FeatureFlags`
2. Add to `FeatureFlagsCfg` (Spring config bean)
3. Document in `broker.yaml.template`
4. Write tests in `FeaturesFlagCfgTest`

New flags default to `false` (opt-in). When stable, flip the default to `true`.

### Either Type for Error Handling

`io.camunda.zeebe.util.Either<L, R>` is used extensively in processor methods that can fail:

```java
Either<Failure, ?> onActivate(T element, BpmnElementContext context) {
    return expressionProcessor.evaluateExpression(expr)
        .flatMap(result -> doSomethingWith(result))
        .ifLeft(failure -> incidentBehavior.createIncident(failure, context));
}
```

- `Either.right(value)` = success
- `Either.left(failure)` = business error (creates an incident)
- Supports `flatMap`, `map`, `ifRightOrLeft`, `fold`
- `BpmnElementProcessor.SUCCESS = Either.right(null)` is the common no-op success return

`Failure` carries an `ErrorType` and message, used to create engine incidents.

### Banned Instance Pattern

When an unexpected error occurs during processing of a process-instance-related command, the process instance is **banned**:
- A `ErrorIntent.CREATED` event is written, which triggers `DbBannedInstanceState`
- Subsequent commands targeting the same process instance are rejected (except CANCEL and TERMINATE)
- This prevents infinite error loops

### Authorization and Multi-Tenancy

`AuthorizationCheckBehavior` is injected into processors that require authorization. It validates the authenticated user's permissions against the resource being accessed.

Multi-tenancy: records carry a `tenantId`. The `@RequiresSecondaryStorage` annotation on REST endpoints indicates that the endpoint reads from Elasticsearch/OpenSearch rather than the primary log.

---

## Testing Strategy

### Unit Tests — Engine Layer

The engine has ~638 test files. Tests are **not unit tests in the traditional sense** — they test the full engine processing loop end-to-end within a single partition, using an in-memory log.

Test file naming: `<Feature>Test.java` (e.g., `ServiceTaskTest`, `MultiInstanceTest`, `ProcessInstanceModificationTest`).

### EngineRule — The Core Test Harness

`EngineRule` (JUnit 4 `@ClassRule`) sets up an in-process engine with:
- A real `StreamProcessor` backed by `ListLogStorage` (in-memory)
- A real `RocksDB` database
- A wired `RecordingExporter` that captures all written records
- Fluent client builders for issuing commands

```java
@ClassRule
public static final EngineRule ENGINE = EngineRule.singlePartition();

@Rule
public final RecordingExporterTestWatcher testWatcher = new RecordingExporterTestWatcher();
```

For multi-partition tests: `EngineRule.multiplePartitions(3)`.

### RecordingExporter

`RecordingExporter` is a thread-safe in-memory exporter that collects all records written during a test. It provides a fluent API for asserting on records:

```java
// Wait for and assert on records
RecordingExporter.processInstanceRecords()
    .withIntent(ProcessInstanceIntent.ELEMENT_ACTIVATED)
    .withElementId("serviceTask")
    .getFirst();

RecordingExporter.jobRecords(JobIntent.CREATED)
    .withType("myJobType")
    .getFirst();
```

It uses a position-based blocking mechanism: `withWaitUntilMaxPositionExported()` to avoid flakiness. Wrapped by `RecordingExporterTestWatcher` which resets it between tests.

### Client Builders in Tests

`EngineRule` exposes fluent builders for all commands:

```java
ENGINE.deployment()
    .withXmlResource(process)
    .deploy();

ENGINE.processInstance()
    .ofBpmnProcessId("myProcess")
    .withVariable("key", "value")
    .create();

ENGINE.job()
    .ofInstance(processInstanceKey)
    .withType("myJobType")
    .complete();
```

Client classes are in `engine/src/test/java/.../util/client/`.

### Integration Tests (QA)

`zeebe/qa/` contains integration tests that run against real broker instances (usually via `@ZeebeIntegration` or embedded cluster utilities):

- `integration-tests/`: Engine behavior tests against a real cluster (use `TestApplication` or `ZeebeTestEngine`)
- `update-tests/`: Rolling update compatibility tests
- Test naming: `*Test.java` for QA tests (not `*IT.java`)

Broker-level IT tests use the `*IT.java` suffix and often require Docker (Testcontainers) for external dependencies (Elasticsearch, S3, etc.).

---

## Coding Guidelines and Style

### Formatting

- **Java formatter**: Google Java Format (GOOGLE style), version 1.34.1
- Applied via Spotless Maven plugin: `mvn spotless:apply`
- Checked in CI via: `mvn spotless:check`
- All code must be formatted before committing
- Build with `-DspotChecks.skip=true` (or `-Dquickly=true`) to skip during local development

### Checkstyle and SpotBugs

- Checkstyle enforces import ordering, Javadoc presence, and other style rules
- SpotBugs performs static analysis (include/exclude lists in `spotbugs/`)
- Both run as part of the Maven build
- Skip with `-DskipChecks=true` or `-Dquickly=true`

### Naming Conventions

| Pattern | Example |
|---------|---------|
| Record processor | `JobCreateProcessor`, `DeploymentCreateProcessor` |
| Event applier | `JobCreatedApplier`, `UserTaskCreatedV2Applier` |
| State interface (mutable) | `MutableJobState`, `MutableElementInstanceState` |
| State interface (immutable) | `JobState`, `ElementInstanceState` |
| Behavior class | `BpmnJobBehavior`, `BpmnStateTransitionBehavior` |
| DB column family | `ZbColumnFamilies.JOBS`, `ZbColumnFamilies.ELEMENT_INSTANCE_KEY` |
| Test class | `JobCreationTest`, `ServiceTaskTest` |
| IT class | `ElasticsearchExporterIT`, `BackupIT` |

### Immutability and State Segregation

- **Read-only state** is accessed through the immutable interfaces in `state/immutable/`
- **Mutable state** is only accessed in `TypedEventApplier` implementations through `MutableProcessingState`
- Processors receive only `ProcessingState` (immutable), ensuring they cannot accidentally mutate state outside of event appliers
- `final` is used extensively on fields and local variables
- Prefer `final` parameters (all parameters are `final` in most code)

---

## How to Implement a New Feature — Step-by-Step Guide

This guide covers the typical path for adding a new engine feature (e.g., a new BPMN element behavior or a new command type).

### Step 1: Define the Protocol

If the feature needs new record types:

1. **Add a new `Intent` enum** in `protocol/src/main/java/.../intent/`:
   ```java
   public enum MyNewIntent implements Intent {
     CREATE(0), CREATED(1), ...;
     // see existing intents for boilerplate
   }
   ```
2. **Add a `RecordValue` interface** in `protocol/src/main/java/.../record/value/` if new fields are needed.
3. **Add a `ValueType` entry** in `ValueType` enum and register in `ValueTypeMapping`.
4. **Add protocol-impl record** in `protocol-impl/` implementing the interface.

### Step 2: Add Event Appliers

For every event intent your feature produces, add a `TypedEventApplier`:

```java
// engine/state/appliers/MyEntityCreatedApplier.java
public class MyEntityCreatedApplier implements TypedEventApplier<MyNewIntent, MyNewRecord> {
    private final MutableMyEntityState myEntityState;

    public MyEntityCreatedApplier(final MutableProcessingState state) {
        this.myEntityState = state.getMyEntityState();
    }

    @Override
    public void applyState(final long key, final MyNewRecord value) {
        myEntityState.put(key, value);
    }
}
```

Register it in `EventAppliers.registerEventAppliers()`:
```java
register(MyNewIntent.CREATED, 1, new MyEntityCreatedApplier(state));
```

If you change an event's schema later, create `MyEntityCreatedV2Applier` and register both.

### Step 3: Add State Interfaces

If the feature needs persistent state:

1. Add `immutable/MyEntityState.java` — read-only methods
2. Add `mutable/MutableMyEntityState.java extends MyEntityState` — write methods
3. Implement in `engine/state/instance/DbMyEntityState.java` using `ColumnFamily`
4. Add `ZbColumnFamilies.MY_ENTITY_...` entries
5. Wire into `MutableProcessingState` interface and `ProcessingDbState` implementation

### Step 4: Add the TypedRecordProcessor

```java
public class MyEntityCreateProcessor implements TypedRecordProcessor<MyNewRecord> {
    private final Writers writers;
    private final ProcessingState processingState;
    private final AuthorizationCheckBehavior authCheckBehavior;

    @Override
    public void processRecord(final TypedRecord<MyNewRecord> record) {
        final var value = record.getValue();

        // 1. Validate
        final var validationResult = validate(value);
        if (validationResult.isLeft()) {
            writers.rejection().appendRejection(record, RejectionType.INVALID_ARGUMENT,
                validationResult.getLeft().getMessage());
            writers.response().writeRejectionOnCommand(...);
            return;
        }

        // 2. Write event (this also applies state via EventApplier)
        final long key = processingState.getKeyGenerator().nextKey();
        writers.state().appendFollowUpEvent(key, MyNewIntent.CREATED, value);

        // 3. Send response
        writers.response().writeEventOnCommand(key, MyNewIntent.CREATED, value, record);
    }
}
```

### Step 5: Register the Processor

In `EngineProcessors.createEngineProcessors()` (or a dedicated `MyEntityProcessors.addProcessors()` method):

```java
typedRecordProcessors.onCommand(
    ValueType.MY_NEW_TYPE,
    MyNewIntent.CREATE,
    new MyEntityCreateProcessor(processingState, writers, authCheckBehavior));
```

### Step 6: Write Tests

Create `engine/src/test/java/.../processing/myentity/MyEntityCreateTest.java`:

```java
public class MyEntityCreateTest {
    @ClassRule
    public static final EngineRule ENGINE = EngineRule.singlePartition();

    @Rule
    public final RecordingExporterTestWatcher testWatcher = new RecordingExporterTestWatcher();

    @Test
    public void shouldCreateMyEntity() {
        // given
        // ... setup

        // when
        final var result = ENGINE.myEntityClient().create(...).send();

        // then
        final var record = RecordingExporter.myEntityRecords(MyNewIntent.CREATED)
            .getFirst();
        assertThat(record.getValue().getSomeField()).isEqualTo("expected");
    }

    @Test
    public void shouldRejectWhenInvalidInput() {
        // test rejection path
    }
}
```

**Test naming convention**: `shouldDoX()`, `shouldRejectWhenY()`, `shouldNotDoXWhenZ()`.

### Step 7: Handle Cross-Partition Distribution (if needed)

If your feature needs to run on all partitions, use `CommandDistributionBehavior`:
```java
commandDistributionBehavior.withKey(key)
    .inQueue(DistributionQueue.DEPLOYMENT)
    .distribute(ValueType.MY_NEW_TYPE, MyNewIntent.CREATE, value);
```

Add `CommandDistributionIntent.DISTRIBUTING` and `DISTRIBUTED` appliers that track distribution state.

### Step 8: Migration (if DB schema changed)

If you changed column family structure:
1. Create `engine/state/migration/to_8_N/MyEntityMigration.java` implementing `MigrationTask`
2. Add it to `DbMigratorImpl.MIGRATION_TASKS` list

### Step 9: Feature Flag (optional)

For experimental features:
1. Add boolean field to `FeatureFlags` with `false` default
2. Add to `FeatureFlagsCfg`
3. Document in `broker.yaml.template`
4. Guard feature code: `if (featureFlags.myNewFeature()) { ... }`

---

## Quick Reference: Key Classes by Role

| Role | Class |
|------|-------|
| Engine entry point | `io.camunda.zeebe.engine.Engine` |
| All processor registration | `io.camunda.zeebe.engine.processing.EngineProcessors` |
| BPMN dispatch | `io.camunda.zeebe.engine.processing.bpmn.BpmnStreamProcessor` |
| Element lifecycle | `io.camunda.zeebe.engine.processing.bpmn.ProcessInstanceLifecycle` |
| BPMN element interface | `io.camunda.zeebe.engine.processing.bpmn.BpmnElementProcessor` |
| All behaviors | `io.camunda.zeebe.engine.processing.bpmn.behavior.BpmnBehaviors` |
| All state | `io.camunda.zeebe.engine.state.immutable.ProcessingState` |
| All event appliers | `io.camunda.zeebe.engine.state.appliers.EventAppliers` |
| Output mechanism | `io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers` |
| Test harness | `io.camunda.zeebe.engine.util.EngineRule` |
| Test assertions | `io.camunda.zeebe.test.util.record.RecordingExporter` |
| Error monad | `io.camunda.zeebe.util.Either` |
| Feature flags | `io.camunda.zeebe.util.FeatureFlags` |
| DB access | `io.camunda.zeebe.db.ColumnFamily` |
| Broker startup | `io.camunda.zeebe.broker.bootstrap.BrokerStartupProcess` |
| Partition actor | `io.camunda.zeebe.broker.system.partitions.ZeebePartition` |
| Exporter SPI | `io.camunda.zeebe.exporter.api.Exporter` |
| REST controller base | `io.camunda.zeebe.gateway.rest.controller.CamundaRestController` |
| Actor base | `io.camunda.zeebe.scheduler.Actor` |

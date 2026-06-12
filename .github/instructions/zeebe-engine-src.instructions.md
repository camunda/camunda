```yaml
---
applyTo: "zeebe/engine/src/**"
---
```
# Zeebe Workflow Engine (`zeebe/engine/src/`)

The workflow engine is the core of Camunda's process automation platform. It is an event-sourced state machine that processes BPMN/DMN commands from a replicated log stream, applies state changes via versioned event appliers to RocksDB, and manages all system entities (processes, jobs, users, authorizations, tenants). Read `zeebe/engine/README.md` and `docs/zeebe/developer_handbook.md` before modifying this module.

## Architecture

The engine operates in two modes: **processing** (handles COMMAND records, produces EVENT/REJECTION records) and **replay** (applies EVENT records to rebuild state from log). The single-threaded stream processor guarantees exactly-once processing per partition.

### Core Data Flow

```
Command (from gateway/scheduler/follow-up)
  → Engine.process() dispatches via RecordProcessorMap
    → TypedRecordProcessor.processRecord() (domain-specific Processor)
      → Writers.state() appends EVENT → EventApplier modifies RocksDB state
      → Writers.command() appends follow-up COMMAND
      → Writers.response() sends response to client
      → Writers.rejection() rejects invalid commands
```

### Key Components

- **`Engine`** (`Engine.java`): Top-level `RecordProcessor`. Routes records by `(RecordType, ValueType, Intent)` to the correct `TypedRecordProcessor` via `RecordProcessorMap`. Handles ban checks for process instances and unexpected error recovery.
- **`EngineProcessors`** (`processing/EngineProcessors.java`): Factory that wires all processors, behaviors, and scheduled tasks. This is where new processor registrations go.
- **`BpmnStreamProcessor`** (`processing/bpmn/BpmnStreamProcessor.java`): Handles all `PROCESS_INSTANCE` commands. Delegates to element-specific `BpmnElementProcessor` implementations via `BpmnElementProcessors` (one per `BpmnElementType`).
- **`EventAppliers`** (`state/appliers/EventAppliers.java`): Registry of ~211 `TypedEventApplier` implementations. Each applier is versioned and handles a specific `(Intent, version)` pair.
- **`ProcessingDbState`** (`state/ProcessingDbState.java`): Instantiates all `Db*State` classes from the RocksDB `ZeebeDb`. Provides both `ProcessingState` (read-only) and `MutableProcessingState` interfaces.
- **`Writers`** (`processing/streamprocessor/writers/Writers.java`): Aggregates `StateWriter`, `CommandWriter`, `RejectionWriter`, `ResponseWriter`, `SideEffectWriter`. All processor output goes through these.

## State Architecture: Immutable/Mutable Split

Every state domain follows a strict interface split enforced by convention:
- **`state/immutable/<Entity>State`**: Read-only interface used by processors (e.g., `UserState`).
- **`state/mutable/Mutable<Entity>State`**: Write interface used only by event appliers (e.g., `MutableUserState`).
- **`state/<domain>/Db<Entity>State`**: Single implementation class (e.g., `state/user/DbUserState`) implementing both interfaces, backed by RocksDB `ColumnFamily` instances.

All state classes are accessible via `ProcessingState` (read-only, ~43 accessor methods) or `MutableProcessingState`. Processors MUST only use `ProcessingState`; appliers use `MutableProcessingState`.

## BPMN Element Processing

`BpmnElementProcessors` maps each `BpmnElementType` to a `BpmnElementProcessor<T>` implementation. The lifecycle is: `onActivate` → `finalizeActivation` → `onComplete` → `finalizeCompletion` (or `onTerminate` → `finalizeTermination`). The lifecycle state machine is defined in `ProcessInstanceLifecycle` with transitions: `ACTIVATING → ACTIVATED → COMPLETING → COMPLETED` or `→ TERMINATING → TERMINATED`.

Shared logic lives in `*Behavior` classes (e.g., `BpmnJobBehavior`, `BpmnStateBehavior`, `BpmnEventSubscriptionBehavior`), aggregated via the `BpmnBehaviors` interface and `BpmnBehaviorsImpl`.

## Adding a New Feature

1. **Define record type**: Add `*RecordValue` in `zeebe/protocol`, implement in `zeebe/protocol-impl`.
2. **Add intent**: Create `*Intent` enum in `zeebe/protocol`.
3. **Add state**: Define `state/immutable/<Entity>State` + `state/mutable/Mutable<Entity>State` interfaces, implement in `state/<domain>/Db<Entity>State`. Register in `ProcessingDbState`.
4. **Add processor**: Create `*Processor` implementing `TypedRecordProcessor` (or `DistributedTypedRecordProcessor` for cross-partition commands). Register in `EngineProcessors` via `typedRecordProcessors.onCommand(ValueType, Intent, processor)`.
5. **Add event applier**: Create `*Applier` implementing `TypedEventApplier<Intent, RecordValue>`. Register in `EventAppliers` with version 1: `register(Intent.CREATED, 1, new MyCreatedApplier(state))`.
6. **Add authorization**: Use `AuthorizationCheckBehavior` in processor. Annotate with `@ExcludeAuthorizationCheck` only if authorization is intentionally skipped.

## Critical Invariants

- **Never mutate state from a processor.** State changes flow exclusively through `EventApplier` classes invoked by `StateWriter`.
- **Never modify a released event applier.** Create a new versioned applier (e.g., version 2) and register it in `EventAppliers`. Events must replay identically.
- **Never trust command record values as current.** Read the latest data from state; command values may be stale.
- **Always short-circuit `RecordingExporter`** in tests with `.limit()`, `.getFirst()`, or `.exists()` to avoid slow queries.
- **Single-threaded processing.** Never block the stream processor thread. Break large operations into follow-up commands.
- **Side-effects are not guaranteed.** Use them only for non-critical actions (cache updates, notifications).
- **Composition over inheritance.** Create `*Behavior` classes for reusable logic; never use subclasses for code reuse.

## Testing

- **924 main source files**, **638 test files** in this module.
- **Processor tests** (JUnit 4): Use `EngineRule` + `RecordingExporterTestWatcher`. Use `EngineRule`'s typed clients (e.g., `engine.job()`, `engine.user()`) to submit commands and `RecordingExporter` to assert on produced records. See `processing/job/JobCompleteAuthorizationTest`.
- **State/Applier tests** (JUnit 5): Use `ProcessingStateExtension` to get a `MutableProcessingState` backed by in-memory RocksDB. See `state/user/UserStateTest`, `state/appliers/TenantAppliersTest`.
- **Test client pattern**: When adding a feature, add or expand a test client in `test/.../util/client/` (e.g., `JobClient`, `UserClient`).
- **Scoped run**: `./mvnw -pl zeebe/engine -am test -DskipITs -DskipChecks -Dtest=<TestClass> -T1C`

## Key Reference Files

| File | Role |
|------|------|
| `main/.../Engine.java` | Top-level record processor, dispatch + error handling |
| `main/.../processing/EngineProcessors.java` | Processor wiring factory — register new processors here |
| `main/.../state/appliers/EventAppliers.java` | Applier registry — register new appliers here |
| `main/.../processing/bpmn/BpmnElementProcessor.java` | BPMN element lifecycle interface |
| `main/.../processing/bpmn/BpmnElementProcessors.java` | Element type → processor mapping |
| `main/.../state/ProcessingDbState.java` | State class instantiation from RocksDB |
| `main/.../state/immutable/ProcessingState.java` | Read-only state accessor interface (~43 state types) |
| `main/.../processing/bpmn/behavior/BpmnBehaviors.java` | Shared behavior interface for BPMN processing |
| `main/.../processing/streamprocessor/writers/Writers.java` | All processor output writers |
| `test/.../util/EngineRule.java` | JUnit 4 test harness for processor tests |
| `test/.../util/ProcessingStateExtension.java` | JUnit 5 extension for state/applier tests |

## Common Pitfalls

- Forgetting to register a new processor in `EngineProcessors` or applier in `EventAppliers` — the command silently does nothing.
- Using `MutableProcessingState` or `Mutable*State` in a processor — violates the state-change-via-events invariant.
- Modifying code called by existing event appliers (e.g., `Db*State` methods used by appliers) — breaks replay determinism.
- Not adding `.limit()` to `RecordingExporter` queries in tests — causes tests to hang.
- Using `Thread.sleep` in tests instead of Awaitility.
- Flooding the log stream from a scheduled task without yielding after a batch of commands.
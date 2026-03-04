```yaml
---
applyTo: "zeebe/exporter-test/**"
---
```
# Zeebe Exporter Test Harness

## Purpose

This module (`zeebe-exporter-test`) provides controllable, in-memory test doubles for the `zeebe/exporter-api` interfaces (`Context`, `Controller`, `Configuration`, `ScheduledTask`). It enables unit and integration testing of exporter implementations without requiring the Zeebe broker runtime or real infrastructure (Elasticsearch, OpenSearch, etc.). It is a published library — not a test-scope module — so exporter authors can depend on it in their own test suites.

## Architecture

The module contains exactly four classes, each implementing one exporter API interface:

| Class | Implements | Thread-Safety | Role |
|---|---|---|---|
| `ExporterTestContext` | `Context` | `@NotThreadSafe` | Provides configuration, partition ID, logger, meter registry, and record filter during the configure phase |
| `ExporterTestController` | `Controller` | `@ThreadSafe` | Tracks exported record positions, metadata, and scheduled tasks with manual time control |
| `ExporterTestConfiguration<T>` | `Configuration` | `@Immutable` | Wraps an exporter's typed config object with optional argument-map-based supplier |
| `ExporterTestScheduledTask` | `ScheduledTask` + `Runnable` | `@ThreadSafe` | Cancellable, runnable wrapper around a delayed task with executed/canceled state tracking |

## Exporter Lifecycle Under Test

The exporter API lifecycle is: `configure(Context)` → `open(Controller)` → `export(Record)` (repeated) → `close()`. Use the test harness classes to drive each phase:

1. Create `ExporterTestConfiguration` wrapping your exporter's config POJO.
2. Create `ExporterTestContext`, call `setConfiguration(...)` and optionally `setPartitionId(...)`.
3. Call `exporter.configure(context)` — the exporter registers its `RecordFilter` via `context.setFilter(...)`.
4. Create `ExporterTestController`.
5. Call `exporter.open(controller)` — the exporter may schedule periodic tasks.
6. Feed records via `exporter.export(record)` — the exporter calls `controller.updateLastExportedRecordPosition(...)`.
7. Advance scheduled tasks manually via `controller.runScheduledTasks(Duration)`.

## Key Design Decisions

- **Manual time control**: `ExporterTestController.runScheduledTasks(Duration)` advances an internal clock. Tasks with `Duration.ZERO` delay do NOT run immediately — they run on the next `runScheduledTasks` call. Time is cumulative across calls.
- **Position monotonicity**: `updateLastExportedRecordPosition` only advances forward (`Math::max`). Passing a lower position is silently ignored.
- **Task retention**: Executed and canceled tasks remain in the `scheduledTasks` list for assertion. Call `resetScheduledTasks()` to clear.
- **Configuration supplier pattern**: `ExporterTestConfiguration` accepts a `Function<Map<String, Object>, T>` so tests can dynamically construct config from the arguments map, or a direct `T` instance for simple cases.
- **Thread safety boundary**: `ExporterTestContext` is NOT thread-safe (used only during single-threaded configure phase). `ExporterTestController` and `ExporterTestScheduledTask` ARE thread-safe (exporters may interact with the controller from multiple threads).

## Usage Pattern (Canonical)

```java
final var config = new MyExporterConfiguration();
final var context = new ExporterTestContext()
    .setConfiguration(new ExporterTestConfiguration<>("myExporter", config))
    .setPartitionId(1);
final var controller = new ExporterTestController();

exporter.configure(context);
exporter.open(controller);
exporter.export(someRecord);

assertThat(controller.getPosition()).isEqualTo(someRecord.getPosition());
controller.runScheduledTasks(Duration.ofSeconds(30));
```

See `ElasticsearchExporterTest`, `CamundaExporterTest`, and `OpensearchExporterTest` for real-world usage.

## Consumers

This module is depended on by:
- `zeebe/exporters/elasticsearch-exporter` (test scope)
- `zeebe/exporters/opensearch-exporter` (test scope)
- `zeebe/exporters/camunda-exporter` (test scope)
- `zeebe/exporters/app-integrations-exporter` (test scope)
- `zeebe/broker` (test scope)
- `operate/qa/integration-tests` (test scope)
- `qa/acceptance-tests` (test scope)

## Extension Points

- To support a new `Context` method added to the exporter API, add the implementation to `ExporterTestContext`. Keep `@NotThreadSafe` — it is only used during configure.
- To support a new `Controller` method, add it to `ExporterTestController`. Maintain `@ThreadSafe` using atomics or `synchronized`.
- If the `ScheduledTask` contract changes, update `ExporterTestScheduledTask`. Preserve the `volatile` flags and `synchronized` run/cancel methods.

## Invariants

- Never remove the `@ThreadSafe` annotation or weaken thread-safety guarantees on `ExporterTestController` and `ExporterTestScheduledTask` — exporters depend on concurrent access.
- `ExporterTestConfiguration` is `@Immutable` — do not add mutable state.
- Position tracking must remain monotonically increasing (the `Math::max` accumulator in `updateLastExportedRecordPosition`).
- The `instantiate(Class<R>)` method on `ExporterTestConfiguration` uses `Class.cast()` — the supplied config object must be assignable to the requested class.

## Common Pitfalls

- Forgetting to call `controller.runScheduledTasks(duration)` after `open()` — scheduled flush tasks will never execute.
- Expecting `Duration.ZERO` tasks to run immediately — they require an explicit `runScheduledTasks` call.
- Not checking `context.getRecordFilter()` after `configure()` — this is how you verify the exporter registered the correct filter.
- Using `resetScheduledTasks()` prematurely — it clears all tasks AND resets the internal clock to 0.

## Key Files

- `src/main/java/.../ExporterTestController.java` — core controller with position tracking and task scheduling
- `src/main/java/.../ExporterTestContext.java` — configure-phase context with filter capture
- `src/main/java/.../ExporterTestConfiguration.java` — immutable config wrapper with supplier pattern
- `src/main/java/.../ExporterTestScheduledTask.java` — thread-safe cancellable task wrapper
- `zeebe/exporter-api/src/main/java/.../Exporter.java` — the interface under test (lifecycle contract)
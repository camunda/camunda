```yaml
---
applyTo: "zeebe/test-util/src/**"
---
```
# Zeebe Test Util Module

Shared test infrastructure library for the Zeebe engine and broader Camunda monorepo. Provides the `RecordingExporter` (in-memory record capture), typed record stream wrappers, random BPMN process generation, custom AssertJ assertions, Testcontainers factories, JUnit extensions, and general test utilities. This module is a *compile-time dependency* of test code across many modules — not a test-scoped dependency itself.

## Architecture

The module is organized into these layers:

- **`record/`** — `RecordingExporter` (implements `Exporter`) + ~45 typed `*RecordStream` classes for fluent record querying.
- **`stream/`** — `StreamWrapper<T,S>`, a generic `Stream<T>` decorator that preserves the subtype through chaining. Foundation for all record stream types.
- **`bpmn/random/`** — Random BPMN process generator (`RandomProcessGenerator`, `BlockBuilder`, `BlockBuilderFactory`) + execution path computation. Used in chaos/fuzz testing.
- **`asserts/`** — Custom AssertJ assertions: `EitherAssert`, `DirectoryAssert`, `TopologyAssert`, `SslAssert`, `ClientStatusExceptionAssert`, `STracerAssert`.
- **`junit/`** — JUnit 5 annotations and extensions: `@RegressionTest`, `@JMHTest`, `@StraceTest`, `CachedTestResultsExtension`.
- **`testcontainers/`** — Pre-configured container factories: `TestSearchContainers` (ES, OS, Postgres, MariaDB, MySQL, MSSQL, Oracle), `DefaultTestContainers` (Keycloak), plus cloud storage containers (`MinioContainer`, `GcsContainer`, `AzuriteContainer`).
- **`socket/`** — `SocketUtil` / `PortRange` for deterministic port allocation across parallel test forks.
- **Root utilities** — `TestUtil` (retry/poll), `BrokerClassRuleHelper` (JUnit 4 test watcher), `TestConfigurationFactory` (Spring Boot YAML binding for tests), `STracer` (strace wrapper), `RecordingAppender` (Log4j2 capture).

## Key Abstractions

### RecordingExporter
Central in-memory exporter implementing `io.camunda.zeebe.exporter.api.Exporter`. Stores records in a `ConcurrentSkipListMap`. Access via static methods like `RecordingExporter.processInstanceRecords()`. The `AwaitingRecordIterator` blocks on `hasNext()` until records arrive or `maximumWaitTime` elapses. Always call `RecordingExporter.reset()` between tests (handled by `RecordingExporterTestWatcher` or `BrokerClassRuleHelper`).

### StreamWrapper / ExporterRecordStream Hierarchy
`StreamWrapper<T,S>` wraps `java.util.Stream<T>` and returns `S` from `filter()`, `limit()`, etc. via the abstract `supply()` method. `ExporterRecordStream<T,S>` adds record-specific filters (`withIntent`, `onlyEvents`, `withRecordKey`). `ExporterRecordWithVariablesStream<T,S>` adds `withVariables`/`withVariablesContaining`. Concrete streams (e.g., `ProcessInstanceRecordStream`, `JobRecordStream`) add domain-specific filters like `withBpmnProcessId`, `withElementType`.

### Random Process Generator
`RandomProcessGenerator` builds random BPMN models with configurable depth/branching via `BlockBuilder` implementations registered in `BlockSequenceBuilder.BLOCK_BUILDER_FACTORIES`. Each `BlockBuilder` produces flow nodes deterministically via `buildFlowNodes()` and generates random execution paths via `findRandomExecutionPath()`. The `ConstructionContext` carries randomness seed, depth limits, and the `IDGenerator`.

## Extension Points

### Adding a new record stream type
1. Create `<Name>RecordStream extends ExporterRecordStream<NameRecordValue, <Name>RecordStream>` in `record/`.
2. Implement `supply()` to return a new instance wrapping the stream.
3. Add domain-specific filter methods using `valueFilter(v -> ...)`.
4. Add static factory methods in `RecordingExporter` using `records(ValueType.NAME, NameRecordValue.class)`.
5. Add an accessor method in `RecordStream` filtering by `ValueType`.
6. Follow the exact pattern of `JobRecordStream` or `ProcessInstanceRecordStream`.

### Adding a new random BPMN block
1. Create a `*BlockBuilder` class in `bpmn/random/blocks/` extending `AbstractBlockBuilder`.
2. Implement `BlockBuilderFactory` as a nested `Factory` class.
3. Register the factory in `BlockSequenceBuilder.BLOCK_BUILDER_FACTORIES`.
4. Implement `buildFlowNodes()` deterministically and `generateRandomExecutionPath()` using `ExecutionPathContext`.

### Adding a new custom assertion
1. Create a class in `asserts/` extending the appropriate AssertJ base class (`AbstractObjectAssert`, `AbstractPathAssert`, etc.).
2. Provide a static `assertThat()` factory method.
3. Optionally provide an `InstanceOfAssertFactory` via a `factory()` method for chaining with `asInstanceOf()`.

## Invariants

- **Always short-circuit RecordingExporter streams.** Use `.limit()`, `.getFirst()`, `.exists()`, or `.between()`. The `AwaitingRecordIterator` throws `IllegalStateException` if `hasNext()` times out without short-circuiting. For negative assertions, use `RecordingExporter.expectNoMatchingRecords()`.
- **Always reset RecordingExporter between tests.** Use `RecordingExporterTestWatcher` (JUnit 4) or call `RecordingExporter.reset()` in `@BeforeEach`. Failure to reset causes test pollution from stale records.
- **`StreamWrapper.supply()` must return the correct subtype.** Every concrete stream must override `supply()` to preserve fluent chaining.
- **`BlockBuilder.buildFlowNodes()` must be deterministic.** All randomness happens at construction time via `ConstructionContext.getRandom()`.
- **Port allocation via `SocketUtil`** is partitioned by test fork number and maven stage. Never hardcode ports in tests; use `SocketUtil.getNextAddress()`.

## Common Pitfalls

- Forgetting `.limit()` on `RecordingExporter` queries causes tests to hang for `maximumWaitTime` then throw. Place `.limit()` *before* filters when the limiting record might not match the filters.
- Using `RecordingExporter.await()` incorrectly — it temporarily sets `maximumWaitTime` to 100ms to let Awaitility control polling. Do not nest `await()` calls.
- Modifying `BlockSequenceBuilder.BLOCK_BUILDER_FACTORIES` ordering changes random process generation for all seeds, potentially breaking existing chaos tests.
- `TestSearchContainers` container images have hardcoded tags — update them when upgrading ES/OS versions.
- `CompactRecordLogger` is complex (~700 lines) and formats records for test failure output. Changes here affect readability of all engine test failures.

## Key Reference Files

- `record/RecordingExporter.java` — Core in-memory exporter with `AwaitingRecordIterator` and `AwaitilityWrapper`
- `stream/StreamWrapper.java` — Generic typed stream decorator, base of all record streams
- `record/ExporterRecordStream.java` — Record-level filter methods (`withIntent`, `onlyEvents`, etc.)
- `bpmn/random/blocks/BlockSequenceBuilder.java` — Registry of all `BlockBuilderFactory` implementations
- `asserts/EitherAssert.java` — AssertJ extension for `Either<L,R>` testing pattern

I have thoroughly analyzed the module. Here is the instruction file:
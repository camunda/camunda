---
name: analytics-exporter
description: Use when adding support for a new event or metric in the analytics exporter at zeebe/exporters/analytics-exporter/ — creating handlers, adding AnalyticsAttributes, registering in the HandlerRegistry, and writing tests. Also use when modifying existing handlers or attributes.
---

# Analytics Exporter: Adding a New Event

Reference for extending the analytics exporter with a new event handler. The exporter ships
process-level OTel telemetry to the Camunda Analytics backend; downstream dashboards and alerts
depend on stable attribute key strings and event names across versions. Getting registration or
backwards compatibility wrong silently drops data or breaks analytics.

## Module layout

```
zeebe/exporters/analytics-exporter/src/main/java/io/camunda/exporter/analytics/
  AnalyticsExporter.java        ← entry point; configure() wires up the HandlerRegistry
  AnalyticsHandler.java         ← @FunctionalInterface — implement this
  HandlerRegistry.java          ← routes (ValueType, Intent) → handler
  AnalyticsAttributes.java      ← all OTel attribute keys and event/metric name constants
  OtelSdkManager.java           ← logEvent() / incrementMetric() / emitHeartbeat()
  handler/                      ← one class per event type
```

## Step 1 — Identify the Zeebe record to handle

Determine the `ValueType`, `Intent`, and `RecordValue` type from the Zeebe protocol:

```
zeebe/protocol/src/main/java/io/camunda/zeebe/protocol/record/value/
zeebe/protocol/src/main/java/io/camunda/zeebe/protocol/record/intent/
```

Check `HandlerRegistry` in `AnalyticsExporter.configure()` to confirm there is no existing
handler for that `(ValueType, Intent)` pair — the registry throws `IllegalStateException` on
duplicate registration.

> **One handler per `(ValueType, Intent)`.** If the same intent covers multiple element types
> (like `PROCESS_INSTANCE / ELEMENT_ACTIVATED`), add filtering logic *inside* the handler
> (see `AdHocSubProcessHandler` for an example).

## Step 2 — Add AnalyticsAttributes constants

Open `AnalyticsAttributes.java` and add any new `AttributeKey` constants or string constants.

**OTel naming rules:**
- Attribute keys: dot-delimited namespaces, snake_case — e.g. `"camunda.job.type"` 
- Event name strings: snake_case — e.g. `"job_created"`
- Metric name strings: dot-delimited — e.g. `"camunda.job.created"`

**Iron rule — never remove or rename existing constants.** Attribute key strings, event names,
and metric names are part of the analytics schema. They are baked into downstream dashboards,
queries, and alerts. Renaming or removing one silently breaks consumers. Only ever *add* new
constants. If semantics change, add a new constant alongside the old one.

Group the new constants with the domain they belong to. Follow the existing block comments:

```java
// Job domain
public static final AttributeKey<String> JOB_TYPE = AttributeKey.stringKey("camunda.job.type");
public static final AttributeKey<String> JOB_WORKER = AttributeKey.stringKey("camunda.job.worker");

// Event names
public static final String EVENT_JOB_CREATED = "job_created";
```

## Step 3 — Create the handler

Create `handler/MyEventHandler.java` in the same package as the other handlers:

```java
package io.camunda.exporter.analytics.handler;

import static io.camunda.exporter.analytics.AnalyticsAttributes.*;

import io.camunda.exporter.analytics.AnalyticsHandler;
import io.camunda.exporter.analytics.OtelSdkManager;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.value.MyRecordValue;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

public final class MyEventHandler implements AnalyticsHandler<MyRecordValue> {

  private final OtelSdkManager otelSdkManager;

  public MyEventHandler(final OtelSdkManager otelSdkManager) {
    this.otelSdkManager = Objects.requireNonNull(otelSdkManager);
  }

  @Override
  public void handle(final Record<MyRecordValue> record) {
    final var value = record.getValue();
    // Optional: filter on a sub-condition and return early if not applicable.

    otelSdkManager.logEvent(
        EVENT_MY_EVENT,
        record.getPosition(),
        log ->
            log.setAttribute(BPMN_PROCESS_ID, value.getBpmnProcessId())
                .setAttribute(TENANT_ID, value.getTenantId())
                .setTimestamp(record.getTimestamp(), TimeUnit.MILLISECONDS));
  }
}
```

Use `otelSdkManager.logEvent()` for discrete events and `otelSdkManager.incrementMetric()` for
counters/gauges. See `ProcessInstanceCreationHandler` for an example that uses both.

## Step 4 — Register the handler

Open `AnalyticsExporter.configure()` and add a `.register(...)` call to the `HandlerRegistry`
chain:

```java
handlers =
    new HandlerRegistry()
        ...
        .register(
            ValueType.MY_VALUE_TYPE,
            MyIntent.MY_INTENT,
            new MyEventHandler(otelSdkManager))
        .apply(context);
```

The `apply(context)` call installs an `AnalyticsRecordFilter` that only passes records matching
the registered `(ValueType, Intent)` pairs to the exporter — no other change needed for filtering.

## Step 5 — Write tests

### Handler unit test

Create `handler/MyEventHandlerTest.java`:

```java
class MyEventHandlerTest {

  private static final ProtocolFactory FACTORY = new ProtocolFactory();

  private InMemoryLogRecordExporter memoryExporter;
  private MyEventHandler handler;

  @BeforeEach
  void setUp() {
    memoryExporter = InMemoryLogRecordExporter.create();
    handler = new MyEventHandler(TestOtelSdkManager.inMemory(memoryExporter));
    // For metric assertions: TestOtelSdkManager.inMemoryWithMetrics(logExporter, metricReader)
  }

  @Test
  void shouldEmitEventWithCorrectAttributes() {
    // given
    final var value = ImmutableMyRecordValue.builder()
        .withBpmnProcessId("my-process")
        .withTenantId("tenant-a")
        .build();
    final var record = FACTORY.generateRecord(
        ValueType.MY_VALUE_TYPE,
        r -> r.withRecordType(RecordType.EVENT)
              .withIntent(MyIntent.MY_INTENT)
              .withValue(value));

    // when
    handler.handle(typed(record));

    // then
    assertThat(memoryExporter.getFinishedLogRecordItems())
        .singleElement()
        .satisfies(log ->
            assertThat(log.getAttributes().asMap())
                .containsEntry(AnalyticsAttributes.EVENT_NAME, AnalyticsAttributes.EVENT_MY_EVENT)
                .containsEntry(AnalyticsAttributes.BPMN_PROCESS_ID, "my-process")
                .containsEntry(AnalyticsAttributes.TENANT_ID, "tenant-a"));
  }

  // If the handler silently skips some records, test that path too:
  @Test
  void shouldSkipUnmatchedRecords() {
    // given — build a record that should be filtered out
    // when
    handler.handle(typed(unrelatedRecord));
    // then
    assertThat(memoryExporter.getFinishedLogRecordItems()).isEmpty();
  }

  @SuppressWarnings("unchecked")
  private static <T extends RecordValue> Record<T> typed(final Record<?> record) {
    return (Record<T>) record;
  }
}
```

**Required test cases per handler:**
- Happy path: correct attributes are emitted for a matching record
- Skip path (if the handler filters internally): no event emitted for a non-matching record
- If the handler emits a counter metric: verify `incrementMetric()` accumulates across multiple calls

### Integration wiring check

Add a test to `AnalyticsExporterTest` that feeds a record of the new type through the full
exporter (`exporter.export(record)`) and asserts the expected event name appears. The test
setup (`exporter`, `memoryExporter`, `controller`) is already provided by `@BeforeEach`:

```java
@Test
void shouldEmitMyEventWhenRecordExported() {
    // given
    final var record =
        FACTORY.generateRecord(
            ValueType.MY_VALUE_TYPE,
            r -> r.withRecordType(RecordType.EVENT).withIntent(MyIntent.MY_INTENT));

    // when
    exporter.export(record);

    // then
    assertThat(memoryExporter.getFinishedLogRecordItems())
        .singleElement()
        .satisfies(
            log ->
                assertThat(log.getAttributes().get(AnalyticsAttributes.EVENT_NAME))
                    .isEqualTo(AnalyticsAttributes.EVENT_MY_EVENT));
}
```

> **Note on `Immutable*RecordValue` builders.** When a test needs to set specific field values
> on the record (e.g. element type, process ID), use the generated `Immutable*` builder from
> `zeebe-protocol-immutables`, e.g. `ImmutableProcessInstanceRecordValue.builder()`. See the
> existing handler tests in `handler/*HandlerTest.java` for exact usage.

## Step 6 — Build and verify

```bash
# Format before committing (mandatory when touching Java/markdown/pom.xml)
./mvnw license:format spotless:apply -T1C

# Run the analytics exporter tests
./mvnw verify -pl zeebe/exporters/analytics-exporter -DskipTests=false -Dquickly -T1C
```

All tests must pass before committing.

## Quick-reference: key files

| File | Purpose |
|------|---------|
| `AnalyticsAttributes.java` | Add new `AttributeKey` constants and event/metric name strings here |
| `handler/` | One class per event type; implement `AnalyticsHandler<T>` |
| `AnalyticsExporter.java:configure()` | Register new handlers in the `HandlerRegistry` chain |
| `TestOtelSdkManager.java` | Test factory — `inMemory()` for log-only, `inMemoryWithMetrics()` for both |
| `handler/*HandlerTest.java` | Pattern to follow for handler unit tests |
| `AnalyticsExporterTest.java` | Integration-level wiring test to extend |

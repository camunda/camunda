---
name: analytics-exporter
description: Use when adding support for a new event or metric in the analytics exporter at zeebe/exporters/analytics-exporter/ — creating handlers, adding AnalyticsAttributes, registering in the HandlerRegistry, and writing tests. Also use when modifying existing handlers or attributes.
---

# Analytics Exporter: Adding a New Event

Reference for extending the analytics exporter with a new event handler. The exporter ships
process-level OTel telemetry to the Camunda Analytics backend; downstream dashboards and alerts
depend on stable attribute key strings and event names across versions. Getting registration or
backwards compatibility wrong silently drops data or breaks analytics.

> **Iron rule — NEVER expose PII.** Variable values, usernames, email addresses, user IDs,
> or any other personally identifiable data must never be emitted. Only process metadata
> (process IDs, definition keys, instance keys, element IDs, tenant IDs, timestamps) is
> acceptable. When in doubt, leave it out.

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

`AnalyticsAttributes` is organized into domain-specific nested classes (`Process`, `Event`,
`Tenant`, `Element`, `Metric`, etc.). Add new constants to the appropriate nested class, or
create a new one if a new domain is needed.

**OTel naming rules:**
- Attribute keys: dot-delimited namespaces, snake_case — e.g. `"camunda.job.type"`
- Event name strings: snake_case — e.g. `"job_created"` (go inside the `Event` nested class)
- Metric name strings: dot-delimited — e.g. `"camunda.job.created"` (go inside `Metric`)

**Keep attribute count minimal.** Every attribute added to a metric becomes a dimension in the
time-series backend. Too many attributes — especially high-cardinality ones — cause dimension
explosion and drive up storage and query costs. Only add attributes that are genuinely needed.
For log events this is less critical, but the same principle applies.

**Iron rule — never remove or rename existing constants.** Attribute key strings, event names,
and metric names are part of the analytics schema. They are baked into downstream dashboards,
queries, and alerts. Renaming or removing one silently breaks consumers. Only ever *add* new
constants. If semantics change, add a new constant alongside the old one.

Adding a new domain (e.g. `Job`):

```java
public static final class Job {
  public static final AttributeKey<String> TYPE = AttributeKey.stringKey("camunda.job.type");
  public static final AttributeKey<String> WORKER = AttributeKey.stringKey("camunda.job.worker");

  private Job() {}
}
```

Adding an event name for the new event (inside the existing `Event` nested class):

```java
public static final class Event {
  // ... existing constants ...
  public static final String JOB_CREATED = "job_created";

  private Event() {}
}
```

## Step 3 — Create the handler

Create `handler/MyEventHandler.java` in the same package as the other handlers:

```java
package io.camunda.exporter.analytics.handler;

import static io.camunda.exporter.analytics.AnalyticsAttributes.Event.MY_EVENT;
import static io.camunda.exporter.analytics.AnalyticsAttributes.Process.BPMN_PROCESS_ID;

import io.camunda.exporter.analytics.AnalyticsAttributes;
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
        MY_EVENT,
        record.getPosition(),
        log ->
            log.setAttribute(BPMN_PROCESS_ID, value.getBpmnProcessId())
                // Tenant.ID and Element.ID share the unqualified name ID — use qualified form
                .setAttribute(AnalyticsAttributes.Tenant.ID, value.getTenantId())
                .setTimestamp(record.getTimestamp(), TimeUnit.MILLISECONDS));
  }
}
```

**Import style:** use explicit static imports from the nested class (e.g.
`AnalyticsAttributes.Process.BPMN_PROCESS_ID`). When the unqualified name would be ambiguous
(e.g. both `Tenant.ID` and `Element.ID` are named `ID`), use the qualified form
`AnalyticsAttributes.Tenant.ID` directly rather than a static import.

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

The `apply(context)` call installs an `AnalyticsRecordFilter`. The filter is an
over-approximation: it accepts records whose `ValueType` is in the registered set *and* whose
`Intent` is in the registered set, but those two sets are evaluated independently — a record can
pass the filter even if its exact `(ValueType, Intent)` pair has no handler. Exact routing and
no-ops happen in `HandlerRegistry.handle()`. No other change is needed for filtering.

## Step 5 — Write tests

### Handler unit test

Create `handler/MyEventHandlerTest.java`:

```java
class MyEventHandlerTest {

  // io.camunda.zeebe.test.broker.protocol.ProtocolFactory
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
                .containsEntry(AnalyticsAttributes.Event.NAME, AnalyticsAttributes.Event.MY_EVENT)
                .containsEntry(AnalyticsAttributes.Process.BPMN_PROCESS_ID, "my-process")
                .containsEntry(AnalyticsAttributes.Tenant.ID, "tenant-a"));
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
                assertThat(log.getAttributes().get(AnalyticsAttributes.Event.NAME))
                    .isEqualTo(AnalyticsAttributes.Event.MY_EVENT));
}
```

> **Note on `Immutable*RecordValue` builders.** When a test needs to set specific field values
> on the record (e.g. element type, process ID), use the generated `Immutable*` builder from
> `zeebe-protocol-immutables`, e.g. `ImmutableProcessInstanceRecordValue.builder()`. See the
> existing handler tests in `handler/*HandlerTest.java` for exact usage.

## Step 6 — Update the module README

Open `zeebe/exporters/analytics-exporter/README.md` and update it in lockstep with the code
changes above — the README is the source of truth downstream consumers read to understand
what the exporter emits, and it drifts silently if this step is skipped:

- Add a row for the new event to the **Event types** table (source record, intent, event
  name, and a short note on when it's emitted).
- Document the event's *specific* attributes — not just the common ones already covered in
  **Common log record attributes**. Add a new event-specific attributes section/table using
  the same format as the existing per-event sections (e.g. **Heartbeat attributes**).
- **Verify attribute names against `AnalyticsAttributes.java`.** Every attribute key string
  written in the README must match the actual constant value in code, not just look
  plausible. Cross-check each one you add (and, ideally, any existing ones you touch)
  against the real `AttributeKey`/string constant — README prose can drift from the code
  over time, so don't introduce or perpetuate that class of mismatch.

## Step 7 — Build and verify

```bash
# Format before committing (mandatory when touching Java/markdown/pom.xml)
./mvnw license:format spotless:apply -T1C

# Run the analytics exporter tests
./mvnw verify -pl zeebe/exporters/analytics-exporter -DskipTests=false -Dquickly -T1C
```

All tests must pass before committing.

## Step 8 — Final review checklist

Before opening the PR, go through this checklist:

1. **No PII exposed** — double-check every attribute: no variable values, usernames, email
   addresses, or any other personally identifiable data.
2. **No attributes renamed or removed** — existing constants in `AnalyticsAttributes` are
   unchanged; only new constants were added.
3. **Attribute count is minimal** — no unnecessary dimensions; every attribute added to a
   metric has a clear analytical purpose.
4. **Handler is registered** — `.register(ValueType, Intent, handler)` call is present in
   `AnalyticsExporter.configure()`.
5. **Tests pass** — `./mvnw verify -pl zeebe/exporters/analytics-exporter -DskipTests=false -Dquickly -T1C` is green.
6. **`zeebe/exporters/analytics-exporter/README.md` updated** — the new event type is listed in the **Event types** table and
   its specific attributes are documented, with attribute names matching the
   `AnalyticsAttributes` constants.

## Quick-reference: key files

| File | Purpose |
|------|---------|
| `AnalyticsAttributes.java` | Add new `AttributeKey` constants and event/metric name strings here |
| `handler/` | One class per event type; implement `AnalyticsHandler<T>` |
| `AnalyticsExporter.java:configure()` | Register new handlers in the `HandlerRegistry` chain |
| `TestOtelSdkManager.java` | Test factory — `inMemory()` for log-only, `inMemoryWithMetrics()` for both |
| `handler/*HandlerTest.java` | Pattern to follow for handler unit tests |
| `AnalyticsExporterTest.java` | Integration-level wiring test to extend |
| `zeebe/exporters/analytics-exporter/README.md` | Update when adding/changing event types or attributes |

---
name: analytics-exporter
description: Use when adding support for a new event or metric in the analytics exporter at zeebe/exporters/analytics-exporter/ — creating handlers, adding AnalyticsAttributes, registering in AnalyticsHandlerCatalog, and writing tests. Also use when modifying existing handlers or attributes.
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
>
> Identity-domain records (tenant, user, group, role, mapping rule, authorization) carry
> author-chosen or user-supplied identifiers alongside their keys — `name`, `description`,
> `entityId` and similar. These are PII or free text and must not be emitted. Emit only
> keys, ids, entity *types*, status/result flags, and timestamps.

## Module layout

```
zeebe/exporters/analytics-exporter/src/main/java/io/camunda/exporter/analytics/
  AnalyticsHandlerCatalog.java  ← build() registers every handler — the only wiring file to edit
  AnalyticsExporter.java        ← entry point; calls AnalyticsHandlerCatalog.build(...).apply(context)
  AnalyticsHandler.java         ← interface — implement this with a named class
  HandlerRegistry.java          ← routes (ValueType, Intent) → handler
  AnalyticsAttributes.java      ← all OTel attribute keys and event/metric name constants
  OtelSdkManager.java           ← logEvent() / incrementMetric() / emitHeartbeat()
  handler/                      ← one class per event type
```

## Step 1 — Identify the Zeebe record to handle

Determine the `ValueType`, `Intent`, and `RecordValue` type from the Zeebe protocol. `ValueType`
itself is SBE-generated from `zeebe/protocol/src/main/resources/protocol.xml`, so there is no
`ValueType.java` to read. The in-source index that maps each `ValueType` to its `RecordValue` and
`Intent` class is:

```
zeebe/protocol/src/main/java/io/camunda/zeebe/protocol/record/ValueTypeMapping.java
```

The `RecordValue` and `Intent` classes it names live under
`io/camunda/zeebe/protocol/record/value/` and `io/camunda/zeebe/protocol/record/intent/`.

> **Pick the runtime value type, not its deployment-time namesake.** Several subjects have both.
> `DECISION` / `DecisionIntent` is the deployed decision *definition*; the runtime evaluation is
> `DECISION_EVALUATION` / `DecisionEvaluationIntent` with `DecisionEvaluationRecordValue`.

Check `AnalyticsHandlerCatalog.build(...)` to confirm there is no existing handler for that
`(ValueType, Intent)` pair — the registry throws `IllegalStateException` on duplicate
registration.

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
- Event name strings: take the name from the data contract for the signal you are adding (go
  inside the `Event` nested class)
- Metric name strings: dot-delimited — e.g. `"camunda.job.activated"` (go inside `Metric`)

**Event names come from the data contract, not from a house convention.** Contract-defined
signals use canonical dotted names — e.g. `"camunda.tenant.created"`. The bare snake_case names
already in `AnalyticsAttributes.Event` (`process_instance_created`, `user_task_created`) predate
the contract; they stay as they are because renaming them would break consumers, but they are not
a rule to follow for new signals. If there is no contract entry, match the closest contract name
rather than inventing a snake_case one.

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
  public static final String JOB_CREATED = "camunda.job.created";

  private Event() {}
}
```

## Step 3 — Create the handler

Create `handler/MyEventHandler.java` in the same package as the other handlers.

**Choose the correct category** by implementing `category()` (there is no default — every handler
must make a deliberate choice):
- `AnalyticsCategory.CONTRACTUAL` — commercial/licence metrics (process instances, decision
  instances, task users, tenant events, usage metrics)
- `AnalyticsCategory.OPTIONAL` — non-commercial product usage metrics (ad-hoc subprocess activations,
  feature adoption signals)

The category determines whether the handler is active based on the exporter's `categories`
configuration. If a category is removed from the config array, all handlers in that category are
excluded at startup. Changing categories also changes the exporter digest fingerprint.

> **Handlers must be named classes.** Do not treat `AnalyticsHandler` as a functional interface.
> A lambda, anonymous class, or local class compiles, but `AnalyticsHandler.digestInput()` hashes
> the handler's `.class` bytes and throws `IllegalArgumentException` for those forms — so a lambda
> registered in the catalog fails at `configure()` time, when the exporter digest is computed.
> (Routing tests that never compute a digest may still use lambdas; see `HandlerRegistryTest`.)

```java
package io.camunda.exporter.analytics.handler;

import static io.camunda.exporter.analytics.AnalyticsAttributes.Event.MY_EVENT;
import static io.camunda.exporter.analytics.AnalyticsAttributes.Process.BPMN_PROCESS_ID;

import io.camunda.exporter.analytics.AnalyticsAttributes;
import io.camunda.exporter.analytics.AnalyticsCategory;
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
  public AnalyticsCategory category() {
    // CONTRACTUAL — commercial/licence metrics (process instances, decision instances, task users)
    // OPTIONAL — non-commercial product usage metrics (e.g. ad-hoc subprocess activations)
    return AnalyticsCategory.CONTRACTUAL;
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
`incrementMetric(metricName, position, eventTimeMs, dimensions)` takes a pre-built
`Attributes.of(...)` as its last argument — there is no builder callback as there is for
`logEvent`.

### What the platform injects vs what your handler sets

Do not set the injected fields yourself. `logEvent()` sets them before it runs your builder
callback, so anything you set on the same key silently overwrites the platform's value.

| Field | Set by |
|-------|--------|
| `event.name` | `OtelSdkManager.logEvent()`, from the event name you pass |
| `camunda.log.position` | `OtelSdkManager.logEvent()`, from the position you pass |
| `camunda.event.sequence_number` | `OtelSdkManager.logEvent()`, per-partition counter |
| `camunda.event.sample_rate` | `OtelSdkManager.logEvent()`, only when the applied rate is below 1.0 |
| `service.name`, `camunda.cluster.id`, `camunda.partition.id`, `camunda.exporter.digest` | OTel `Resource`, built once per partition in `OtelSdkManager.buildResource()` |
| schema version | the OTel instrumentation scope's schema URL, not a per-record attribute |
| record timestamp | your handler — `.setTimestamp(record.getTimestamp(), TimeUnit.MILLISECONDS)` |
| every domain attribute | your handler |

There is no `event.id` attribute. Records are identified downstream by cluster, partition,
log position and sequence number; do not invent one.

## Step 4 — Register the handler in the catalog

Open `AnalyticsHandlerCatalog.build(...)` and add a `.register(...)` call to the `HandlerRegistry`
chain. This is the only main-source file outside `handler/` and `AnalyticsAttributes` that a new
event touches — `AnalyticsExporter` never changes:

```java
static HandlerRegistry build(final OtelSdkManager otelSdkManager) {
  return new HandlerRegistry()
      ...
      .register(
          ValueType.MY_VALUE_TYPE,
          MyIntent.MY_INTENT,
          new MyEventHandler(otelSdkManager));
}
```

`AnalyticsExporter.configure()` calls `AnalyticsHandlerCatalog.build(otelSdkManager).apply(context)`.
The `apply(context)` call installs an `AnalyticsRecordFilter`. The filter is an
over-approximation: it accepts records whose `ValueType` is in the registered set *and* whose
`Intent` is in the registered set, but those two sets are evaluated independently — a record can
pass the filter even if its exact `(ValueType, Intent)` pair has no handler. Exact routing and
no-ops happen in `HandlerRegistry.handle()`. No other change is needed for filtering.

## Step 5 — Add the pair to the catalog test

`AnalyticsHandlerCatalogTest.shouldRegisterAllExpectedHandlers` asserts the registered set
*exactly*, so a new `.register(...)` fails that test until the same `(ValueType, Intent)` entry is
added there. The failure names only the set difference, not this step, so do it now:

```java
assertThat(registry.registrations())
    .containsExactlyInAnyOrder(
        ...
        Map.entry(ValueType.MY_VALUE_TYPE, MyIntent.MY_INTENT));
```

## Step 6 — Write tests

### Handler unit test

Create `handler/MyEventHandlerTest.java`. The exporter and handler are built fresh for each test —
`UserTaskCreatedHandlerTest` builds them inline in the `@Test`, the older tests use `@BeforeEach`;
either is fine, but do not share one `InMemoryLogRecordExporter` across tests.

The four types the test needs, in full:

- `io.opentelemetry.sdk.testing.exporter.InMemoryLogRecordExporter` — captures emitted log records
- `io.opentelemetry.sdk.testing.exporter.InMemoryMetricReader` — captures metrics (only if the
  handler increments one)
- `io.camunda.zeebe.test.broker.protocol.ProtocolFactory` — random record generator, from
  `zeebe-protocol-test-util` (already a test dependency of this module)
- `io.camunda.exporter.analytics.TestOtelSdkManager` — in-memory `OtelSdkManager` factory
- `io.camunda.zeebe.protocol.record.value.Immutable*RecordValue` — generated builders for record
  values

```java
class MyEventHandlerTest {

  private static final ProtocolFactory FACTORY = new ProtocolFactory();

  @Test
  void shouldEmitEventWithSafeAttributesOnly() {
    // given
    final var logExporter = InMemoryLogRecordExporter.create();
    final var handler = new MyEventHandler(TestOtelSdkManager.inMemory(logExporter));

    final var value = ImmutableMyRecordValue.builder()
        .withBpmnProcessId("my-process")
        .withTenantId("tenant-a")
        // PII fields — must NOT appear in the emitted event
        .withAssignee("john.doe@example.com")
        .build();
    final var record = FACTORY.generateRecord(
        ValueType.MY_VALUE_TYPE,
        r -> r.withRecordType(RecordType.EVENT)
              .withIntent(MyIntent.MY_INTENT)
              .withValue(value));

    // when
    handler.handle(typed(record));

    // then
    assertThat(logExporter.getFinishedLogRecordItems())
        .singleElement()
        .satisfies(log -> {
          final var attrs = log.getAttributes().asMap();

          assertThat(attrs)
              .containsEntry(AnalyticsAttributes.Event.NAME, AnalyticsAttributes.Event.MY_EVENT)
              .containsEntry(AnalyticsAttributes.Process.BPMN_PROCESS_ID, "my-process")
              .containsEntry(AnalyticsAttributes.Tenant.ID, "tenant-a");

          // PII must not appear in any attribute value
          final var allValues = attrs.values().stream().map(Object::toString).toList();
          assertThat(allValues).doesNotContain("john.doe@example.com");
        });
  }

  // If the handler silently skips some records, test that path too:
  @Test
  void shouldSkipUnmatchedRecords() {
    // given — a fresh exporter and handler, and a record that should be filtered out
    // when
    handler.handle(typed(unrelatedRecord));
    // then
    assertThat(logExporter.getFinishedLogRecordItems()).isEmpty();
  }

  @SuppressWarnings("unchecked")
  private static <T extends RecordValue> Record<T> typed(final Record<?> record) {
    return (Record<T>) record;
  }
}
```

**Required test cases per handler:**
- Happy path: correct attributes are emitted for a matching record
- PII sweep: set the record's PII-carrying fields to recognisable values and assert none of them
  appear in *any* emitted attribute value, not just the ones you happen to assert on
- Skip path (if the handler filters internally): no event emitted for a non-matching record
- If the handler emits a counter metric: verify `incrementMetric()` accumulates across multiple calls

**Testing a metric.** `TestOtelSdkManager.inMemory(logExporter)` creates a metric reader you have
no handle on, so metrics emitted through it cannot be asserted. A handler that calls
`incrementMetric()` needs its own reader:

```java
final var metricReader = InMemoryMetricReader.create();
final var handler =
    new MyEventHandler(TestOtelSdkManager.inMemoryWithMetrics(logExporter, metricReader));
// assert on metricReader.collectAllMetrics()
```

See `ProcessInstanceCreationHandlerTest` for the full pattern.

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
> `io.camunda.zeebe.protocol.record.value`, e.g. `ImmutableProcessInstanceRecordValue.builder()`.
> Two things that are not obvious from the `with*` names:
>
> - **List-typed properties take a collection.** `getTools()`, `getChangedAttributes()`,
>   `getEvaluatedDecisions()` and friends are `List<...>` on the record value, so the builder wants
>   `java.util.List.of(...)` — passing a bare scalar does not compile.
> - **Partial builders are legal.** The immutables are generated with
>   `validationMethod = ValidationMethod.NONE` (see `ImmutableProtocol`), so you only need to set
>   the fields the handler reads; unset fields come back `null`.
>
> See the existing handler tests in `handler/*HandlerTest.java` for exact usage.

## Step 7 — Update the module docs

Two files in the module document the event set, and both drift silently if skipped.

**`zeebe/exporters/analytics-exporter/AGENTS.md`** — add a row to the **Current Event Handlers**
table (ValueType, Intent, handler class, `event.name`, extra filter). This one is easy to miss
because it duplicates part of the README's table.

**`zeebe/exporters/analytics-exporter/README.md`** — update it in lockstep with the code changes
above; it is the source of truth downstream consumers read to understand what the exporter emits:

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

## Step 8 — Build and verify

Java 21 is required; point `JAVA_HOME` at a JDK 21 before running anything. All commands run from
the repository root. See the **Building** section of the module README for the canonical list.

```bash
# Build the module and everything it depends on. Without -am this fails on a fresh clone,
# because the module's dependencies are not yet in the local repository.
./mvnw install -pl zeebe/exporters/analytics-exporter -am -Dquickly -T1C

# Run this module's tests. The install step above put the module and its dependencies in the
# local repository, so -pl without -am resolves them and runs all of this module's tests. Do
# not scope with -Dtest here: -Dsurefire.failIfNoSpecifiedTests=false would let a mistyped
# pattern that matches nothing pass as a false green.
./mvnw verify -pl zeebe/exporters/analytics-exporter -DskipTests=false -DskipITs -Dquickly

# Format the Java in this module. Unscoped, spotless:apply reformats the whole repository.
./mvnw license:format spotless:apply -pl zeebe/exporters/analytics-exporter
```

Drop `-DskipITs` only when Docker is running: `verify` otherwise runs `AnalyticsExporterOtelIT`,
which starts a real OTel Collector via Testcontainers. Note also that markdown formatting is
configured on the root POM only, so running `spotless:apply` from the root touches markdown across
the entire repository, not just your module.

All tests must pass before committing.

## Step 9 — Final review checklist

Before opening the PR, go through this checklist:

1. **No PII exposed** — double-check every attribute: no variable values, usernames, email
   addresses, author-chosen names, or any other personally identifiable data.
2. **No attributes renamed or removed** — existing constants in `AnalyticsAttributes` are
   unchanged; only new constants were added.
3. **Attribute count is minimal** — no unnecessary dimensions; every attribute added to a
   metric has a clear analytical purpose.
4. **Handler is registered** — `.register(ValueType, Intent, handler)` call is present in
   `AnalyticsHandlerCatalog.build(...)`.
5. **Catalog test updated** — the same `(ValueType, Intent)` pair is in the
   `containsExactlyInAnyOrder` set in `AnalyticsHandlerCatalogTest`.
6. **Tests pass** — the scoped `./mvnw verify` from Step 8 is green.
7. **Module docs updated** — the new event is a row in the **Current Event Handlers** table in
   `zeebe/exporters/analytics-exporter/AGENTS.md`, and is listed in the **Event types** table in
   `zeebe/exporters/analytics-exporter/README.md` with its specific attributes documented and
   attribute names matching the `AnalyticsAttributes` constants.

## Quick-reference: key files

| File | Purpose |
|------|---------|
| `AnalyticsAttributes.java` | Add new `AttributeKey` constants and event/metric name strings here |
| `handler/` | One class per event type; implement `AnalyticsHandler<T>` with a named class |
| `AnalyticsHandlerCatalog.java:build()` | Register new handlers in the `HandlerRegistry` chain |
| `AnalyticsHandlerCatalogTest.java` | Exact-set assertion — add the new `(ValueType, Intent)` pair or the build goes red |
| `TestOtelSdkManager.java` | Test factory — `inMemory()` for log-only, `inMemoryWithMetrics()` for both |
| `handler/*HandlerTest.java` | Pattern to follow for handler unit tests |
| `AnalyticsExporterTest.java` | Integration-level wiring test to extend |
| `zeebe/exporters/analytics-exporter/AGENTS.md` | Update the **Current Event Handlers** table |
| `zeebe/exporters/analytics-exporter/README.md` | Update when adding/changing event types or attributes |

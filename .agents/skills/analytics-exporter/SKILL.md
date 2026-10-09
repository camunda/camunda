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
> `entityId` and similar. These are PII or free text and must not be emitted, even when the
> field name ends in `Id`. Emit only engine-generated record keys, the tenant id, the attributes
> the data contract defines for the signal, entity *types*, status/result flags, and timestamps.

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
> (see `ProcessInstanceElementActivatedHandler` for an example).

## Step 2 — Add AnalyticsAttributes constants

Open `AnalyticsAttributes.java` and add any new `AttributeKey` constants or string constants.

`AnalyticsAttributes` is organized into domain-specific nested classes (`Process`, `Event`,
`Tenant`, `Element`, `Metric`, etc.). Add new constants to the appropriate nested class, or
create a new one if a new domain is needed.

**Names come from the data contract.** Every attribute key, event name, metric name, and metric
unit the exporter emits is defined in the product-telemetry data contract, which is the authority:

- **The contract** lives in the internal `camunda/Holistic-Data-Platform` repository under
  `ingest/camunda-product-telemetry/schemas/`; its rendered catalog is
  [`docs/catalog.md`](https://github.com/camunda/Holistic-Data-Platform/blob/main/ingest/camunda-product-telemetry/schemas/docs/catalog.md)
  (Camunda organization access required). Find the signal's entry and copy its names exactly.
- **The public mirror** is this module's `README.md` (**Event types**, **Per-event attributes**,
  **Pre-aggregated counters**), which must match the contract. Use it when you cannot open the
  contract.
- **No entry, or no access:** do not invent a name. Ask in the PR for Core Features or the HDP
  team to propose the contract entry, and add the constant once it exists.

- Attribute keys go in the domain's nested class (e.g. `"camunda.process.definition.key"`)
- Event name strings go inside the `Event` nested class (e.g. `"camunda.tenant.created"`)
- Metric name strings and their units go inside `Metric` (e.g.
  `"camunda.decision.instance.evaluated"` with unit `"{decision_instance}"`)

**Keep attribute count minimal.** Every attribute added to a metric becomes a dimension in the
time-series backend. Too many attributes — especially high-cardinality ones — cause dimension
explosion and drive up storage and query costs. Only add attributes that are genuinely needed.
For log events this is less critical, but the same principle applies.

**Iron rule — never remove or rename existing constants as part of feature work.** Attribute key
strings, event names, and metric names are part of the analytics schema. They are baked into
downstream dashboards, queries, and alerts. Renaming or removing one silently breaks consumers.
When adding an event, only ever *add* new constants; if semantics change, add a new constant
alongside the old one. A rename is only acceptable as an explicitly agreed contract migration
with downstream consumers, done in its own PR that updates the pinning tests
(`AnalyticsEventNamesTest`, `AnalyticsAttributeKeysTest`, `AnalyticsMetricNamesTest`) and the
README. A new constant also has to be added to the matching pinning test.

**Key types follow the contract.** Use the `AttributeKey` type that matches the contract's type:
`longKey` for `int` (all keys and versions), `stringKey` for `string` and enums, `doubleKey` for
`double`. In the handler:

- `int` getters such as `getVersion()` do not fit an `AttributeKey<Long>`; cast with
  `(long) value.getVersion()`.
- Send an enum as `value.getStatus().name()` on a `stringKey`.
- OTel silently drops an attribute whose value is `null`, so a nullable field (for example
  `getTenantId()`) disappears rather than failing. Check the contract's requirement level: if
  the attribute is required, decide explicitly whether to skip the record.

Adding a new domain (e.g. `Job`):

```java
public static final class Job {
  public static final AttributeKey<String> TYPE = AttributeKey.stringKey("camunda.job.type");
  public static final AttributeKey<Long> KEY = AttributeKey.longKey("camunda.job.key");

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

**Choose the category** by implementing `category()`: `AnalyticsCategory.CONTRACTUAL` or
`AnalyticsCategory.OPTIONAL`. There is no default, and the category is not a judgement call: take it
from the signal's entry in the data contract (its `contractual` / `optional` category). Without
the contract entry there is no handler to add, category included. How the `categories`
configuration activates handlers is described under **Configuration reference** in the module
README. Only handlers registered in the catalog are category-gated: the heartbeat is not, so do
not model a new signal on `emitHeartbeat()`.

> **Handlers must be named classes.** Do not treat `AnalyticsHandler` as a functional interface.
> A lambda, anonymous class, or local class compiles, but `AnalyticsHandler.digestInput()` hashes
> the handler's `.class` bytes and throws `IllegalArgumentException` for those forms. For a lambda
> in an active category, `AnalyticsExporter.resolveDigest()` catches that, logs a warning, and
> continues with an empty `camunda.exporter.digest`, so `configure()` succeeds and the exporter's
> fingerprint silently disappears from every record. (Routing tests that never compute a digest may
> still use lambdas; see `HandlerRegistryTest`.)

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
    // Take the category from the signal's data-contract entry.
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
                // several nested classes declare ID — use the qualified form
                .setAttribute(AnalyticsAttributes.Tenant.ID, value.getTenantId())
                .setTimestamp(record.getTimestamp(), TimeUnit.MILLISECONDS));
  }
}
```

**Import style:** use explicit static imports from the nested class (e.g.
`AnalyticsAttributes.Process.BPMN_PROCESS_ID`). When the unqualified name would be ambiguous
(`Tenant.ID`, `Element.ID`, `Decision.ID` and `Form.ID` are all named `ID`), use the qualified form
`AnalyticsAttributes.Tenant.ID` directly rather than a static import.

Use `otelSdkManager.logEvent()` for discrete events (see `TenantCreatedHandler`) and
`otelSdkManager.incrementMetric()` for counters (see `DecisionInstanceEvaluatedHandler`).
`incrementMetric(metricName, unit, position, eventTimeMs, dimensions)` takes the counter's
contracted unit and a pre-built `Attributes.of(...)` as its last argument — there is no builder
callback as there is for `logEvent`. `logEvent` also has an overload taking a sampling rate; the
applied rate is the lower of that and the configured `sampling-rate`.

### What the platform sets vs what your handler sets

The fields every record carries are listed in the module README under **Common log record
attributes** and **Resource attributes**. Three things the README does not spell out:

- `logEvent()` sets `event.name`, `camunda.log.position`, `camunda.event.sequence_number` and,
  when the applied sampling rate is below 1.0, `camunda.event.sample_rate` *before* it runs your
  builder callback. Do not set them yourself: anything you set on the same key silently
  overwrites the platform's value.
- Resource attributes (including `camunda.tenant.physical_id` and `camunda.exporter.digest`) are
  set once per exporter instance. Never set them per record; the per-record tenant is the logical
  `camunda.tenant.id`.
- The record timestamp and every domain attribute are yours:
  `.setTimestamp(record.getTimestamp(), TimeUnit.MILLISECONDS)`. There is no `event.id`
  attribute; records are identified downstream by cluster, partition, log position and sequence
  number, so do not invent one.

## Step 4 — Register the handler in the catalog

Open `AnalyticsHandlerCatalog.build(...)` and add a `.register(...)` call to the `HandlerRegistry`
chain. This is the only main-source file outside `handler/` and `AnalyticsAttributes` that a new
event touches — `AnalyticsExporter` never changes:

```java
static HandlerRegistry build(
    final OtelSdkManager otelSdkManager, final Set<AnalyticsCategory> activeCategories) {
  return new HandlerRegistry(activeCategories)
      ...
      .register(
          ValueType.MY_VALUE_TYPE,
          MyIntent.MY_INTENT,
          new MyEventHandler(otelSdkManager));
}
```

`AnalyticsExporter.configure()` calls
`AnalyticsHandlerCatalog.build(otelSdkManager, config.getActiveCategories()).apply(context)`.
The `apply(context)` call installs an `AnalyticsRecordFilter`. The filter is an
over-approximation: it accepts records whose `ValueType` is in the registered set *and* whose
`Intent` is in the registered set, but those two sets are evaluated independently — a record can
pass the filter even if its exact `(ValueType, Intent)` pair has no handler. Exact routing and
no-ops happen in `HandlerRegistry.handle()`. No other change is needed for filtering.

## Step 5 — Add the pair to the catalog test

`AnalyticsHandlerCatalogTest.shouldRegisterAllExpectedHandlersWhenAllCategoriesActive` asserts the
registered set *exactly*, so a new `.register(...)` fails that test until the same
`(ValueType, Intent)` entry is added there. The failure names only the set difference, not this
step, so do it now:

```java
assertThat(registry.registrations())
    .containsExactlyInAnyOrder(
        ...
        Map.entry(ValueType.MY_VALUE_TYPE, MyIntent.MY_INTENT));
```

The per-category tests (`shouldRegisterOnlyContractualHandlersWhenOptionalCategoryDisabled` and
`shouldRegisterOnlyOptionalHandlersWhenContractualCategoryDisabled`) are not exact-set: add the
pair to the `contains(...)` list of the test for its own category and to the `doesNotContain(...)`
list of the other one.

## Step 6 — Write tests

### Handler unit test

Create `handler/MyEventHandlerTest.java`. The exporter and handler are built fresh for each test —
`UserTaskCreatedHandlerTest` builds them inline in the `@Test`, the older tests use `@BeforeEach`;
either is fine, but do not share one `InMemoryLogRecordExporter` across tests.

The five types the test needs, in full:

- `io.opentelemetry.sdk.testing.exporter.InMemoryLogRecordExporter` — captures emitted log records
- `io.opentelemetry.sdk.testing.exporter.InMemoryMetricReader` — captures metrics (only if the
  handler increments one; the log exporter above is still needed to build the manager)
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
        // every PII field — none may appear in the emitted event
        .withAssignee("john.doe@example.com")
        .withCandidateUsersList(List.of("jane.roe@example.com"))
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
          assertThat(allValues)
              .noneMatch(v -> v.contains("john.doe@example.com") || v.contains("jane.roe@example.com"));
        });
  }

  // If the handler silently skips some records, test that path too:
  @Test
  void shouldSkipUnmatchedRecords() {
    // given — a fresh exporter and handler, and a record that should be filtered out
    final var logExporter = InMemoryLogRecordExporter.create();
    final var handler = new MyEventHandler(TestOtelSdkManager.inMemory(logExporter));
    // build a value whose filtered-on field does NOT match, e.g. a different element type
    final var nonMatchingValue = ImmutableMyRecordValue.builder().withBpmnElementType(...).build();
    final var unrelatedRecord = FACTORY.generateRecord(ValueType.MY_VALUE_TYPE,
        r -> r.withRecordType(RecordType.EVENT)
              .withIntent(MyIntent.MY_INTENT)
              .withValue(nonMatchingValue));

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

**Required test cases for a log-event handler** (model: `UserTaskAssignedHandlerTest`):
- Happy path: correct attributes are emitted for a matching record
- PII sweep: set *every* PII-carrying field on the record value (assignee, candidate users and
  groups, names, variables, error messages) to recognisable values, and assert that no emitted
  attribute value *contains* any of them (`noneMatch(v -> v.contains(pii))`), and that no
  attribute derived from them (a hash, a length) exists
- Skip path (if the handler filters internally): no event emitted for a non-matching record. A
  skip guard on a field that feeds a contractual count must match the engine's own guard exactly
  (for example `isEmpty`, not `isBlank`), or the count diverges from the engine's

**Required test cases for a counter handler** (model: `DecisionInstanceEvaluatedHandlerTest`):
- One increment per source record, accumulating across calls
- The contracted unit (`metric.getUnit()`)
- The dimensions on each point are exactly the contracted set, and split per dimension value
- PII sweep over the point attributes, as above

**Testing a metric.** `TestOtelSdkManager.inMemory(logExporter)` creates a metric reader you have
no handle on, so metrics emitted through it cannot be asserted. A handler that calls
`incrementMetric()` needs its own reader:

```java
final var metricReader = InMemoryMetricReader.create();
final var handler =
    new MyEventHandler(TestOtelSdkManager.inMemoryWithMetrics(logExporter, metricReader));
// assert on metricReader.collectAllMetrics()
```

See `DecisionInstanceEvaluatedHandlerTest` for the full pattern.

### Integration wiring check

Add a test to `AnalyticsExporterTest` that feeds a record of the new type through the full
exporter (`exporter.export(record)`) and asserts the expected event name appears. The test
setup (`exporter`, `memoryExporter`, `controller`) is already provided by `@BeforeEach` for a
log-event handler. A counter handler emits no log record and the `@BeforeEach` exporter has no
reachable metric reader: build the exporter with `TestOtelSdkManager.inMemoryWithMetrics(...)` and
assert on `metricReader.collectAllMetrics()`, as `shouldIncrementDecisionInstanceEvaluatedCounter`
does. For a log-event handler:

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
>   the fields the handler reads. Unset reference fields come back `null`, but unset primitive
>   fields (`long getDecisionKey()`, `int getDecisionVersion()` and similar) come back as Java
>   defaults (`0`, `false`), so do not read a `0` key in a partial-builder test as a real value.
>
> See the existing handler tests in `handler/*HandlerTest.java` for exact usage.

## Step 7 — Update the module docs

Two files in the module document the event set, and both drift silently if skipped. A counter goes
in the README's **Pre-aggregated counters** table (unit, source record, dimensions) instead of the
event tables below.

**`zeebe/exporters/analytics-exporter/AGENTS.md`** — add a row to the **Current Event Handlers**
table (ValueType, Intent, handler class, `event.name`, extra filter). This one is easy to miss
because it duplicates part of the README's table.

**`zeebe/exporters/analytics-exporter/README.md`** — update it in lockstep with the code changes
above; it is the public mirror of the data contract that readers outside Camunda rely on:

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

Use the commands in the **Building** section of the module README, from the repository root.
What the README does not say:

- Java 21 is required; point `JAVA_HOME` at a JDK 21 first.
- On a fresh clone, run the README's `install ... -am` step before `verify`: without `-am` the
  module's dependencies are not in the local repository yet.
- Do not scope `verify` with `-Dtest`: together with `-Dsurefire.failIfNoSpecifiedTests=false`, a
  mistyped pattern that matches nothing passes as a false green.
- `verify` without `-DskipITs` runs `AnalyticsExporterOtelIT`, which needs Docker.
- Format with `./mvnw license:format spotless:apply -pl zeebe/exporters/analytics-exporter`.
  Unscoped, `spotless:apply` reformats the whole repository, and markdown formatting is configured
  on the root POM only.

All tests must pass before committing.

## Step 9 — Final review checklist

Before opening the PR, go through this checklist:

1. **No PII exposed** — double-check every attribute: no variable values, usernames, email
   addresses, author-chosen names, or any other personally identifiable data.
2. **No attributes renamed or removed** — existing constants in `AnalyticsAttributes` are
   unchanged; only new constants were added (unless the PR is an explicitly agreed contract
   migration, see the iron rule in Step 2).
3. **Attribute count is minimal** — no unnecessary dimensions; every attribute added to a
   metric has a clear analytical purpose.
4. **Handler is registered** — `.register(ValueType, Intent, handler)` call is present in
   `AnalyticsHandlerCatalog.build(...)`.
5. **Catalog test updated** — the same `(ValueType, Intent)` pair is in the
   `containsExactlyInAnyOrder` set of the all-categories test in `AnalyticsHandlerCatalogTest`, and
   its per-category test asserts it is registered (`contains`) when its category is active and
   absent (`doesNotContain`) when it is not.
6. **Names match the contract** — every new name and unit is copied from the data contract and
   pinned in `AnalyticsAttributeKeysTest`, `AnalyticsEventNamesTest` or `AnalyticsMetricNamesTest`.
7. **Tests pass** — the module's `./mvnw verify` from Step 8 is green.
8. **Module docs updated** — the new handler is a row in the **Current Event Handlers** table in
   `zeebe/exporters/analytics-exporter/AGENTS.md`; an event is listed in the README **Event
   types** table with its specific attributes, a counter in **Pre-aggregated counters**; every
   attribute name and type in the README matches the `AnalyticsAttributes` constant.

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

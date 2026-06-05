/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.exporter;

import static org.assertj.core.api.Assertions.assertThat;

import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import io.camunda.zeebe.exporter.filter.DefaultRecordFilter;
import io.camunda.zeebe.exporter.test.ExporterTestConfiguration;
import io.camunda.zeebe.exporter.test.ExporterTestContext;
import io.camunda.zeebe.exporter.test.ExporterTestController;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.intent.VariableIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.ImmutableProcessInstanceRecordValue;
import io.camunda.zeebe.protocol.record.value.ImmutableVariableRecordValue;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceRecordValue;
import io.camunda.zeebe.protocol.record.value.VariableRecordValue;
import io.camunda.zeebe.test.broker.protocol.ProtocolFactory;
import io.camunda.zeebe.test.util.testcontainers.TestSearchContainers;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.UnaryOperator;
import org.agrona.CloseHelper;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * End-to-end coverage of {@link DefaultRecordFilter} routed through a real {@link
 * ElasticsearchExporter} against a real Elasticsearch container.
 *
 * <p>The Zeebe broker invokes the filter chain in {@code ExporterContainer.acceptRecord(record)}
 * before calling {@code exporter.export(record)} (see {@code ExporterContainer}). These tests
 * mirror that contract: each record is first passed through {@link
 * DefaultRecordFilter#acceptRecord(Record)} and only exported when the filter accepts it. This is
 * the same boundary the broker enforces, so the assertions exercise the same end-to-end behavior
 * that downstream consumers (Operate / Optimize / index importers) observe in production.
 *
 * <p>Each scenario maps to one of the six scenarios in epic camunda/product-hub#3288: variable
 * name, variable type, variable scope, optimize mode, BPMN process id, and the combined chain.
 */
@Testcontainers
final class ExporterFilterIT {

  /**
   * Pin the broker version on every test record to a value comfortably greater than every {@code
   * MIN_BROKER_VERSION} declared by the filters in {@code zeebe/exporter-filter/}. This isolates
   * the tests from the version-gating logic in {@link
   * io.camunda.zeebe.exporter.filter.ExportRecordFilterChain} — we want to exercise the filter
   * behavior itself here, not the gating. The current value from {@code VersionUtil} can be a
   * pre-release such as {@code 8.10.0-snapshot}, which per SemVer is <em>less</em> than {@code
   * 8.10.0}, which in turn would cause filters with {@code MIN_BROKER_VERSION = 8.10.0} (e.g.
   * {@link io.camunda.zeebe.exporter.filter.VariableNameScopeFilter}) to be silently skipped.
   */
  private static final String BROKER_VERSION = "99.0.0";

  @Container
  private static final ElasticsearchContainer CONTAINER =
      TestSearchContainers.createDefaultElasticsearchContainer()
          .withEnv("action.destructive_requires_name", "false");

  private static ElasticsearchExporterConfiguration config;
  private static ElasticsearchExporter exporter;
  private static RecordIndexRouter indexRouter;
  private static TestClient testClient;
  private static ExporterTestController controller;
  private static ExporterTestContext exporterTestContext;

  private final ProtocolFactory factory =
      new ProtocolFactory(b -> b.withAuthorizations(Map.of()))
          .registerRandomizer(field -> "agent".equals(field.getName()), random -> null);

  @BeforeAll
  static void beforeAll() {
    config = new ElasticsearchExporterConfiguration();
    config.url = CONTAINER.getHttpHostAddress();
    config.setIncludeEnabledRecords(true);
    config.index.setNumberOfShards(1);
    config.index.setNumberOfReplicas(1);
    config.index.createTemplate = true;
    // force the exporter to flush after every accepted record so assertions don't race the bulk
    // delay window
    config.bulk.size = 1;

    // enable all value types we exercise; per-type indexing is independent from the record-level
    // filter chain we're testing here
    TestSupport.provideValueTypes()
        .forEach(valueType -> TestSupport.setIndexingForValueType(config.index, valueType, true));

    indexRouter = new RecordIndexRouter(config.index);
    testClient = new TestClient(config, indexRouter);
  }

  @BeforeEach
  void beforeEach() {
    // Reset all filter state up-front so each test starts from a known-clean configuration. Doing
    // this in @BeforeEach (rather than try/finally inside each test) ensures the reset still runs
    // even if a previous test failed mid-assertion.
    resetAllFilters();

    // ProtocolFactory is seeded deterministically per test, so without a fresh ES state the
    // (partitionId, position) -> document-id collisions between tests would let one test see
    // documents written by another. Delete all our indices between tests to keep the assertions
    // honest end-to-end.
    try {
      testClient.deleteIndices();
    } catch (final Exception ignored) {
      // best-effort; first test won't have any indices yet
    }

    // re-instantiate the exporter so each test sees a clean configuration; this matches the
    // pattern used by ElasticsearchExporterIT and ensures filter changes set on `config.index`
    // take effect for the test that follows
    controller = new ExporterTestController();
    exporter = new ElasticsearchExporter();
    exporterTestContext =
        new ExporterTestContext()
            .setConfiguration(new ExporterTestConfiguration<>("elastic", config));
    exporter.configure(exporterTestContext);
    exporter.open(controller);
  }

  @AfterEach
  void afterEach() {
    // Close the exporter between tests so its scheduled flush task is cancelled and its bulk
    // client released, instead of leaking until @AfterAll.
    quietCloseExporter();
  }

  @AfterAll
  static void afterAll() {
    try {
      testClient.deleteIndices();
      testClient.deleteIndexTemplates();
      testClient.deleteComponentTemplates();
    } catch (final Exception ignored) {
      // best-effort cleanup; container is also being torn down
    }
    quietCloseExporter();
    CloseHelper.quietCloseAll(testClient);
  }

  private static void quietCloseExporter() {
    // Mirrors `CloseHelper.quietCloseAll` for `Exporter`, which is not `AutoCloseable`.
    if (exporter != null) {
      try {
        exporter.close();
      } catch (final Exception ignored) {
        // best-effort; tests don't care about close-time failures
      }
      exporter = null;
    }
  }

  // ---------------------------------------------------------------------------
  // Scenario 1 — Variable name filter (exclusion via startsWith)
  // ---------------------------------------------------------------------------
  @Test
  void shouldFilterVariablesByNameWhenExclusionStartWithRuleIsConfigured() {
    // given: exclude variables whose name starts with "pii_"
    config.index.setVariableNameExclusionStartWith(List.of("pii_"));

    final var pii = variableRecord("pii_ssn", "\"123-45-6789\"");
    final var orderId = variableRecord("orderId", "\"order-1\"");
    final var amount = variableRecord("amount", "42");

    // when
    exportFiltered(pii);
    exportFiltered(orderId);
    exportFiltered(amount);

    // then
    assertNotIndexed(pii);
    assertIndexed(orderId);
    assertIndexed(amount);
  }

  // ---------------------------------------------------------------------------
  // Scenario 2 — Variable type filter (inclusion = STRING, NUMBER)
  // ---------------------------------------------------------------------------
  @Test
  void shouldFilterVariablesByTypeWhenInclusionIsConfigured() {
    // given: only STRING and NUMBER variables are exported
    config.index.setVariableValueTypeInclusion(List.of("STRING", "NUMBER"));

    final var stringVar = variableRecord("s", "\"hello\"");
    final var numberVar = variableRecord("n", "42");
    final var booleanVar = variableRecord("b", "true");
    final var objectVar = variableRecord("o", "{\"k\":1}");
    final var nullVar = variableRecord("z", "null");

    // when
    exportFiltered(stringVar);
    exportFiltered(numberVar);
    exportFiltered(booleanVar);
    exportFiltered(objectVar);
    exportFiltered(nullVar);

    // then
    assertIndexed(stringVar);
    assertIndexed(numberVar);
    assertNotIndexed(booleanVar);
    assertNotIndexed(objectVar);
    assertNotIndexed(nullVar);
  }

  // ---------------------------------------------------------------------------
  // Scenario 3 — Variable name scope filter (root vs. local)
  // ---------------------------------------------------------------------------
  @Test
  void shouldFilterVariablesByNameWithinTheirScope() {
    // given: at the root scope, exclude variables named "secret"; at the local scope, exclude
    // variables named "debug"
    config.index.setRootVariableNameExclusionExact(List.of("secret"));
    config.index.setLocalVariableNameExclusionExact(List.of("debug"));

    // process-level (root) variables: scopeKey == processInstanceKey
    final var rootSecret = scopedVariable("secret", "\"shhh\"", /* local */ false);
    final var rootKeep = scopedVariable("keep-root", "\"yes\"", false);

    // sub-element (local) variables: scopeKey != processInstanceKey
    final var localDebug = scopedVariable("debug", "true", /* local */ true);
    final var localKeep = scopedVariable("keep-local", "\"yes\"", true);

    // when
    exportFiltered(rootSecret);
    exportFiltered(rootKeep);
    exportFiltered(localDebug);
    exportFiltered(localKeep);

    // then: scope rules apply only within their scope, the other scope is untouched
    assertNotIndexed(rootSecret);
    assertIndexed(rootKeep);
    assertNotIndexed(localDebug);
    assertIndexed(localKeep);
  }

  // ---------------------------------------------------------------------------
  // Scenario 4 — Optimize mode filter
  // ---------------------------------------------------------------------------
  @Test
  void shouldRestrictRecordsToOptimizeRelevantSubsetWhenOptimizeModeEnabled() {
    // given
    config.index.setOptimizeModeEnabled(true);
    // VariableIntent.CREATED is in the Optimize-allowed set
    final var variableCreated =
        variableRecord(
            "v",
            "\"x\"",
            r -> r.withRecordType(RecordType.EVENT).withIntent(VariableIntent.CREATED));

    // VariableIntent.MIGRATED is not in the Optimize-allowed set
    final var variableMigrated =
        variableRecord(
            "v",
            "\"x\"",
            r -> r.withRecordType(RecordType.EVENT).withIntent(VariableIntent.MIGRATED));

    // ProcessInstance ELEMENT_COMPLETED on a non-excluded element type is allowed
    final var piCompleted =
        processInstanceRecord(
            "any-process", ProcessInstanceIntent.ELEMENT_COMPLETED, BpmnElementType.PROCESS);

    // ProcessInstance ELEMENT_COMPLETED on SEQUENCE_FLOW is explicitly excluded
    final var piSequenceFlow =
        processInstanceRecord(
            "any-process", ProcessInstanceIntent.ELEMENT_COMPLETED, BpmnElementType.SEQUENCE_FLOW);

    // when
    exportFiltered(variableCreated);
    exportFiltered(variableMigrated);
    exportFiltered(piCompleted);
    exportFiltered(piSequenceFlow);

    // then
    assertIndexed(variableCreated);
    assertNotIndexed(variableMigrated);
    assertIndexed(piCompleted);
    assertNotIndexed(piSequenceFlow);

    // and: disabling optimize mode again restores full export for the same records
    config.index.setOptimizeModeEnabled(false);
    // re-configure so the filter chain is rebuilt
    exporter.configure(exporterTestContext);
    exporter.open(controller);

    final var variableMigratedAfter =
        variableRecord(
            "v",
            "\"x\"",
            r -> r.withRecordType(RecordType.EVENT).withIntent(VariableIntent.MIGRATED));
    exportFiltered(variableMigratedAfter);
    assertIndexed(variableMigratedAfter);
  }

  // ---------------------------------------------------------------------------
  // Scenario 5 — BPMN process id filter (inclusion allowlist)
  // ---------------------------------------------------------------------------
  @Test
  void shouldFilterRecordsByBpmnProcessIdWhenInclusionIsConfigured() {
    // given: only "process-A" instances should be exported
    config.index.setBpmnProcessIdInclusion(List.of("process-A"));
    final var procA =
        processInstanceRecord(
            "process-A", ProcessInstanceIntent.ELEMENT_ACTIVATED, BpmnElementType.PROCESS);
    final var procB =
        processInstanceRecord(
            "process-B", ProcessInstanceIntent.ELEMENT_ACTIVATED, BpmnElementType.PROCESS);
    final var varInProcA = variableRecord("anything", "1", "process-A");
    final var varInProcB = variableRecord("anything", "1", "process-B");

    // when
    exportFiltered(procA);
    exportFiltered(procB);
    exportFiltered(varInProcA);
    exportFiltered(varInProcB);

    // then
    assertIndexed(procA);
    assertNotIndexed(procB);
    assertIndexed(varInProcA);
    assertNotIndexed(varInProcB);
  }

  // ---------------------------------------------------------------------------
  // Scenario 6 — Combined filter chain (BPMN process id + variable name + optimize mode)
  // ---------------------------------------------------------------------------
  @Test
  void shouldEnforceUnionOfConstraintsWhenMultipleFiltersAreConfigured() {
    // given: allow only "process-A", exclude variables starting with "pii_", and enable optimize
    // mode (which restricts to CREATED/UPDATED for VARIABLE records)
    config.index.setBpmnProcessIdInclusion(List.of("process-A"));
    config.index.setVariableNameExclusionStartWith(List.of("pii_"));
    config.index.setOptimizeModeEnabled(true);
    // passes all filters
    final var ok =
        variableRecord(
            "orderId",
            "\"order-1\"",
            "process-A",
            r -> r.withRecordType(RecordType.EVENT).withIntent(VariableIntent.CREATED));

    // wrong process id
    final var wrongProcess =
        variableRecord(
            "orderId",
            "\"order-1\"",
            "process-B",
            r -> r.withRecordType(RecordType.EVENT).withIntent(VariableIntent.CREATED));

    // excluded variable name
    final var excludedName =
        variableRecord(
            "pii_ssn",
            "\"123-45-6789\"",
            "process-A",
            r -> r.withRecordType(RecordType.EVENT).withIntent(VariableIntent.CREATED));

    // excluded by optimize mode (MIGRATED intent not in the allowed set)
    final var optimizeRejected =
        variableRecord(
            "orderId",
            "\"order-1\"",
            "process-A",
            r -> r.withRecordType(RecordType.EVENT).withIntent(VariableIntent.MIGRATED));

    // when
    exportFiltered(ok);
    exportFiltered(wrongProcess);
    exportFiltered(excludedName);
    exportFiltered(optimizeRejected);

    // then: only the record that passes every filter is exported
    assertIndexed(ok);
    assertNotIndexed(wrongProcess);
    assertNotIndexed(excludedName);
    assertNotIndexed(optimizeRejected);
  }

  // ---------------------------------------------------------------------------
  // Scenario 7 — Remaining variable name matcher kinds (parameterized)
  // ---------------------------------------------------------------------------
  @ParameterizedTest(name = "{0}")
  @EnumSource(VariableNameMatcher.class)
  void shouldApplyVariableNameMatcherEndToEnd(final VariableNameMatcher matcher) {
    // given
    matcher.apply(config, List.of(matcher.pattern));

    final var matching = variableRecord(matcher.matchingName, "\"x\"");
    final var nonMatching = variableRecord(matcher.nonMatchingName, "\"x\"");

    // when
    exportFiltered(matching);
    exportFiltered(nonMatching);

    // then
    if (matcher.matchingShouldBeExported) {
      assertIndexed(matching);
      assertNotIndexed(nonMatching);
    } else {
      assertNotIndexed(matching);
      assertIndexed(nonMatching);
    }
  }

  // ---------------------------------------------------------------------------
  // Scenario 8 — Variable value type filter (exclusion)
  // ---------------------------------------------------------------------------
  @Test
  void shouldFilterVariablesByTypeWhenExclusionIsConfigured() {
    // given
    config.index.setVariableValueTypeExclusion(List.of("BOOLEAN", "OBJECT"));

    final var stringVar = variableRecord("s", "\"hello\"");
    final var numberVar = variableRecord("n", "42");
    final var booleanVar = variableRecord("b", "true");
    final var objectVar = variableRecord("o", "{\"k\":1}");
    final var nullVar = variableRecord("z", "null");

    // when
    exportFiltered(stringVar);
    exportFiltered(numberVar);
    exportFiltered(booleanVar);
    exportFiltered(objectVar);
    exportFiltered(nullVar);

    // then
    assertIndexed(stringVar);
    assertIndexed(numberVar);
    assertNotIndexed(booleanVar);
    assertNotIndexed(objectVar);
    assertIndexed(nullVar);
  }

  // ---------------------------------------------------------------------------
  // Scenario 9 — BPMN process id filter (exclusion blocklist)
  // ---------------------------------------------------------------------------
  @Test
  void shouldFilterRecordsByBpmnProcessIdWhenExclusionIsConfigured() {
    // given
    config.index.setBpmnProcessIdExclusion(List.of("process-blocked"));

    final var procBlocked =
        processInstanceRecord(
            "process-blocked", ProcessInstanceIntent.ELEMENT_ACTIVATED, BpmnElementType.PROCESS);
    final var procAllowed =
        processInstanceRecord(
            "process-allowed", ProcessInstanceIntent.ELEMENT_ACTIVATED, BpmnElementType.PROCESS);
    final var varInBlocked = variableRecord("anything", "1", "process-blocked");
    final var varInAllowed = variableRecord("anything", "1", "process-allowed");

    // when
    exportFiltered(procBlocked);
    exportFiltered(procAllowed);
    exportFiltered(varInBlocked);
    exportFiltered(varInAllowed);

    // then
    assertNotIndexed(procBlocked);
    assertIndexed(procAllowed);
    assertNotIndexed(varInBlocked);
    assertIndexed(varInAllowed);
  }

  // ---------------------------------------------------------------------------
  // Helpers
  // ---------------------------------------------------------------------------

  /**
   * Enumerates every variable-name matcher kind not already covered by Scenarios 1 and 3 so that
   * Scenario 7 can iterate over them with one record pair each.
   */
  private enum VariableNameMatcher {
    EXCLUSION_END_WITH(
        "_secret",
        "api_secret",
        "orderId",
        false,
        (cfg, p) -> cfg.index.setVariableNameExclusionEndWith(p)),
    EXCLUSION_EXACT(
        "password",
        "password",
        "orderId",
        false,
        (cfg, p) -> cfg.index.setVariableNameExclusionExact(p)),
    INCLUSION_START_WITH(
        "order_",
        "order_id",
        "unrelated",
        true,
        (cfg, p) -> cfg.index.setVariableNameInclusionStartWith(p)),
    INCLUSION_END_WITH(
        "_id",
        "order_id",
        "unrelated",
        true,
        (cfg, p) -> cfg.index.setVariableNameInclusionEndWith(p)),
    INCLUSION_EXACT(
        "orderId",
        "orderId",
        "unrelated",
        true,
        (cfg, p) -> cfg.index.setVariableNameInclusionExact(p));

    private final String pattern;
    private final String matchingName;
    private final String nonMatchingName;
    private final boolean matchingShouldBeExported;
    private final BiConsumer<ElasticsearchExporterConfiguration, List<String>> setter;

    VariableNameMatcher(
        final String pattern,
        final String matchingName,
        final String nonMatchingName,
        final boolean matchingShouldBeExported,
        final BiConsumer<ElasticsearchExporterConfiguration, List<String>> setter) {
      this.pattern = pattern;
      this.matchingName = matchingName;
      this.nonMatchingName = nonMatchingName;
      this.matchingShouldBeExported = matchingShouldBeExported;
      this.setter = setter;
    }

    void apply(final ElasticsearchExporterConfiguration cfg, final List<String> patterns) {
      setter.accept(cfg, patterns);
    }
  }

  /**
   * Reproduces the broker's per-record contract: {@code ExporterContainer.acceptRecord} consults
   * the filter chain wired in {@code exporter.configure(...)} and only invokes {@code
   * exporter.export} when the chain accepts the record. We build a fresh {@link
   * DefaultRecordFilter} from the same {@link ElasticsearchExporterConfiguration} so the test
   * exercises exactly the production filter behavior.
   */
  private void exportFiltered(final Record<?> record) {
    final var filter = new DefaultRecordFilter(config);
    if (filter.acceptType(record.getRecordType())
        && filter.acceptValue(record.getValueType())
        && filter.acceptRecord(record)) {
      exporter.export(record);
    }
  }

  private void assertIndexed(final Record<?> record) {
    final var response = testClient.getExportedDocumentFor(record);
    assertThat(response.found())
        .as(
            "record id=%s value=%s should have been exported to %s",
            indexRouter.idFor(record), record.getValueType(), indexRouter.indexFor(record))
        .isTrue();
  }

  private void assertNotIndexed(final Record<?> record) {
    try {
      final var response = testClient.getExportedDocumentFor(record);
      assertThat(response.found())
          .as(
              "record id=%s value=%s should NOT have been exported, but found in %s",
              indexRouter.idFor(record), record.getValueType(), indexRouter.indexFor(record))
          .isFalse();
    } catch (final ElasticsearchException e) {
      // A 404 from ES (the target index doesn't exist yet) is also acceptable evidence that
      // nothing was exported for this record. Anything else (shard routing, cluster health,
      // auth, ...) must surface as a real test error rather than being silently swallowed and
      // converted into a confusing assertion message.
      if (e.status() != 404) {
        throw e;
      }
    }
  }

  private void resetAllFilters() {
    config.index.setVariableNameExclusionStartWith(List.of());
    config.index.setVariableNameExclusionEndWith(List.of());
    config.index.setVariableNameExclusionExact(List.of());
    config.index.setVariableNameInclusionStartWith(List.of());
    config.index.setVariableNameInclusionEndWith(List.of());
    config.index.setVariableNameInclusionExact(List.of());
    config.index.setRootVariableNameExclusionExact(List.of());
    config.index.setLocalVariableNameExclusionExact(List.of());
    config.index.setVariableValueTypeInclusion(List.of());
    config.index.setVariableValueTypeExclusion(List.of());
    config.index.setOptimizeModeEnabled(false);
    config.index.setBpmnProcessIdInclusion(List.of());
    config.index.setBpmnProcessIdExclusion(List.of());
  }

  // --- record builders -------------------------------------------------------

  private Record<VariableRecordValue> variableRecord(final String name, final String jsonValue) {
    return variableRecord(name, jsonValue, "any-process", r -> r);
  }

  private Record<VariableRecordValue> variableRecord(
      final String name, final String jsonValue, final String bpmnProcessId) {
    return variableRecord(name, jsonValue, bpmnProcessId, r -> r);
  }

  private Record<VariableRecordValue> variableRecord(
      final String name,
      final String jsonValue,
      final UnaryOperator<
              io.camunda.zeebe.protocol.record.ImmutableRecord.Builder<VariableRecordValue>>
          recordModifier) {
    return variableRecord(name, jsonValue, "any-process", recordModifier);
  }

  /**
   * Builds a fully-shaped variable record with deterministic name/value/process id. The {@code
   * scopeKey} is set equal to {@code processInstanceKey}, so by default the record is a root
   * variable (see {@link io.camunda.zeebe.exporter.filter.VariableScope}).
   */
  private Record<VariableRecordValue> variableRecord(
      final String name,
      final String jsonValue,
      final String bpmnProcessId,
      final UnaryOperator<
              io.camunda.zeebe.protocol.record.ImmutableRecord.Builder<VariableRecordValue>>
          recordModifier) {
    final var generated = factory.<VariableRecordValue>generateRecord(ValueType.VARIABLE);
    final long processInstanceKey = generated.getValue().getProcessInstanceKey();
    final var value =
        ImmutableVariableRecordValue.builder()
            .from(generated.getValue())
            .withName(name)
            .withValue(jsonValue)
            .withBpmnProcessId(bpmnProcessId)
            .withScopeKey(processInstanceKey) // root variable by default
            .build();

    return factory.generateRecord(
        ValueType.VARIABLE,
        r ->
            recordModifier.apply(
                r.withBrokerVersion(BROKER_VERSION)
                    .withRecordType(RecordType.EVENT)
                    .withIntent(VariableIntent.CREATED)
                    .withValue(value)));
  }

  /** Builds a variable record whose scope is local (scopeKey != processInstanceKey) or root. */
  private Record<VariableRecordValue> scopedVariable(
      final String name, final String jsonValue, final boolean local) {
    final var generated = factory.<VariableRecordValue>generateRecord(ValueType.VARIABLE);
    // Use a fixed process-instance key so the local/root pair never collides with `Long.MAX_VALUE`
    // and the +1 trick has a clear, bounded meaning to the next reader.
    final long processInstanceKey = 1L;
    final long scopeKey = local ? processInstanceKey + 1 : processInstanceKey;
    final var value =
        ImmutableVariableRecordValue.builder()
            .from(generated.getValue())
            .withName(name)
            .withValue(jsonValue)
            .withBpmnProcessId("any-process")
            .withProcessInstanceKey(processInstanceKey)
            .withScopeKey(scopeKey)
            .build();

    return factory.generateRecord(
        ValueType.VARIABLE,
        r ->
            r.withBrokerVersion(BROKER_VERSION)
                .withRecordType(RecordType.EVENT)
                .withIntent(VariableIntent.CREATED)
                .withValue(value));
  }

  private Record<ProcessInstanceRecordValue> processInstanceRecord(
      final String bpmnProcessId,
      final ProcessInstanceIntent intent,
      final BpmnElementType elementType) {
    final var generated =
        factory.<ProcessInstanceRecordValue>generateRecord(ValueType.PROCESS_INSTANCE);
    final var value =
        ImmutableProcessInstanceRecordValue.builder()
            .from(generated.getValue())
            .withBpmnProcessId(bpmnProcessId)
            .withBpmnElementType(elementType)
            .build();

    return factory.generateRecord(
        ValueType.PROCESS_INSTANCE,
        r ->
            r.withBrokerVersion(BROKER_VERSION)
                .withRecordType(RecordType.EVENT)
                .withIntent(intent)
                .withValue(value));
  }
}

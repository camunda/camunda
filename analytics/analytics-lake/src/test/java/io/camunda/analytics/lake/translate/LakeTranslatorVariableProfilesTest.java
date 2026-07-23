/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.lake.translate;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.analytics.lake.metrics.CompiledEntityMetrics;
import io.camunda.analytics.lake.metrics.EntityMetrics;
import io.camunda.analytics.lake.metrics.PollFedRider;
import io.camunda.analytics.lake.sink.BatchEncoder;
import io.camunda.analytics.lake.sink.ColumnType;
import io.camunda.analytics.lake.sink.DataFileResult;
import io.camunda.analytics.lake.sink.RowAppender;
import io.camunda.analytics.lake.sink.SortedRun;
import io.camunda.analytics.lake.sink.TableSchema;
import io.camunda.analytics.lake.sink.algebra.Algebras;
import io.camunda.analytics.lake.state.TranslatorState;
import io.camunda.eventbridge.zeebe.connector.ZeebeRecord;
import io.camunda.zeebe.protocol.record.ImmutableRecord;
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
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.iceberg.Metrics;
import org.junit.jupiter.api.Test;

/**
 * {@link LakeTranslator}'s variable-profile fold (see its class javadoc's "Variable profiling"
 * section): every completed instance's final root variables classified and folded into {@link
 * PollFedRider}, exactly at the same hook point (after {@code endRow()}, success path only) the
 * variant dictionary emission already uses.
 */
class LakeTranslatorVariableProfilesTest {

  private static final String TOPIC = "test-topic";
  private static final String PROCESS_ID = "profiles-test-process";
  private static final int VERSION = 1;
  private static final String TENANT_ID = "<default>";
  private static final long PROCESS_DEFINITION_KEY = 7L;

  @Test
  void shouldClassifyAndFoldEveryFinalVariableByType() {
    // given a translator wired with a variable-profiles rider, one instance with a variable of
    // each classified type
    final RecordingEncoderFactory encoders = new RecordingEncoderFactory();
    final PollFedRider profilesRider = new PollFedRider(profileMetrics(), encoders, 128);
    final TranslatorState state = new InMemoryOnlyState();
    final LakeTranslator translator =
        new LakeTranslator(
            state, new NoOpRowAppender(), new NoOpRowAppender(), null, null, null, profilesRider);

    translator.onRecord(activateRoot(1L, 1_700_000_000_000L, 10L));
    translator.onRecord(variable(1L, "amount", "42.5", 1_700_000_000_010L, 11L));
    translator.onRecord(variable(1L, "label", "\"foo\"", 1_700_000_000_020L, 12L));
    translator.onRecord(variable(1L, "flag", "true", 1_700_000_000_030L, 13L));
    translator.onRecord(variable(1L, "note", "null", 1_700_000_000_040L, 14L));
    translator.onRecord(variable(1L, "meta", "{\"a\":1}", 1_700_000_000_050L, 15L));
    translator.onRecord(completeRoot(1L, 1_700_000_000_100L, 16L));
    profilesRider.onPollBoundary();
    final Map<String, List<DataFileResult>> derived = profilesRider.onWindowClose();

    // then: one _metrics row per variable name, correctly type-classified
    assertThat(derived).containsKey("variable_profiles_test_metrics");
    final List<Map<String, Object>> rows = encoders.rows("variable_profiles_test_metrics");
    assertThat(rows).hasSize(5);

    assertRow(
        rows,
        "amount",
        row -> {
          assertThat(row).containsEntry("cnt", 1L).containsEntry("type_number_cnt", 1L);
          assertThat(row)
              .containsEntry("type_string_cnt", 0L)
              .containsEntry("type_boolean_cnt", 0L)
              .containsEntry("type_null_cnt", 0L)
              .containsEntry("type_object_or_array_cnt", 0L);
          assertThat(row)
              .containsEntry("value_cnt", 1L)
              .containsEntry("value_sum", 42.5)
              .containsEntry("value_min", 42.5)
              .containsEntry("value_max", 42.5)
              .containsEntry("value_nonfinite_cnt", 0L);
        });
    assertRow(
        rows,
        "label",
        row -> {
          assertThat(row).containsEntry("cnt", 1L).containsEntry("type_string_cnt", 1L);
          // the measure was never staged for this group -- honest defaults, not dropped
          assertThat(row)
              .containsEntry("value_cnt", 0L)
              .containsEntry("value_sum", 0.0)
              .containsEntry("value_nonfinite_cnt", 0L);
          assertThat(row.get("value_min")).isNull();
          assertThat(row.get("value_max")).isNull();
        });
    assertRow(rows, "flag", row -> assertThat(row).containsEntry("type_boolean_cnt", 1L));
    assertRow(rows, "note", row -> assertThat(row).containsEntry("type_null_cnt", 1L));
    assertRow(rows, "meta", row -> assertThat(row).containsEntry("type_object_or_array_cnt", 1L));

    // and: the numeric variable's histogram drains one (or more) tall rows, the non-numeric
    // variables drain none at all
    final List<Map<String, Object>> histRows = encoders.rows("variable_profiles_test_hist");
    assertThat(histRows).isNotEmpty();
    assertThat(histRows)
        .allSatisfy(
            row -> {
              assertThat(row.get("var_name")).isEqualTo("amount");
              assertThat(row.get("measure")).isEqualTo("value");
              assertThat(row.get("scheme")).isEqualTo("dexp2ll-3");
            });
  }

  @Test
  void shouldAccumulateAcrossInstancesInTheSameGroup() {
    // given two instances, each completing with the same variable name
    final RecordingEncoderFactory encoders = new RecordingEncoderFactory();
    final PollFedRider profilesRider = new PollFedRider(profileMetrics(), encoders, 128);
    final TranslatorState state = new InMemoryOnlyState();
    final LakeTranslator translator =
        new LakeTranslator(
            state, new NoOpRowAppender(), new NoOpRowAppender(), null, null, null, profilesRider);

    translator.onRecord(activateRoot(1L, 1_700_000_000_000L, 10L));
    translator.onRecord(variable(1L, "amount", "10", 1_700_000_000_010L, 11L));
    translator.onRecord(completeRoot(1L, 1_700_000_000_020L, 12L));
    translator.onRecord(activateRoot(2L, 1_700_000_000_030L, 13L));
    translator.onRecord(variable(2L, "amount", "30", 1_700_000_000_040L, 14L));
    translator.onRecord(completeRoot(2L, 1_700_000_000_050L, 15L));
    profilesRider.onPollBoundary();
    profilesRider.onWindowClose();

    // then: one group, folded twice
    final List<Map<String, Object>> rows = encoders.rows("variable_profiles_test_metrics");
    assertThat(rows).hasSize(1);
    assertThat(rows.get(0))
        .containsEntry("cnt", 2L)
        .containsEntry("value_cnt", 2L)
        .containsEntry("value_sum", 40.0)
        .containsEntry("value_min", 10.0)
        .containsEntry("value_max", 30.0);
  }

  @Test
  void shouldRootScopeAndLastValueRulesAlsoApplyToProfiling() {
    // given a variable updated twice (last value wins is already LakeTranslator's own rule for
    // vars_json; this asserts the profile fold reads the exact same final state)
    final RecordingEncoderFactory encoders = new RecordingEncoderFactory();
    final PollFedRider profilesRider = new PollFedRider(profileMetrics(), encoders, 128);
    final TranslatorState state = new InMemoryOnlyState();
    final LakeTranslator translator =
        new LakeTranslator(
            state, new NoOpRowAppender(), new NoOpRowAppender(), null, null, null, profilesRider);

    translator.onRecord(activateRoot(1L, 1_700_000_000_000L, 10L));
    translator.onRecord(variable(1L, "amount", "10", 1_700_000_000_010L, 11L));
    translator.onRecord(variable(1L, "amount", "99", 1_700_000_000_020L, 12L)); // overwrite
    translator.onRecord(completeRoot(1L, 1_700_000_000_030L, 13L));
    profilesRider.onPollBoundary();
    profilesRider.onWindowClose();

    // then: only the final value (99) was folded, not both
    final List<Map<String, Object>> rows = encoders.rows("variable_profiles_test_metrics");
    assertThat(rows).hasSize(1);
    assertThat(rows.get(0)).containsEntry("value_cnt", 1L).containsEntry("value_sum", 99.0);
  }

  @Test
  void shouldTreatANonFiniteNumericTokenAsNonfiniteNotAsAValue() {
    // given a variable value that parses to +Infinity (a real path -- see class javadoc)
    final RecordingEncoderFactory encoders = new RecordingEncoderFactory();
    final PollFedRider profilesRider = new PollFedRider(profileMetrics(), encoders, 128);
    final TranslatorState state = new InMemoryOnlyState();
    final LakeTranslator translator =
        new LakeTranslator(
            state, new NoOpRowAppender(), new NoOpRowAppender(), null, null, null, profilesRider);

    translator.onRecord(activateRoot(1L, 1_700_000_000_000L, 10L));
    translator.onRecord(variable(1L, "amount", "1e999", 1_700_000_000_010L, 11L));
    translator.onRecord(completeRoot(1L, 1_700_000_000_020L, 12L));
    profilesRider.onPollBoundary();
    profilesRider.onWindowClose();

    // then: classified as a number, but folded as non-finite, not into sum/min/max
    final List<Map<String, Object>> rows = encoders.rows("variable_profiles_test_metrics");
    assertThat(rows).hasSize(1);
    assertThat(rows.get(0))
        .containsEntry("type_number_cnt", 1L)
        .containsEntry("value_cnt", 0L)
        .containsEntry("value_nonfinite_cnt", 1L);
    assertThat(rows.get(0).get("value_min")).isNull();
    assertThat(rows.get(0).get("value_max")).isNull();
    // and: no histogram row for a group with no finite value to bin
    assertThat(encoders.rows("variable_profiles_test_hist")).isEmpty();
  }

  @Test
  void shouldNeverStoreAStringVariablesOwnValue() {
    // given a string variable whose content must never reach the rider -- only its classification
    final RecordingEncoderFactory encoders = new RecordingEncoderFactory();
    final PollFedRider profilesRider = new PollFedRider(profileMetrics(), encoders, 128);
    final TranslatorState state = new InMemoryOnlyState();
    final LakeTranslator translator =
        new LakeTranslator(
            state, new NoOpRowAppender(), new NoOpRowAppender(), null, null, null, profilesRider);

    translator.onRecord(activateRoot(1L, 1_700_000_000_000L, 10L));
    translator.onRecord(
        variable(1L, "secret", "\"super-sensitive-value\"", 1_700_000_000_010L, 11L));
    translator.onRecord(completeRoot(1L, 1_700_000_000_020L, 12L));
    profilesRider.onPollBoundary();
    profilesRider.onWindowClose();

    // then: no column anywhere on the generated schemas carries a raw string value
    final List<Map<String, Object>> rows = encoders.rows("variable_profiles_test_metrics");
    assertThat(rows).hasSize(1);
    assertThat(rows.get(0).values()).noneMatch(v -> "super-sensitive-value".equals(v));
  }

  // ---- declaration -----------------------------------------------------------------------------

  private static CompiledEntityMetrics profileMetrics() {
    final TableSchema schema =
        new TableSchema(
            "variable_profiles_test",
            List.of(
                new TableSchema.Column("process_id", ColumnType.STRING_DICT, 1, false, -1, false),
                new TableSchema.Column("var_name", ColumnType.STRING_DICT, 2, false, -1, false),
                new TableSchema.Column(
                    "completed_at",
                    ColumnType.LONG,
                    3,
                    false,
                    -1,
                    true,
                    TableSchema.LogicalType.TIMESTAMPTZ),
                new TableSchema.Column("value", ColumnType.DOUBLE, 4, true, -1, false)));
    return EntityMetrics.declare("variable_profiles_test", schema)
        .dims("process_id", "var_name")
        .window(Duration.ofMinutes(1), "completed_at")
        .count()
        .counter("type_number_cnt")
        .counter("type_string_cnt")
        .counter("type_boolean_cnt")
        .counter("type_null_cnt")
        .counter("type_object_or_array_cnt")
        .measure("value", Algebras.doubleScalarStats(), Algebras.signedDoubleExpHistogram(3))
        .build();
  }

  private static void assertRow(
      final List<Map<String, Object>> rows,
      final String varName,
      final java.util.function.Consumer<Map<String, Object>> assertion) {
    final Map<String, Object> row =
        rows.stream().filter(r -> varName.equals(r.get("var_name"))).findFirst().orElseThrow();
    assertion.accept(row);
  }

  // ---- record construction ---------------------------------------------------------------------

  private static ZeebeRecord activateRoot(
      final long instanceKey, final long timestamp, final long position) {
    final ProcessInstanceRecordValue value =
        ImmutableProcessInstanceRecordValue.builder()
            .withBpmnProcessId(PROCESS_ID)
            .withVersion(VERSION)
            .withProcessInstanceKey(instanceKey)
            .withProcessDefinitionKey(PROCESS_DEFINITION_KEY)
            .withTenantId(TENANT_ID)
            .withElementId("")
            .withBpmnElementType(BpmnElementType.PROCESS)
            .build();
    return processInstanceRecord(
        instanceKey, timestamp, ProcessInstanceIntent.ELEMENT_ACTIVATED, value, position);
  }

  private static ZeebeRecord completeRoot(
      final long instanceKey, final long timestamp, final long position) {
    final ProcessInstanceRecordValue value =
        ImmutableProcessInstanceRecordValue.builder()
            .withBpmnProcessId(PROCESS_ID)
            .withVersion(VERSION)
            .withProcessInstanceKey(instanceKey)
            .withProcessDefinitionKey(PROCESS_DEFINITION_KEY)
            .withTenantId(TENANT_ID)
            .withElementId("")
            .withBpmnElementType(BpmnElementType.PROCESS)
            .build();
    return processInstanceRecord(
        instanceKey, timestamp, ProcessInstanceIntent.ELEMENT_COMPLETED, value, position);
  }

  private static ZeebeRecord processInstanceRecord(
      final long instanceKey,
      final long timestamp,
      final ProcessInstanceIntent intent,
      final ProcessInstanceRecordValue value,
      final long position) {
    final Record<ProcessInstanceRecordValue> record =
        ImmutableRecord.<ProcessInstanceRecordValue>builder()
            .withRecordType(RecordType.EVENT)
            .withValueType(ValueType.PROCESS_INSTANCE)
            .withIntent(intent)
            .withKey(instanceKey)
            .withTimestamp(timestamp)
            .withPartitionId(1)
            .withPosition(position)
            .withValue(value)
            .build();
    return new ZeebeRecord(TOPIC, 0, 0L, record);
  }

  private static ZeebeRecord variable(
      final long instanceKey,
      final String name,
      final String valueJson,
      final long timestamp,
      final long position) {
    final VariableRecordValue value =
        ImmutableVariableRecordValue.builder()
            .withName(name)
            .withValue(valueJson)
            .withScopeKey(instanceKey)
            .withProcessInstanceKey(instanceKey)
            .withProcessDefinitionKey(PROCESS_DEFINITION_KEY)
            .withTenantId(TENANT_ID)
            .withBpmnProcessId(PROCESS_ID)
            .build();
    final Record<VariableRecordValue> record =
        ImmutableRecord.<VariableRecordValue>builder()
            .withRecordType(RecordType.EVENT)
            .withValueType(ValueType.VARIABLE)
            .withIntent(VariableIntent.CREATED)
            .withKey(instanceKey)
            .withTimestamp(timestamp)
            .withPartitionId(1)
            .withPosition(position)
            .withValue(value)
            .build();
    return new ZeebeRecord(TOPIC, 0, 0L, record);
  }

  // ---- test doubles -------------------------------------------------------------------------

  /** Minimal in-memory {@link TranslatorState}: only what these tests actually touch. */
  private static final class InMemoryOnlyState implements TranslatorState {
    private final Map<Long, OpenInstance> instances = new HashMap<>();
    private final Map<Long, OpenElement> elements = new HashMap<>();
    private final Map<Long, Map<String, String>> variables = new HashMap<>();
    private final Map<Long, Map<String, FlowEndpoints>> flowEndpoints = new HashMap<>();
    private final Map<Long, VariantAccumulator> variantAccumulators = new HashMap<>();
    private final Map<String, VariantName> variantNames = new HashMap<>();
    private final Map<Long, ObjectSightingList> objectSightings = new HashMap<>();
    private final Map<String, ObjectLifecycle> objectLifecycle = new HashMap<>();

    @Override
    public void putVariantAccumulator(
        final long instanceKey, final VariantAccumulator accumulator) {
      variantAccumulators.put(instanceKey, accumulator);
    }

    @Override
    public VariantAccumulator getVariantAccumulator(final long instanceKey) {
      return variantAccumulators.get(instanceKey);
    }

    @Override
    public void deleteVariantAccumulator(final long instanceKey) {
      variantAccumulators.remove(instanceKey);
    }

    @Override
    public void putVariantName(final String bpmnProcessId, final int h32, final VariantName name) {
      variantNames.put(bpmnProcessId + "#" + h32, name);
    }

    @Override
    public VariantName getVariantName(final String bpmnProcessId, final int h32) {
      return variantNames.get(bpmnProcessId + "#" + h32);
    }

    @Override
    public void putInstance(final long instanceKey, final OpenInstance instance) {
      instances.put(instanceKey, instance);
    }

    @Override
    public OpenInstance getInstance(final long instanceKey) {
      return instances.get(instanceKey);
    }

    @Override
    public void deleteInstance(final long instanceKey) {
      instances.remove(instanceKey);
    }

    @Override
    public void putElement(final long elementKey, final OpenElement element) {
      elements.put(elementKey, element);
    }

    @Override
    public OpenElement getElement(final long elementKey) {
      return elements.get(elementKey);
    }

    @Override
    public void deleteElement(final long elementKey) {
      elements.remove(elementKey);
    }

    @Override
    public void putVariable(final long instanceKey, final String name, final String valueJson) {
      variables.computeIfAbsent(instanceKey, k -> new HashMap<>()).put(name, valueJson);
    }

    @Override
    public Map<String, String> variablesOf(final long instanceKey) {
      return variables.getOrDefault(instanceKey, Map.of());
    }

    @Override
    public void deleteVariablesOf(final long instanceKey) {
      variables.remove(instanceKey);
    }

    @Override
    public void putFlowEndpoints(
        final long processDefinitionKey, final String flowId, final FlowEndpoints endpoints) {
      flowEndpoints
          .computeIfAbsent(processDefinitionKey, k -> new HashMap<>())
          .put(flowId, endpoints);
    }

    @Override
    public FlowEndpoints flowEndpoints(final long processDefinitionKey, final String flowId) {
      return flowEndpoints.getOrDefault(processDefinitionKey, Map.of()).get(flowId);
    }

    @Override
    public void forEachOpenInstance(
        final java.util.function.BiConsumer<Long, OpenInstance> consumer) {
      instances.forEach(consumer);
    }

    @Override
    public void forEachOpenElement(
        final java.util.function.BiConsumer<Long, OpenElement> consumer) {
      elements.forEach(consumer);
    }

    @Override
    public void putObjectSightings(final long instanceKey, final ObjectSightingList sightings) {
      objectSightings.put(instanceKey, sightings);
    }

    @Override
    public ObjectSightingList getObjectSightings(final long instanceKey) {
      return objectSightings.get(instanceKey);
    }

    @Override
    public void deleteObjectSightings(final long instanceKey) {
      objectSightings.remove(instanceKey);
    }

    @Override
    public void putObjectLifecycle(
        final String objectType, final String objectId, final ObjectLifecycle lifecycle) {
      objectLifecycle.put(objectType + '#' + objectId, lifecycle);
    }

    @Override
    public ObjectLifecycle getObjectLifecycle(final String objectType, final String objectId) {
      return objectLifecycle.get(objectType + '#' + objectId);
    }

    @Override
    public int sweepObjectLifecycleTombstones(final long cutoffMs) {
      final int before = objectLifecycle.size();
      objectLifecycle
          .values()
          .removeIf(
              lifecycle ->
                  lifecycle.status() == TranslatorState.LifecycleStatus.CLOSED_TOMBSTONE
                      && lifecycle.closedAtMs() < cutoffMs);
      return before - objectLifecycle.size();
    }

    @Override
    public void close() {}
  }

  /** Never touches a real ring; this test only exercises the profiling rider. */
  private static final class NoOpRowAppender implements RowAppender {
    @Override
    public boolean begin() {
      return true;
    }

    @Override
    public RowAppender putLong(final int column, final long value) {
      return this;
    }

    @Override
    public RowAppender putInt(final int column, final int value) {
      return this;
    }

    @Override
    public RowAppender putDict(final int column, final CharSequence value) {
      return this;
    }

    @Override
    public RowAppender putBinary(
        final int column, final byte[] src, final int offset, final int len) {
      return this;
    }

    @Override
    public RowAppender putDouble(final int column, final double value) {
      return this;
    }

    @Override
    public RowAppender putNull(final int column) {
      return this;
    }

    @Override
    public void endRow() {}
  }

  /** Captures every appended row per table instead of writing Parquet. */
  private static final class RecordingEncoderFactory implements BatchEncoder.Factory {

    private final Map<String, List<Map<String, Object>>> rowsByTable = new HashMap<>();

    List<Map<String, Object>> rows(final String table) {
      return rowsByTable.getOrDefault(table, List.of());
    }

    @Override
    public BatchEncoder newFile(final TableSchema schema, final long epochDay) {
      final List<Map<String, Object>> sink =
          rowsByTable.computeIfAbsent(schema.table(), t -> new ArrayList<>());
      return new BatchEncoder() {
        private long rowCount;

        @Override
        public void append(final SortedRun run, final int fromIndex, final int toIndex) {
          for (int i = fromIndex; i < toIndex; i++) {
            final Map<String, Object> row = new HashMap<>();
            for (int c = 0; c < schema.columns().size(); c++) {
              final TableSchema.Column column = schema.columns().get(c);
              if (run.isNullAt(c, i)) {
                row.put(column.name(), null);
              } else if (column.type() == ColumnType.STRING_DICT) {
                row.put(column.name(), run.stringAt(c, i));
              } else if (column.type() == ColumnType.INT) {
                row.put(column.name(), run.intAt(c, i));
              } else if (column.type() == ColumnType.DOUBLE) {
                row.put(column.name(), run.doubleAt(c, i));
              } else {
                row.put(column.name(), run.longAt(c, i));
              }
            }
            sink.add(row);
            rowCount++;
          }
        }

        @Override
        public DataFileResult finish() {
          return new DataFileResult(
              schema.table(),
              "mem://" + schema.table() + "/" + epochDay,
              rowCount,
              rowCount * 64,
              new Metrics(rowCount, null, null, null, null),
              epochDay);
        }

        @Override
        public void abort() {}
      };
    }
  }
}

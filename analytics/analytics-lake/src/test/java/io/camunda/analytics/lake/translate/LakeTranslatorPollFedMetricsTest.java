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
import io.camunda.analytics.lake.state.TranslatorState;
import io.camunda.eventbridge.zeebe.connector.ZeebeRecord;
import io.camunda.zeebe.protocol.record.ImmutableRecord;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.ImmutableProcessInstanceRecordValue;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceRecordValue;
import io.camunda.zeebe.protocol.record.value.deployment.ImmutableProcess;
import io.camunda.zeebe.protocol.record.value.deployment.Process;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.iceberg.Metrics;
import org.junit.jupiter.api.Test;

/**
 * {@link LakeTranslator}'s two poll-fed metric folds: branch counts (from {@code
 * SEQUENCE_FLOW_TAKEN} records) and started counters (from a root {@code ELEMENT_ACTIVATED}) — see
 * {@link PollFedRider}'s own class javadoc for the lane these ride on. Also covers {@link
 * LakeTranslator}'s {@code PROCESS}/{@code CREATED} BPMN parsing and the state-backed resolution
 * path a restart takes.
 */
class LakeTranslatorPollFedMetricsTest {

  private static final String TOPIC = "test-topic";
  private static final String PROCESS_ID = "poll-fed-test-process";
  private static final int VERSION = 3;
  private static final String TENANT_ID = "<default>";
  private static final long PROCESS_DEFINITION_KEY = 42L;

  /** A minimal BPMN resource: {@code start -[flow-a]-> task-a}, resolvable to source/target ids. */
  private static final String BPMN_XML =
      "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
          + "<bpmn:definitions xmlns:bpmn=\"http://www.omg.org/spec/BPMN/20100524/MODEL\""
          + " id=\"defs\" targetNamespace=\"http://camunda.org/test\">\n"
          + "  <bpmn:process id=\""
          + PROCESS_ID
          + "\" isExecutable=\"true\">\n"
          + "    <bpmn:startEvent id=\"start\" />\n"
          + "    <bpmn:sequenceFlow id=\"flow-a\" sourceRef=\"start\" targetRef=\"task-a\" />\n"
          + "    <bpmn:serviceTask id=\"task-a\" />\n"
          + "  </bpmn:process>\n"
          + "</bpmn:definitions>\n";

  @Test
  void shouldFoldABranchCountWithResolvedSourceAndTargetAfterTheDeploymentRecord() {
    // given a translator wired with a flow-counts rider, and the definition's own deployment
    // record already folded (so the flow's endpoints are resolvable)
    final RecordingEncoderFactory encoders = new RecordingEncoderFactory();
    final PollFedRider flowRider = new PollFedRider(flowMetrics(), encoders, 128);
    final TranslatorState state = new InMemoryOnlyState();
    final LakeTranslator translator =
        new LakeTranslator(state, new NoOpRowAppender(), new NoOpRowAppender(), flowRider, null);
    translator.onRecord(processCreatedRecord(1L));

    // when: the sequence flow is taken
    translator.onRecord(sequenceFlowTakenRecord(100L, 2L));
    flowRider.onPollBoundary();
    final Map<String, List<DataFileResult>> derived = flowRider.onWindowClose();

    // then: exactly one row, with the flow's real source/target (not the flow id alone)
    assertThat(derived).containsOnlyKeys("flows_test_metrics");
    final List<Map<String, Object>> rows = encoders.rows("flows_test_metrics");
    assertThat(rows).hasSize(1);
    assertThat(rows.get(0))
        .containsEntry("process_id", PROCESS_ID)
        .containsEntry("version", VERSION)
        .containsEntry("flow_id", "flow-a")
        .containsEntry("source_element_id", "start")
        .containsEntry("target_element_id", "task-a")
        .containsEntry("cnt", 1L);
  }

  @Test
  void shouldResolveSourceAndTargetAsNullWhenTheDefinitionWasNeverDeployed() {
    // given a translator that never saw this definition's own deployment record
    final RecordingEncoderFactory encoders = new RecordingEncoderFactory();
    final PollFedRider flowRider = new PollFedRider(flowMetrics(), encoders, 128);
    final TranslatorState state = new InMemoryOnlyState();
    final LakeTranslator translator =
        new LakeTranslator(state, new NoOpRowAppender(), new NoOpRowAppender(), flowRider, null);

    // when: a sequence-flow-taken record arrives anyway (e.g. the deployment record's own log
    // position was retention-trimmed before this translator's bootstrap offset)
    translator.onRecord(sequenceFlowTakenRecord(100L, 2L));
    flowRider.onPollBoundary();
    flowRider.onWindowClose();

    // then: the flow is still counted, honestly NULL for both endpoints -- never guessed, never
    // dropped
    final List<Map<String, Object>> rows = encoders.rows("flows_test_metrics");
    assertThat(rows).hasSize(1);
    assertThat(rows.get(0)).containsEntry("cnt", 1L);
    assertThat(rows.get(0)).containsKey("source_element_id");
    assertThat(rows.get(0).get("source_element_id")).isNull();
    assertThat(rows.get(0).get("target_element_id")).isNull();
  }

  @Test
  void shouldFoldAStartedCountOnRootActivation() {
    // given a translator wired with a started-counts rider
    final RecordingEncoderFactory encoders = new RecordingEncoderFactory();
    final PollFedRider startedRider = new PollFedRider(instanceStartMetrics(), encoders, 128);
    final TranslatorState state = new InMemoryOnlyState();
    final LakeTranslator translator =
        new LakeTranslator(state, new NoOpRowAppender(), new NoOpRowAppender(), null, startedRider);

    // when: two root instances activate (a non-root activation must never feed this rider)
    translator.onRecord(activateRoot(1L, 1_700_000_000_000L, 10L));
    translator.onRecord(activateRoot(2L, 1_700_000_000_050L, 11L));
    translator.onRecord(activateElement(1L, 900L, 1_700_000_000_060L, 12L));
    startedRider.onPollBoundary();
    final Map<String, List<DataFileResult>> derived = startedRider.onWindowClose();

    // then: exactly one group (process_id, version), counted twice
    assertThat(derived).containsOnlyKeys("instance_starts_test_metrics");
    final List<Map<String, Object>> rows = encoders.rows("instance_starts_test_metrics");
    assertThat(rows).hasSize(1);
    assertThat(rows.get(0))
        .containsEntry("process_id", PROCESS_ID)
        .containsEntry("version", VERSION)
        .containsEntry("cnt", 2L);
  }

  @Test
  void shouldResolveFlowEndpointsFromDurableStateAfterASimulatedRestart() {
    // given a first translator that folds the deployment record, persisting its flow endpoints
    // to the durable TranslatorState store (not just its own heap cache) -- a plain in-memory
    // TranslatorState double exercises this exactly like RocksDbTranslatorState would
    // (LakeTranslator
    // only ever calls the interface; see InMemoryOnlyState's own javadoc for why this module's
    // other translator tests use the same substitution rather than a real RocksDB instance)
    final TranslatorState warmState = new InMemoryOnlyState();
    final RecordingEncoderFactory firstEncoders = new RecordingEncoderFactory();
    final PollFedRider firstRider = new PollFedRider(flowMetrics(), firstEncoders, 128);
    final LakeTranslator firstTranslator =
        new LakeTranslator(
            warmState, new NoOpRowAppender(), new NoOpRowAppender(), firstRider, null);
    firstTranslator.onRecord(processCreatedRecord(1L));

    // when: a brand new translator (fresh heap cache -- simulating a restart) is built against
    // the SAME durable store, and folds a sequence-flow-taken record WITHOUT ever seeing the
    // deployment record itself (a restart never replays past its own bootstrap offset)
    final RecordingEncoderFactory secondEncoders = new RecordingEncoderFactory();
    final PollFedRider secondRider = new PollFedRider(flowMetrics(), secondEncoders, 128);
    final LakeTranslator secondTranslator =
        new LakeTranslator(
            warmState, new NoOpRowAppender(), new NoOpRowAppender(), secondRider, null);
    secondTranslator.onRecord(sequenceFlowTakenRecord(200L, 2L));
    secondRider.onPollBoundary();
    secondRider.onWindowClose();

    // then: the flow's endpoints resolve correctly from the durable store alone -- the second
    // translator's own heap cache started out completely empty
    final List<Map<String, Object>> rows = secondEncoders.rows("flows_test_metrics");
    assertThat(rows).hasSize(1);
    assertThat(rows.get(0))
        .containsEntry("source_element_id", "start")
        .containsEntry("target_element_id", "task-a");
  }

  // ---- declarations ---------------------------------------------------------------------------

  private static CompiledEntityMetrics flowMetrics() {
    final TableSchema schema =
        new TableSchema(
            "flows_test",
            List.of(
                new TableSchema.Column("process_id", ColumnType.STRING_DICT, 1, false, -1, false),
                new TableSchema.Column("version", ColumnType.INT, 2, false, -1, false),
                new TableSchema.Column("flow_id", ColumnType.STRING_DICT, 3, false, -1, false),
                new TableSchema.Column(
                    "source_element_id", ColumnType.STRING_DICT, 4, true, -1, false),
                new TableSchema.Column(
                    "target_element_id", ColumnType.STRING_DICT, 5, true, -1, false),
                new TableSchema.Column(
                    "taken_at",
                    ColumnType.LONG,
                    6,
                    false,
                    -1,
                    true,
                    TableSchema.LogicalType.TIMESTAMPTZ)));
    return EntityMetrics.declare("flows_test", schema)
        .dims("process_id", "version", "flow_id", "source_element_id", "target_element_id")
        .window(Duration.ofMinutes(1), "taken_at")
        .count()
        .build();
  }

  private static CompiledEntityMetrics instanceStartMetrics() {
    final TableSchema schema =
        new TableSchema(
            "instance_starts_test",
            List.of(
                new TableSchema.Column("process_id", ColumnType.STRING_DICT, 1, false, -1, false),
                new TableSchema.Column("version", ColumnType.INT, 2, false, -1, false),
                new TableSchema.Column(
                    "started_at",
                    ColumnType.LONG,
                    3,
                    false,
                    -1,
                    true,
                    TableSchema.LogicalType.TIMESTAMPTZ)));
    return EntityMetrics.declare("instance_starts_test", schema)
        .dims("process_id", "version")
        .window(Duration.ofMinutes(1), "started_at")
        .count()
        .build();
  }

  // ---- record construction ---------------------------------------------------------------------

  private static ZeebeRecord processCreatedRecord(final long position) {
    final Process value =
        ImmutableProcess.builder()
            .withBpmnProcessId(PROCESS_ID)
            .withVersion(VERSION)
            .withVersionTag("")
            .withProcessDefinitionKey(PROCESS_DEFINITION_KEY)
            .withResourceName("test.bpmn")
            .withChecksum(new byte[0])
            .withDuplicate(false)
            .withDeploymentKey(1L)
            .withTenantId(TENANT_ID)
            .withResource(BPMN_XML.getBytes(StandardCharsets.UTF_8))
            .build();
    final Record<Process> record =
        ImmutableRecord.<Process>builder()
            .withRecordType(RecordType.EVENT)
            .withValueType(ValueType.PROCESS)
            .withIntent(ProcessIntent.CREATED)
            .withKey(PROCESS_DEFINITION_KEY)
            .withTimestamp(1_700_000_000_000L)
            .withPartitionId(1)
            .withPosition(position)
            .withValue(value)
            .build();
    return new ZeebeRecord(TOPIC, 0, 0L, record);
  }

  private static ZeebeRecord sequenceFlowTakenRecord(final long timestamp, final long position) {
    final ProcessInstanceRecordValue value =
        ImmutableProcessInstanceRecordValue.builder()
            .withBpmnProcessId(PROCESS_ID)
            .withVersion(VERSION)
            .withProcessInstanceKey(999L)
            .withProcessDefinitionKey(PROCESS_DEFINITION_KEY)
            .withTenantId(TENANT_ID)
            .withElementId("flow-a")
            .withBpmnElementType(BpmnElementType.SEQUENCE_FLOW)
            .build();
    final Record<ProcessInstanceRecordValue> record =
        ImmutableRecord.<ProcessInstanceRecordValue>builder()
            .withRecordType(RecordType.EVENT)
            .withValueType(ValueType.PROCESS_INSTANCE)
            .withIntent(ProcessInstanceIntent.SEQUENCE_FLOW_TAKEN)
            .withKey(999L)
            .withTimestamp(timestamp)
            .withPartitionId(1)
            .withPosition(position)
            .withValue(value)
            .build();
    return new ZeebeRecord(TOPIC, 0, 0L, record);
  }

  private static ZeebeRecord activateRoot(
      final long instanceKey, final long timestamp, final long position) {
    return processInstanceRecord(
        instanceKey, instanceKey, timestamp, BpmnElementType.PROCESS, "", position);
  }

  private static ZeebeRecord activateElement(
      final long instanceKey,
      final long elementInstanceKey,
      final long timestamp,
      final long position) {
    return processInstanceRecord(
        instanceKey,
        elementInstanceKey,
        timestamp,
        BpmnElementType.SERVICE_TASK,
        "task-a",
        position);
  }

  private static ZeebeRecord processInstanceRecord(
      final long instanceKey,
      final long elementInstanceKey,
      final long timestamp,
      final BpmnElementType elementType,
      final String elementId,
      final long position) {
    final ProcessInstanceRecordValue value =
        ImmutableProcessInstanceRecordValue.builder()
            .withBpmnProcessId(PROCESS_ID)
            .withVersion(VERSION)
            .withProcessInstanceKey(instanceKey)
            .withProcessDefinitionKey(PROCESS_DEFINITION_KEY)
            .withTenantId(TENANT_ID)
            .withElementId(elementId)
            .withBpmnElementType(elementType)
            .build();
    final Record<ProcessInstanceRecordValue> record =
        ImmutableRecord.<ProcessInstanceRecordValue>builder()
            .withRecordType(RecordType.EVENT)
            .withValueType(ValueType.PROCESS_INSTANCE)
            .withIntent(ProcessInstanceIntent.ELEMENT_ACTIVATED)
            .withKey(elementInstanceKey)
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

  /** Never touches a real ring; branch/start-count records never reach a RowAppender anyway. */
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

  /**
   * Captures every appended row per table instead of writing Parquet — mirrors MetricsRiderTest.
   */
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

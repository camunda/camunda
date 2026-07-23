/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.lake.write;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.analytics.lake.LakeConfig;
import io.camunda.analytics.lake.sink.Descriptor;
import io.camunda.analytics.lake.sink.RowAppender;
import io.camunda.analytics.lake.sink.pipeline.DirectCommitSink;
import io.camunda.analytics.lake.state.TranslatorState;
import io.camunda.analytics.lake.translate.LakeTranslator;
import io.camunda.eventbridge.zeebe.connector.ZeebeRecord;
import io.camunda.zeebe.protocol.record.ImmutableRecord;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.ImmutableProcessInstanceRecordValue;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceRecordValue;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Exercises the M1 restart path end to end: {@link IcebergLakeWriter#committedZeebeWatermark(int)}
 * reading real {@code lake.zbpos.z*} stamps {@link DirectCommitSink} landed on the two raw tables,
 * then a fresh {@link LakeTranslator} seeded from that value via {@link
 * LakeTranslator#seedWatermark} folding a replayed record correctly (above the seed: folds again;
 * at or below: dropped) — see {@code LakeTranslator}'s "Origin-position dedup" javadoc's "Restart
 * rule".
 */
class ZeebeWatermarkRestartSeedingTest {

  private static final String PROCESS_ID = "restart-seed-process";
  private static final int VERSION = 1;
  private static final String TENANT_ID = "<default>";
  private static final long PROCESS_DEFINITION_KEY = 1L;
  private static final int ZEEBE_PARTITION_ID = 3;

  @Test
  void shouldSeedWatermarkFromMinAcrossTablesAndFoldRecordsAboveIt(@TempDir final Path tempDir) {
    // given a writer whose two raw tables carry DIFFERENT zbpos stamps for the same Zeebe
    // partition -- mirroring committedOffset(int)'s own MIN-across-tables convention, this is the
    // shape a crash between the two independent per-table commits leaves behind
    final LakeConfig config =
        new LakeConfig(
            "http://localhost:0",
            "test-topic",
            "test-group",
            tempDir.resolve("warehouse"),
            tempDir.resolve("state"),
            1,
            2000L,
            0L,
            0L,
            0,
            null);
    final IcebergLakeWriter writer = new IcebergLakeWriter(config);
    try {
      final DirectCommitSink instancesSink =
          new DirectCommitSink(writer.instancesTable(), writer.commitLock(writer.instancesTable()));
      final DirectCommitSink activitiesSink =
          new DirectCommitSink(
              writer.activitiesTable(), writer.commitLock(writer.activitiesTable()));

      instancesSink.accept(
          new Descriptor("instances", 0, List.of(), 0L, 0L, 0L, Map.of(ZEEBE_PARTITION_ID, 500L)));
      activitiesSink.accept(
          new Descriptor("activities", 0, List.of(), 0L, 0L, 0L, Map.of(ZEEBE_PARTITION_ID, 300L)));

      // when / then: the enumerated partition set includes it, and the cut is the MIN of the two
      assertThat(writer.stampedZeebePartitionIds()).contains(ZEEBE_PARTITION_ID);
      assertThat(writer.committedZeebeWatermark(ZEEBE_PARTITION_ID)).isEqualTo(300L);

      // and: seeding a fresh translator from that cut, then feeding a replayed record above it,
      // folds again -- its row was never committed on both tables, so this is correct, not a gap
      final InMemoryTranslatorState state = new InMemoryTranslatorState();
      final LakeTranslator translator =
          new LakeTranslator(state, new NoOpRowAppender(), new NoOpRowAppender());
      translator.seedWatermark(
          ZEEBE_PARTITION_ID, writer.committedZeebeWatermark(ZEEBE_PARTITION_ID));

      translator.onRecord(activateRoot(1L, 100L, ZEEBE_PARTITION_ID, 301L));
      assertThat(state.getInstance(1L)).isNotNull();

      // and: a replayed record at (or below) the seeded cut is dropped -- it was already durable
      translator.onRecord(activateRoot(2L, 200L, ZEEBE_PARTITION_ID, 300L));
      translator.onRecord(activateRoot(3L, 300L, ZEEBE_PARTITION_ID, 100L));
      assertThat(state.getInstance(2L)).isNull();
      assertThat(state.getInstance(3L)).isNull();
    } finally {
      writer.close();
    }
  }

  @Test
  void shouldSeedNothingWhenNeitherTableHasEverStampedTheirPartition(@TempDir final Path tempDir) {
    // given a fresh writer with no zbpos stamps at all
    final LakeConfig config =
        new LakeConfig(
            "http://localhost:0",
            "test-topic",
            "test-group",
            tempDir.resolve("warehouse"),
            tempDir.resolve("state"),
            1,
            2000L,
            0L,
            0L,
            0,
            null);
    final IcebergLakeWriter writer = new IcebergLakeWriter(config);
    try {
      // when / then
      assertThat(writer.stampedZeebePartitionIds()).isEmpty();
      assertThat(writer.committedZeebeWatermark(ZEEBE_PARTITION_ID)).isEqualTo(-1L);
    } finally {
      writer.close();
    }
  }

  // ---- record construction ------------------------------------------------------------------

  private static ZeebeRecord activateRoot(
      final long instanceKey,
      final long timestamp,
      final int zeebePartitionId,
      final long position) {
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
    final Record<ProcessInstanceRecordValue> record =
        ImmutableRecord.<ProcessInstanceRecordValue>builder()
            .withRecordType(RecordType.EVENT)
            .withValueType(ValueType.PROCESS_INSTANCE)
            .withIntent(ProcessInstanceIntent.ELEMENT_ACTIVATED)
            .withKey(instanceKey)
            .withTimestamp(timestamp)
            .withPartitionId(zeebePartitionId)
            .withPosition(position)
            .withValue(value)
            .build();
    return new ZeebeRecord("test-topic", 0, 0L, record);
  }

  // ---- test doubles -------------------------------------------------------------------------

  /** Never engages backpressure and ignores every column write -- this test never reads rows. */
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
    public RowAppender putNull(final int column) {
      return this;
    }

    @Override
    public void endRow() {}
  }

  /**
   * Minimal in-memory {@link TranslatorState} (mirrors {@code SinkIntegrationTest}'s own test
   * double, duplicated here rather than shared — see that class's own javadoc for why).
   */
  private static final class InMemoryTranslatorState implements TranslatorState {
    private final Map<Long, OpenInstance> instances = new HashMap<>();
    private final Map<Long, OpenElement> elements = new HashMap<>();
    private final Map<Long, Map<String, String>> variables = new HashMap<>();
    private final Map<Long, Map<String, FlowEndpoints>> flowEndpoints = new HashMap<>();

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
    public void forEachOpenInstance(final BiConsumer<Long, OpenInstance> consumer) {
      instances.forEach(consumer);
    }

    @Override
    public void forEachOpenElement(final BiConsumer<Long, OpenElement> consumer) {
      elements.forEach(consumer);
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
    public void close() {}
  }
}

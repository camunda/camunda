/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.lake.translate;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.analytics.lake.sink.RowAppender;
import io.camunda.analytics.lake.state.TranslatorState;
import io.camunda.eventbridge.zeebe.connector.ZeebeRecord;
import io.camunda.zeebe.protocol.record.ImmutableRecord;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.ImmutableProcessInstanceRecordValue;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceRecordValue;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link LakeTranslator}'s origin-position dedup gate (see its class javadoc's
 * "Origin-position dedup" section): the check on a record's Zeebe {@code partitionId}/{@code
 * position} that runs before any state mutation or row emission. Uses a minimal in-memory {@link
 * TranslatorState} and a counting {@link RowAppender} fake rather than the real L0 sink — this
 * class exercises the gate itself, not the sink wiring (see {@code SinkIntegrationTest} for that).
 */
class LakeTranslatorTest {

  private static final String TOPIC = "test-topic";
  private static final String PROCESS_ID = "gate-test-process";
  private static final int VERSION = 1;
  private static final String TENANT_ID = "<default>";
  private static final long PROCESS_DEFINITION_KEY = 1L;

  @Test
  void shouldAdmitRecordsWithIncreasingPositionsOnTheSamePartition() {
    // given
    final InMemoryTranslatorState state = new InMemoryTranslatorState();
    final CountingRowAppender instanceAppender = new CountingRowAppender();
    final LakeTranslator translator =
        new LakeTranslator(state, instanceAppender, new CountingRowAppender());

    // when: activation then completion, strictly increasing positions on the same Zeebe partition
    translator.onRecord(activateRoot(1L, 100L, 1, 10L));
    translator.onRecord(completeRoot(1L, 150L, 1, 11L));

    // then: the row was appended and the instance evicted after emit
    assertThat(instanceAppender.rowsAppended).isEqualTo(1);
    assertThat(state.getInstance(1L)).isNull();
  }

  @Test
  void shouldDropRewoundDuplicatePosition() {
    // given a translator whose watermark for partition 1 has advanced to 11
    final InMemoryTranslatorState state = new InMemoryTranslatorState();
    final LakeTranslator translator =
        new LakeTranslator(state, new CountingRowAppender(), new CountingRowAppender());
    translator.onRecord(activateRoot(1L, 100L, 1, 10L));
    translator.onRecord(completeRoot(1L, 150L, 1, 11L));

    // when: a redelivered record for a brand new instance key arrives at a position at (or below)
    // the watermark -- exactly the shape of an at-least-once exporter's post-failover re-export
    translator.onRecord(activateRoot(2L, 200L, 1, 5L));

    // then: the gate drops it before any state mutation -- the instance was never opened
    assertThat(state.getInstance(2L)).isNull();
  }

  @Test
  void shouldDropEqualPosition() {
    // given a translator whose watermark for partition 1 has advanced to 11
    final InMemoryTranslatorState state = new InMemoryTranslatorState();
    final LakeTranslator translator =
        new LakeTranslator(state, new CountingRowAppender(), new CountingRowAppender());
    translator.onRecord(activateRoot(1L, 100L, 1, 10L));
    translator.onRecord(completeRoot(1L, 150L, 1, 11L));

    // when: a record lands at exactly the watermark's own position (not just below it)
    translator.onRecord(activateRoot(3L, 300L, 1, 11L));

    // then: an equal position is a duplicate too, dropped the same way as a lower one
    assertThat(state.getInstance(3L)).isNull();
  }

  @Test
  void shouldTrackIndependentWatermarksPerZeebePartition() {
    // given a translator whose watermark for partition 1 has advanced well past 5
    final InMemoryTranslatorState state = new InMemoryTranslatorState();
    final LakeTranslator translator =
        new LakeTranslator(state, new CountingRowAppender(), new CountingRowAppender());
    translator.onRecord(activateRoot(1L, 100L, 1, 10L));
    translator.onRecord(completeRoot(1L, 150L, 1, 11L));

    // when: a record for a DIFFERENT Zeebe partition arrives at position 5 -- lower than
    // partition 1's watermark, but partition 2 has never seen a record before
    translator.onRecord(activateRoot(4L, 400L, 2, 5L));

    // then: partition 2's own (independent) watermark starts at -1, so this is admitted
    assertThat(state.getInstance(4L)).isNotNull();
  }

  @Test
  void shouldGrowWatermarkArrayForLargePartitionId() {
    // given a translator with only its default (small) initial watermark array capacity
    final InMemoryTranslatorState state = new InMemoryTranslatorState();
    final LakeTranslator translator =
        new LakeTranslator(state, new CountingRowAppender(), new CountingRowAppender());

    // when: a record arrives for a Zeebe partition id far beyond that initial capacity
    translator.onRecord(activateRoot(5L, 500L, 50, 100L));

    // then: the watermark array grew to admit it without error, and the record folded normally
    assertThat(state.getInstance(5L)).isNotNull();
    assertThat(translator.watermarkSnapshot()).containsEntry(50, 100L);
  }

  @Test
  void shouldReportWatermarkSnapshotKeyedByZeebePartitionId() {
    // given records folded across three distinct Zeebe partitions
    final InMemoryTranslatorState state = new InMemoryTranslatorState();
    final LakeTranslator translator =
        new LakeTranslator(state, new CountingRowAppender(), new CountingRowAppender());
    translator.onRecord(activateRoot(1L, 100L, 1, 10L));
    translator.onRecord(activateRoot(2L, 200L, 2, 20L));
    translator.onRecord(activateRoot(3L, 300L, 50, 30L));

    // when
    final Map<Integer, Long> snapshot = translator.watermarkSnapshot();

    // then: exactly the highest position folded so far, per partition -- nothing more, nothing
    // less (partitions never fed are absent, not defaulted to -1 in the map)
    assertThat(snapshot).containsOnly(Map.entry(1, 10L), Map.entry(2, 20L), Map.entry(50, 30L));
  }

  @Test
  void shouldFoldReplayedRecordAboveASeededWatermark() {
    // given a fresh translator seeded (as if at process restart) from a durable watermark stamp
    final InMemoryTranslatorState state = new InMemoryTranslatorState();
    final LakeTranslator translator =
        new LakeTranslator(state, new CountingRowAppender(), new CountingRowAppender());
    translator.seedWatermark(9, 200L);

    // when: a replayed record above the seeded watermark arrives
    translator.onRecord(activateRoot(6L, 600L, 9, 201L));

    // then: its row was never made durable, so it must fold again -- not a gap
    assertThat(state.getInstance(6L)).isNotNull();
  }

  @Test
  void shouldDropRecordAtOrBelowASeededWatermark() {
    // given a fresh translator seeded from a durable watermark stamp
    final InMemoryTranslatorState state = new InMemoryTranslatorState();
    final LakeTranslator translator =
        new LakeTranslator(state, new CountingRowAppender(), new CountingRowAppender());
    translator.seedWatermark(9, 200L);

    // when: records at, and below, the seeded watermark are replayed
    translator.onRecord(activateRoot(7L, 700L, 9, 200L));
    translator.onRecord(activateRoot(8L, 800L, 9, 150L));

    // then: both are dropped -- their rows were already durable when the watermark was stamped
    assertThat(state.getInstance(7L)).isNull();
    assertThat(state.getInstance(8L)).isNull();
  }

  @Test
  void shouldNotLoseRowOnBackpressureRetryAndStillDropGenuineDuplicateAfterward() {
    // given: an instance already open, and an appender that reports backpressure once before
    // accepting -- simulating the ring being full at the exact moment the completion record
    // (which emits the instances row) first arrives
    final InMemoryTranslatorState state = new InMemoryTranslatorState();
    final FlakyRowAppender instanceAppender = new FlakyRowAppender(1);
    final LakeTranslator translator =
        new LakeTranslator(state, instanceAppender, new CountingRowAppender());
    translator.onRecord(activateRoot(1L, 100L, 1, 10L));

    final ZeebeRecord completion = completeRoot(1L, 150L, 1, 11L);

    // when: the first attempt hits backpressure
    final boolean firstAttempt = translator.onRecord(completion);

    // then: onRecord reports backpressure, no row was appended, the instance is still open, and
    // -- the bug this test guards against -- the watermark must NOT have advanced past this
    // record's position on the failed attempt
    assertThat(firstAttempt).isFalse();
    assertThat(instanceAppender.rowsAppended).isZero();
    assertThat(state.getInstance(1L)).isNotNull();
    assertThat(translator.watermarkSnapshot()).containsEntry(1, 10L);

    // when: the caller retries the exact same record and the ring now has room
    final boolean retryAttempt = translator.onRecord(completion);

    // then: the retry is admitted (not misread as a duplicate of itself), the row is emitted
    // exactly once, and the watermark now sits at the record's own position
    assertThat(retryAttempt).isTrue();
    assertThat(instanceAppender.rowsAppended).isEqualTo(1);
    assertThat(state.getInstance(1L)).isNull();
    assertThat(translator.watermarkSnapshot()).containsEntry(1, 11L);

    // when: a genuinely redelivered duplicate of that same position arrives afterward
    translator.onRecord(activateRoot(2L, 200L, 1, 11L));

    // then: it is still dropped -- the fix does not weaken the dedup gate itself
    assertThat(state.getInstance(2L)).isNull();
    assertThat(instanceAppender.rowsAppended).isEqualTo(1);
  }

  // ---- record construction ------------------------------------------------------------------

  private static ZeebeRecord activateRoot(
      final long instanceKey,
      final long timestamp,
      final int zeebePartitionId,
      final long position) {
    return processInstanceRecord(
        instanceKey,
        timestamp,
        BpmnElementType.PROCESS,
        ProcessInstanceIntent.ELEMENT_ACTIVATED,
        zeebePartitionId,
        position);
  }

  private static ZeebeRecord completeRoot(
      final long instanceKey,
      final long timestamp,
      final int zeebePartitionId,
      final long position) {
    return processInstanceRecord(
        instanceKey,
        timestamp,
        BpmnElementType.PROCESS,
        ProcessInstanceIntent.ELEMENT_COMPLETED,
        zeebePartitionId,
        position);
  }

  private static ZeebeRecord processInstanceRecord(
      final long instanceKey,
      final long timestamp,
      final BpmnElementType elementType,
      final ProcessInstanceIntent intent,
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
            .withBpmnElementType(elementType)
            .build();
    final Record<ProcessInstanceRecordValue> record =
        ImmutableRecord.<ProcessInstanceRecordValue>builder()
            .withRecordType(RecordType.EVENT)
            .withValueType(ValueType.PROCESS_INSTANCE)
            .withIntent(intent)
            .withKey(instanceKey)
            .withTimestamp(timestamp)
            .withPartitionId(zeebePartitionId)
            .withPosition(position)
            .withValue(value)
            .build();
    return new ZeebeRecord(TOPIC, 0, 0L, record);
  }

  // ---- test doubles -------------------------------------------------------------------------

  /** Counts completed rows ({@code endRow()} calls); never engages backpressure. */
  private static final class CountingRowAppender implements RowAppender {
    private int rowsAppended;

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
    public void endRow() {
      rowsAppended++;
    }
  }

  /**
   * Reports backpressure ({@code begin()} returns {@code false}) exactly {@code
   * beginFailuresRemaining} times before accepting every subsequent row -- simulates a ring that is
   * full at the exact moment a row-emitting record first arrives, then has room on retry.
   */
  private static final class FlakyRowAppender implements RowAppender {
    private int beginFailuresRemaining;
    private int rowsAppended;

    FlakyRowAppender(final int beginFailuresRemaining) {
      this.beginFailuresRemaining = beginFailuresRemaining;
    }

    @Override
    public boolean begin() {
      if (beginFailuresRemaining > 0) {
        beginFailuresRemaining--;
        return false;
      }
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
    public void endRow() {
      rowsAppended++;
    }
  }

  /**
   * Minimal in-memory {@link TranslatorState}: this test exercises the origin-position dedup gate,
   * not RocksDB (mirrors {@code SinkIntegrationTest}'s own test double, duplicated here rather than
   * shared — see that class's own javadoc for why).
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

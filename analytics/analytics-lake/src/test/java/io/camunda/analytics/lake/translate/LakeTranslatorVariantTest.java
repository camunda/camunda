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
import io.camunda.analytics.lake.translate.RawTableSchemas.InstanceColumns;
import io.camunda.analytics.lake.translate.RawTableSchemas.VariantColumns;
import io.camunda.eventbridge.zeebe.connector.ZeebeRecord;
import io.camunda.zeebe.protocol.record.ImmutableRecord;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.ImmutableProcessInstanceRecordValue;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceRecordValue;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link LakeTranslator}'s variant-k1 capture: the accumulator fold (set semantics,
 * MI absorption, elements/flows both mixing, different routes producing different hashes), the
 * per-accumulator replay guard, completion's {@code variant_hash} emission and accumulator
 * eviction, and the {@code variants} dictionary row (content, seen-cache suppression, and the
 * {@code null} {@link #variantsAppender} case). Uses a minimal in-memory {@link TranslatorState}
 * and capturing {@link RowAppender} fakes — see {@code LakeTranslatorTest}'s own javadoc for why
 * this mirrors that class's approach rather than exercising the real L0 sink.
 */
class LakeTranslatorVariantTest {

  private static final String TOPIC = "test-topic";
  private static final String PROCESS_ID = "variant-test-process";
  private static final int VERSION = 1;
  private static final String TENANT_ID = "<default>";
  private static final long PROCESS_DEFINITION_KEY = 1L;
  private static final int ZEEBE_PARTITION = 1;

  @Test
  void shouldMixDistinctActivatedElementsIntoTheVariantHash() {
    // given
    final InMemoryTranslatorState state = new InMemoryTranslatorState();
    final CapturingRowAppender instanceAppender = new CapturingRowAppender();
    final LakeTranslator translator =
        new LakeTranslator(state, instanceAppender, new CapturingRowAppender());

    // when: one instance activates two distinct elements and completes
    translator.onRecord(activateRoot(1L, 100L, 1L));
    translator.onRecord(activateElement(1L, 11L, "task-a", BpmnElementType.SERVICE_TASK, 101L, 2L));
    translator.onRecord(activateElement(1L, 12L, "task-b", BpmnElementType.SERVICE_TASK, 102L, 3L));
    translator.onRecord(completeRoot(1L, 200L, 4L));

    // then: the emitted variant_hash matches applying the frozen scheme by hand
    final long seed = VariantHash.h64(PROCESS_ID);
    final long expected =
        VariantHash.mix64(seed, VariantHash.h64("task-a"))
            ^ VariantHash.mix64(seed, VariantHash.h64("task-b"));
    assertThat(instanceAppender.rows).hasSize(1);
    assertThat(instanceAppender.rows.get(0).get(InstanceColumns.VARIANT_HASH))
        .isEqualTo(VariantHash.toHex16(expected));
  }

  @Test
  void shouldAbsorbMultiInstanceBodyRepeatsViaSetSemantics() {
    // given: instance A activates "task-a" once; instance B activates the SAME element id twice
    // under MULTI_INSTANCE_BODY, as a real multi-instance loop's inner instances would (see class
    // javadoc's "Variant capture" section: MI needs no special handling, repeats are absorbed)
    final InMemoryTranslatorState state = new InMemoryTranslatorState();
    final CapturingRowAppender instanceAppender = new CapturingRowAppender();
    final LakeTranslator translator =
        new LakeTranslator(state, instanceAppender, new CapturingRowAppender());

    translator.onRecord(activateRoot(1L, 100L, 1L));
    translator.onRecord(
        activateElement(1L, 11L, "task-a", BpmnElementType.MULTI_INSTANCE_BODY, 101L, 2L));
    translator.onRecord(completeRoot(1L, 200L, 3L));

    translator.onRecord(activateRoot(2L, 100L, 4L));
    translator.onRecord(
        activateElement(2L, 21L, "task-a", BpmnElementType.MULTI_INSTANCE_BODY, 101L, 5L));
    translator.onRecord(
        activateElement(2L, 22L, "task-a", BpmnElementType.MULTI_INSTANCE_BODY, 102L, 6L));
    translator.onRecord(completeRoot(2L, 200L, 7L));

    // then: both instances land on the exact same variant hash
    assertThat(instanceAppender.rows).hasSize(2);
    assertThat(instanceAppender.rows.get(0).get(InstanceColumns.VARIANT_HASH))
        .isEqualTo(instanceAppender.rows.get(1).get(InstanceColumns.VARIANT_HASH));
  }

  @Test
  void shouldMixSequenceFlowsAndElementsSuchThatDifferentRoutesProduceDifferentHashes() {
    // given: three instances all activate "task-a", but take different (or no) sequence flows
    final InMemoryTranslatorState state = new InMemoryTranslatorState();
    final CapturingRowAppender instanceAppender = new CapturingRowAppender();
    final LakeTranslator translator =
        new LakeTranslator(state, instanceAppender, new CapturingRowAppender());

    translator.onRecord(activateRoot(1L, 100L, 1L));
    translator.onRecord(activateElement(1L, 11L, "task-a", BpmnElementType.SERVICE_TASK, 101L, 2L));
    translator.onRecord(takeSequenceFlow(1L, "flow-1", 102L, 3L));
    translator.onRecord(completeRoot(1L, 200L, 4L));

    translator.onRecord(activateRoot(2L, 100L, 5L));
    translator.onRecord(activateElement(2L, 21L, "task-a", BpmnElementType.SERVICE_TASK, 101L, 6L));
    translator.onRecord(takeSequenceFlow(2L, "flow-2", 102L, 7L));
    translator.onRecord(completeRoot(2L, 200L, 8L));

    translator.onRecord(activateRoot(3L, 100L, 9L));
    translator.onRecord(
        activateElement(3L, 31L, "task-a", BpmnElementType.SERVICE_TASK, 101L, 10L));
    translator.onRecord(completeRoot(3L, 200L, 11L));

    // then: all three variant hashes are pairwise distinct -- flows contribute, and different
    // flows produce different variants over the same element set
    final List<Object> hashes =
        instanceAppender.rows.stream().map(row -> row.get(InstanceColumns.VARIANT_HASH)).toList();
    assertThat(hashes).hasSize(3);
    assertThat(hashes).doesNotHaveDuplicates();
  }

  @Test
  void shouldApplyPerAccumulatorReplayGuardIndependentlyOfTheSeenSet() {
    // given: an instance whose accumulator has folded "task-a" at position 10 (lastPosition == 10)
    final InMemoryTranslatorState state = new InMemoryTranslatorState();
    final CapturingRowAppender instanceAppender = new CapturingRowAppender();
    final LakeTranslator translator =
        new LakeTranslator(state, instanceAppender, new CapturingRowAppender());
    translator.onRecord(activateRoot(1L, 100L, 1L));
    translator.onRecord(
        activateElement(1L, 11L, "task-a", BpmnElementType.SERVICE_TASK, 101L, 10L));

    // when: a NEVER-before-seen element ("task-b") arrives at a position BELOW lastPosition -- the
    // exact shape of a post-crash replay of a stale record, not merely a repeat of an already-seen
    // id (which the seen-set alone would also have absorbed) -- proving the position guard is a
    // real, independent second gate
    translator.onRecord(activateElement(1L, 12L, "task-b", BpmnElementType.SERVICE_TASK, 102L, 5L));
    translator.onRecord(completeRoot(1L, 200L, 20L));

    // then: the completed variant hash reflects ONLY "task-a" -- "task-b" was never folded
    final long seed = VariantHash.h64(PROCESS_ID);
    final long expectedOnlyTaskA = VariantHash.mix64(seed, VariantHash.h64("task-a"));
    assertThat(instanceAppender.rows.get(0).get(InstanceColumns.VARIANT_HASH))
        .isEqualTo(VariantHash.toHex16(expectedOnlyTaskA));
  }

  @Test
  void shouldDeleteTheAccumulatorOnCompletion() {
    // given
    final InMemoryTranslatorState state = new InMemoryTranslatorState();
    final LakeTranslator translator =
        new LakeTranslator(state, new CapturingRowAppender(), new CapturingRowAppender());
    translator.onRecord(activateRoot(1L, 100L, 1L));
    translator.onRecord(activateElement(1L, 11L, "task-a", BpmnElementType.SERVICE_TASK, 101L, 2L));

    // when
    translator.onRecord(completeRoot(1L, 200L, 3L));

    // then
    assertThat(state.getVariantAccumulator(1L)).isNull();
  }

  @Test
  void shouldEmitNullVariantHashAndSkipDictionaryRowWhenAccumulatorIsMissingAtCompletion() {
    // given: the accumulator is lost before completion (e.g. state loss) -- deliberately bypassing
    // the normal fold path to simulate this rare edge
    final InMemoryTranslatorState state = new InMemoryTranslatorState();
    final CapturingRowAppender instanceAppender = new CapturingRowAppender();
    final CapturingRowAppender variantsAppender = new CapturingRowAppender();
    final LakeTranslator translator =
        new LakeTranslator(state, instanceAppender, new CapturingRowAppender(), variantsAppender);
    translator.onRecord(activateRoot(1L, 100L, 1L));
    state.deleteVariantAccumulator(1L);

    // when
    translator.onRecord(completeRoot(1L, 200L, 2L));

    // then: variant_hash is explicitly null (never fails), and no dictionary row is attempted
    assertThat(instanceAppender.rows.get(0)).containsEntry(InstanceColumns.VARIANT_HASH, null);
    assertThat(variantsAppender.rows).isEmpty();
  }

  @Test
  void shouldEmitOneDictionaryRowWithDeterministicSortedContentOnFirstCompletion() {
    // given: elements/flows activated in reverse-alphabetical order -- the emitted row's content
    // must still come out sorted
    final InMemoryTranslatorState state = new InMemoryTranslatorState();
    final CapturingRowAppender instanceAppender = new CapturingRowAppender();
    final CapturingRowAppender variantsAppender = new CapturingRowAppender();
    final LakeTranslator translator =
        new LakeTranslator(state, instanceAppender, new CapturingRowAppender(), variantsAppender);

    translator.onRecord(activateRoot(1L, 100L, 1L));
    translator.onRecord(activateElement(1L, 11L, "task-b", BpmnElementType.SERVICE_TASK, 101L, 2L));
    translator.onRecord(activateElement(1L, 12L, "task-a", BpmnElementType.SERVICE_TASK, 102L, 3L));
    translator.onRecord(takeSequenceFlow(1L, "flow-z", 103L, 4L));
    translator.onRecord(takeSequenceFlow(1L, "flow-a", 104L, 5L));
    translator.onRecord(completeRoot(1L, 200L, 6L));

    // then
    assertThat(variantsAppender.rows).hasSize(1);
    final Map<Integer, Object> row = variantsAppender.rows.get(0);
    assertThat(row.get(VariantColumns.PROCESS_ID)).isEqualTo(PROCESS_ID);
    assertThat(row.get(VariantColumns.VERSION)).isEqualTo(VERSION);
    assertThat(row.get(VariantColumns.VARIANT_HASH))
        .isEqualTo(instanceAppender.rows.get(0).get(InstanceColumns.VARIANT_HASH));
    assertThat(row.get(VariantColumns.ELEMENTS)).isEqualTo("task-a\ntask-b");
    assertThat(row.get(VariantColumns.FLOWS)).isEqualTo("flow-a\nflow-z");
  }

  @Test
  void shouldSuppressDuplicateDictionaryRowsAcrossInstancesOfTheSameVariant() {
    // given: two DIFFERENT instances that both fold the exact same variant (same element)
    final InMemoryTranslatorState state = new InMemoryTranslatorState();
    final CapturingRowAppender instanceAppender = new CapturingRowAppender();
    final CapturingRowAppender variantsAppender = new CapturingRowAppender();
    final LakeTranslator translator =
        new LakeTranslator(state, instanceAppender, new CapturingRowAppender(), variantsAppender);

    translator.onRecord(activateRoot(1L, 100L, 1L));
    translator.onRecord(activateElement(1L, 11L, "task-a", BpmnElementType.SERVICE_TASK, 101L, 2L));
    translator.onRecord(completeRoot(1L, 200L, 3L));

    translator.onRecord(activateRoot(2L, 100L, 4L));
    translator.onRecord(activateElement(2L, 21L, "task-a", BpmnElementType.SERVICE_TASK, 101L, 5L));
    translator.onRecord(completeRoot(2L, 200L, 6L));

    // then: only the FIRST completion produced a dictionary row -- the second was a seen-cache hit
    assertThat(variantsAppender.rows).hasSize(1);
  }

  @Test
  void shouldPopulateVariantHashWithoutAttemptingDictionaryEmissionWhenUnwired() {
    // given: the 3-arg constructor -- no variants dictionary pipeline wired at all
    final InMemoryTranslatorState state = new InMemoryTranslatorState();
    final CapturingRowAppender instanceAppender = new CapturingRowAppender();
    final LakeTranslator translator =
        new LakeTranslator(state, instanceAppender, new CapturingRowAppender());

    // when
    translator.onRecord(activateRoot(1L, 100L, 1L));
    translator.onRecord(activateElement(1L, 11L, "task-a", BpmnElementType.SERVICE_TASK, 101L, 2L));
    translator.onRecord(completeRoot(1L, 200L, 3L));

    // then: no exception, and the instance row's own variant_hash is populated regardless
    assertThat(instanceAppender.rows.get(0).get(InstanceColumns.VARIANT_HASH)).isNotNull();
  }

  // ---- record construction ------------------------------------------------------------------

  private static ZeebeRecord activateRoot(
      final long instanceKey, final long timestamp, final long position) {
    return processInstanceRecord(
        instanceKey,
        instanceKey,
        timestamp,
        BpmnElementType.PROCESS,
        "",
        ProcessInstanceIntent.ELEMENT_ACTIVATED,
        position);
  }

  private static ZeebeRecord completeRoot(
      final long instanceKey, final long timestamp, final long position) {
    return processInstanceRecord(
        instanceKey,
        instanceKey,
        timestamp,
        BpmnElementType.PROCESS,
        "",
        ProcessInstanceIntent.ELEMENT_COMPLETED,
        position);
  }

  private static ZeebeRecord activateElement(
      final long instanceKey,
      final long elementInstanceKey,
      final String elementId,
      final BpmnElementType elementType,
      final long timestamp,
      final long position) {
    return processInstanceRecord(
        instanceKey,
        elementInstanceKey,
        timestamp,
        elementType,
        elementId,
        ProcessInstanceIntent.ELEMENT_ACTIVATED,
        position);
  }

  private static ZeebeRecord takeSequenceFlow(
      final long instanceKey, final String flowId, final long timestamp, final long position) {
    return processInstanceRecord(
        instanceKey,
        instanceKey,
        timestamp,
        BpmnElementType.SEQUENCE_FLOW,
        flowId,
        ProcessInstanceIntent.SEQUENCE_FLOW_TAKEN,
        position);
  }

  private static ZeebeRecord processInstanceRecord(
      final long instanceKey,
      final long elementInstanceKey,
      final long timestamp,
      final BpmnElementType elementType,
      final String elementId,
      final ProcessInstanceIntent intent,
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
            .withIntent(intent)
            .withKey(elementInstanceKey)
            .withTimestamp(timestamp)
            .withPartitionId(ZEEBE_PARTITION)
            .withPosition(position)
            .withValue(value)
            .build();
    return new ZeebeRecord(TOPIC, 0, 0L, record);
  }

  // ---- test doubles -------------------------------------------------------------------------

  /**
   * Captures every appended row as a {@code column index -> value} map (String for {@code
   * putDict}/{@code putBinary}, boxed primitives otherwise, {@code null} for {@code putNull}) —
   * never engages backpressure.
   */
  private static final class CapturingRowAppender implements RowAppender {
    private final List<Map<Integer, Object>> rows = new ArrayList<>();
    private Map<Integer, Object> current;

    @Override
    public boolean begin() {
      current = new HashMap<>();
      return true;
    }

    @Override
    public RowAppender putLong(final int column, final long value) {
      current.put(column, value);
      return this;
    }

    @Override
    public RowAppender putInt(final int column, final int value) {
      current.put(column, value);
      return this;
    }

    @Override
    public RowAppender putDict(final int column, final CharSequence value) {
      current.put(column, value.toString());
      return this;
    }

    @Override
    public RowAppender putBinary(
        final int column, final byte[] src, final int offset, final int len) {
      current.put(column, new String(src, offset, len, StandardCharsets.UTF_8));
      return this;
    }

    @Override
    public RowAppender putDouble(final int column, final double value) {
      current.put(column, value);
      return this;
    }

    @Override
    public RowAppender putNull(final int column) {
      current.put(column, null);
      return this;
    }

    @Override
    public void endRow() {
      rows.add(current);
      current = null;
    }
  }

  /**
   * Minimal in-memory {@link TranslatorState} (mirrors {@code SinkIntegrationTest}'s/{@code
   * LakeTranslatorTest}'s own test doubles, duplicated here rather than shared — see their javadoc
   * for why), extended with real in-memory variant accumulator/name-map storage so the fold logic
   * under test runs against genuine state transitions.
   */
  private static final class InMemoryTranslatorState implements TranslatorState {
    private final Map<Long, OpenInstance> instances = new HashMap<>();
    private final Map<Long, OpenElement> elements = new HashMap<>();
    private final Map<Long, Map<String, String>> variables = new HashMap<>();
    private final Map<Long, VariantAccumulator> variantAccumulators = new HashMap<>();
    private final Map<String, VariantName> variantNames = new HashMap<>();
    private final Map<String, FlowEndpoints> flowEndpointsByKey = new HashMap<>();
    private final Map<Long, ObjectSightingList> objectSightings = new HashMap<>();

    @Override
    public void putFlowEndpoints(
        final long processDefinitionKey, final String flowId, final FlowEndpoints endpoints) {
      flowEndpointsByKey.put(processDefinitionKey + "#" + flowId, endpoints);
    }

    @Override
    public FlowEndpoints flowEndpoints(final long processDefinitionKey, final String flowId) {
      return flowEndpointsByKey.get(processDefinitionKey + "#" + flowId);
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
      variantNames.put(bpmnProcessId + '#' + h32, name);
    }

    @Override
    public VariantName getVariantName(final String bpmnProcessId, final int h32) {
      return variantNames.get(bpmnProcessId + '#' + h32);
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
    public void close() {}
  }
}

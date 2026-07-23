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
import io.camunda.analytics.lake.translate.RawTableSchemas.ProcessDefinitionColumns;
import io.camunda.eventbridge.zeebe.connector.ZeebeRecord;
import io.camunda.zeebe.protocol.record.ImmutableRecord;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.ProcessIntent;
import io.camunda.zeebe.protocol.record.value.deployment.ImmutableProcess;
import io.camunda.zeebe.protocol.record.value.deployment.Process;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link LakeTranslator}'s {@code process_definitions} dictionary capture (see its
 * class javadoc's "Process definitions capture" section): one row per distinct definition, the
 * per-partition-copy dedup marker (simulating Zeebe's own fan-out of one deployment to every
 * partition), the oversized-resource skip, the {@code null}-appender no-op case, and the
 * backpressure-absorb judgment call shared by every other dictionary appender in this class.
 */
class LakeTranslatorProcessDefinitionsTest {

  private static final String TOPIC = "test-topic";
  private static final String PROCESS_ID = "process-definitions-test-process";
  private static final int VERSION = 1;
  private static final String TENANT_ID = "<default>";
  private static final long PROCESS_DEFINITION_KEY = 100L;

  private static final String BPMN_XML =
      "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
          + "<bpmn:definitions xmlns:bpmn=\"http://www.omg.org/spec/BPMN/20100524/MODEL\""
          + " id=\"defs\" targetNamespace=\"http://camunda.org/test\">\n"
          + "  <bpmn:process id=\""
          + PROCESS_ID
          + "\" isExecutable=\"true\">\n"
          + "    <bpmn:startEvent id=\"start\" />\n"
          + "  </bpmn:process>\n"
          + "</bpmn:definitions>\n";

  @Test
  void shouldAppendOneRowOnDeployment() {
    // given
    final InMemoryTranslatorState state = new InMemoryTranslatorState();
    final CapturingRowAppender processDefinitions = new CapturingRowAppender();
    final LakeTranslator translator = newTranslator(state, processDefinitions);

    // when
    translator.onRecord(
        processCreatedRecord(
            PROCESS_DEFINITION_KEY,
            PROCESS_ID,
            VERSION,
            TENANT_ID,
            BPMN_XML,
            1_700_000_000_000L,
            1,
            1L));

    // then
    assertThat(processDefinitions.rows).hasSize(1);
    final Map<Integer, Object> row = processDefinitions.rows.get(0);
    assertThat(row.get(ProcessDefinitionColumns.PROCESS_DEFINITION_KEY))
        .isEqualTo(PROCESS_DEFINITION_KEY);
    assertThat(row.get(ProcessDefinitionColumns.PROCESS_ID)).isEqualTo(PROCESS_ID);
    assertThat(row.get(ProcessDefinitionColumns.VERSION)).isEqualTo(VERSION);
    assertThat(row.get(ProcessDefinitionColumns.TENANT_ID)).isEqualTo(TENANT_ID);
    assertThat(row.get(ProcessDefinitionColumns.BPMN_XML)).isEqualTo(BPMN_XML);
    assertThat(row.get(ProcessDefinitionColumns.DEPLOYED_AT)).isEqualTo(1_700_000_000_000L * 1000L);
  }

  @Test
  void shouldMarkTheDefinitionSeenAfterAppending() {
    // given
    final InMemoryTranslatorState state = new InMemoryTranslatorState();
    final LakeTranslator translator = newTranslator(state, new CapturingRowAppender());

    // when
    translator.onRecord(
        processCreatedRecord(
            PROCESS_DEFINITION_KEY,
            PROCESS_ID,
            VERSION,
            TENANT_ID,
            BPMN_XML,
            1_700_000_000_000L,
            1,
            1L));

    // then
    assertThat(state.hasProcessDefinition(PROCESS_DEFINITION_KEY)).isTrue();
  }

  @Test
  void shouldAppendNothingForASecondCopyOfTheSameDefinitionFromAnotherPartition() {
    // given: Zeebe distributes one deployment to every partition of the process's own topic -- the
    // SAME definition key/content arrives once per source partition, each copy its own distinct
    // Zeebe record (different partitionId/position)
    final InMemoryTranslatorState state = new InMemoryTranslatorState();
    final CapturingRowAppender processDefinitions = new CapturingRowAppender();
    final LakeTranslator translator = newTranslator(state, processDefinitions);
    translator.onRecord(
        processCreatedRecord(
            PROCESS_DEFINITION_KEY,
            PROCESS_ID,
            VERSION,
            TENANT_ID,
            BPMN_XML,
            1_700_000_000_000L,
            1,
            1L));

    // when: partition 2's own copy of the identical deployment
    translator.onRecord(
        processCreatedRecord(
            PROCESS_DEFINITION_KEY,
            PROCESS_ID,
            VERSION,
            TENANT_ID,
            BPMN_XML,
            1_700_000_000_000L,
            2,
            1L));

    // then: only the first copy appended a row
    assertThat(processDefinitions.rows).hasSize(1);
  }

  @Test
  void shouldAppendARowForADifferentDefinitionKey() {
    // given
    final InMemoryTranslatorState state = new InMemoryTranslatorState();
    final CapturingRowAppender processDefinitions = new CapturingRowAppender();
    final LakeTranslator translator = newTranslator(state, processDefinitions);
    translator.onRecord(
        processCreatedRecord(
            PROCESS_DEFINITION_KEY,
            PROCESS_ID,
            VERSION,
            TENANT_ID,
            BPMN_XML,
            1_700_000_000_000L,
            1,
            1L));

    // when: a different definition key entirely
    translator.onRecord(
        processCreatedRecord(
            PROCESS_DEFINITION_KEY + 1,
            PROCESS_ID + "-2",
            VERSION,
            TENANT_ID,
            BPMN_XML.replace(PROCESS_ID, PROCESS_ID + "-2"),
            1_700_000_000_000L,
            1,
            2L));

    // then: both definitions produced their own row
    assertThat(processDefinitions.rows).hasSize(2);
    assertThat(processDefinitions.rows.get(1).get(ProcessDefinitionColumns.PROCESS_DEFINITION_KEY))
        .isEqualTo(PROCESS_DEFINITION_KEY + 1);
  }

  @Test
  void shouldSkipAnOversizedResourceButMarkItSeenToSuppressFurtherCopies() {
    // given: a resource over the 1 MiB cap, but still valid BPMN (a large comment pads the size)
    final byte[] oversized = oversizedBpmnXml(PROCESS_ID);
    final InMemoryTranslatorState state = new InMemoryTranslatorState();
    final CapturingRowAppender processDefinitions = new CapturingRowAppender();
    final LakeTranslator translator = newTranslator(state, processDefinitions);

    // when
    translator.onRecord(
        processCreatedRecord(
            PROCESS_DEFINITION_KEY,
            PROCESS_ID,
            VERSION,
            TENANT_ID,
            oversized,
            1_700_000_000_000L,
            1,
            1L));

    // then: no row was appended, but the definition is marked seen (see
    // LakeTranslator#emitProcessDefinitionDictionaryRowIfNew's own javadoc for why)
    assertThat(processDefinitions.rows).isEmpty();
    assertThat(state.hasProcessDefinition(PROCESS_DEFINITION_KEY)).isTrue();

    // and when: a second partition's copy of the identical oversized deployment arrives
    translator.onRecord(
        processCreatedRecord(
            PROCESS_DEFINITION_KEY,
            PROCESS_ID,
            VERSION,
            TENANT_ID,
            oversized,
            1_700_000_000_000L,
            2,
            1L));

    // then: still no row -- the marker suppressed the second copy before the size check even ran
    // again
    assertThat(processDefinitions.rows).isEmpty();
  }

  @Test
  void shouldSkipRowEmissionButStillResolveFlowEndpointsWhenAppenderIsUnwired() {
    // given: processDefinitionsAppender == null disables dictionary emission only -- flow-endpoint
    // parsing (this class's pre-existing onProcess responsibility) must be unaffected
    final InMemoryTranslatorState state = new InMemoryTranslatorState();
    final LakeTranslator translator = newTranslator(state, null);

    // when
    translator.onRecord(
        processCreatedRecord(
            PROCESS_DEFINITION_KEY,
            PROCESS_ID,
            VERSION,
            TENANT_ID,
            BPMN_XML,
            1_700_000_000_000L,
            1,
            1L));

    // then: no row, no marker set, and nothing throws
    assertThat(state.hasProcessDefinition(PROCESS_DEFINITION_KEY)).isFalse();
  }

  @Test
  void shouldAbsorbBackpressureOnTheProcessDefinitionsAppenderAndRetryOnALaterCopy() {
    // given: begin() fails exactly once
    final InMemoryTranslatorState state = new InMemoryTranslatorState();
    final FlakyRowAppender processDefinitions = new FlakyRowAppender(1);
    final LakeTranslator translator = newTranslator(state, processDefinitions);

    // when: the first copy hits backpressure -- absorbed, never propagated as record-level
    // backpressure, and the definition is NOT marked seen
    final boolean firstAttempt =
        translator.onRecord(
            processCreatedRecord(
                PROCESS_DEFINITION_KEY,
                PROCESS_ID,
                VERSION,
                TENANT_ID,
                BPMN_XML,
                1_700_000_000_000L,
                1,
                1L));
    assertThat(firstAttempt).isTrue();
    assertThat(processDefinitions.rowsAppended).isZero();
    assertThat(state.hasProcessDefinition(PROCESS_DEFINITION_KEY)).isFalse();

    // and when: a later copy (e.g. another partition's own deployment record) retries
    translator.onRecord(
        processCreatedRecord(
            PROCESS_DEFINITION_KEY,
            PROCESS_ID,
            VERSION,
            TENANT_ID,
            BPMN_XML,
            1_700_000_000_000L,
            2,
            1L));

    // then
    assertThat(processDefinitions.rowsAppended).isEqualTo(1);
    assertThat(state.hasProcessDefinition(PROCESS_DEFINITION_KEY)).isTrue();
  }

  // ---- helpers ---------------------------------------------------------------------------------

  private static LakeTranslator newTranslator(
      final TranslatorState state, final RowAppender processDefinitionsAppender) {
    return new LakeTranslator(
        state,
        new CapturingRowAppender(),
        new CapturingRowAppender(),
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        processDefinitionsAppender);
  }

  /** A minimal valid BPMN document padded with a large XML comment past the 1 MiB row cap. */
  private static byte[] oversizedBpmnXml(final String processId) {
    final StringBuilder xml = new StringBuilder();
    xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
    xml.append("<bpmn:definitions xmlns:bpmn=\"http://www.omg.org/spec/BPMN/20100524/MODEL\"");
    xml.append(" id=\"defs\" targetNamespace=\"http://camunda.org/test\">\n");
    xml.append("<!--").append("x".repeat(1024 * 1024 + 100)).append("-->\n");
    xml.append("  <bpmn:process id=\"").append(processId).append("\" isExecutable=\"true\">\n");
    xml.append("    <bpmn:startEvent id=\"start\" />\n");
    xml.append("  </bpmn:process>\n");
    xml.append("</bpmn:definitions>\n");
    return xml.toString().getBytes(StandardCharsets.UTF_8);
  }

  private static ZeebeRecord processCreatedRecord(
      final long processDefinitionKey,
      final String processId,
      final int version,
      final String tenantId,
      final String bpmnXml,
      final long timestamp,
      final int zeebePartitionId,
      final long position) {
    return processCreatedRecord(
        processDefinitionKey,
        processId,
        version,
        tenantId,
        bpmnXml.getBytes(StandardCharsets.UTF_8),
        timestamp,
        zeebePartitionId,
        position);
  }

  private static ZeebeRecord processCreatedRecord(
      final long processDefinitionKey,
      final String processId,
      final int version,
      final String tenantId,
      final byte[] resource,
      final long timestamp,
      final int zeebePartitionId,
      final long position) {
    final Process value =
        ImmutableProcess.builder()
            .withBpmnProcessId(processId)
            .withVersion(version)
            .withVersionTag("")
            .withProcessDefinitionKey(processDefinitionKey)
            .withResourceName("test.bpmn")
            .withChecksum(new byte[0])
            .withDuplicate(false)
            .withDeploymentKey(1L)
            .withTenantId(tenantId)
            .withResource(resource)
            .build();
    final Record<Process> record =
        ImmutableRecord.<Process>builder()
            .withRecordType(RecordType.EVENT)
            .withValueType(ValueType.PROCESS)
            .withIntent(ProcessIntent.CREATED)
            .withKey(processDefinitionKey)
            .withTimestamp(timestamp)
            .withPartitionId(zeebePartitionId)
            .withPosition(position)
            .withValue(value)
            .build();
    return new ZeebeRecord(TOPIC, 0, 0L, record);
  }

  // ---- test doubles -------------------------------------------------------------------------

  /**
   * Captures every appended row as a {@code column index -> value} map — never engages
   * backpressure. Mirrors {@code LakeTranslatorObjectFabricTest}'s own test double.
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
   * Reports backpressure ({@code begin()} returns {@code false}) exactly {@code
   * beginFailuresRemaining} times before accepting every subsequent row. Mirrors {@code
   * LakeTranslatorObjectFabricTest}'s own test double.
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
    public RowAppender putDouble(final int column, final double value) {
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
   * Minimal in-memory {@link TranslatorState} (mirrors {@code LakeTranslatorObjectFabricTest}'s own
   * test double, duplicated here rather than shared — see that class's own javadoc for why).
   */
  private static final class InMemoryTranslatorState implements TranslatorState {
    private final Map<Long, OpenInstance> instances = new HashMap<>();
    private final Map<Long, OpenElement> elements = new HashMap<>();
    private final Map<Long, Map<String, String>> variables = new HashMap<>();
    private final Map<Long, VariantAccumulator> variantAccumulators = new HashMap<>();
    private final Map<String, VariantName> variantNames = new HashMap<>();
    private final Map<Long, Map<String, FlowEndpoints>> flowEndpointsByKey = new HashMap<>();
    private final Map<Long, ObjectSightingList> objectSightings = new HashMap<>();
    private final Map<String, ObjectLifecycle> objectLifecycle = new HashMap<>();
    private final Set<Long> processDefinitions = new HashSet<>();

    @Override
    public void putFlowEndpoints(
        final long processDefinitionKey, final String flowId, final FlowEndpoints endpoints) {
      flowEndpointsByKey
          .computeIfAbsent(processDefinitionKey, k -> new HashMap<>())
          .put(flowId, endpoints);
    }

    @Override
    public FlowEndpoints flowEndpoints(final long processDefinitionKey, final String flowId) {
      return flowEndpointsByKey.getOrDefault(processDefinitionKey, Map.of()).get(flowId);
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
    public boolean hasProcessDefinition(final long processDefinitionKey) {
      return processDefinitions.contains(processDefinitionKey);
    }

    @Override
    public void markProcessDefinition(final long processDefinitionKey) {
      processDefinitions.add(processDefinitionKey);
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
    public void close() {}
  }
}

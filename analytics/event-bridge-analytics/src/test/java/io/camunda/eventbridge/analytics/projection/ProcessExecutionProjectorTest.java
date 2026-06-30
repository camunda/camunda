/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.analytics.projection;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.eventbridge.analytics.element.ElementExecutionFact;
import io.camunda.eventbridge.analytics.fact.ProcessExecutionFact;
import io.camunda.eventbridge.analytics.fact.ProcessInstanceExecutionTimeFact;
import io.camunda.eventbridge.zeebe.connector.ZeebeRecord;
import io.camunda.zeebe.protocol.impl.encoding.MsgPackConverter;
import io.camunda.zeebe.protocol.impl.record.CopiedRecord;
import io.camunda.zeebe.protocol.impl.record.RecordMetadata;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceRecord;
import io.camunda.zeebe.protocol.impl.record.value.variable.VariableRecord;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.intent.VariableIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.ArrayList;
import java.util.List;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;

/**
 * The one base-projection fold derives both fact types — process-instance time and element time.
 */
final class ProcessExecutionProjectorTest {

  private static final long PI_KEY = 123L;

  private final BaseProjectionStore store = StateBackedProjectionStore.inMemory();
  private final ProcessExecutionProjector projector = new ProcessExecutionProjector(store);
  private final List<ProcessExecutionFact> facts = new ArrayList<>();

  private void apply(final ZeebeRecord record) {
    projector.apply(record, facts::add);
  }

  private List<ProcessInstanceExecutionTimeFact> instanceFacts() {
    return facts.stream()
        .filter(ProcessInstanceExecutionTimeFact.class::isInstance)
        .map(ProcessInstanceExecutionTimeFact.class::cast)
        .toList();
  }

  private List<ElementExecutionFact> elementFacts() {
    return facts.stream()
        .filter(ElementExecutionFact.class::isInstance)
        .map(ElementExecutionFact.class::cast)
        .toList();
  }

  @Test
  void shouldDeriveExecutionTimeFactOnInstanceCompletion() {
    apply(process(ProcessInstanceIntent.ELEMENT_ACTIVATED, 1000L, 10L));
    apply(process(ProcessInstanceIntent.ELEMENT_COMPLETED, 1500L, 11L));

    assertThat(elementFacts()).isEmpty();
    assertThat(instanceFacts())
        .singleElement()
        .satisfies(f -> assertThat(f.durationMs()).isEqualTo(500L));
  }

  @Test
  void shouldDeriveElementFactOnElementCompletion() {
    apply(element("reviewOrder", ProcessInstanceIntent.ELEMENT_ACTIVATED, 1000L, 10L));
    apply(element("reviewOrder", ProcessInstanceIntent.ELEMENT_COMPLETED, 1300L, 11L));

    assertThat(instanceFacts()).isEmpty();
    assertThat(elementFacts())
        .singleElement()
        .satisfies(
            f -> {
              assertThat(f.elementId()).isEqualTo("reviewOrder");
              assertThat(f.durationMs()).isEqualTo(300L);
            });
  }

  @Test
  void shouldEnrichInstanceFactWithVariables() {
    apply(process(ProcessInstanceIntent.ELEMENT_ACTIVATED, 1000L, 10L));
    apply(variable("region", "EU", 11L));
    apply(process(ProcessInstanceIntent.ELEMENT_COMPLETED, 1500L, 12L));

    assertThat(instanceFacts())
        .singleElement()
        .satisfies(f -> assertThat(f.variables()).containsEntry("region", "EU"));
  }

  @Test
  void shouldServeBothMetricsFromOneFold() {
    // a full instance: activated, region variable, one element runs, then completes
    apply(process(ProcessInstanceIntent.ELEMENT_ACTIVATED, 1000L, 10L));
    apply(variable("region", "US", 11L));
    apply(element("processOrder", ProcessInstanceIntent.ELEMENT_ACTIVATED, 1100L, 12L));
    apply(element("processOrder", ProcessInstanceIntent.ELEMENT_COMPLETED, 1400L, 13L));
    apply(process(ProcessInstanceIntent.ELEMENT_COMPLETED, 1500L, 14L));

    assertThat(elementFacts())
        .singleElement()
        .satisfies(
            f -> {
              assertThat(f.elementId()).isEqualTo("processOrder");
              assertThat(f.durationMs()).isEqualTo(300L); // 1400 - 1100
            });
    assertThat(instanceFacts())
        .singleElement()
        .satisfies(
            f -> {
              assertThat(f.durationMs()).isEqualTo(500L); // 1500 - 1000
              assertThat(f.variables()).containsEntry("region", "US");
            });
  }

  private static ZeebeRecord process(
      final ProcessInstanceIntent intent, final long timestamp, final long position) {
    return processInstance(intent, BpmnElementType.PROCESS, "the-process", timestamp, position);
  }

  private static ZeebeRecord element(
      final String elementId,
      final ProcessInstanceIntent intent,
      final long timestamp,
      final long position) {
    return processInstance(
        intent, BpmnElementType.INTERMEDIATE_CATCH_EVENT, elementId, timestamp, position);
  }

  private static ZeebeRecord processInstance(
      final ProcessInstanceIntent intent,
      final BpmnElementType elementType,
      final String elementId,
      final long timestamp,
      final long position) {
    final ProcessInstanceRecord value =
        new ProcessInstanceRecord()
            .setProcessInstanceKey(PI_KEY)
            .setProcessDefinitionKey(77L)
            .setBpmnProcessId("order")
            .setVersion(3)
            .setTenantId("<default>")
            .setElementId(elementId)
            .setBpmnElementType(elementType);
    final RecordMetadata metadata =
        new RecordMetadata()
            .recordType(RecordType.EVENT)
            .valueType(ValueType.PROCESS_INSTANCE)
            .intent(intent);
    final Record<ProcessInstanceRecord> record =
        new CopiedRecord<>(value, metadata, PI_KEY, 1, position, position - 1, timestamp);
    return new ZeebeRecord("zeebe-records", 1, position, record);
  }

  private static ZeebeRecord variable(final String name, final String value, final long position) {
    final VariableRecord variable =
        new VariableRecord()
            .setProcessInstanceKey(PI_KEY)
            .setScopeKey(PI_KEY)
            .setName(BufferUtil.wrapString(name))
            .setValue(new UnsafeBuffer(MsgPackConverter.convertToMsgPack("\"" + value + "\"")));
    final RecordMetadata metadata =
        new RecordMetadata()
            .recordType(RecordType.EVENT)
            .valueType(ValueType.VARIABLE)
            .intent(VariableIntent.CREATED);
    final Record<VariableRecord> record =
        new CopiedRecord<>(variable, metadata, PI_KEY, 1, position, position - 1, position);
    return new ZeebeRecord("zeebe-records", 1, position, record);
  }
}

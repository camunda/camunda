/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.projection;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.analytics.fact.Fact;
import io.camunda.analytics.fact.FactType;
import io.camunda.analytics.fact.Transition;
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

final class AnalyticsFactProjectorTest {

  private static final long PI_KEY = 123L;

  private final BaseProjectionStore store = StateBackedProjectionStore.inMemory();
  private final AnalyticsFactProjector projector = new AnalyticsFactProjector(store);
  private final List<Fact> facts = new ArrayList<>();

  private void apply(final SourceRecord record) {
    projector.apply(record, facts::add);
  }

  private Fact only(final FactType type, final Transition transition) {
    final List<Fact> matches =
        facts.stream()
            .filter(f -> f.factType() == type && transition.name().equals(f.get(Fact.TRANSITION)))
            .toList();
    assertThat(matches).as("facts of %s/%s", type, transition).hasSize(1);
    return matches.get(0);
  }

  @Test
  void shouldEmitActivatedAndCompletedInstanceFacts() {
    apply(process(ProcessInstanceIntent.ELEMENT_ACTIVATED, 1000L, 10L));
    apply(process(ProcessInstanceIntent.ELEMENT_COMPLETED, 1500L, 11L));

    // an ACTIVATED fact with no duration, and a COMPLETED fact carrying the derived duration
    assertThat(only(FactType.PROCESS_INSTANCE, Transition.ACTIVATED).get("durationMs")).isNull();
    final Fact completed = only(FactType.PROCESS_INSTANCE, Transition.COMPLETED);
    assertThat(completed.get("durationMs")).isEqualTo(500L);
    assertThat(completed.get("completedNormally")).isEqualTo(true);
    assertThat(completed.get("hadIncident")).isEqualTo(false);
    assertThat(completed.get("bpmnProcessId")).isEqualTo("order");
    assertThat(completed.eventTime()).isEqualTo(1500L);
  }

  @Test
  void shouldTagTerminationDistinctly() {
    apply(process(ProcessInstanceIntent.ELEMENT_ACTIVATED, 1000L, 10L));
    apply(process(ProcessInstanceIntent.ELEMENT_TERMINATED, 1200L, 11L));

    final Fact terminated = only(FactType.PROCESS_INSTANCE, Transition.TERMINATED);
    assertThat(terminated.get("durationMs")).isEqualTo(200L);
    assertThat(terminated.get("completedNormally")).isEqualTo(false);
  }

  @Test
  void shouldEnrichCompletedInstanceWithNamespacedVariable() {
    apply(process(ProcessInstanceIntent.ELEMENT_ACTIVATED, 1000L, 10L));
    apply(variable("region", "EU", 11L));
    apply(process(ProcessInstanceIntent.ELEMENT_COMPLETED, 1500L, 12L));

    // the variable is stamped under the var. namespace so it cannot collide with a structural field
    assertThat(only(FactType.PROCESS_INSTANCE, Transition.COMPLETED).get("var.region"))
        .isEqualTo("EU");
  }

  @Test
  void shouldEmitElementFacts() {
    apply(element("reviewOrder", ProcessInstanceIntent.ELEMENT_ACTIVATED, 1000L, 10L));
    apply(element("reviewOrder", ProcessInstanceIntent.ELEMENT_COMPLETED, 1300L, 11L));

    final Fact completed = only(FactType.ELEMENT, Transition.COMPLETED);
    assertThat(completed.get("elementId")).isEqualTo("reviewOrder");
    assertThat(completed.get("elementType"))
        .isEqualTo(BpmnElementType.INTERMEDIATE_CATCH_EVENT.name());
    assertThat(completed.get("durationMs")).isEqualTo(300L);
  }

  private static SourceRecord process(
      final ProcessInstanceIntent intent, final long timestamp, final long position) {
    return processInstance(intent, BpmnElementType.PROCESS, "the-process", timestamp, position);
  }

  private static SourceRecord element(
      final String elementId,
      final ProcessInstanceIntent intent,
      final long timestamp,
      final long position) {
    return processInstance(
        intent, BpmnElementType.INTERMEDIATE_CATCH_EVENT, elementId, timestamp, position);
  }

  private static SourceRecord processInstance(
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
    return new SourceRecord(1, position, record);
  }

  private static SourceRecord variable(final String name, final String value, final long position) {
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
    return new SourceRecord(1, position, record);
  }
}

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.analytics.projection;

import static org.assertj.core.api.Assertions.assertThat;

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
import java.util.Optional;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;

final class ProcessInstanceProjectorTest {

  private static final long PI_KEY = 123L;

  private final BaseProjectionStore store = StateBackedProjectionStore.inMemory();
  private final ProcessInstanceProjector projector = new ProcessInstanceProjector(store);

  @Test
  void shouldDeriveExecutionTimeFactWhenInstanceCompletes() {
    // given
    projector.apply(
        event(ProcessInstanceIntent.ELEMENT_ACTIVATED, BpmnElementType.PROCESS, 1000L, 10L));

    // when
    final Optional<ProcessInstanceExecutionTimeFact> fact =
        projector.apply(
            event(ProcessInstanceIntent.ELEMENT_COMPLETED, BpmnElementType.PROCESS, 1500L, 11L));

    // then
    assertThat(fact).isPresent();
    final ProcessInstanceExecutionTimeFact f = fact.orElseThrow();
    assertThat(f.processInstanceKey()).isEqualTo(PI_KEY);
    assertThat(f.processDefinitionKey()).isEqualTo(77L);
    assertThat(f.bpmnProcessId()).isEqualTo("order");
    assertThat(f.version()).isEqualTo(3);
    assertThat(f.startTime()).isEqualTo(1000L);
    assertThat(f.endTime()).isEqualTo(1500L);
    assertThat(f.durationMs()).isEqualTo(500L);
    assertThat(f.completedNormally()).isTrue();
    assertThat(f.sourcePartitionId()).isEqualTo(1);
    assertThat(f.sourcePosition()).isEqualTo(11L);
  }

  @Test
  void shouldNotEmitFactBeforeCompletion() {
    // when — only the activation is seen
    final Optional<ProcessInstanceExecutionTimeFact> fact =
        projector.apply(
            event(ProcessInstanceIntent.ELEMENT_ACTIVATED, BpmnElementType.PROCESS, 1000L, 10L));

    // then
    assertThat(fact).isEmpty();
    final ProcessInstanceProjection projection = store.get(PI_KEY).orElseThrow();
    assertThat(projection.hasStart()).isTrue();
    assertThat(projection.isComplete()).isFalse();
    assertThat(projection.factEmitted()).isFalse();
  }

  @Test
  void shouldEnrichFactWithCapturedVariables() {
    // given — an instance whose region variable is observed between activation and completion
    projector.apply(
        event(ProcessInstanceIntent.ELEMENT_ACTIVATED, BpmnElementType.PROCESS, 1000L, 10L));
    final Optional<ProcessInstanceExecutionTimeFact> none =
        projector.apply(variableEvent("region", "EU", 11L));
    assertThat(none).isEmpty();

    // when
    final Optional<ProcessInstanceExecutionTimeFact> fact =
        projector.apply(
            event(ProcessInstanceIntent.ELEMENT_COMPLETED, BpmnElementType.PROCESS, 1500L, 12L));

    // then — the JSON-quoted value is unquoted and carried on the fact
    assertThat(fact).isPresent();
    assertThat(fact.orElseThrow().variables()).containsEntry("region", "EU");
  }

  @Test
  void shouldFlagTerminatedInstanceAsNotCompletedNormally() {
    // given
    projector.apply(
        event(ProcessInstanceIntent.ELEMENT_ACTIVATED, BpmnElementType.PROCESS, 1000L, 10L));

    // when
    final Optional<ProcessInstanceExecutionTimeFact> fact =
        projector.apply(
            event(ProcessInstanceIntent.ELEMENT_TERMINATED, BpmnElementType.PROCESS, 1200L, 11L));

    // then
    assertThat(fact).isPresent();
    assertThat(fact.orElseThrow().completedNormally()).isFalse();
    assertThat(fact.orElseThrow().durationMs()).isEqualTo(200L);
  }

  @Test
  void shouldEmitFactOnlyOnce() {
    // given — a completed instance that already produced its fact
    projector.apply(
        event(ProcessInstanceIntent.ELEMENT_ACTIVATED, BpmnElementType.PROCESS, 1000L, 10L));
    projector.apply(
        event(ProcessInstanceIntent.ELEMENT_COMPLETED, BpmnElementType.PROCESS, 1500L, 11L));

    // when — the completion record is redelivered (at-least-once)
    final Optional<ProcessInstanceExecutionTimeFact> redelivered =
        projector.apply(
            event(ProcessInstanceIntent.ELEMENT_COMPLETED, BpmnElementType.PROCESS, 1500L, 12L));

    // then
    assertThat(redelivered).isEmpty();
  }

  @Test
  void shouldIgnoreNonRootElements() {
    // when — a child service task element, not the root process
    final Optional<ProcessInstanceExecutionTimeFact> fact =
        projector.apply(
            event(
                ProcessInstanceIntent.ELEMENT_COMPLETED, BpmnElementType.SERVICE_TASK, 1500L, 11L));

    // then
    assertThat(fact).isEmpty();
    assertThat(store.get(PI_KEY)).isEmpty();
  }

  @Test
  void shouldAdvanceConsumedPosition() {
    // when
    projector.apply(
        event(ProcessInstanceIntent.ELEMENT_ACTIVATED, BpmnElementType.PROCESS, 1000L, 10L));

    // then
    assertThat(store.getConsumedPosition()).isEqualTo(10L);
  }

  private static ZeebeRecord event(
      final ProcessInstanceIntent intent,
      final BpmnElementType elementType,
      final long timestamp,
      final long position) {
    final ProcessInstanceRecord value =
        new ProcessInstanceRecord()
            .setProcessInstanceKey(PI_KEY)
            .setProcessDefinitionKey(77L)
            .setBpmnProcessId("order")
            .setVersion(3)
            .setTenantId("<default>")
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

  private static ZeebeRecord variableEvent(
      final String name, final String value, final long position) {
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

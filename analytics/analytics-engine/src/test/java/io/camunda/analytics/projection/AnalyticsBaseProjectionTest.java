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
import io.camunda.analytics.state.ElementStatus;
import io.camunda.analytics.state.StateBackedProjectionState;
import io.camunda.eventbridge.streaming.processor.ProcessorContext;
import io.camunda.eventbridge.streaming.processor.PunctuationType;
import io.camunda.eventbridge.streaming.processor.Punctuator;
import io.camunda.zeebe.protocol.impl.encoding.MsgPackConverter;
import io.camunda.zeebe.protocol.impl.record.CopiedRecord;
import io.camunda.zeebe.protocol.impl.record.RecordMetadata;
import io.camunda.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.camunda.zeebe.protocol.impl.record.value.incident.IncidentRecord;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceRecord;
import io.camunda.zeebe.protocol.impl.record.value.variable.VariableRecord;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.IncidentIntent;
import io.camunda.zeebe.protocol.record.intent.Intent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.intent.VariableIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.ErrorType;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;

final class AnalyticsBaseProjectionTest {

  private static final long PI_KEY = 123L;
  private static final long TASK_KEY = 456L;

  private final StateBackedProjectionState state = StateBackedProjectionState.inMemory();
  private final CapturingContext context = new CapturingContext();
  private final AnalyticsBaseProjection projection =
      new AnalyticsBaseProjection(state, Set.of("region"));

  AnalyticsBaseProjectionTest() {
    projection.init(context);
  }

  private Fact only(final FactType type, final Transition transition) {
    final List<Fact> matches =
        context.facts.stream()
            .filter(f -> f.factType() == type && transition.name().equals(f.get(Fact.TRANSITION)))
            .toList();
    assertThat(matches).as("facts of %s/%s", type, transition).hasSize(1);
    return matches.get(0);
  }

  @Test
  void shouldEmitActivatedAndCompletedInstanceFacts() {
    projection.process(process(ProcessInstanceIntent.ELEMENT_ACTIVATED, 1000L, 10L));
    projection.process(process(ProcessInstanceIntent.ELEMENT_COMPLETED, 1500L, 11L));

    assertThat(only(FactType.PROCESS_INSTANCE, Transition.ACTIVATED).get("durationMs")).isNull();
    final Fact completed = only(FactType.PROCESS_INSTANCE, Transition.COMPLETED);
    assertThat(completed.get("durationMs")).isEqualTo(500L);
    assertThat(completed.get("startTime")).isEqualTo(1000L);
    assertThat(completed.get("endTime")).isEqualTo(1500L);
    assertThat(completed.get("completedNormally")).isEqualTo(true);
    assertThat(completed.get("hadIncident")).isEqualTo(false);
    assertThat(completed.get("bpmnProcessId")).isEqualTo("order");
    assertThat(completed.eventTime()).isEqualTo(1500L);
  }

  @Test
  void shouldTagTerminationDistinctly() {
    projection.process(process(ProcessInstanceIntent.ELEMENT_ACTIVATED, 1000L, 10L));
    projection.process(process(ProcessInstanceIntent.ELEMENT_TERMINATED, 1200L, 11L));

    final Fact terminated = only(FactType.PROCESS_INSTANCE, Transition.TERMINATED);
    assertThat(terminated.get("durationMs")).isEqualTo(200L);
    assertThat(terminated.get("completedNormally")).isEqualTo(false);
  }

  @Test
  void shouldMaterializeTheRowThenEvictItAfterEmit() {
    // given an activated process instance, the row is queryable in its own right (Model A)
    projection.process(process(ProcessInstanceIntent.ELEMENT_ACTIVATED, 1000L, 10L));
    assertThat(state.element(PI_KEY)).isNotNull();
    assertThat(state.element(PI_KEY).status()).isEqualTo(ElementStatus.ACTIVE);
    assertThat(state.element(PI_KEY).start()).isEqualTo(1000L);

    // when it completes
    projection.process(variable(PI_KEY, "region", "EU", 11L));
    projection.process(process(ProcessInstanceIntent.ELEMENT_COMPLETED, 1500L, 12L));

    // then the terminal row and its variables are evicted after the fact is derived
    assertThat(state.element(PI_KEY)).as("row evicted after emit").isNull();
    assertThat(state.variables(PI_KEY)).as("variables cleared with the scope").isEmpty();
  }

  @Test
  void shouldEnrichCompletionWithNamespacedVariable() {
    projection.process(process(ProcessInstanceIntent.ELEMENT_ACTIVATED, 1000L, 10L));
    projection.process(variable(PI_KEY, "region", "EU", 11L));
    projection.process(process(ProcessInstanceIntent.ELEMENT_COMPLETED, 1500L, 12L));

    assertThat(only(FactType.PROCESS_INSTANCE, Transition.COMPLETED).get("var.region"))
        .isEqualTo("EU");
  }

  @Test
  void shouldResolveVariablesUpTheScopeHierarchy() {
    // given a process-scoped variable and a task nested in that process instance
    projection.process(process(ProcessInstanceIntent.ELEMENT_ACTIVATED, 1000L, 10L));
    projection.process(variable(PI_KEY, "region", "EU", 11L));
    projection.process(task(ProcessInstanceIntent.ELEMENT_ACTIVATED, 1100L, 12L));
    // the task has no local variable, but resolves the process-instance-scoped one up its parent
    // (flow) scope chain — the engine's variable visibility
    assertThat(state.variables(TASK_KEY))
        .as("resolved up the scope hierarchy")
        .containsEntry("region", "EU");

    // when the task completes
    projection.process(task(ProcessInstanceIntent.ELEMENT_COMPLETED, 1300L, 13L));

    // then its completion fact carries the process-instance-scoped variable
    assertThat(only(FactType.ELEMENT, Transition.COMPLETED).get("var.region")).isEqualTo("EU");
  }

  @Test
  void shouldStampHadIncidentOnTheElementItOccurredOn() {
    projection.process(process(ProcessInstanceIntent.ELEMENT_ACTIVATED, 1000L, 10L));
    projection.process(incident(IncidentIntent.CREATED, PI_KEY, 1100L, 11L));
    projection.process(process(ProcessInstanceIntent.ELEMENT_COMPLETED, 1500L, 12L));

    assertThat(only(FactType.PROCESS_INSTANCE, Transition.COMPLETED).get("hadIncident"))
        .isEqualTo(true);
  }

  @Test
  void shouldDeriveIncidentResolutionDurationFromTheRow() {
    projection.process(incident(IncidentIntent.CREATED, TASK_KEY, 1000L, 10L));
    projection.process(incident(IncidentIntent.RESOLVED, TASK_KEY, 1700L, 11L));

    final Fact created = only(FactType.INCIDENT, Transition.CREATED);
    assertThat(created.get("delta")).isEqualTo(1L);
    assertThat(created.get("errorType")).isEqualTo("JOB_NO_RETRIES");
    final Fact resolved = only(FactType.INCIDENT, Transition.RESOLVED);
    assertThat(resolved.get("delta")).isEqualTo(-1L);
    assertThat(resolved.get("durationMs")).isEqualTo(700L);
    // the incident type is read from the materialized row (Model A), not just the resolve record
    assertThat(resolved.get("errorType")).isEqualTo("JOB_NO_RETRIES");
    // the incident row is evicted after resolution
    assertThat(state.incident(TASK_KEY)).isNull();
  }

  private static SourceRecord process(
      final ProcessInstanceIntent intent, final long timestamp, final long position) {
    return processInstance(
        PI_KEY, -1L, intent, BpmnElementType.PROCESS, "order", timestamp, position);
  }

  private static SourceRecord task(
      final ProcessInstanceIntent intent, final long timestamp, final long position) {
    return processInstance(
        TASK_KEY, PI_KEY, intent, BpmnElementType.SERVICE_TASK, "reviewOrder", timestamp, position);
  }

  private static SourceRecord processInstance(
      final long elementInstanceKey,
      final long flowScopeKey,
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
            .setFlowScopeKey(flowScopeKey)
            .setBpmnElementType(elementType);
    return record(
        value, ValueType.PROCESS_INSTANCE, intent, elementInstanceKey, position, timestamp);
  }

  private static SourceRecord variable(
      final long scopeKey, final String name, final String value, final long position) {
    final VariableRecord variable =
        new VariableRecord()
            .setProcessInstanceKey(PI_KEY)
            .setScopeKey(scopeKey)
            .setName(BufferUtil.wrapString(name))
            .setValue(new UnsafeBuffer(MsgPackConverter.convertToMsgPack("\"" + value + "\"")));
    return record(
        variable, ValueType.VARIABLE, VariableIntent.CREATED, scopeKey, position, position);
  }

  private static SourceRecord incident(
      final IncidentIntent intent,
      final long elementInstanceKey,
      final long timestamp,
      final long position) {
    final IncidentRecord incident =
        new IncidentRecord()
            .setProcessInstanceKey(PI_KEY)
            .setElementInstanceKey(elementInstanceKey)
            .setBpmnProcessId(BufferUtil.wrapString("order"))
            .setElementId(BufferUtil.wrapString("reviewOrder"))
            .setTenantId("<default>")
            .setErrorType(ErrorType.JOB_NO_RETRIES);
    return record(incident, ValueType.INCIDENT, intent, position, position, timestamp);
  }

  private static SourceRecord record(
      final UnifiedRecordValue value,
      final ValueType valueType,
      final Intent intent,
      final long key,
      final long position,
      final long timestamp) {
    final RecordMetadata metadata =
        new RecordMetadata().recordType(RecordType.EVENT).valueType(valueType).intent(intent);
    final Record<?> record =
        new CopiedRecord<>(value, metadata, key, 1, position, position - 1, timestamp);
    return new SourceRecord(1, position, record);
  }

  /** A {@link ProcessorContext} that captures the forwarded (materialized) facts. */
  private static final class CapturingContext implements ProcessorContext<Fact> {

    private final List<Fact> facts = new ArrayList<>();

    @Override
    public void forward(final Fact value) {
      // Materialize now — while the projection is still live (before evict) — mirroring how a
      // downstream aggregate resolves a fact's variables at fold time, so post-hoc assertions see
      // the resolved snapshot.
      facts.add(value.materialize());
    }

    @Override
    public void forward(final Fact value, final String childName) {
      facts.add(value.materialize());
    }

    @Override
    public void schedule(
        final Duration interval, final PunctuationType type, final Punctuator punctuator) {
      // no punctuation used by the base projection
    }

    @Override
    public <S> S getStateStore(final String name) {
      throw new UnsupportedOperationException();
    }
  }
}

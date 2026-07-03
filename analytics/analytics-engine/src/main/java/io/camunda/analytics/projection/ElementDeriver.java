/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.projection;

import io.camunda.analytics.dataset.DimensionSpec;
import io.camunda.analytics.dataset.EnrichmentTiming;
import io.camunda.analytics.fact.Fact;
import io.camunda.analytics.fact.FactType;
import io.camunda.analytics.fact.Transition;
import io.camunda.eventbridge.streaming.fold.Collector;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceRecordValue;
import java.util.Map;

/**
 * Folds {@code PROCESS_INSTANCE} records — every BPMN element, the root {@code PROCESS} included,
 * since an instance is just the root element. {@code ELEMENT_ACTIVATED} records the element's start
 * and emits an {@code ACTIVATED} fact; {@code ELEMENT_COMPLETED}/{@code ELEMENT_TERMINATED} reads
 * that start, derives the execution duration, and emits a {@code COMPLETED}/{@code TERMINATED}
 * fact. A completing instance additionally carries its {@link EnrichmentTiming#EVENT_TIME} variable
 * snapshot and its {@code hadIncident} flag, then drops both from the read-model.
 */
final class ElementDeriver implements FactDeriver {

  @Override
  public void derive(
      final SourceRecord source, final BaseProjectionStore state, final Collector<Fact> out) {
    final Record<?> record = source.record();
    if (!(record.getValue() instanceof final ProcessInstanceRecordValue value)
        || !(record.getIntent() instanceof final ProcessInstanceIntent intent)) {
      return;
    }
    final boolean isProcess = value.getBpmnElementType() == BpmnElementType.PROCESS;
    switch (intent) {
      case ELEMENT_ACTIVATED -> {
        state.recordElementStart(
            value.getProcessInstanceKey(), value.getElementId(), record.getTimestamp());
        out.collect(base(source, value, isProcess, Transition.ACTIVATED).build());
      }
      case ELEMENT_COMPLETED, ELEMENT_TERMINATED -> {
        final long start =
            state.takeElementStart(value.getProcessInstanceKey(), value.getElementId());
        if (start != BaseProjectionStore.NO_POSITION) {
          emitCompletion(
              source,
              state,
              value,
              isProcess,
              intent == ProcessInstanceIntent.ELEMENT_COMPLETED
                  ? Transition.COMPLETED
                  : Transition.TERMINATED,
              record.getTimestamp() - start,
              out);
        }
      }
      default -> {
        // other element lifecycle intents do not bound execution time
      }
    }
  }

  private void emitCompletion(
      final SourceRecord source,
      final BaseProjectionStore state,
      final ProcessInstanceRecordValue value,
      final boolean isProcess,
      final Transition transition,
      final long durationMs,
      final Collector<Fact> out) {
    final Record<?> record = source.record();
    final Fact.Builder fact =
        base(source, value, isProcess, transition)
            .field("startTime", record.getTimestamp() - durationMs)
            .field("endTime", record.getTimestamp())
            .field("durationMs", durationMs);
    if (isProcess) {
      fact.field("processInstanceKey", value.getProcessInstanceKey())
          .field("completedNormally", transition == Transition.COMPLETED)
          .field("hadIncident", state.hasIncident(value.getProcessInstanceKey()));
      // EVENT_TIME enrichment: the variable snapshot as of this completion event.
      final Map<String, String> variables = state.getVariables(value.getProcessInstanceKey());
      variables.forEach((name, val) -> fact.field(DimensionSpec.VARIABLE_PREFIX + name, val));
      out.collect(fact.build());
      state.deleteVariables(value.getProcessInstanceKey());
      state.clearIncident(value.getProcessInstanceKey());
    } else {
      fact.field("elementId", value.getElementId())
          .field("elementType", value.getBpmnElementType().name());
      out.collect(fact.build());
    }
  }

  /** The common structural fields + transition for a process-instance or element fact. */
  private Fact.Builder base(
      final SourceRecord source,
      final ProcessInstanceRecordValue value,
      final boolean isProcess,
      final Transition transition) {
    return Fact.builder(isProcess ? FactType.PROCESS_INSTANCE : FactType.ELEMENT)
        .eventTime(source.record().getTimestamp())
        .source(source.partitionId(), source.offset())
        .transition(transition)
        .field("bpmnProcessId", value.getBpmnProcessId())
        .field("processDefinitionKey", value.getProcessDefinitionKey())
        .field("version", value.getVersion())
        .field("tenantId", value.getTenantId());
  }
}

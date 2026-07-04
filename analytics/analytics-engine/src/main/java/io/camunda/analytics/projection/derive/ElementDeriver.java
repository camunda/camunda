/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.projection.derive;

import io.camunda.analytics.dataset.DimensionSpec;
import io.camunda.analytics.fact.Fact;
import io.camunda.analytics.fact.FactType;
import io.camunda.analytics.fact.Transition;
import io.camunda.analytics.projection.SourceRecord;
import io.camunda.analytics.state.ElementEntity;
import io.camunda.analytics.state.immutable.ProjectionState;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceRecordValue;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Derives element/process-instance facts as a pure projection of the materialized {@link
 * ElementEntity} row. Activation emits an {@code ACTIVATED} fact; a terminal transition reads the
 * finalized row ({@code start, end, durationMs, hadIncident}) plus its scoped variable snapshot and
 * emits a {@code COMPLETED}/{@code TERMINATED} fact — never mutating state (eviction is the
 * applier's job, after this returns).
 */
public final class ElementDeriver {

  public void activated(
      final SourceRecord source, final ProcessInstanceRecordValue value, final Consumer<Fact> out) {
    out.accept(base(source, value, Transition.ACTIVATED).build());
  }

  public void completed(
      final SourceRecord source,
      final ProcessInstanceRecordValue value,
      final ProjectionState state,
      final Transition transition,
      final Consumer<Fact> out) {
    final long elementInstanceKey = source.record().getKey();
    final ElementEntity row = state.element(elementInstanceKey);
    if (row == null) {
      return; // no activation was folded (out of order / already evicted) — nothing to derive
    }
    final long start = row.start();
    final long end = row.end();
    final long durationMs = row.durationMs();
    final boolean hadIncident = row.hadIncident();
    final boolean isProcess = value.getBpmnElementType() == BpmnElementType.PROCESS;

    final Fact.Builder fact =
        base(source, value, transition)
            .field("startTime", start)
            .field("endTime", end)
            .field("durationMs", durationMs)
            .field("hadIncident", hadIncident);
    // Enrich with the variable snapshot visible to this element instance, resolved up the scope
    // hierarchy (nearer scope wins), like the engine's variable visibility.
    final Map<String, String> variables = state.variables(elementInstanceKey);
    variables.forEach((name, val) -> fact.field(DimensionSpec.VARIABLE_PREFIX + name, val));
    if (isProcess) {
      fact.field("processInstanceKey", value.getProcessInstanceKey())
          .field("completedNormally", transition == Transition.COMPLETED);
    } else {
      fact.field("elementId", value.getElementId())
          .field("elementType", value.getBpmnElementType().name());
    }
    out.accept(fact.build());
  }

  /** The common structural fields + transition for a process-instance or element fact. */
  private Fact.Builder base(
      final SourceRecord source,
      final ProcessInstanceRecordValue value,
      final Transition transition) {
    final Record<?> record = source.record();
    final boolean isProcess = value.getBpmnElementType() == BpmnElementType.PROCESS;
    return Fact.builder(isProcess ? FactType.PROCESS_INSTANCE : FactType.ELEMENT)
        .eventTime(record.getTimestamp())
        .source(source.partitionId(), source.offset())
        .transition(transition)
        .field("bpmnProcessId", value.getBpmnProcessId())
        .field("processDefinitionKey", value.getProcessDefinitionKey())
        .field("version", value.getVersion())
        .field("tenantId", value.getTenantId());
  }
}

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.projection.derive;

import io.camunda.analytics.fact.Fact;
import io.camunda.analytics.fact.Transition;
import io.camunda.analytics.projection.SourceRecord;
import io.camunda.analytics.state.ElementEntity;
import io.camunda.analytics.state.immutable.ProjectionState;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceRecordValue;
import java.util.function.Consumer;

/**
 * Emits a completion fact as a pure projection of the finalized element row: it reads {@code start,
 * end, durationMs, hadIncident} off the row and binds a lazy view of the visible variable snapshot
 * (resolved on demand when a dataset groups/filters by a variable). Declared once per terminal
 * transition — {@link Transition#COMPLETED} and {@link Transition#TERMINATED}.
 */
public final class ElementCompletedDeriver implements FactDeriver {

  private final ProjectionState state;
  private final Consumer<Fact> facts;
  private final Transition transition;

  public ElementCompletedDeriver(
      final ProjectionState state, final Consumer<Fact> facts, final Transition transition) {
    this.state = state;
    this.facts = facts;
    this.transition = transition;
  }

  @Override
  public void derive(final SourceRecord source) {
    final long elementInstanceKey = source.record().getKey();
    final ElementEntity row = state.element(elementInstanceKey);
    if (row == null) {
      return; // no activation was folded (out of order / already evicted) — nothing to derive
    }
    final ProcessInstanceRecordValue value =
        (ProcessInstanceRecordValue) source.record().getValue();
    final boolean isProcess = value.getBpmnElementType() == BpmnElementType.PROCESS;

    final Fact.Builder fact =
        ElementFacts.base(source, value, transition)
            .field("startTime", row.start())
            .field("endTime", row.end())
            .field("durationMs", row.durationMs())
            .field("hadIncident", row.hadIncident())
            // Lazy, collect-once: the visible variable snapshot is resolved only if a dataset reads
            // a var.* dimension/filter, and then at most once across all cube-meters.
            .variables(() -> state.variables(elementInstanceKey));
    if (isProcess) {
      fact.field("processInstanceKey", value.getProcessInstanceKey())
          .field("completedNormally", transition == Transition.COMPLETED);
    } else {
      fact.field("elementId", value.getElementId())
          .field("elementType", value.getBpmnElementType().name());
    }
    facts.accept(fact.build());
  }
}

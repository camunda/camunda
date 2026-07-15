/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.projection.derive;

import io.camunda.analytics.dimension.Utf8View;
import io.camunda.analytics.fact.Fact;
import io.camunda.analytics.fact.Transition;
import io.camunda.analytics.projection.SourceRecord;
import io.camunda.analytics.state.immutable.ProjectionState;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceRecord;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import java.util.function.Consumer;

/**
 * Emits an {@code ACTIVATED} fact (structural fields only — no duration yet) on activation. Beyond
 * the shared base, it stamps {@code startTime} (the activation event time — the same field name the
 * completion fact reads off the finalized row, so both fact families speak one vocabulary; open-
 * instance tables read it as the row's "started at") and, for non-process elements, {@code
 * elementId} (mirroring the completion deriver; the rework cube groups activations by element). A
 * PROCESS activation additionally carries the {@link BusinessValue} fields — the start-payload
 * variables are already folded when the process element activates, so the read hits state.
 */
public final class ElementActivatedDeriver implements FactDeriver {

  private final ProjectionState state;
  private final Consumer<Fact> facts;

  public ElementActivatedDeriver(final ProjectionState state, final Consumer<Fact> facts) {
    this.state = state;
    this.facts = facts;
  }

  @Override
  public void derive(final SourceRecord source) {
    final ProcessInstanceRecord value = (ProcessInstanceRecord) source.record().getValue();
    final Fact.Builder fact =
        ElementFacts.base(source, value, Transition.ACTIVATED)
            .field("startTime", source.record().getTimestamp());
    if (value.getBpmnElementType() != BpmnElementType.PROCESS) {
      fact.field("elementId", Utf8View.copyOf(value.getElementIdBuffer()));
    } else {
      final Long businessValue = BusinessValue.read(state, source.record().getKey());
      if (businessValue != null) {
        // +value: the instance's worth enters the system (see BusinessValue for the field pair).
        fact.field("value", businessValue).field("valueDelta", businessValue);
      }
    }
    facts.accept(fact.build());
  }
}

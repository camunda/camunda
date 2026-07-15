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
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceRecord;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import java.util.function.Consumer;

/**
 * Emits an {@code ACTIVATED} fact (structural fields only — no duration yet) on activation. Beyond
 * the shared base, it stamps {@code startTime} (the activation event time — the same field name the
 * completion fact reads off the finalized row, so both fact families speak one vocabulary; open-
 * instance tables read it as the row's "started at") and, for non-process elements, {@code
 * elementId} (mirroring the completion deriver; the rework cube groups activations by element).
 */
public final class ElementActivatedDeriver implements FactDeriver {

  private final Consumer<Fact> facts;

  public ElementActivatedDeriver(final Consumer<Fact> facts) {
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
    }
    facts.accept(fact.build());
  }
}

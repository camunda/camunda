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
import io.camunda.analytics.state.IncidentEntity;
import io.camunda.analytics.state.immutable.ProjectionState;
import io.camunda.zeebe.protocol.record.value.IncidentRecordValue;
import java.util.function.Consumer;

/** Emits a {@code +1} incident level fact on creation, reading the type off the just-opened row. */
public final class IncidentCreatedDeriver implements FactDeriver {

  private final ProjectionState state;
  private final Consumer<Fact> facts;

  public IncidentCreatedDeriver(final ProjectionState state, final Consumer<Fact> facts) {
    this.state = state;
    this.facts = facts;
  }

  @Override
  public void derive(final SourceRecord source) {
    final IncidentRecordValue value = (IncidentRecordValue) source.record().getValue();
    final IncidentEntity row = state.incident(value.getElementInstanceKey());
    final String errorType =
        row != null
            ? row.errorType()
            : (value.getErrorType() == null ? "UNKNOWN" : value.getErrorType().name());
    facts.accept(IncidentFacts.base(source, value, Transition.CREATED, errorType, 1L).build());
  }
}

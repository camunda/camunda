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
import io.camunda.zeebe.protocol.impl.record.value.incident.IncidentRecord;
import java.util.function.Consumer;

/**
 * Emits a {@code -1} incident level fact on resolution, reading the resolution duration and the
 * incident type off the finalized row.
 */
public final class IncidentResolvedDeriver implements FactDeriver {

  private final ProjectionState state;
  private final Consumer<Fact> facts;

  public IncidentResolvedDeriver(final ProjectionState state, final Consumer<Fact> facts) {
    this.state = state;
    this.facts = facts;
  }

  @Override
  public void derive(final SourceRecord source) {
    final IncidentRecord value = (IncidentRecord) source.record().getValue();
    final IncidentEntity row = state.incident(value.getElementInstanceKey());
    final String errorType =
        row != null
            ? row.errorType()
            : (value.getErrorType() == null ? "UNKNOWN" : value.getErrorType().name());
    final Fact.Builder fact =
        IncidentFacts.base(source, value, Transition.RESOLVED, errorType, -1L);
    if (row != null) {
      fact.field("durationMs", row.resolveMs() - row.createMs())
          .field("resolvedTimeMs", row.resolveMs());
    }
    facts.accept(fact.build());
  }
}

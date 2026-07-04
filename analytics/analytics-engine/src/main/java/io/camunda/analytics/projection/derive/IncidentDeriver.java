/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.projection.derive;

import io.camunda.analytics.fact.Fact;
import io.camunda.analytics.fact.FactType;
import io.camunda.analytics.fact.Transition;
import io.camunda.analytics.projection.SourceRecord;
import io.camunda.analytics.state.IncidentEntity;
import io.camunda.analytics.state.immutable.ProjectionState;
import io.camunda.zeebe.protocol.record.value.IncidentRecordValue;
import java.util.function.Consumer;

/**
 * Derives incident facts as a projection of the materialized {@link IncidentEntity} row. {@code
 * CREATED} emits a {@code +1} level fact; {@code RESOLVED} reads the row's create→resolve duration
 * and its incident type and emits a {@code -1} level fact — never mutating state (eviction is the
 * applier's job).
 */
public final class IncidentDeriver {

  public void created(
      final SourceRecord source,
      final IncidentRecordValue incident,
      final String errorType,
      final Consumer<Fact> out) {
    out.accept(fact(source, incident, Transition.CREATED, errorType, 1L).build());
  }

  public void resolved(
      final SourceRecord source,
      final IncidentRecordValue incident,
      final ProjectionState state,
      final Consumer<Fact> out) {
    final IncidentEntity row = state.incident(incident.getElementInstanceKey());
    // The incident type is a property of the row (stamped on CREATED); fall back to the record if
    // the open row was never observed (out of order).
    final String errorType = row != null ? row.errorType() : errorTypeOf(incident);
    final Fact.Builder resolved = fact(source, incident, Transition.RESOLVED, errorType, -1L);
    if (row != null) {
      resolved
          .field("durationMs", row.resolveMs() - row.createMs())
          .field("resolvedTimeMs", row.resolveMs());
    }
    out.accept(resolved.build());
  }

  /** The incident's error type, defaulting to {@code UNKNOWN} when the record carries none. */
  public static String errorTypeOf(final IncidentRecordValue incident) {
    return incident.getErrorType() == null ? "UNKNOWN" : incident.getErrorType().name();
  }

  private Fact.Builder fact(
      final SourceRecord source,
      final IncidentRecordValue incident,
      final Transition transition,
      final String errorType,
      final long delta) {
    return Fact.builder(FactType.INCIDENT)
        .eventTime(source.record().getTimestamp())
        .source(source.partitionId(), source.offset())
        .transition(transition)
        .field("bpmnProcessId", incident.getBpmnProcessId())
        .field("elementId", incident.getElementId())
        .field("tenantId", incident.getTenantId())
        .field("errorType", errorType)
        .field("delta", delta);
  }
}

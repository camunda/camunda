/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.projection;

import io.camunda.analytics.fact.Fact;
import io.camunda.analytics.fact.FactType;
import io.camunda.analytics.fact.Transition;
import io.camunda.eventbridge.streaming.fold.Collector;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.IncidentIntent;
import io.camunda.zeebe.protocol.record.value.IncidentRecordValue;

/**
 * Folds {@code INCIDENT} records: a {@code CREATED} marks its instance as having had an incident,
 * remembers the open incident's create time, and emits a {@code CREATED} fact ({@code delta=+1}); a
 * {@code RESOLVED} pairs against that create time to derive a resolution duration and emits a
 * {@code RESOLVED} fact ({@code delta=-1}). The instance-level incident flag is read back by the
 * {@link ElementDeriver} when the instance completes (the {@code hadIncident} enrichment).
 */
final class IncidentDeriver implements FactDeriver {

  @Override
  public void derive(
      final SourceRecord source, final BaseProjectionStore state, final Collector<Fact> out) {
    final Record<?> record = source.record();
    if (!(record.getValue() instanceof final IncidentRecordValue incident)
        || !(record.getIntent() instanceof final IncidentIntent intent)) {
      return;
    }
    final String errorType =
        incident.getErrorType() == null ? "UNKNOWN" : incident.getErrorType().name();
    if (intent == IncidentIntent.CREATED) {
      state.markIncident(incident.getProcessInstanceKey());
      state.putIncidentStart(incident.getElementInstanceKey(), record.getTimestamp());
      out.collect(fact(source, incident, Transition.CREATED, errorType, 1L).build());
    } else if (intent == IncidentIntent.RESOLVED) {
      final Fact.Builder resolved = fact(source, incident, Transition.RESOLVED, errorType, -1L);
      final long createdAt = state.takeIncidentStart(incident.getElementInstanceKey());
      if (createdAt != BaseProjectionStore.NO_POSITION) {
        resolved
            .field("durationMs", record.getTimestamp() - createdAt)
            .field("resolvedTimeMs", record.getTimestamp());
      }
      out.collect(resolved.build());
    }
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

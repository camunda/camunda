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
import io.camunda.analytics.fact.FactType;
import io.camunda.analytics.fact.Transition;
import io.camunda.analytics.projection.SourceRecord;
import io.camunda.zeebe.protocol.impl.record.value.incident.IncidentRecord;

/** The structural fields shared by the incident facts (creation and resolution). */
final class IncidentFacts {

  private IncidentFacts() {}

  static Fact.Builder base(
      final SourceRecord source,
      final IncidentRecord incident,
      final Transition transition,
      final String errorType,
      final long delta) {
    return Fact.builder(FactType.INCIDENT)
        .eventTime(source.record().getTimestamp())
        .source(source.partitionId(), source.offset())
        .transition(transition)
        .field("bpmnProcessId", Utf8View.copyOf(incident.getBpmnProcessIdBuffer()))
        .field("elementId", Utf8View.copyOf(incident.getElementIdBuffer()))
        .field("tenantId", incident.getTenantId())
        .field("errorType", errorType)
        .field("delta", delta);
  }
}

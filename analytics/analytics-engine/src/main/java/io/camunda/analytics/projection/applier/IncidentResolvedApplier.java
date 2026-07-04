/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.projection.applier;

import io.camunda.analytics.projection.SourceRecord;
import io.camunda.analytics.state.mutable.MutableProjectionState;
import io.camunda.zeebe.protocol.record.value.IncidentRecordValue;

/** Finalizes an incident row with its resolve time, so the deriver can read the duration off it. */
public final class IncidentResolvedApplier implements EventApplier {

  private final MutableProjectionState state;

  public IncidentResolvedApplier(final MutableProjectionState state) {
    this.state = state;
  }

  @Override
  public void apply(final SourceRecord source) {
    final IncidentRecordValue value = (IncidentRecordValue) source.record().getValue();
    state.resolveIncident(value.getElementInstanceKey(), source.record().getTimestamp());
  }
}

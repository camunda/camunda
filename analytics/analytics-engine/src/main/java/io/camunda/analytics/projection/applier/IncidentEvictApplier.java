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

/** Evicts a resolved incident row after its resolution fact has been derived (evict-after-emit). */
public final class IncidentEvictApplier implements EventApplier {

  private final MutableProjectionState state;

  public IncidentEvictApplier(final MutableProjectionState state) {
    this.state = state;
  }

  @Override
  public void apply(final SourceRecord source) {
    final IncidentRecordValue value = (IncidentRecordValue) source.record().getValue();
    state.evictIncident(value.getElementInstanceKey());
  }
}

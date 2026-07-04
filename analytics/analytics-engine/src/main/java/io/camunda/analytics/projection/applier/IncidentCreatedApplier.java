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

/**
 * Opens an incident: stamps {@code hadIncident} on the element instance's row and materializes an
 * incident row with its create time and type, so a later resolution can read the duration and type
 * off the row.
 */
public final class IncidentCreatedApplier implements EventApplier {

  private final MutableProjectionState state;

  public IncidentCreatedApplier(final MutableProjectionState state) {
    this.state = state;
  }

  @Override
  public void apply(final SourceRecord source) {
    final IncidentRecordValue value = (IncidentRecordValue) source.record().getValue();
    final long elementInstanceKey = value.getElementInstanceKey();
    state.markIncident(elementInstanceKey);
    state.openIncident(elementInstanceKey, source.record().getTimestamp(), errorType(value));
  }

  /** The incident's error type, defaulting to {@code UNKNOWN} when the record carries none. */
  static String errorType(final IncidentRecordValue value) {
    return value.getErrorType() == null ? "UNKNOWN" : value.getErrorType().name();
  }
}

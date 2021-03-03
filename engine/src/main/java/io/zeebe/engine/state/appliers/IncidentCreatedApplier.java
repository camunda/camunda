/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.engine.state.appliers;

import io.zeebe.engine.state.TypedEventApplier;
import io.zeebe.engine.state.mutable.MutableIncidentState;
import io.zeebe.engine.state.mutable.MutableJobState;
import io.zeebe.protocol.impl.record.value.incident.IncidentRecord;
import io.zeebe.protocol.record.intent.IncidentIntent;
import io.zeebe.protocol.record.value.ErrorType;

final class IncidentCreatedApplier implements TypedEventApplier<IncidentIntent, IncidentRecord> {

  private final MutableIncidentState incidentState;
  private final MutableJobState jobState;

  public IncidentCreatedApplier(
      final MutableIncidentState incidentState, final MutableJobState jobState) {
    this.incidentState = incidentState;
    this.jobState = jobState;
  }

  @Override
  public void applyState(final long incidentKey, final IncidentRecord value) {
    incidentState.createIncident(incidentKey, value);

    if (ErrorType.MESSAGE_SIZE_EXCEEDED == value.getErrorType()) {
      final var jobKey = value.getJobKey();
      final var jobRecord = jobState.getJob(jobKey);
      jobState.disable(jobKey, jobRecord);
    }
  }
}

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.appliers;

import io.camunda.zeebe.engine.state.TypedEventApplier;
import io.camunda.zeebe.engine.state.mutable.MutableIncidentState;
import io.camunda.zeebe.engine.state.mutable.MutableJobState;
import io.camunda.zeebe.protocol.impl.record.value.incident.IncidentRecord;
import io.camunda.zeebe.protocol.record.intent.IncidentIntent;
import io.camunda.zeebe.protocol.record.value.ErrorType;

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

    if (ErrorType.MESSAGE_SIZE_EXCEEDED == value.getErrorType() && value.getJobKey() != -1) {
      final var jobKey = value.getJobKey();
      final var jobRecord = jobState.getJob(jobKey);
      jobState.disable(jobKey, jobRecord);
    }
  }
}

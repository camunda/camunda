/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.state.appliers;

import io.camunda.zeebe.engine.state.TypedEventApplier;
import io.camunda.zeebe.engine.state.immutable.JobState.State;
import io.camunda.zeebe.engine.state.mutable.MutableIncidentState;
import io.camunda.zeebe.engine.state.mutable.MutableJobState;
import io.camunda.zeebe.protocol.impl.record.value.incident.IncidentRecord;
import io.camunda.zeebe.protocol.impl.record.value.job.JobRecord;
import io.camunda.zeebe.protocol.record.intent.IncidentIntent;

final class IncidentResolvedApplier implements TypedEventApplier<IncidentIntent, IncidentRecord> {

  private final MutableIncidentState incidentState;
  private final MutableJobState jobState;

  public IncidentResolvedApplier(
      final MutableIncidentState incidentState, final MutableJobState jobState) {
    this.incidentState = incidentState;
    this.jobState = jobState;
  }

  @Override
  public void applyState(final long incidentKey, final IncidentRecord value) {
    final var jobKey = value.getJobKey();
    if (jobKey > 0) {
      // incident belonged to job
      final State stateOfJob = jobState.getState(jobKey);
      if (stateOfJob == State.FAILED) {
        final JobRecord job = jobState.getJob(jobKey);
        jobState.resolve(jobKey, job);
      }
    }
    incidentState.deleteIncident(incidentKey);
  }
}

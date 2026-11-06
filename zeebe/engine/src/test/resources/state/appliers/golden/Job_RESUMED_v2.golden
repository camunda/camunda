/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.appliers;

import io.camunda.zeebe.engine.state.TypedEventApplier;
import io.camunda.zeebe.engine.state.immutable.IncidentState;
import io.camunda.zeebe.engine.state.immutable.JobState.State;
import io.camunda.zeebe.engine.state.mutable.MutableJobState;
import io.camunda.zeebe.engine.state.mutable.MutableProcessingState;
import io.camunda.zeebe.protocol.impl.record.value.incident.IncidentRecord;
import io.camunda.zeebe.protocol.impl.record.value.job.JobRecord;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.value.ErrorType;

/**
 * Resumes a suspended job like {@link JobResumedApplier}, except for a job with an open secret
 * resolution incident. That job goes back to {@link State#WAITING_FOR_SECRET_RESOLUTION}, the state
 * the incident put it in, so only resolving the incident hands it out again.
 */
public final class JobResumedV2Applier implements TypedEventApplier<JobIntent, JobRecord> {

  private final MutableJobState jobState;
  private final IncidentState incidentState;

  JobResumedV2Applier(final MutableProcessingState state) {
    jobState = state.getJobState();
    incidentState = state.getIncidentState();
  }

  @Override
  public void applyState(final long key, final JobRecord value) {
    if (!jobState.isInState(key, State.SUSPENDED)) {
      return;
    }
    if (hasOpenSecretResolutionIncident(key)) {
      // suspension already took the job off the activatable index, so only its state changes
      jobState.updateJobState(key, State.WAITING_FOR_SECRET_RESOLUTION);
      return;
    }
    final JobRecord storedJob = jobState.getJob(key);
    if (storedJob == null) {
      return;
    }
    jobState.updateJobState(key, State.ACTIVATABLE);
    jobState.makeJobActivatableByPriority(
        storedJob.getTypeBuffer(), key, storedJob.getTenantId(), storedJob.getPriority());
  }

  private boolean hasOpenSecretResolutionIncident(final long jobKey) {
    final long incidentKey = incidentState.getJobIncidentKey(jobKey);
    if (incidentKey == IncidentState.MISSING_INCIDENT) {
      return false;
    }
    final IncidentRecord incident = incidentState.getIncidentRecord(incidentKey);
    return incident != null && incident.getErrorType() == ErrorType.SECRET_RESOLUTION_ERROR;
  }
}

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.state.appliers;

import io.camunda.zeebe.engine.processing.job.JobThrowErrorProcessor;
import io.camunda.zeebe.engine.state.TypedEventApplier;
import io.camunda.zeebe.engine.state.immutable.ElementInstanceState;
import io.camunda.zeebe.engine.state.immutable.JobState.State;
import io.camunda.zeebe.engine.state.mutable.MutableIncidentState;
import io.camunda.zeebe.engine.state.mutable.MutableJobState;
import io.camunda.zeebe.protocol.impl.record.value.incident.IncidentRecord;
import io.camunda.zeebe.protocol.impl.record.value.job.JobRecord;
import io.camunda.zeebe.protocol.record.intent.IncidentIntent;
import java.util.EnumSet;
import java.util.Set;

final class IncidentResolvedApplier implements TypedEventApplier<IncidentIntent, IncidentRecord> {

  private static final Set<State> RESOLVABLE_JOB_STATES =
      EnumSet.of(State.FAILED, State.ERROR_THROWN);

  private final MutableIncidentState incidentState;
  private final MutableJobState jobState;
  private final ElementInstanceState elementInstanceState;

  public IncidentResolvedApplier(
      final MutableIncidentState incidentState,
      final MutableJobState jobState,
      final ElementInstanceState elementInstanceState) {
    this.incidentState = incidentState;
    this.jobState = jobState;
    this.elementInstanceState = elementInstanceState;
  }

  @Override
  public void applyState(final long incidentKey, final IncidentRecord value) {
    final var jobKey = value.getJobKey();
    final boolean isJobRelatedIncident = jobKey > 0;
    if (isJobRelatedIncident) {
      final var stateOfJob = jobState.getState(jobKey);
      if (RESOLVABLE_JOB_STATES.contains(stateOfJob)) {
        final var job = jobState.getJob(jobKey);
        resetElementId(job);
        jobState.resolve(jobKey, job);
      }
    }
    incidentState.deleteIncident(incidentKey);
  }

  /**
   * {@link JobThrowErrorProcessor} sets the job's elementId to NO_CATCH_EVENT_FOUND for unhandled
   * error incidents. In order to completely resolve the issue, the elementId must be reset.
   */
  private void resetElementId(final JobRecord job) {
    if (JobThrowErrorProcessor.NO_CATCH_EVENT_FOUND.equals(job.getElementId())) {
      final var elementInstance = elementInstanceState.getInstance(job.getElementInstanceKey());
      if (elementInstance != null) {
        // change the job object here, it will be persisted with the jobState.resolve call
        job.setElementId(elementInstance.getValue().getElementId());
      }
    }
  }
}

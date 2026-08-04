/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.appliers;

import io.camunda.zeebe.engine.processing.job.JobThrowErrorProcessor;
import io.camunda.zeebe.engine.state.TypedEventApplier;
import io.camunda.zeebe.engine.state.immutable.JobState.State;
import io.camunda.zeebe.engine.state.mutable.MutableElementInstanceState;
import io.camunda.zeebe.engine.state.mutable.MutableIncidentState;
import io.camunda.zeebe.engine.state.mutable.MutableJobState;
import io.camunda.zeebe.protocol.impl.record.value.incident.IncidentRecord;
import io.camunda.zeebe.protocol.impl.record.value.job.JobRecord;
import io.camunda.zeebe.protocol.record.intent.IncidentIntent;
import io.camunda.zeebe.protocol.record.value.ErrorType;
import java.util.EnumSet;
import java.util.Set;

/**
 * Owns the RESOLVED orchestration directly and inserts the resolved job into {@code
 * JOB_ACTIVATABLE_BY_PRIORITY} via {@link MutableJobState#makeJobActivatableByPriority}, instead of
 * going through the deprecated {@link MutableJobState#resolve}.
 */
final class IncidentResolvedV4Applier implements TypedEventApplier<IncidentIntent, IncidentRecord> {

  private static final Set<State> RESOLVABLE_JOB_STATES =
      EnumSet.of(State.FAILED, State.ERROR_THROWN);

  private final MutableIncidentState incidentState;
  private final MutableJobState jobState;
  private final MutableElementInstanceState elementInstanceState;

  IncidentResolvedV4Applier(
      final MutableIncidentState incidentState,
      final MutableJobState jobState,
      final MutableElementInstanceState elementInstanceState) {
    this.incidentState = incidentState;
    this.jobState = jobState;
    this.elementInstanceState = elementInstanceState;
  }

  @Override
  public void applyState(final long incidentKey, final IncidentRecord value) {
    if (value.getErrorType() == ErrorType.EXTRACT_VALUE_ERROR) {
      resetListenerIndices(value);
    }
    reactivateJobIfNeeded(value);
    incidentState.deleteIncident(incidentKey);
  }

  private void reactivateJobIfNeeded(final IncidentRecord value) {
    final var jobKey = value.getJobKey();
    if (jobKey <= 0) {
      return; // not a job-related incident
    }
    if (value.getErrorType() == ErrorType.SECRET_RESOLUTION_ERROR) {
      // job state reactivates the job only if it is still parked, so a job that this error type was
      // raised for without parking it (a failed secret value injection) is left alone
      jobState.makeActivatableAfterSecretResolution(jobKey);
      return;
    }
    final var stateOfJob = jobState.getState(jobKey);
    if (!RESOLVABLE_JOB_STATES.contains(stateOfJob)) {
      return;
    }
    final var job = jobState.getJob(jobKey);
    resetElementId(job, value.getElementId());
    jobState.updateJobRecord(jobKey, job);
    jobState.updateJobState(jobKey, State.ACTIVATABLE);
    jobState.removeJobDeadline(jobKey, job.getDeadline());
    jobState.makeJobActivatableByPriority(
        job.getTypeBuffer(), jobKey, job.getTenantId(), job.getPriority());
  }

  private void resetListenerIndices(final IncidentRecord value) {
    final var elementInstance = elementInstanceState.getInstance(value.getElementInstanceKey());
    if (elementInstance != null) {
      elementInstance.resetExecutionListenerIndex();
      elementInstance.resetTaskListenerIndices();
      elementInstanceState.updateInstance(elementInstance);
    }
  }

  /**
   * {@link JobThrowErrorProcessor} sets the job's elementId to NO_CATCH_EVENT_FOUND for unhandled
   * error incidents. In order to completely resolve the issue, the elementId must be reset.
   */
  private void resetElementId(final JobRecord job, final String incidentRecordElementId) {
    if (JobThrowErrorProcessor.NO_CATCH_EVENT_FOUND.equals(job.getElementId())) {

      // change the job object here, it will be persisted via the jobState.updateJobRecord call in
      // applyState
      if (JobThrowErrorProcessor.NO_CATCH_EVENT_FOUND.equals(incidentRecordElementId)) {
        final var elementInstance = elementInstanceState.getInstance(job.getElementInstanceKey());
        if (elementInstance != null) {
          job.setElementId(elementInstance.getValue().getElementId());
        }
      } else {
        job.setElementId(incidentRecordElementId);
      }
    }
  }
}

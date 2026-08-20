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

final class IncidentResolvedV2Applier implements TypedEventApplier<IncidentIntent, IncidentRecord> {

  private static final Set<State> RESOLVABLE_JOB_STATES =
      EnumSet.of(State.FAILED, State.ERROR_THROWN);

  private final MutableIncidentState incidentState;
  private final MutableJobState jobState;
  private final MutableElementInstanceState elementInstanceState;

  public IncidentResolvedV2Applier(
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

    final var jobKey = value.getJobKey();
    final boolean isJobRelatedIncident = jobKey > 0;

    if (isJobRelatedIncident) {
      final var stateOfJob = jobState.getState(jobKey);
      if (RESOLVABLE_JOB_STATES.contains(stateOfJob)) {
        final var job = jobState.getJob(jobKey);
        resetElementId(job, value.getElementId());
        jobState.resolve(jobKey, job);
      }
    }
    incidentState.deleteIncident(incidentKey);
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
      // change the job object here, it will be persisted with the jobState.resolve call
      job.setElementId(incidentRecordElementId);
    }
  }
}

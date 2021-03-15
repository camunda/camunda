/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.engine.processing.incident;

import io.zeebe.engine.processing.streamprocessor.CommandProcessor;
import io.zeebe.engine.processing.streamprocessor.TypedRecord;
import io.zeebe.engine.state.immutable.IncidentState;
import io.zeebe.engine.state.immutable.JobState;
import io.zeebe.engine.state.immutable.JobState.State;
import io.zeebe.engine.state.mutable.MutableZeebeState;
import io.zeebe.protocol.impl.record.value.incident.IncidentRecord;
import io.zeebe.protocol.record.RejectionType;
import io.zeebe.protocol.record.intent.IncidentIntent;
import io.zeebe.protocol.record.value.ErrorType;

public final class CreateIncidentProcessor implements CommandProcessor<IncidentRecord> {

  private static final String INVALID_JOB_STATE_MESSAGE =
      "Expected to create incident for failed job with key '%d', but it is in state '%s'";
  private static final String NO_FAILED_JOB_MESSAGE =
      "Expected to create incident for failed job with key '%d', but no such job was found";

  private final JobState jobState;
  private final IncidentState incidentState;

  public CreateIncidentProcessor(final MutableZeebeState zeebeState) {
    jobState = zeebeState.getJobState();
    incidentState = zeebeState.getIncidentState();
  }

  @Override
  public boolean onCommand(
      final TypedRecord<IncidentRecord> command,
      final CommandControl<IncidentRecord> commandControl) {
    final IncidentRecord incidentEvent = command.getValue();

    final boolean incidentIsNotRejected =
        !tryRejectIncidentCreation(incidentEvent, commandControl, command);

    if (incidentIsNotRejected) {
      commandControl.accept(IncidentIntent.CREATED, incidentEvent);
    }

    return true;
  }

  /** @return true if rejected, otherwise false */
  public boolean tryRejectIncidentCreation(
      final IncidentRecord incidentEvent,
      final CommandControl<IncidentRecord> commandControl,
      final TypedRecord<IncidentRecord> command) {

    final boolean isJobIncident = incidentState.isJobIncident(incidentEvent);

    if (isJobIncident) {
      return tryRejectJobIncident(incidentEvent.getJobKey(), commandControl, command);
    }

    return false;
  }

  /** @return true if rejected, otherwise false */
  private boolean tryRejectJobIncident(
      final long jobKey,
      final CommandControl<IncidentRecord> commandControl,
      final TypedRecord<IncidentRecord> command) {

    final JobState.State currentJobState = jobState.getState(jobKey);

    if (currentJobState == State.NOT_FOUND) {
      commandControl.reject(RejectionType.NOT_FOUND, String.format(NO_FAILED_JOB_MESSAGE, jobKey));
      return true;
    } else if (currentJobState == State.ACTIVATABLE
        && ErrorType.MESSAGE_SIZE_EXCEEDED == command.getValue().getErrorType()) {
      /**
       * if the message size is exceeded the job is still in activatable stage. An incident needs to
       * be created and after the incident is created, the job needs to be disabled
       */
      return false;
    } else if (currentJobState != State.FAILED && currentJobState != State.ERROR_THROWN) {
      commandControl.reject(
          RejectionType.INVALID_STATE,
          String.format(INVALID_JOB_STATE_MESSAGE, jobKey, currentJobState));
      return true;
    }

    return false;
  }
}

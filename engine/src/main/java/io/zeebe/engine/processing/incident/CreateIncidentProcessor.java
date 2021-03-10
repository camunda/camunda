/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.processing.incident;

import io.zeebe.engine.processing.streamprocessor.CommandProcessor;
import io.zeebe.engine.processing.streamprocessor.TypedRecord;
import io.zeebe.engine.state.ZeebeState;
import io.zeebe.engine.state.immutable.ElementInstanceState;
import io.zeebe.engine.state.immutable.IncidentState;
import io.zeebe.engine.state.immutable.JobState;
import io.zeebe.engine.state.immutable.JobState.State;
import io.zeebe.engine.state.instance.IndexedRecord;
import io.zeebe.protocol.impl.record.value.incident.IncidentRecord;
import io.zeebe.protocol.record.RejectionType;
import io.zeebe.protocol.record.intent.IncidentIntent;

public final class CreateIncidentProcessor implements CommandProcessor<IncidentRecord> {

  private static final String NO_FAILED_RECORD_MESSAGE =
      "Expected to create incident for failed record with key '%d', but no such record was found";

  private static final String INVALID_JOB_STATE_MESSAGE =
      "Expected to create incident for failed job with key '%d', but it is in state '%s'";
  private static final String NO_FAILED_JOB_MESSAGE =
      "Expected to create incident for failed job with key '%d', but no such job was found";

  private final ElementInstanceState elementInstanceState;
  private final JobState jobState;
  private final IncidentState incidentState;

  public CreateIncidentProcessor(final ZeebeState zeebeState) {
    jobState = zeebeState.getJobState();
    elementInstanceState = zeebeState.getElementInstanceState();
    incidentState = zeebeState.getIncidentState();
  }

  @Override
  public boolean onCommand(
      final TypedRecord<IncidentRecord> command,
      final CommandControl<IncidentRecord> commandControl) {
    final IncidentRecord incidentEvent = command.getValue();

    final boolean incidentIsNotRejected = !tryRejectIncidentCreation(incidentEvent, commandControl);

    if (incidentIsNotRejected) {
      commandControl.accept(IncidentIntent.CREATED, incidentEvent);
    }

    return true;
  }

  /** @return true if rejected, otherwise false */
  public boolean tryRejectIncidentCreation(
      final IncidentRecord incidentEvent, final CommandControl<IncidentRecord> commandControl) {

    final boolean isJobIncident = incidentState.isJobIncident(incidentEvent);

    if (isJobIncident) {
      return tryRejectJobIncident(incidentEvent.getJobKey(), commandControl);
    } else {
      return tryRejectProcessInstanceIncident(
          incidentEvent.getElementInstanceKey(), commandControl);
    }
  }

  /** @return true if rejected, otherwise false */
  private boolean tryRejectJobIncident(
      final long jobKey, final CommandControl<IncidentRecord> commandControl) {

    final JobState.State currentJobState = jobState.getState(jobKey);

    if (currentJobState == State.NOT_FOUND) {
      commandControl.reject(RejectionType.NOT_FOUND, String.format(NO_FAILED_JOB_MESSAGE, jobKey));
      return true;

    } else if (currentJobState != State.FAILED && currentJobState != State.ERROR_THROWN) {
      commandControl.reject(
          RejectionType.INVALID_STATE,
          String.format(INVALID_JOB_STATE_MESSAGE, jobKey, currentJobState));
      return true;
    }

    return false;
  }

  /** @return true if rejected, otherwise false */
  private boolean tryRejectProcessInstanceIncident(
      final long elementInstanceKey, final CommandControl<IncidentRecord> commandControl) {
    final IndexedRecord failedRecord = elementInstanceState.getFailedRecord(elementInstanceKey);
    final boolean noFailedRecord = failedRecord == null;
    if (noFailedRecord) {
      commandControl.reject(
          RejectionType.NOT_FOUND, String.format(NO_FAILED_RECORD_MESSAGE, elementInstanceKey));
    }
    return noFailedRecord;
  }
}

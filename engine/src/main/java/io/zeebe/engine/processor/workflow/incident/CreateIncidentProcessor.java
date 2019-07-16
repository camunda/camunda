/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.processor.workflow.incident;

import io.zeebe.engine.processor.CommandProcessor;
import io.zeebe.engine.processor.TypedRecord;
import io.zeebe.engine.state.ZeebeState;
import io.zeebe.engine.state.instance.ElementInstanceState;
import io.zeebe.engine.state.instance.IncidentState;
import io.zeebe.engine.state.instance.IndexedRecord;
import io.zeebe.engine.state.instance.JobState;
import io.zeebe.engine.state.instance.JobState.State;
import io.zeebe.protocol.impl.record.value.incident.IncidentRecord;
import io.zeebe.protocol.record.RejectionType;
import io.zeebe.protocol.record.intent.IncidentIntent;

public final class CreateIncidentProcessor implements CommandProcessor<IncidentRecord> {
  public static final String NO_FAILED_RECORD_MESSAGE =
      "Expected to create incident for failed record with key '%d', but no such record was found";
  public static final String JOB_NOT_FAILED_MESSAGE =
      "Expected to create incident for failed job with key '%d', but it is not failed";
  public static final String NO_FAILED_JOB_MESSAGE =
      "Expected to create incident for failed job with key '%d', but no such job was found";

  private final ZeebeState zeebeState;

  public CreateIncidentProcessor(ZeebeState zeebeState) {
    this.zeebeState = zeebeState;
  }

  @Override
  public void onCommand(
      TypedRecord<IncidentRecord> command, CommandControl<IncidentRecord> commandControl) {
    final IncidentRecord incidentEvent = command.getValue();

    final boolean incidentIsNotRejected = !rejectIncidentCreation(incidentEvent, commandControl);

    if (incidentIsNotRejected) {
      final long incidentKey = commandControl.accept(IncidentIntent.CREATED, incidentEvent);
      zeebeState.getIncidentState().createIncident(incidentKey, incidentEvent);
    }
  }

  public boolean rejectIncidentCreation(
      IncidentRecord incidentEvent, CommandControl<IncidentRecord> commandControl) {
    final IncidentState incidentState = zeebeState.getIncidentState();

    final boolean isJobIncident = incidentState.isJobIncident(incidentEvent);

    if (isJobIncident) {
      return rejectJobIncident(incidentEvent.getJobKey(), commandControl);
    } else {
      return rejectWorkflowInstanceIncident(incidentEvent.getElementInstanceKey(), commandControl);
    }
  }

  private boolean rejectJobIncident(long jobKey, CommandControl<IncidentRecord> commandControl) {
    final JobState state = zeebeState.getJobState();
    final JobState.State jobState = state.getState(jobKey);

    if (jobState == State.NOT_FOUND) {
      commandControl.reject(RejectionType.NOT_FOUND, String.format(NO_FAILED_JOB_MESSAGE, jobKey));
    } else if (jobState != State.FAILED) {
      commandControl.reject(
          RejectionType.INVALID_STATE, String.format(JOB_NOT_FAILED_MESSAGE, jobKey));
    }

    return jobState != State.FAILED;
  }

  private boolean rejectWorkflowInstanceIncident(
      long elementInstanceKey, CommandControl<IncidentRecord> commandControl) {
    final ElementInstanceState elementInstanceState =
        zeebeState.getWorkflowState().getElementInstanceState();

    final IndexedRecord failedRecord = elementInstanceState.getFailedRecord(elementInstanceKey);
    final boolean noFailedRecord = failedRecord == null;
    if (noFailedRecord) {
      commandControl.reject(
          RejectionType.NOT_FOUND, String.format(NO_FAILED_RECORD_MESSAGE, elementInstanceKey));
    }
    return noFailedRecord;
  }
}

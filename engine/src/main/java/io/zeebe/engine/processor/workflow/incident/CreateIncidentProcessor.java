/*
 * Zeebe Workflow Engine
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
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
import io.zeebe.protocol.clientapi.RejectionType;
import io.zeebe.protocol.impl.record.value.incident.IncidentRecord;
import io.zeebe.protocol.intent.IncidentIntent;

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

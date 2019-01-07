/*
 * Zeebe Broker Core
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
package io.zeebe.broker.workflow.processor.servicetask;

import io.zeebe.broker.incident.processor.IncidentState;
import io.zeebe.broker.job.JobState;
import io.zeebe.broker.logstreams.state.ZeebeState;
import io.zeebe.broker.workflow.model.element.ExecutableFlowNode;
import io.zeebe.broker.workflow.processor.BpmnStepContext;
import io.zeebe.broker.workflow.processor.activity.TerminateActivityHandler;
import io.zeebe.broker.workflow.state.ElementInstance;
import io.zeebe.protocol.impl.record.value.incident.IncidentRecord;
import io.zeebe.protocol.impl.record.value.job.JobRecord;
import io.zeebe.protocol.intent.JobIntent;

public class TerminateServiceTaskHandler extends TerminateActivityHandler {
  private final JobState jobState;

  public TerminateServiceTaskHandler(ZeebeState zeebeState) {
    super(zeebeState.getIncidentState());
    jobState = zeebeState.getJobState();
  }

  @Override
  protected void terminate(BpmnStepContext<ExecutableFlowNode> context) {
    super.terminate(context);

    final ElementInstance elementInstance = context.getElementInstance();
    final long jobKey = elementInstance.getJobKey();
    if (jobKey > 0) {
      final JobRecord job = jobState.getJob(jobKey);

      if (job == null) {
        throw new IllegalStateException(
            String.format("Expected to find job with key %d, but no job found", jobKey));
      }

      context.getCommandWriter().appendFollowUpCommand(jobKey, JobIntent.CANCEL, job);
      resolveExistingJobIncident(jobKey, context);
    }
  }

  public void resolveExistingJobIncident(long jobKey, BpmnStepContext<ExecutableFlowNode> context) {
    final long jobIncidentKey = incidentState.getJobIncidentKey(jobKey);

    final boolean hasIncident = jobIncidentKey != IncidentState.MISSING_INCIDENT;
    if (hasIncident) {
      final IncidentRecord incidentRecord = incidentState.getIncidentRecord(jobIncidentKey);
      context.getOutput().appendResolvedIncidentEvent(jobIncidentKey, incidentRecord);
    }
  }
}

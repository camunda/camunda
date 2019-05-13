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
package io.zeebe.engine.processor.workflow.handlers.servicetask;

import io.zeebe.engine.Loggers;
import io.zeebe.engine.processor.workflow.BpmnStepContext;
import io.zeebe.engine.processor.workflow.deployment.model.element.ExecutableServiceTask;
import io.zeebe.engine.processor.workflow.handlers.CatchEventSubscriber;
import io.zeebe.engine.processor.workflow.handlers.activity.ActivityElementTerminatingHandler;
import io.zeebe.engine.state.instance.ElementInstance;
import io.zeebe.engine.state.instance.IncidentState;
import io.zeebe.engine.state.instance.JobState;
import io.zeebe.protocol.impl.record.value.incident.IncidentRecord;
import io.zeebe.protocol.impl.record.value.job.JobRecord;
import io.zeebe.protocol.intent.JobIntent;
import io.zeebe.protocol.intent.WorkflowInstanceIntent;

public class ServiceTaskElementTerminatingHandler<T extends ExecutableServiceTask>
    extends ActivityElementTerminatingHandler<T> {
  private final IncidentState incidentState;
  private final JobState jobState;

  public ServiceTaskElementTerminatingHandler(
      IncidentState incidentState, CatchEventSubscriber catchEventSubscriber, JobState jobState) {
    super(catchEventSubscriber);
    this.incidentState = incidentState;
    this.jobState = jobState;
  }

  public ServiceTaskElementTerminatingHandler(
      WorkflowInstanceIntent nextState,
      IncidentState incidentState,
      CatchEventSubscriber catchEventSubscriber,
      JobState jobState) {
    super(nextState, catchEventSubscriber);
    this.incidentState = incidentState;
    this.jobState = jobState;
  }

  @Override
  protected boolean handleState(BpmnStepContext<T> context) {
    if (!super.handleState(context)) {
      return false;
    }

    final ElementInstance elementInstance = context.getElementInstance();
    final long jobKey = elementInstance.getJobKey();
    if (jobKey > 0) {
      final JobRecord job = jobState.getJob(jobKey);

      if (job != null) {
        context.getCommandWriter().appendFollowUpCommand(jobKey, JobIntent.CANCEL, job);
      } else {
        Loggers.WORKFLOW_PROCESSOR_LOGGER.warn(
            "Expected to find job with key {}, but no job found", jobKey);
      }

      resolveExistingJobIncident(jobKey, context);
    }

    return true;
  }

  private void resolveExistingJobIncident(long jobKey, BpmnStepContext<T> context) {
    final long jobIncidentKey = incidentState.getJobIncidentKey(jobKey);
    final boolean hasIncident = jobIncidentKey != IncidentState.MISSING_INCIDENT;

    if (hasIncident) {
      final IncidentRecord incidentRecord = incidentState.getIncidentRecord(jobIncidentKey);
      context.getOutput().appendResolvedIncidentEvent(jobIncidentKey, incidentRecord);
    }
  }
}

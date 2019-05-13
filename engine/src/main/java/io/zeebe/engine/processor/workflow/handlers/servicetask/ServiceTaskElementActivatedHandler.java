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

import io.zeebe.engine.processor.workflow.BpmnStepContext;
import io.zeebe.engine.processor.workflow.deployment.model.element.ExecutableServiceTask;
import io.zeebe.engine.processor.workflow.handlers.element.ElementActivatedHandler;
import io.zeebe.msgpack.value.DocumentValue;
import io.zeebe.protocol.impl.record.value.job.JobRecord;
import io.zeebe.protocol.impl.record.value.workflowinstance.WorkflowInstanceRecord;
import io.zeebe.protocol.intent.JobIntent;
import io.zeebe.protocol.intent.WorkflowInstanceIntent;
import org.agrona.DirectBuffer;

public class ServiceTaskElementActivatedHandler<T extends ExecutableServiceTask>
    extends ElementActivatedHandler<T> {

  public ServiceTaskElementActivatedHandler() {
    this(null);
  }

  public ServiceTaskElementActivatedHandler(WorkflowInstanceIntent nextState) {
    super(nextState);
  }

  private final JobRecord jobCommand = new JobRecord();

  @Override
  protected boolean handleState(BpmnStepContext<T> context) {
    if (super.handleState(context)) {
      final WorkflowInstanceRecord value = context.getValue();
      final ExecutableServiceTask serviceTask = context.getElement();

      populateJobFromTask(context, value, serviceTask);
      context.getCommandWriter().appendNewCommand(JobIntent.CREATE, jobCommand);

      return true;
    }

    return false;
  }

  private void populateJobFromTask(
      BpmnStepContext<T> context, WorkflowInstanceRecord value, ExecutableServiceTask serviceTask) {
    final DirectBuffer headers = serviceTask.getEncodedHeaders();

    jobCommand.reset();
    jobCommand
        .setType(serviceTask.getType())
        .setRetries(serviceTask.getRetries())
        .setVariables(DocumentValue.EMPTY_DOCUMENT)
        .setCustomHeaders(headers)
        .getHeaders()
        .setBpmnProcessId(value.getBpmnProcessId())
        .setWorkflowDefinitionVersion(value.getVersion())
        .setWorkflowKey(value.getWorkflowKey())
        .setWorkflowInstanceKey(value.getWorkflowInstanceKey())
        .setElementId(serviceTask.getId())
        .setElementInstanceKey(context.getRecord().getKey());
  }
}

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

import io.zeebe.broker.workflow.model.element.ExecutableServiceTask;
import io.zeebe.broker.workflow.processor.BpmnStepContext;
import io.zeebe.broker.workflow.processor.BpmnStepHandler;
import io.zeebe.protocol.impl.record.value.job.JobRecord;
import io.zeebe.protocol.impl.record.value.workflowinstance.WorkflowInstanceRecord;
import io.zeebe.protocol.intent.JobIntent;
import org.agrona.DirectBuffer;

public class CreateJobHandler implements BpmnStepHandler<ExecutableServiceTask> {

  private final JobRecord jobCommand = new JobRecord();

  @Override
  public void handle(BpmnStepContext<ExecutableServiceTask> context) {

    final WorkflowInstanceRecord value = context.getValue();
    final ExecutableServiceTask serviceTask = context.getElement();

    jobCommand.reset();

    jobCommand
        .setType(serviceTask.getType())
        .setRetries(serviceTask.getRetries())
        .setPayload(value.getPayload())
        .getHeaders()
        .setBpmnProcessId(value.getBpmnProcessId())
        .setWorkflowDefinitionVersion(value.getVersion())
        .setWorkflowKey(value.getWorkflowKey())
        .setWorkflowInstanceKey(value.getWorkflowInstanceKey())
        .setActivityId(serviceTask.getId())
        .setActivityInstanceKey(context.getRecord().getKey());

    final DirectBuffer headers = serviceTask.getEncodedHeaders();
    jobCommand.setCustomHeaders(headers);

    context.getCommandWriter().writeNewCommand(JobIntent.CREATE, jobCommand);
  }
}

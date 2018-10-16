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
package io.zeebe.broker.workflow.processor.instance;

import io.zeebe.broker.logstreams.processor.KeyGenerator;
import io.zeebe.broker.logstreams.processor.TypedRecord;
import io.zeebe.broker.logstreams.processor.TypedResponseWriter;
import io.zeebe.broker.workflow.processor.EventOutput;
import io.zeebe.broker.workflow.processor.WorkflowInstanceCommandContext;
import io.zeebe.broker.workflow.processor.WorkflowInstanceCommandHandler;
import io.zeebe.broker.workflow.state.DeployedWorkflow;
import io.zeebe.broker.workflow.state.WorkflowState;
import io.zeebe.protocol.clientapi.RejectionType;
import io.zeebe.protocol.impl.record.value.workflowinstance.WorkflowInstanceRecord;
import io.zeebe.protocol.intent.WorkflowInstanceIntent;
import org.agrona.DirectBuffer;

public class CreateWorkflowInstanceHandler implements WorkflowInstanceCommandHandler {

  private final KeyGenerator keyGenerator;
  private final WorkflowState workflowState;

  public CreateWorkflowInstanceHandler(KeyGenerator keyGenerator, WorkflowState workflowState) {
    this.keyGenerator = keyGenerator;
    this.workflowState = workflowState;
  }

  @Override
  public void handle(WorkflowInstanceCommandContext commandContext) {
    final TypedRecord<WorkflowInstanceRecord> record = commandContext.getRecord();
    final WorkflowInstanceRecord command = record.getValue();
    final TypedResponseWriter responseWriter = commandContext.getResponseWriter();

    final DeployedWorkflow workflowDefinition = getWorkflowDefinition(command);

    if (workflowDefinition != null) {
      final long workflowInstanceKey = keyGenerator.nextKey();
      command.setWorkflowInstanceKey(workflowInstanceKey);
      final DirectBuffer bpmnId = workflowDefinition.getWorkflow().getId();
      command
          .setBpmnProcessId(bpmnId)
          .setWorkflowKey(workflowDefinition.getKey())
          .setVersion(workflowDefinition.getVersion())
          .setActivityId(bpmnId);

      final EventOutput eventOutput = commandContext.getOutput();
      eventOutput.newBatch();
      eventOutput.writeFollowUpEvent(workflowInstanceKey, WorkflowInstanceIntent.CREATED, command);
      eventOutput.writeFollowUpEvent(
          workflowInstanceKey, WorkflowInstanceIntent.ELEMENT_READY, command);

      responseWriter.writeEventOnCommand(
          workflowInstanceKey, WorkflowInstanceIntent.CREATED, command, record);
    } else {
      commandContext.reject(RejectionType.BAD_VALUE, "Workflow is not deployed");
    }
  }

  private DeployedWorkflow getWorkflowDefinition(WorkflowInstanceRecord value) {
    final long workflowKey = value.getWorkflowKey();
    final DirectBuffer bpmnProcessId = value.getBpmnProcessId();
    final int version = value.getVersion();

    final DeployedWorkflow workflowDefinition;
    if (workflowKey <= 0) {
      if (version > 0) {
        workflowDefinition = workflowState.getWorkflowByProcessIdAndVersion(bpmnProcessId, version);
      } else {
        workflowDefinition = workflowState.getLatestWorkflowVersionByProcessId(bpmnProcessId);
      }
    } else {
      workflowDefinition = workflowState.getWorkflowByKey(workflowKey);
    }

    return workflowDefinition;
  }
}

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

import io.zeebe.broker.logstreams.processor.TypedRecord;
import io.zeebe.broker.logstreams.processor.TypedResponseWriter;
import io.zeebe.broker.workflow.model.element.ExecutableWorkflow;
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

  public static final String NO_WORKFLOW_FOUND_MESSAGE =
      "Expected to create an instance of workflow with key '%d', but no such workflow was found";
  public static final String NO_START_EVENT_FOUND_MESSAGE =
      "Expected to create an instance of workflow with key '%d', but no none start event was found";

  private final WorkflowState workflowState;

  public CreateWorkflowInstanceHandler(WorkflowState workflowState) {
    this.workflowState = workflowState;
  }

  @Override
  public void handle(WorkflowInstanceCommandContext commandContext) {
    final TypedRecord<WorkflowInstanceRecord> record = commandContext.getRecord();
    final WorkflowInstanceRecord command = record.getValue();
    final TypedResponseWriter responseWriter = commandContext.getResponseWriter();

    final DeployedWorkflow workflowDefinition = getWorkflowDefinition(command);

    if (workflowDefinition != null) {
      if (workflowDefinition.getWorkflow().getNoneStartEvent() != null) {
        final long workflowInstanceKey = commandContext.getKeyGenerator().nextKey();
        command.setWorkflowInstanceKey(workflowInstanceKey);
        final ExecutableWorkflow workflow = workflowDefinition.getWorkflow();
        final DirectBuffer bpmnId = workflow.getId();
        command
            .setBpmnProcessId(bpmnId)
            .setWorkflowKey(workflowDefinition.getKey())
            .setVersion(workflowDefinition.getVersion());

        final EventOutput eventOutput = commandContext.getOutput();
        eventOutput.appendFollowUpEvent(
            workflowInstanceKey, WorkflowInstanceIntent.ELEMENT_READY, command, workflow);

        workflowState
            .getElementInstanceState()
            .getVariablesState()
            .setVariablesLocalFromDocument(workflowInstanceKey, command.getPayload());

        responseWriter.writeEventOnCommand(
            workflowInstanceKey, WorkflowInstanceIntent.ELEMENT_READY, command, record);
      } else {
        commandContext.reject(
            RejectionType.INVALID_STATE,
            String.format(NO_START_EVENT_FOUND_MESSAGE, workflowDefinition.getKey()));
      }
    } else {
      commandContext.reject(
          RejectionType.NOT_FOUND,
          String.format(NO_WORKFLOW_FOUND_MESSAGE, command.getWorkflowKey()));
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

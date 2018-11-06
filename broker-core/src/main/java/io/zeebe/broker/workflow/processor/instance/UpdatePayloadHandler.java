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
import io.zeebe.broker.workflow.processor.EventOutput;
import io.zeebe.broker.workflow.processor.WorkflowInstanceCommandContext;
import io.zeebe.broker.workflow.processor.WorkflowInstanceCommandHandler;
import io.zeebe.broker.workflow.state.ElementInstance;
import io.zeebe.broker.workflow.state.IndexedRecord;
import io.zeebe.broker.workflow.state.WorkflowState;
import io.zeebe.protocol.clientapi.RejectionType;
import io.zeebe.protocol.impl.record.value.workflowinstance.WorkflowInstanceRecord;
import io.zeebe.protocol.intent.WorkflowInstanceIntent;

public class UpdatePayloadHandler implements WorkflowInstanceCommandHandler {

  private final WorkflowState workflowState;

  public UpdatePayloadHandler(WorkflowState workflowState) {
    this.workflowState = workflowState;
  }

  @Override
  public void handle(WorkflowInstanceCommandContext commandContext) {
    final TypedRecord<WorkflowInstanceRecord> command = commandContext.getRecord();
    final WorkflowInstanceRecord commandValue = command.getValue();
    final EventOutput output = commandContext.getOutput();
    final TypedResponseWriter responseWriter = commandContext.getResponseWriter();

    final ElementInstance elementInstance = commandContext.getElementInstance();

    if (elementInstance != null) {
      final WorkflowInstanceRecord elementInstanceValue = elementInstance.getValue();
      elementInstanceValue.setPayload(commandValue.getPayload());

      output.writeFollowUpEvent(
          command.getKey(), WorkflowInstanceIntent.PAYLOAD_UPDATED, elementInstanceValue);
      responseWriter.writeEventOnCommand(
          command.getKey(), WorkflowInstanceIntent.PAYLOAD_UPDATED, elementInstanceValue, command);
    } else {
      final IndexedRecord failedToken =
          workflowState.getElementInstanceState().getFailedToken(command.getKey());

      if (failedToken != null) {
        final WorkflowInstanceRecord value = failedToken.getValue();
        value.setPayload(commandValue.getPayload());

        output.writeFollowUpEvent(command.getKey(), WorkflowInstanceIntent.PAYLOAD_UPDATED, value);
        responseWriter.writeEventOnCommand(
            command.getKey(), WorkflowInstanceIntent.PAYLOAD_UPDATED, value, command);
      } else {
        commandContext.reject(RejectionType.NOT_APPLICABLE, "Workflow instance is not running");
      }
    }
  }
}

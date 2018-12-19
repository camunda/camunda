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
import io.zeebe.broker.workflow.processor.EventOutput;
import io.zeebe.broker.workflow.processor.WorkflowInstanceCommandContext;
import io.zeebe.broker.workflow.processor.WorkflowInstanceCommandHandler;
import io.zeebe.broker.workflow.state.ElementInstance;
import io.zeebe.protocol.clientapi.RejectionType;
import io.zeebe.protocol.impl.record.value.workflowinstance.WorkflowInstanceRecord;
import io.zeebe.protocol.intent.WorkflowInstanceIntent;

public class CancelWorkflowInstanceHandler implements WorkflowInstanceCommandHandler {

  private static final String ERROR_MSG_CHILD_INSTANCE =
      "Expected to find a workflow instance with key %d to cancel, but found a child element instance which cannot be canceled.";
  private static final String ERROR_MSG_NOT_FOUND =
      "Expected to find a running workflow instance with key %d, but found no workflow instance.";

  @Override
  public void handle(WorkflowInstanceCommandContext commandContext) {

    final TypedRecord<WorkflowInstanceRecord> command = commandContext.getRecord();
    final ElementInstance elementInstance = commandContext.getElementInstance();

    final boolean canCancel = elementInstance != null && elementInstance.canTerminate();

    if (canCancel) {
      if (elementInstance.getParentKey() < 0) {
        final EventOutput output = commandContext.getOutput();
        final WorkflowInstanceRecord value = elementInstance.getValue();

        output.appendFollowUpEvent(
            command.getKey(), WorkflowInstanceIntent.ELEMENT_TERMINATING, value);

        commandContext
            .getResponseWriter()
            .writeEventOnCommand(
                command.getKey(), WorkflowInstanceIntent.ELEMENT_TERMINATING, value, command);
      } else {
        commandContext.reject(
            RejectionType.NOT_APPLICABLE,
            String.format(ERROR_MSG_CHILD_INSTANCE, command.getKey()));
      }
    } else {
      commandContext.reject(
          RejectionType.NOT_APPLICABLE, String.format(ERROR_MSG_NOT_FOUND, command.getKey()));
    }
  }
}

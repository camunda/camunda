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

import io.zeebe.broker.logstreams.processor.CommandProcessor;
import io.zeebe.broker.logstreams.processor.TypedRecord;
import io.zeebe.broker.workflow.data.WorkflowInstanceRecord;
import io.zeebe.broker.workflow.index.ElementInstance;
import io.zeebe.broker.workflow.index.ElementInstanceIndex;
import io.zeebe.protocol.clientapi.RejectionType;
import io.zeebe.protocol.intent.WorkflowInstanceIntent;

public final class UpdatePayloadProcessor implements CommandProcessor<WorkflowInstanceRecord> {

  private final ElementInstanceIndex scopeInstances;

  public UpdatePayloadProcessor(ElementInstanceIndex scopeInstances) {
    this.scopeInstances = scopeInstances;
  }

  @Override
  public void onCommand(
      TypedRecord<WorkflowInstanceRecord> command, CommandControl commandControl) {
    final WorkflowInstanceRecord commandValue = command.getValue();

    final ElementInstance workflowInstance =
        scopeInstances.getInstance(commandValue.getWorkflowInstanceKey());

    if (workflowInstance != null) {
      final WorkflowInstanceRecord workflowInstanceValue = workflowInstance.getValue();
      workflowInstanceValue.setPayload(commandValue.getPayload());
      workflowInstance.setValue(workflowInstance.getValue());
      commandControl.accept(WorkflowInstanceIntent.PAYLOAD_UPDATED);
    } else {
      commandControl.reject(RejectionType.NOT_APPLICABLE, "Workflow instance is not running");
    }
  }
}

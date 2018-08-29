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

import static io.zeebe.broker.workflow.data.WorkflowInstanceRecord.EMPTY_PAYLOAD;

import io.zeebe.broker.logstreams.processor.TypedBatchWriter;
import io.zeebe.broker.logstreams.processor.TypedRecord;
import io.zeebe.broker.logstreams.processor.TypedRecordProcessor;
import io.zeebe.broker.logstreams.processor.TypedResponseWriter;
import io.zeebe.broker.logstreams.processor.TypedStreamWriter;
import io.zeebe.broker.workflow.data.WorkflowInstanceRecord;
import io.zeebe.broker.workflow.index.ElementInstance;
import io.zeebe.broker.workflow.index.ElementInstanceIndex;
import io.zeebe.protocol.clientapi.RejectionType;
import io.zeebe.protocol.intent.WorkflowInstanceIntent;

public final class CancelWorkflowInstanceProcessor
    implements TypedRecordProcessor<WorkflowInstanceRecord> {

  private final ElementInstanceIndex scopeInstances;

  public CancelWorkflowInstanceProcessor(ElementInstanceIndex scopeInstances) {
    this.scopeInstances = scopeInstances;
  }

  @Override
  public void processRecord(
      TypedRecord<WorkflowInstanceRecord> command,
      TypedResponseWriter responseWriter,
      TypedStreamWriter streamWriter) {

    final ElementInstance workflowInstance = scopeInstances.getInstance(command.getKey());

    final boolean canCancel = workflowInstance != null && workflowInstance.canTerminate();

    if (canCancel) {
      cancelWorkflowInstance(command, workflowInstance, streamWriter, responseWriter);
    } else {
      final RejectionType rejectionType = RejectionType.NOT_APPLICABLE;
      final String rejectionReason = "Workflow instance is not running";
      streamWriter.writeRejection(command, rejectionType, rejectionReason);
      responseWriter.writeRejectionOnCommand(command, rejectionType, rejectionReason);
    }
  }

  private void cancelWorkflowInstance(
      TypedRecord<WorkflowInstanceRecord> command,
      ElementInstance workflowInstance,
      TypedStreamWriter writer,
      TypedResponseWriter responseWriter) {
    final WorkflowInstanceRecord workflowInstanceEvent = workflowInstance.getValue();

    workflowInstanceEvent.setPayload(EMPTY_PAYLOAD);

    final TypedBatchWriter batchWriter = writer.newBatch();

    batchWriter.addFollowUpEvent(
        command.getKey(), WorkflowInstanceIntent.CANCELING, workflowInstanceEvent);
    batchWriter.addFollowUpEvent(
        command.getKey(), WorkflowInstanceIntent.ELEMENT_TERMINATING, workflowInstanceEvent);

    workflowInstance.setState(WorkflowInstanceIntent.ELEMENT_TERMINATING);

    responseWriter.writeEventOnCommand(command.getKey(), WorkflowInstanceIntent.CANCELING, command);
  }
}

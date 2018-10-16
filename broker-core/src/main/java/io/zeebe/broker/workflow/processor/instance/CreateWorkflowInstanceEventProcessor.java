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
import io.zeebe.broker.logstreams.processor.TypedBatchWriter;
import io.zeebe.broker.logstreams.processor.TypedRecord;
import io.zeebe.broker.logstreams.processor.TypedRecordProcessor;
import io.zeebe.broker.logstreams.processor.TypedResponseWriter;
import io.zeebe.broker.logstreams.processor.TypedStreamWriter;
import io.zeebe.broker.workflow.state.DeployedWorkflow;
import io.zeebe.broker.workflow.state.WorkflowState;
import io.zeebe.protocol.clientapi.RejectionType;
import io.zeebe.protocol.impl.record.RecordMetadata;
import io.zeebe.protocol.impl.record.value.workflowinstance.WorkflowInstanceRecord;
import io.zeebe.protocol.intent.WorkflowInstanceIntent;
import org.agrona.DirectBuffer;

public final class CreateWorkflowInstanceEventProcessor
    implements TypedRecordProcessor<WorkflowInstanceRecord> {

  private final WorkflowState workflowState;

  public CreateWorkflowInstanceEventProcessor(WorkflowState workflowState) {
    this.workflowState = workflowState;
  }

  private long requestId;
  private int requestStreamId;

  private long workflowInstanceKey;

  @Override
  public void processRecord(
      TypedRecord<WorkflowInstanceRecord> command,
      TypedResponseWriter responseWriter,
      TypedStreamWriter streamWriter) {
    final WorkflowInstanceRecord workflowInstanceCommand = command.getValue();

    this.requestId = command.getMetadata().getRequestId();
    this.requestStreamId = command.getMetadata().getRequestStreamId();

    final KeyGenerator keyGenerator = streamWriter.getKeyGenerator();
    this.workflowInstanceKey = keyGenerator.nextKey();

    workflowInstanceCommand.setWorkflowInstanceKey(workflowInstanceKey);

    createWorkflowInstance(command, streamWriter);
  }

  private void addRequestMetadata(RecordMetadata metadata) {
    metadata.requestId(requestId).requestStreamId(requestStreamId);
  }

  private void createWorkflowInstance(
      TypedRecord<WorkflowInstanceRecord> command, TypedStreamWriter streamWriter) {
    final WorkflowInstanceRecord value = command.getValue();

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

    if (workflowDefinition != null) {
      value
          .setBpmnProcessId(workflowDefinition.getWorkflow().getId())
          .setWorkflowKey(workflowDefinition.getKey())
          .setVersion(workflowDefinition.getVersion());
      acceptCommand(command, streamWriter);
    } else {
      rejectCommand(command, streamWriter, RejectionType.BAD_VALUE, "Workflow is not deployed");
    }
  }

  private void acceptCommand(
      TypedRecord<WorkflowInstanceRecord> command, TypedStreamWriter writer) {
    final WorkflowInstanceRecord value = command.getValue();
    value.setActivityId(value.getBpmnProcessId());

    final TypedBatchWriter batchWriter = writer.newBatch();
    batchWriter.addFollowUpEvent(
        workflowInstanceKey, WorkflowInstanceIntent.CREATED, value, this::addRequestMetadata);
    batchWriter.addFollowUpEvent(workflowInstanceKey, WorkflowInstanceIntent.ELEMENT_READY, value);
  }

  private void rejectCommand(
      TypedRecord<WorkflowInstanceRecord> command,
      TypedStreamWriter writer,
      RejectionType rejectionType,
      String rejectionReason) {
    writer.writeRejection(command, rejectionType, rejectionReason, this::addRequestMetadata);
  }
}

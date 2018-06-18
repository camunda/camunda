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
package io.zeebe.broker.system.workflow.repository.api.client;

import io.zeebe.broker.system.workflow.repository.processor.state.WorkflowRepositoryIndex.WorkflowMetadata;
import io.zeebe.broker.system.workflow.repository.service.WorkflowRepositoryService;
import io.zeebe.broker.transport.controlmessage.AbstractControlMessageHandler;
import io.zeebe.msgpack.value.ValueArray;
import io.zeebe.protocol.Protocol;
import io.zeebe.protocol.clientapi.ControlMessageType;
import io.zeebe.protocol.clientapi.ErrorCode;
import io.zeebe.protocol.impl.RecordMetadata;
import io.zeebe.transport.ServerOutput;
import io.zeebe.util.buffer.BufferUtil;
import io.zeebe.util.sched.ActorControl;
import io.zeebe.util.sched.future.ActorFuture;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.agrona.DirectBuffer;

public class ListWorkflowsControlMessageHandler extends AbstractControlMessageHandler {
  private AtomicReference<WorkflowRepositoryService> workflowRepositoryServiceRef =
      new AtomicReference<>();

  public ListWorkflowsControlMessageHandler(ServerOutput output) {
    super(output);
  }

  @Override
  public ControlMessageType getMessageType() {
    return ControlMessageType.LIST_WORKFLOWS;
  }

  @Override
  public void handle(
      ActorControl actor, int partitionId, DirectBuffer buffer, RecordMetadata metadata) {
    final WorkflowRepositoryService repository = workflowRepositoryServiceRef.get();

    if (repository == null) {
      sendErrorResponse(
          actor,
          metadata.getRequestStreamId(),
          metadata.getRequestId(),
          ErrorCode.PARTITION_NOT_FOUND,
          "Workflow request must address the leader of the system partition %d",
          Protocol.SYSTEM_PARTITION);
    } else {
      final ListWorkflowsControlRequest controlRequest = new ListWorkflowsControlRequest();
      controlRequest.wrap(buffer);

      final String topicName = BufferUtil.bufferAsString(controlRequest.getTopicName());
      final String bpmnProcessId = BufferUtil.bufferAsString(controlRequest.getBpmnProcessId());

      final ActorFuture<List<WorkflowMetadata>> future;

      if (!bpmnProcessId.isEmpty()) {
        future = repository.getWorkflowsByBpmnProcessId(topicName, bpmnProcessId);
      } else {
        future = repository.getWorkflowsByTopic(topicName);
      }

      actor.runOnCompletion(
          future,
          (workflows, err) -> {
            if (err != null) {
              sendErrorResponse(
                  actor, metadata.getRequestStreamId(), metadata.getRequestId(), err.getMessage());
            } else {
              final ListWorkflowsResponse response = new ListWorkflowsResponse();
              final ValueArray<
                      io.zeebe.broker.system.workflow.repository.api.client.WorkflowMetadata>
                  responseWorklows = response.getWorkflows();

              workflows.forEach(
                  (workflow) ->
                      responseWorklows
                          .add()
                          .setTopicName(workflow.getTopicName())
                          .setBpmnProcessId(workflow.getBpmnProcessId())
                          .setWorkflowKey(workflow.getKey())
                          .setResourceName(workflow.getResourceName())
                          .setVersion(workflow.getVersion()));

              sendResponse(actor, metadata.getRequestStreamId(), metadata.getRequestId(), response);
            }
          });
    }
  }

  public void setWorkflowRepositoryService(WorkflowRepositoryService service) {
    workflowRepositoryServiceRef.set(service);
  }
}

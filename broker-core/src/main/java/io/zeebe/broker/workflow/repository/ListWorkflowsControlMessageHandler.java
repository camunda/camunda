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
package io.zeebe.broker.workflow.repository;

import io.zeebe.broker.transport.controlmessage.AbstractControlMessageHandler;
import io.zeebe.msgpack.value.ValueArray;
import io.zeebe.protocol.clientapi.ControlMessageType;
import io.zeebe.protocol.impl.data.repository.ListWorkflowsControlRequest;
import io.zeebe.protocol.impl.data.repository.ListWorkflowsResponse;
import io.zeebe.protocol.impl.data.repository.WorkflowMetadata;
import io.zeebe.transport.ServerOutput;
import io.zeebe.util.buffer.BufferUtil;
import io.zeebe.util.sched.ActorControl;
import io.zeebe.util.sched.future.ActorFuture;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.agrona.DirectBuffer;

public class ListWorkflowsControlMessageHandler extends AbstractControlMessageHandler {
  private final AtomicReference<WorkflowRepositoryService> workflowRepositoryServiceRef =
      new AtomicReference<>();

  public ListWorkflowsControlMessageHandler(final ServerOutput output) {
    super(output);
  }

  @Override
  public ControlMessageType getMessageType() {
    return ControlMessageType.LIST_WORKFLOWS;
  }

  @Override
  public void handle(
      final ActorControl actor,
      final int partitionId,
      final DirectBuffer buffer,
      final long requestId,
      final int requestStreamId) {
    final WorkflowRepositoryService repository = workflowRepositoryServiceRef.get();

    if (repository == null) {
      sendResponse(
          actor,
          () ->
              errorResponseWriter
                  .invalidDeploymentPartition(partitionId, requestStreamId)
                  .tryWriteResponse(requestStreamId, requestStreamId));
    } else {
      final ListWorkflowsControlRequest controlRequest = new ListWorkflowsControlRequest();
      controlRequest.wrap(buffer);

      final String bpmnProcessId = BufferUtil.bufferAsString(controlRequest.getBpmnProcessId());

      final ActorFuture<List<WorkflowMetadata>> future;

      if (!bpmnProcessId.isEmpty()) {
        future = repository.getWorkflowsByBpmnProcessId(controlRequest.getBpmnProcessId());
      } else {
        future = repository.getWorkflows();
      }

      actor.runOnCompletion(
          future,
          (workflows, err) -> {
            if (err != null) {
              sendErrorResponse(actor, requestStreamId, requestId, err.getMessage());
            } else {
              final ListWorkflowsResponse response = new ListWorkflowsResponse();
              final ValueArray<WorkflowMetadata> responseWorkflows = response.getWorkflows();

              if (workflows != null) {
                workflows.forEach(
                    (workflow) ->
                        responseWorkflows
                            .add()
                            .setBpmnProcessId(workflow.getBpmnProcessId())
                            .setWorkflowKey(workflow.getWorkflowKey())
                            .setResourceName(workflow.getResourceName())
                            .setVersion(workflow.getVersion()));
              }

              sendResponse(actor, requestStreamId, requestId, response);
            }
          });
    }
  }

  public void setWorkflowRepositoryService(final WorkflowRepositoryService service) {
    workflowRepositoryServiceRef.set(service);
  }
}

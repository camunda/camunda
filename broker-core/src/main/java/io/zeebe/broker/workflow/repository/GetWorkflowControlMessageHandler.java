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
import io.zeebe.protocol.Protocol;
import io.zeebe.protocol.clientapi.ControlMessageType;
import io.zeebe.protocol.clientapi.ErrorCode;
import io.zeebe.protocol.impl.data.repository.GetWorkflowControlRequest;
import io.zeebe.protocol.impl.data.repository.WorkflowMetadataAndResource;
import io.zeebe.transport.ServerOutput;
import io.zeebe.util.buffer.BufferUtil;
import io.zeebe.util.sched.ActorControl;
import io.zeebe.util.sched.future.ActorFuture;
import java.util.concurrent.atomic.AtomicReference;
import org.agrona.DirectBuffer;

public class GetWorkflowControlMessageHandler extends AbstractControlMessageHandler {
  private final AtomicReference<WorkflowRepositoryService> workflowRepositroyServiceRef =
      new AtomicReference<>();

  public GetWorkflowControlMessageHandler(final ServerOutput output) {
    super(output);
  }

  @Override
  public ControlMessageType getMessageType() {
    return ControlMessageType.GET_WORKFLOW;
  }

  @Override
  public void handle(
      final ActorControl actor,
      final int partitionId,
      final DirectBuffer buffer,
      final long requestId,
      final int requestStreamId) {
    final WorkflowRepositoryService repository = workflowRepositroyServiceRef.get();

    if (repository == null) {
      sendErrorResponse(
          actor,
          requestStreamId,
          requestId,
          ErrorCode.PARTITION_NOT_FOUND,
          "Workflow request must address the leader of the first partition %d",
          Protocol.DEPLOYMENT_PARTITION);
    } else {
      final GetWorkflowControlRequest controlRequest = new GetWorkflowControlRequest();
      controlRequest.wrap(buffer);

      final String bpmnProcessId = BufferUtil.bufferAsString(controlRequest.getBpmnProcessId());
      final long workflowKey = controlRequest.getWorkflowKey();

      final ActorFuture<WorkflowMetadataAndResource> future;

      final String errorMessage;

      if (workflowKey > 0) {
        future = repository.getWorkflowByKey(workflowKey);
        errorMessage = String.format("No workflow found with key '%d'", workflowKey);
      } else {
        final int version = controlRequest.getVersion();

        if (version == -1) {
          future = repository.getLatestWorkflowByBpmnProcessId(controlRequest.getBpmnProcessId());
          errorMessage =
              String.format("No workflow found with BPMN process id '%s'", bpmnProcessId);
        } else {
          future =
              repository.getWorkflowByBpmnProcessIdAndVersion(
                  controlRequest.getBpmnProcessId(), version);
          errorMessage =
              String.format(
                  "No workflow found with BPMN process id '%s' and version '%d'",
                  bpmnProcessId, version);
        }
      }

      actor.runOnCompletion(
          future,
          (workflowAndResource, err) -> {
            if (err != null) {
              sendErrorResponse(actor, requestStreamId, requestId, err.getMessage());
            } else {
              if (workflowAndResource != null) {
                sendResponse(actor, requestStreamId, requestId, workflowAndResource);
              } else {
                sendErrorResponse(
                    actor, requestStreamId, requestId, ErrorCode.NOT_FOUND, errorMessage);
              }
            }
          });
    }
  }

  public void setWorkflowRepositoryService(final WorkflowRepositoryService service) {
    workflowRepositroyServiceRef.set(service);
  }
}

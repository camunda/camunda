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
import io.zeebe.protocol.impl.data.repository.GetWorkflowControlRequest;
import io.zeebe.protocol.impl.data.repository.WorkflowMetadataAndResource;
import io.zeebe.transport.ServerOutput;
import io.zeebe.util.buffer.BufferUtil;
import io.zeebe.util.sched.ActorControl;
import io.zeebe.util.sched.future.ActorFuture;
import java.util.concurrent.atomic.AtomicReference;
import org.agrona.DirectBuffer;

public class GetWorkflowControlMessageHandler extends AbstractControlMessageHandler {
  private static final String WRONG_PARTITION_ERROR_MESSAGE =
      "Expected to request workflow from partition '%d', but it should be requested from the leader of partition '%d'";
  private static final String NO_WORKFLOW_WITH_KEY_MESSAGE =
      "Expected to get workflow with key '%d', but no such workflow found";
  private static final String NO_WORKFLOW_WITH_ID_MESSAGE =
      "Expected to get workflow with BPMN process id '%s', but no such workflow found";
  private static final String NO_WORKFLOW_WITH_ID_AND_VERSION_MESSAGE =
      "Expected to get workflow with BPMN process id '%s' and version '%d', but no such workflow found";

  private final AtomicReference<WorkflowRepositoryService> workflowRepositoryServiceRef =
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
    final WorkflowRepositoryService repository = workflowRepositoryServiceRef.get();

    if (repository == null) {
      sendResponse(
          actor,
          () ->
              errorResponseWriter
                  .invalidDeploymentPartition(Protocol.DEPLOYMENT_PARTITION, partitionId)
                  .tryWriteResponse(requestStreamId, requestId));
    } else {
      final GetWorkflowControlRequest controlRequest = new GetWorkflowControlRequest();
      controlRequest.wrap(buffer);

      final String bpmnProcessId = BufferUtil.bufferAsString(controlRequest.getBpmnProcessId());
      final long workflowKey = controlRequest.getWorkflowKey();

      final ActorFuture<WorkflowMetadataAndResource> future;

      final String workflowIdentifier;

      if (workflowKey > 0) {
        future = repository.getWorkflowByKey(workflowKey);
        workflowIdentifier = String.format("key '%d'", workflowKey);
      } else {
        final int version = controlRequest.getVersion();

        if (version == -1) {
          future = repository.getLatestWorkflowByBpmnProcessId(controlRequest.getBpmnProcessId());
          workflowIdentifier = String.format("BPMN process ID '%s'", bpmnProcessId);
        } else {
          future =
              repository.getWorkflowByBpmnProcessIdAndVersion(
                  controlRequest.getBpmnProcessId(), version);
          workflowIdentifier =
              String.format("BPMN process ID '%s' and version '%d'", bpmnProcessId, version);
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
                sendResponse(
                    actor,
                    () ->
                        errorResponseWriter
                            .workflowNotFound(workflowIdentifier)
                            .tryWriteResponse(requestStreamId, requestId));
              }
            }
          });
    }
  }

  public void setWorkflowRepositoryService(final WorkflowRepositoryService service) {
    workflowRepositoryServiceRef.set(service);
  }
}

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
package io.zeebe.broker.system.workflow.repository.api.management;

import io.zeebe.broker.system.workflow.repository.processor.state.WorkflowRepositoryIndex.WorkflowMetadata;
import io.zeebe.broker.system.workflow.repository.service.WorkflowRepositoryService;
import io.zeebe.clustering.management.FetchWorkflowRequestDecoder;
import io.zeebe.transport.*;
import io.zeebe.util.buffer.BufferUtil;
import io.zeebe.util.collection.Tuple;
import io.zeebe.util.sched.ActorControl;
import io.zeebe.util.sched.future.ActorFuture;
import org.agrona.DirectBuffer;

public class FetchWorkflowRequestHandler {
  private final FetchWorkflowRequest fetchWorkflowRequest = new FetchWorkflowRequest();

  private final WorkflowRepositoryService workflowRepositoryService;

  public FetchWorkflowRequestHandler(WorkflowRepositoryService workflowRepositoryService) {
    this.workflowRepositoryService = workflowRepositoryService;
  }

  public void onFetchWorkflow(
      DirectBuffer buffer,
      int offset,
      int length,
      ServerOutput output,
      RemoteAddress remoteAddress,
      long requestId,
      ActorControl actor) {
    fetchWorkflowRequest.wrap(buffer, offset, length);

    final ActorFuture<Tuple<WorkflowMetadata, DirectBuffer>> future;

    final long workflowKey = fetchWorkflowRequest.getWorkflowKey();

    if (workflowKey == FetchWorkflowRequestDecoder.workflowKeyNullValue()) {
      final String topicName = BufferUtil.bufferAsString(fetchWorkflowRequest.getTopicName());

      final int version = fetchWorkflowRequest.getVersion();

      final String bpmnProcessId =
          BufferUtil.bufferAsString(fetchWorkflowRequest.getBpmnProcessId());

      if (version == FetchWorkflowRequestDecoder.versionMaxValue()) {
        future =
            workflowRepositoryService.getLatestWorkflowByBpmnProcessId(topicName, bpmnProcessId);
      } else {
        future =
            workflowRepositoryService.getWorkflowByBpmnProcessIdAndVersion(
                topicName, bpmnProcessId, version);
      }
    } else {
      future = workflowRepositoryService.getWorkflowByKey(workflowKey);
    }

    actor.runOnCompletion(
        future,
        (workflowAndResource, err) -> {
          final FetchWorkflowResponse fetchWorkflowResponse = new FetchWorkflowResponse();

          if (workflowAndResource != null) {
            final WorkflowMetadata workflow = workflowAndResource.getLeft();

            fetchWorkflowResponse
                .workflowKey(workflow.getKey())
                .version(workflow.getVersion())
                .bpmnProcessId(BufferUtil.wrapString(workflow.getBpmnProcessId()))
                .bpmnXml(workflowAndResource.getRight());
          }

          final ServerResponse serverResponse =
              new ServerResponse()
                  .writer(fetchWorkflowResponse)
                  .requestId(requestId)
                  .remoteAddress(remoteAddress);

          actor.runUntilDone(
              () -> {
                if (output.sendResponse(serverResponse)) {
                  actor.done();
                } else {
                  actor.yield();
                }
              });
        });
  }
}

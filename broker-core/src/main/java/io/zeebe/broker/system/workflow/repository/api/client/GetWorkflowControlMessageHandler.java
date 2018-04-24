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

import java.util.concurrent.atomic.AtomicReference;

import org.agrona.DirectBuffer;

import io.zeebe.broker.system.workflow.repository.processor.state.WorkflowRepositoryIndex.WorkflowMetatata;
import io.zeebe.broker.system.workflow.repository.service.WorkflowRepositoryService;
import io.zeebe.broker.transport.controlmessage.AbstractControlMessageHandler;
import io.zeebe.protocol.Protocol;
import io.zeebe.protocol.clientapi.ControlMessageType;
import io.zeebe.protocol.clientapi.ErrorCode;
import io.zeebe.protocol.impl.RecordMetadata;
import io.zeebe.transport.ServerOutput;
import io.zeebe.util.buffer.BufferUtil;
import io.zeebe.util.collection.Tuple;
import io.zeebe.util.sched.ActorControl;
import io.zeebe.util.sched.future.ActorFuture;

public class GetWorkflowControlMessageHandler extends AbstractControlMessageHandler
{
    private AtomicReference<WorkflowRepositoryService> workflowRepositroyServiceRef = new AtomicReference<>();

    public GetWorkflowControlMessageHandler(ServerOutput output)
    {
        super(output);
    }

    @Override
    public ControlMessageType getMessageType()
    {
        return ControlMessageType.GET_WORKFLOW;
    }

    @Override
    public void handle(ActorControl actor, int partitionId, DirectBuffer buffer, RecordMetadata metadata)
    {
        final WorkflowRepositoryService repository = workflowRepositroyServiceRef.get();

        if (repository == null)
        {
            sendErrorResponse(actor, metadata.getRequestStreamId(), metadata.getRequestId(), ErrorCode.PARTITION_NOT_FOUND, "Workflow request must address the leader of the system partition %d", Protocol.SYSTEM_PARTITION);
        }
        else
        {
            final GetWorkflowControlRequest controlRequest = new GetWorkflowControlRequest();
            controlRequest.wrap(buffer);

            final String topicName = BufferUtil.bufferAsString(controlRequest.getTopicName());
            final String bpmnProcessId = BufferUtil.bufferAsString(controlRequest.getBpmnProcessId());
            final long workflowKey = controlRequest.getWorkflowKey();

            final ActorFuture<Tuple<WorkflowMetatata, DirectBuffer>> future;

            final String errorMessage;

            if (workflowKey != -1)
            {
                future = repository.getWorkflowByKey(workflowKey);
                errorMessage = String.format("No workflow found with key '%d'", workflowKey);
            }
            else
            {
                final int version = controlRequest.getVersion();

                if (version == -1)
                {
                    future = repository.getLatestWorkflowByBpmnProcessId(topicName, bpmnProcessId);
                    errorMessage = String.format("No workflow found with bpmnProcessId '%s'", bpmnProcessId);
                }
                else
                {
                    future = repository.getWorkflowByBpmnProcessIdAndVersion(topicName, bpmnProcessId, version);
                    errorMessage = String.format("No workflow found with bpmnProcessId '%s' and version '%d'", bpmnProcessId, version);
                }
            }

            actor.runOnCompletion(future, (workflowAndResource, err) ->
            {
                if (err != null)
                {
                    sendErrorResponse(actor, metadata.getRequestStreamId(), metadata.getRequestId(), err.getMessage());
                }
                else
                {
                    if (workflowAndResource != null)
                    {
                        final WorkflowMetatata workflow = workflowAndResource.getLeft();

                        final WorkflowMetadataAndResource controlResponse = new WorkflowMetadataAndResource();

                        controlResponse.setBpmnXml(workflowAndResource.getRight())
                            .setWorkflowKey(workflow.getKey())
                            .setTopicName(BufferUtil.wrapString(topicName))
                            .setVersion(workflow.getVersion())
                            .setBpmnProcessId(BufferUtil.wrapString(workflow.getBpmnProcessId()));

                        sendResponse(actor, metadata.getRequestStreamId(), metadata.getRequestId(), controlResponse);
                    }
                    else
                    {
                        sendErrorResponse(actor, metadata.getRequestStreamId(), metadata.getRequestId(), ErrorCode.NOT_FOUND, errorMessage);
                    }
                }
            });
        }
    }


    public void setWorkflowRepositoryService(WorkflowRepositoryService service)
    {
        workflowRepositroyServiceRef.set(service);
    }
}

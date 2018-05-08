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
package io.zeebe.broker.system.deployment.service;

import java.util.concurrent.atomic.AtomicReference;

import org.agrona.DirectBuffer;

import io.zeebe.broker.transport.controlmessage.AbstractControlMessageHandler;
import io.zeebe.broker.transport.controlmessage.RequestWorkflowControlRequest;
import io.zeebe.broker.transport.controlmessage.RequestWorkflowControlResponse;
import io.zeebe.protocol.Protocol;
import io.zeebe.protocol.clientapi.ControlMessageType;
import io.zeebe.protocol.clientapi.ErrorCode;
import io.zeebe.protocol.impl.RecordMetadata;
import io.zeebe.transport.ServerOutput;
import io.zeebe.util.sched.ActorControl;
import io.zeebe.util.sched.future.ActorFuture;

public class RequestWorkflowControlMessageHandler extends AbstractControlMessageHandler
{
    private AtomicReference<WorkflowRepositoryService> workflowRepositroyServiceRef = new AtomicReference<>();

    public RequestWorkflowControlMessageHandler(ServerOutput output)
    {
        super(output);
    }

    @Override
    public ControlMessageType getMessageType()
    {
        return ControlMessageType.REQUEST_WORKFLOW;
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
            final RequestWorkflowControlRequest controlRequest = new RequestWorkflowControlRequest();
            controlRequest.wrap(buffer);

            final DirectBuffer topicName = controlRequest.getTopicName();
            final long workflowKey = controlRequest.getWorkflowKey();

            final ActorFuture<DeploymentCachedWorkflow> workflowFuture;

            if (workflowKey != -1)
            {
                workflowFuture = repository.getWorkflowByKey(workflowKey);
            }
            else
            {
                final DirectBuffer bpmnProcessId = controlRequest.getBpmnProcessId();
                final int version = controlRequest.getVersion();

                if (version == -1)
                {
                    workflowFuture = repository.getLatestWorkflowByBpmnProcessId(topicName, bpmnProcessId);
                }
                else
                {
                    workflowFuture = repository.getWorkflowByBpmnProcessIdAndVersion(topicName, bpmnProcessId, version);
                }
            }

            actor.runOnCompletion(workflowFuture, (workflow, err) ->
            {
                if (err != null)
                {
                    sendErrorResponse(actor, metadata.getRequestStreamId(), metadata.getRequestId(), err.getMessage());
                }
                else
                {
                    if (workflow != null)
                    {

                        final RequestWorkflowControlResponse controlResponse = new RequestWorkflowControlResponse();

                        controlResponse.setWorkflowKey(workflow.getWorkflowKey())
                            .setBpmnXml(workflow.getBpmnXml())
                            .setTopicName(topicName)
                            .setVersion(workflow.getVersion())
                            .setBpmnProcessId(workflow.getBpmnProcessId());

                        sendResponse(actor, metadata.getRequestStreamId(), metadata.getRequestId(), controlResponse);
                    }
                    else
                    {
                        sendErrorResponse(actor, metadata.getRequestStreamId(), metadata.getRequestId(), ErrorCode.NOT_FOUND, "No workflow with key %d deployed", workflowKey);
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

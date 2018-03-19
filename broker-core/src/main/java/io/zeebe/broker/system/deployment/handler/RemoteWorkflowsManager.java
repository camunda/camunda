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
package io.zeebe.broker.system.deployment.handler;

import java.util.Iterator;
import java.util.function.IntConsumer;
import java.util.function.Predicate;

import org.agrona.DirectBuffer;
import org.agrona.collections.IntArrayList;
import org.slf4j.Logger;

import io.zeebe.broker.Loggers;
import io.zeebe.broker.clustering.management.PartitionManager;
import io.zeebe.broker.clustering.member.Member;
import io.zeebe.broker.logstreams.processor.StreamProcessorLifecycleAware;
import io.zeebe.broker.logstreams.processor.TypedStreamProcessor;
import io.zeebe.broker.system.deployment.data.PendingDeployments;
import io.zeebe.broker.system.deployment.data.PendingDeployments.PendingDeployment;
import io.zeebe.broker.system.deployment.data.PendingWorkflows;
import io.zeebe.broker.system.deployment.data.PendingWorkflows.PendingWorkflow;
import io.zeebe.broker.system.deployment.data.PendingWorkflows.PendingWorkflowIterator;
import io.zeebe.broker.system.deployment.message.CreateWorkflowRequest;
import io.zeebe.broker.system.deployment.message.CreateWorkflowResponse;
import io.zeebe.broker.system.deployment.message.DeleteWorkflowMessage;
import io.zeebe.broker.workflow.data.DeploymentState;
import io.zeebe.broker.workflow.data.WorkflowEvent;
import io.zeebe.transport.ClientOutput;
import io.zeebe.transport.ClientResponse;
import io.zeebe.transport.ClientTransport;
import io.zeebe.transport.RemoteAddress;
import io.zeebe.transport.SocketAddress;
import io.zeebe.transport.TransportMessage;
import io.zeebe.util.buffer.BufferWriter;
import io.zeebe.util.collection.IntIterator;
import io.zeebe.util.sched.ActorControl;
import io.zeebe.util.sched.future.ActorFuture;

public class RemoteWorkflowsManager implements StreamProcessorLifecycleAware
{
    private static final Logger LOG = Loggers.SYSTEM_LOGGER;

    private final CreateWorkflowRequest createRequest = new CreateWorkflowRequest();
    private final CreateWorkflowResponse createResponse = new CreateWorkflowResponse();

    private final DeleteWorkflowMessage deleteMessage = new DeleteWorkflowMessage();

    private final TransportMessage transportMessage = new TransportMessage();

    private final PartitionManager partitionManager;
    private final ClientTransport managementClient;
    private final ClientOutput output;

    private final DeploymentEventWriter writer;
    private final PendingDeployments pendingDeployments;
    private final PendingWorkflows pendingWorkflows;

    private ActorControl actor;

    public RemoteWorkflowsManager(
            PendingDeployments pendingDeployments,
            PendingWorkflows pendingWorkflows,
            PartitionManager partitionManager,
            DeploymentEventWriter writer,
            ClientTransport managementClient)
    {
        this.pendingDeployments = pendingDeployments;
        this.pendingWorkflows = pendingWorkflows;
        this.partitionManager = partitionManager;
        this.managementClient = managementClient;
        this.writer = writer;
        this.output = managementClient.getOutput();
    }

    @Override
    public void onOpen(TypedStreamProcessor streamProcessor)
    {
        this.actor = streamProcessor.getActor();
    }

    public boolean distributeWorkflow(
            IntArrayList partitionIds,
            long workflowKey,
            WorkflowEvent event)
    {
        createRequest
            .workflowKey(workflowKey)
            .deploymentKey(event.getDeploymentKey())
            .version(event.getVersion())
            .bpmnProcessId(event.getBpmnProcessId())
            .bpmnXml(event.getBpmnXml());

        return forEachPartition(partitionIds, createRequest::partitionId, addr ->
        {
            LOG.debug("Send create workflow request to '{}'. Deployment-Key: {}, Workflow-Key: {}",
                addr, event.getDeploymentKey(), workflowKey);

            final ActorFuture<ClientResponse> requestFuture = sendRequest(createRequest, addr);
            actor.runOnCompletion(requestFuture, this::onRequestResolved);

            return true;
        });
    }

    public boolean deleteWorkflow(
            IntArrayList partitionIds,
            long workflowKey,
            WorkflowEvent event)
    {
        deleteMessage
            .workflowKey(workflowKey)
            .deploymentKey(event.getDeploymentKey())
            .version(event.getVersion())
            .bpmnProcessId(event.getBpmnProcessId())
            .bpmnXml(event.getBpmnXml());

        return forEachPartition(partitionIds, deleteMessage::partitionId, addr ->
        {
            LOG.debug("Send delete workflow message to '{}'. Deployment-Key: {}, Workflow-Key: {}",
                      addr, event.getDeploymentKey(), workflowKey);
            return sendMessage(deleteMessage, addr);
        });
    }

    private boolean forEachPartition(IntArrayList partitionIds, IntConsumer partitionIdConsumer, Predicate<SocketAddress> action)
    {
        boolean success = true;

        final Iterator<Member> members = partitionManager.getKnownMembers();

        while (members.hasNext() && success)
        {
            final Member member = members.next();

            final IntIterator leadingPartitions = member.getLeadingPartitions();
            while (leadingPartitions.hasNext() && success)
            {
                final int partitionId = leadingPartitions.nextInt();

                if (partitionIds.containsInt(partitionId))
                {
                    partitionIdConsumer.accept(partitionId);

                    success = action.test(member.getManagementAddress());
                }
            }
        }

        return true;
    }

    private ActorFuture<ClientResponse> sendRequest(final BufferWriter request, final SocketAddress addr)
    {
        final RemoteAddress remoteAddress = managementClient.registerRemoteAddress(addr);
        return output.sendRequest(remoteAddress, request);
    }

    private void onRequestResolved(ClientResponse successfulRequest, Throwable throwable)
    {
        if (throwable != null)
        {
            onRequestFailed();
        }
        else
        {
            onRequestSuccessful(successfulRequest);
        }
    }

    private void onRequestFailed()
    {
        LOG.info("Create workflow request failed.");
    }

    private void onRequestSuccessful(ClientResponse request)
    {
        try
        {
            final DirectBuffer responseBuffer = request.getResponseBuffer();
            createResponse.wrap(responseBuffer, 0, responseBuffer.capacity());

            final long workflowKey = createResponse.getWorkflowKey();
            final int partitionId = createResponse.getPartitionId();
            final long deploymentKey = createResponse.getDeploymentKey();

            final PendingWorkflow pendingWorkflow = pendingWorkflows.get(workflowKey, partitionId);
            if (pendingWorkflow != null && pendingWorkflow.getState() == PendingWorkflows.STATE_CREATE)
            {
                // ignore response if pending workflow or deployment is already processed
                pendingWorkflows.put(workflowKey, partitionId, PendingWorkflows.STATE_CREATED, deploymentKey);
            }

            if (isDeploymentDistributed(deploymentKey))
            {
                final PendingDeployment pendingDeployment = pendingDeployments.get(deploymentKey);
                writer.writeDeploymentEvent(pendingDeployment.getDeploymentEventPosition(), DeploymentState.DISTRIBUTED);
            }
        }
        finally
        {
            request.close();
        }
    }

    private boolean isDeploymentDistributed(long deploymentKey)
    {
        final PendingWorkflowIterator iterator = pendingWorkflows.iterator();
        while (iterator.hasNext())
        {
            final PendingWorkflow pendingWorkflow = iterator.next();

            if (pendingWorkflow.getDeploymentKey() == deploymentKey &&
                    pendingWorkflow.getState() != PendingWorkflows.STATE_CREATED)
            {
                return false;
            }
        }

        return true;
    }

    private boolean sendMessage(final BufferWriter message, final SocketAddress addr)
    {
        final RemoteAddress remoteAddress = managementClient.registerRemoteAddress(addr);

        transportMessage.remoteAddress(remoteAddress).writer(message);

        return output.sendMessage(transportMessage);
    }
}

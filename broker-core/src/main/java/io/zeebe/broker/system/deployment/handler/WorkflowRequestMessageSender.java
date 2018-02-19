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

import io.zeebe.broker.Loggers;
import io.zeebe.broker.clustering.management.PartitionManager;
import io.zeebe.broker.clustering.member.Member;
import io.zeebe.broker.system.deployment.message.CreateWorkflowRequest;
import io.zeebe.broker.system.deployment.message.DeleteWorkflowMessage;
import io.zeebe.broker.workflow.data.WorkflowEvent;
import io.zeebe.transport.*;
import io.zeebe.util.buffer.BufferWriter;
import io.zeebe.util.collection.IntIterator;
import io.zeebe.util.sched.future.ActorFuture;
import org.agrona.collections.IntArrayList;
import org.slf4j.Logger;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Iterator;
import java.util.Queue;
import java.util.function.IntConsumer;

public class WorkflowRequestMessageSender
{
    private static final Logger LOG = Loggers.SYSTEM_LOGGER;

    private final CreateWorkflowRequest createRequest = new CreateWorkflowRequest();

    private final DeleteWorkflowMessage deleteMessage = new DeleteWorkflowMessage();

    private final TransportMessage transportMessage = new TransportMessage();

    private final Queue<ActorFuture<ClientRequest>> pendingRequests = new ArrayDeque<>();

    private final PartitionManager partitionManager;
    private final ClientTransport managementClient;
    private final ClientOutput output;

    public WorkflowRequestMessageSender(PartitionManager partitionManager, ClientTransport managementClient)
    {
        this.partitionManager = partitionManager;
        this.managementClient = managementClient;
        this.output = managementClient.getOutput();
    }

    public boolean sendCreateWorkflowRequest(
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

            sendRequest(createRequest, addr);
            return true;
        });
    }

    public boolean sendDeleteWorkflowMessage(
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

    private boolean forEachPartition(IntArrayList partitionIds, IntConsumer partitionIdConsumer, BooleanConsumer<SocketAddress> action)
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

                    success = action.apply(member.getManagementAddress());
                }
            }
        }

        return true;
    }

    private void sendRequest(final BufferWriter request, final SocketAddress addr)
    {
        final RemoteAddress remoteAddress = managementClient.registerRemoteAddress(addr);

        final ActorFuture<ClientRequest> clientRequestActorFuture = output.sendRequestWithRetry(remoteAddress, request);
        pendingRequests.add(clientRequestActorFuture);
    }

    private boolean sendMessage(final BufferWriter message, final SocketAddress addr)
    {
        final RemoteAddress remoteAddress = managementClient.registerRemoteAddress(addr);

        transportMessage.remoteAddress(remoteAddress).writer(message);

        return output.sendMessage(transportMessage);
    }

    public Collection<ActorFuture<ClientRequest>> getPendingRequests()
    {
        return pendingRequests;
    }

    @FunctionalInterface
    private interface BooleanConsumer<T>
    {
        boolean apply(T value);
    }

}

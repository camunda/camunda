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

import java.util.*;

import io.zeebe.broker.clustering.management.PartitionManager;
import io.zeebe.broker.clustering.member.Member;
import io.zeebe.broker.system.deployment.message.CreateWorkflowRequest;
import io.zeebe.transport.*;
import io.zeebe.util.collection.IntIterator;
import org.agrona.DirectBuffer;
import org.agrona.collections.IntArrayList;

public class CreateWorkflowRequestSender
{
    private final CreateWorkflowRequest request = new CreateWorkflowRequest();

    private final Queue<ClientRequest> pendingRequests = new ArrayDeque<>();

    private final PartitionManager partitionManager;
    private final ClientTransport managementClient;
    private final ClientOutput output;

    public CreateWorkflowRequestSender(PartitionManager partitionManager, ClientTransport managementClient)
    {
        this.partitionManager = partitionManager;
        this.managementClient = managementClient;
        this.output = managementClient.getOutput();
    }

    public boolean sendCreateWorkflowRequest(
            IntArrayList partitionIds,
            long workflowKey,
            DirectBuffer bpmnProcessId,
            int version,
            DirectBuffer bpmnXml,
            long deploymentKey)
    {
        boolean success = true;

        request
            .workflowKey(workflowKey)
            .deploymentKey(deploymentKey)
            .version(version)
            .bpmnProcessId(bpmnProcessId)
            .bpmnXml(bpmnXml);

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
                    request.partitionId(partitionId);

                    success = sendRequest(request, member);
                }
            }
        }

        return success;
    }

    private boolean sendRequest(final CreateWorkflowRequest request, final Member member)
    {
        final SocketAddress addr = member.getManagementAddress();

        final RemoteAddress remoteAddress = managementClient.registerRemoteAddress(addr);

        final ClientRequest clientRequest = output.sendRequest(remoteAddress, request);

        if (clientRequest != null)
        {
            pendingRequests.add(clientRequest);

            return true;
        }
        else
        {
            return false;
        }
    }

    public Collection<ClientRequest> getPendingRequests()
    {
        return pendingRequests;
    }

}

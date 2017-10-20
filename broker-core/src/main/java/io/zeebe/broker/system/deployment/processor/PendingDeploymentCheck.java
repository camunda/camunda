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
package io.zeebe.broker.system.deployment.processor;

import java.util.Iterator;
import java.util.function.Consumer;

import io.zeebe.broker.logstreams.processor.*;
import io.zeebe.broker.system.deployment.data.PendingDeployments;
import io.zeebe.broker.system.deployment.data.PendingWorkflows;
import io.zeebe.broker.system.deployment.data.PendingDeployments.PendingDeployment;
import io.zeebe.broker.system.deployment.data.PendingDeployments.PendingDeploymentIterator;
import io.zeebe.broker.system.deployment.data.PendingWorkflows.PendingWorkflow;
import io.zeebe.broker.system.deployment.data.PendingWorkflows.PendingWorkflowIterator;
import io.zeebe.broker.system.deployment.handler.CreateWorkflowRequestSender;
import io.zeebe.broker.system.deployment.message.CreateWorkflowResponse;
import io.zeebe.broker.workflow.data.DeploymentEvent;
import io.zeebe.broker.workflow.data.DeploymentState;
import io.zeebe.protocol.impl.BrokerEventMetadata;
import io.zeebe.transport.ClientRequest;
import org.agrona.DirectBuffer;
import org.agrona.collections.LongArrayList;

public class PendingDeploymentCheck implements Runnable
{
    private final PendingDeployments pendingDeployments;
    private final PendingWorkflows pendingWorkflows;

    private final CreateWorkflowRequestSender workflowRequestSender;

    private final TypedStreamWriter writer;
    private final TypedStreamReader reader;

    private final CreateWorkflowResponse response = new CreateWorkflowResponse();

    private final LongArrayList pendingDeploymentKeys = new LongArrayList();
    private final LongArrayList distributedDeploymentKeys = new LongArrayList();

    public PendingDeploymentCheck(
            CreateWorkflowRequestSender workflowRequestSender,
            TypedStreamReader reader,
            TypedStreamWriter writer,
            PendingDeployments pendingDeployments,
            PendingWorkflows pendingWorkflows)
    {
        this.pendingDeployments = pendingDeployments;
        this.pendingWorkflows = pendingWorkflows;
        this.workflowRequestSender = workflowRequestSender;
        this.writer = writer;
        this.reader = reader;
    }

    @Override
    public void run()
    {
        if (!pendingDeployments.isEmpty())
        {
            checkPendingRequests();

            checkPendingWorkflows();
        }
    }

    private void checkPendingRequests()
    {
        // it's okay to work on the pending request because
        // the command runs in the context of the stream processor which creates new requests
        final Iterator<ClientRequest> iterator = workflowRequestSender.getPendingRequests().iterator();
        while (iterator.hasNext())
        {
            final ClientRequest pendingRequest = iterator.next();

            if (pendingRequest.isDone())
            {
                final DirectBuffer responseBuffer = pendingRequest.join();
                response.wrap(responseBuffer, 0, responseBuffer.capacity());

                final long workflowKey = response.getWorkflowKey();
                final int partitionId = response.getPartitionId();
                final long deploymentKey = response.getDeploymentKey();

                pendingWorkflows.put(workflowKey, partitionId, PendingWorkflows.STATE_CREATED, deploymentKey);

                // remove completed request
                iterator.remove();
            }
        }
    }

    private void checkPendingWorkflows()
    {
        pendingDeploymentKeys.clear();
        collectPendingDeployments();

        distributedDeploymentKeys.clear();
        collectDistributedDeployments();

        distributedDeploymentKeys.forEachOrderedLong(this::writeEventForDistributedDeployment);
    }

    private void collectPendingDeployments()
    {
        final PendingDeploymentIterator iterator = pendingDeployments.iterator();
        while (iterator.hasNext())
        {
            final PendingDeployment pendingDeployment = iterator.next();
            final long deploymentKey = pendingDeployment.getDeploymentKey();

            pendingDeploymentKeys.add(deploymentKey);
        }
    }

    private void collectDistributedDeployments()
    {
        final PendingWorkflowIterator iterator = pendingWorkflows.iterator();
        while (iterator.hasNext())
        {
            final PendingWorkflow pendingWorkflow = iterator.next();
            final long deploymentKey = pendingWorkflow.getDeploymentKey();

            if (pendingDeploymentKeys.containsLong(deploymentKey))
            {
                if (pendingWorkflow.getState() == PendingWorkflows.STATE_CREATED && !distributedDeploymentKeys.containsLong(deploymentKey))
                {
                    distributedDeploymentKeys.addLong(deploymentKey);
                }

                if (pendingWorkflow.getState() == PendingWorkflows.STATE_CREATE)
                {
                    pendingDeploymentKeys.removeLong(deploymentKey);
                    distributedDeploymentKeys.removeLong(deploymentKey);
                }
            }
        }
    }

    private void writeEventForDistributedDeployment(final long deploymentKey)
    {
        final PendingDeployment pendingDeployment = pendingDeployments.get(deploymentKey);

        final TypedEvent<DeploymentEvent> event = reader.readValue(pendingDeployment.getDeploymentEventPosition(), DeploymentEvent.class);

        final DeploymentEvent deploymentEvent = event.getValue().setState(DeploymentState.DEPLOYMENT_DISTRIBUTED);

        // if the write operation fails then the next check will try again.
        writer.writeFollowupEvent(event.getKey(), deploymentEvent, addRequestMetadata(event));
    }

    private Consumer<BrokerEventMetadata> addRequestMetadata(TypedEvent<DeploymentEvent> event)
    {
        final BrokerEventMetadata metadata = event.getMetadata();
        return m -> m
                .requestId(metadata.getRequestId())
                .requestStreamId(metadata.getRequestStreamId());
    }

}

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

import static io.zeebe.broker.workflow.data.DeploymentState.CREATED;
import static org.agrona.BitUtil.SIZE_OF_INT;
import static org.agrona.BitUtil.SIZE_OF_LONG;

import org.agrona.ExpandableArrayBuffer;

import io.zeebe.broker.Loggers;
import io.zeebe.broker.logstreams.processor.TypedEvent;
import io.zeebe.broker.logstreams.processor.TypedEventProcessor;
import io.zeebe.broker.logstreams.processor.TypedResponseWriter;
import io.zeebe.broker.logstreams.processor.TypedStreamWriter;
import io.zeebe.broker.system.deployment.data.PendingDeployments;
import io.zeebe.broker.system.deployment.data.PendingDeployments.PendingDeployment;
import io.zeebe.broker.system.deployment.data.PendingWorkflows;
import io.zeebe.broker.system.deployment.data.PendingWorkflows.PendingWorkflow;
import io.zeebe.broker.system.deployment.data.PendingWorkflows.PendingWorkflowIterator;
import io.zeebe.broker.system.deployment.handler.DeploymentTimer;
import io.zeebe.broker.workflow.data.DeploymentEvent;
import io.zeebe.util.buffer.BufferUtil;

public class DeploymentDistributedProcessor implements TypedEventProcessor<DeploymentEvent>
{
    private final ExpandableArrayBuffer buffer = new ExpandableArrayBuffer(3 * (SIZE_OF_LONG + SIZE_OF_INT));

    private final PendingDeployments pendingDeployments;
    private final PendingWorkflows pendingWorkflows;
    private final DeploymentTimer timer;

    public DeploymentDistributedProcessor(PendingDeployments pendingDeployments, PendingWorkflows pendingWorkflows, DeploymentTimer timer)
    {
        this.pendingDeployments = pendingDeployments;
        this.pendingWorkflows = pendingWorkflows;
        this.timer = timer;
    }

    @Override
    public void processEvent(TypedEvent<DeploymentEvent> event)
    {
        final PendingDeployment pendingDeployment = pendingDeployments.get(event.getKey());

        if (pendingDeployment != null && !pendingDeployment.isResolved())
        {
            event.getValue().setState(CREATED);

            Loggers.SYSTEM_LOGGER.debug("Deployment with key '{}' on topic '{}' successful.",
                                        pendingDeployment.getDeploymentKey(),
                                        BufferUtil.bufferAsString(pendingDeployment.getTopicName()));
        }

        if (!event.getMetadata().hasRequestMetadata())
        {
            throw new RuntimeException("missing request metadata of deployment");
        }
    }

    @Override
    public boolean executeSideEffects(TypedEvent<DeploymentEvent> event, TypedResponseWriter responseWriter)
    {
        if (event.getValue().getState() == CREATED)
        {
            return responseWriter.write(event);
        }
        else
        {
            return true;
        }
    }

    @Override
    public long writeEvent(TypedEvent<DeploymentEvent> event, TypedStreamWriter writer)
    {
        if (event.getValue().getState() == CREATED)
        {
            return writer.writeFollowupEvent(event.getKey(), event.getValue());
        }
        else
        {
            return 0L;
        }
    }

    @Override
    public void updateState(TypedEvent<DeploymentEvent> event)
    {
        final DeploymentEvent deploymentEvent = event.getValue();
        final long deploymentKey = event.getKey();

        if (deploymentEvent.getState() == CREATED)
        {
            pendingDeployments.remove(deploymentKey);
            timer.onDeploymentResolved(deploymentKey);

            removePendingWorkflowsOfDeployment(deploymentKey);
        }
    }

    private void removePendingWorkflowsOfDeployment(long deploymentKey)
    {
        int offset = 0;

        // cannot delete entries while iterating over it
        final PendingWorkflowIterator iterator = pendingWorkflows.iterator();
        while (iterator.hasNext())
        {
            final PendingWorkflow pendingWorkflow = iterator.next();

            if (deploymentKey == pendingWorkflow.getDeploymentKey())
            {
                buffer.putLong(offset, pendingWorkflow.getWorkflowKey());
                offset += SIZE_OF_LONG;

                buffer.putInt(offset, pendingWorkflow.getPartitionId());
                offset += SIZE_OF_INT;
            }
        }

        final int limit = offset;
        offset = 0;

        while (offset < limit)
        {
            final long workflowKey = buffer.getLong(offset);
            offset += SIZE_OF_LONG;

            final int partitionId = buffer.getInt(offset);
            offset += SIZE_OF_INT;

            pendingWorkflows.remove(workflowKey, partitionId);
        }
    }
}

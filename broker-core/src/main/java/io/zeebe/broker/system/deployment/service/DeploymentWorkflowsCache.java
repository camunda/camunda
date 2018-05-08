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

import java.util.Iterator;

import org.agrona.collections.Long2ObjectHashMap;

import io.zeebe.broker.system.deployment.data.DeploymentPositionByWorkflowKey;
import io.zeebe.broker.workflow.data.DeployedWorkflow;
import io.zeebe.broker.workflow.data.DeploymentRecord;
import io.zeebe.logstreams.log.BufferedLogStreamReader;
import io.zeebe.logstreams.log.LoggedEvent;

/**
 * Caches workflow information for answering requests
 */
public class DeploymentWorkflowsCache
{
    private Long2ObjectHashMap<DeploymentCachedWorkflow> cachedWorkflowsByKey = new Long2ObjectHashMap<>();

    private final DeploymentPositionByWorkflowKey deploymentPositionByWorkflowKey;

    private final BufferedLogStreamReader reader;

    private final DeploymentRecord deploymentEvent = new DeploymentRecord();

    public DeploymentWorkflowsCache(final BufferedLogStreamReader reader,
        DeploymentPositionByWorkflowKey deploymentPositionByWorkflowKey)
    {
        this.reader = reader;
        this.deploymentPositionByWorkflowKey = deploymentPositionByWorkflowKey;
    }

    public DeploymentCachedWorkflow getWorkflow(long key)
    {
        DeploymentCachedWorkflow cachedWorkflow = cachedWorkflowsByKey.get(key);

        if (cachedWorkflow == null)
        {
            cachedWorkflow = resolveWorkflow(key);
        }

        return cachedWorkflow;
    }

    private DeploymentCachedWorkflow resolveWorkflow(long key)
    {
        final long position = deploymentPositionByWorkflowKey.get(key, -1);

        if (position != -1)
        {
            if (reader.seek(position))
            {
                final LoggedEvent event = reader.next();

                event.readValue(deploymentEvent);

                final Iterator<DeployedWorkflow> deployedWorkflowsIterator = deploymentEvent.deployedWorkflows().iterator();

                while (deployedWorkflowsIterator.hasNext())
                {
                    final DeployedWorkflow deployedWorkflow = deployedWorkflowsIterator.next();

                    if (deployedWorkflow.getKey() == key)
                    {
                        return new DeploymentCachedWorkflow()
                            .setVersion(deployedWorkflow.getVersion())
                            .setWorkflowKey(key)
                            .setDeploymentKey(event.getKey())
                            .putBpmnProcessId(deployedWorkflow.getBpmnProcessId())
                            .putBpmnXml(deploymentEvent.resources().iterator().next().getResource());
                    }
                }
            }
        }

        return null;
    }
}

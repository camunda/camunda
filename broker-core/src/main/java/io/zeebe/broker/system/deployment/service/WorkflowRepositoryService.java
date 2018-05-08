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

import io.zeebe.broker.system.deployment.data.LatestVersionByProcessIdAndTopicName;
import io.zeebe.broker.system.deployment.data.WorkflowKeyByProcessIdAndVersion;
import io.zeebe.servicecontainer.Service;
import io.zeebe.util.sched.ActorControl;
import io.zeebe.util.sched.future.ActorFuture;
import org.agrona.DirectBuffer;

public class WorkflowRepositoryService implements Service<WorkflowRepositoryService>
{
    private ActorControl actor;
    private WorkflowKeyByProcessIdAndVersion workflowKeyByProcessIdAndVersion;
    private LatestVersionByProcessIdAndTopicName latestWorkflowKeyByProcessIdAndTopicName;
    private DeploymentWorkflowsCache deploymentWorkflowsCache;

    public WorkflowRepositoryService(ActorControl actor,
            WorkflowKeyByProcessIdAndVersion workflowKeyByProcessIdAndVersion,
            LatestVersionByProcessIdAndTopicName latestWorkflowKeyByProcessIdAndTopicName,
            DeploymentWorkflowsCache deploymentWorkflowsCache)
    {
        this.actor = actor;
        this.workflowKeyByProcessIdAndVersion = workflowKeyByProcessIdAndVersion;
        this.latestWorkflowKeyByProcessIdAndTopicName = latestWorkflowKeyByProcessIdAndTopicName;
        this.deploymentWorkflowsCache = deploymentWorkflowsCache;
    }

    @Override
    public WorkflowRepositoryService get()
    {
        return this;
    }

    public ActorFuture<DeploymentCachedWorkflow> getWorkflowByKey(long key)
    {
        return actor.call(() -> deploymentWorkflowsCache.getWorkflow(key));
    }

    public ActorFuture<DeploymentCachedWorkflow> getLatestWorkflowByBpmnProcessId(DirectBuffer topicName, DirectBuffer bpmnProcessId)
    {
        return actor.call(() ->
        {
            final int version = latestWorkflowKeyByProcessIdAndTopicName.getLatestVersion(topicName, bpmnProcessId, -1);

            if (version != -1)
            {
                final long workflowKey = workflowKeyByProcessIdAndVersion.get(bpmnProcessId, version, -1);

                if (workflowKey != -1)
                {
                    return deploymentWorkflowsCache.getWorkflow(workflowKey);
                }
            }

            return null;
        });
    }

    public ActorFuture<DeploymentCachedWorkflow> getWorkflowByBpmnProcessIdAndVersion(DirectBuffer topicName, DirectBuffer bpmnProcessId, int version)
    {
        return actor.call(() ->
        {
            final long workflowKey = workflowKeyByProcessIdAndVersion.get(bpmnProcessId, version, -1);

            if (workflowKey != -1)
            {
                return deploymentWorkflowsCache.getWorkflow(workflowKey);
            }

            return null;
        });
    }

}

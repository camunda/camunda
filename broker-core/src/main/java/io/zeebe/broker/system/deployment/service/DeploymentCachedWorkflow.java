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

import io.zeebe.util.buffer.BufferUtil;
import org.agrona.DirectBuffer;

/**
 * Cached data about a workflow. Maintained by {@link DeploymentWorkflowsCache}.
 */
public class DeploymentCachedWorkflow
{
    private long workflowKey;
    private int version;
    private long deploymentKey;
    private DirectBuffer bpmnProcessId;
    private DirectBuffer bpmnXml;

    public long getWorkflowKey()
    {
        return workflowKey;
    }

    public DeploymentCachedWorkflow setWorkflowKey(long workflowKey)
    {
        this.workflowKey = workflowKey;
        return this;
    }

    public int getVersion()
    {
        return version;
    }

    public DeploymentCachedWorkflow setVersion(int version)
    {
        this.version = version;
        return this;
    }

    public long getDeploymentKey()
    {
        return deploymentKey;
    }

    public DeploymentCachedWorkflow setDeploymentKey(long deploymentKey)
    {
        this.deploymentKey = deploymentKey;
        return this;
    }

    public DirectBuffer getBpmnProcessId()
    {
        return bpmnProcessId;
    }

    public DirectBuffer getBpmnXml()
    {
        return bpmnXml;
    }

    public DeploymentCachedWorkflow putBpmnProcessId(DirectBuffer src)
    {
        this.bpmnProcessId = BufferUtil.cloneBuffer(src);
        return this;
    }

    public DeploymentCachedWorkflow putBpmnXml(DirectBuffer src)
    {
        this.bpmnXml = BufferUtil.cloneBuffer(src);
        return this;
    }
}

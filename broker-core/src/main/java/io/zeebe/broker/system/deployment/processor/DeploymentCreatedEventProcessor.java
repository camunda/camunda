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

import io.zeebe.broker.logstreams.processor.TypedRecord;
import io.zeebe.broker.logstreams.processor.TypedRecordProcessor;
import io.zeebe.broker.logstreams.processor.TypedResponseWriter;
import io.zeebe.broker.system.deployment.data.DeploymentPositionByWorkflowKey;
import io.zeebe.broker.system.deployment.data.WorkflowKeyByProcessIdAndVersion;
import io.zeebe.broker.workflow.data.DeployedWorkflow;
import io.zeebe.broker.workflow.data.DeploymentRecord;
import io.zeebe.msgpack.value.ValueArray;
import io.zeebe.protocol.intent.DeploymentIntent;

public class DeploymentCreatedEventProcessor implements TypedRecordProcessor<DeploymentRecord>
{
    private final DeploymentPositionByWorkflowKey deploymentPositionByWorkflowKey;
    private final WorkflowKeyByProcessIdAndVersion workflowKeyByProcessIdAndVersion;

    public DeploymentCreatedEventProcessor(DeploymentPositionByWorkflowKey deploymentPositionByWorkflowKey,
        WorkflowKeyByProcessIdAndVersion workflowKeyByProcessIdAndVersion)
    {
        this.deploymentPositionByWorkflowKey = deploymentPositionByWorkflowKey;
        this.workflowKeyByProcessIdAndVersion = workflowKeyByProcessIdAndVersion;
    }

    @Override
    public boolean executeSideEffects(TypedRecord<DeploymentRecord> event, TypedResponseWriter responseWriter)
    {
        return responseWriter.writeEvent(DeploymentIntent.CREATED, event);
    }

    @Override
    public void updateState(TypedRecord<DeploymentRecord> event)
    {
        final DeploymentRecord deploymentEvent = event.getValue();

        final ValueArray<DeployedWorkflow> deployedWorkflows = deploymentEvent.deployedWorkflows();

        final Iterator<DeployedWorkflow> iterator = deployedWorkflows.iterator();

        while (iterator.hasNext())
        {
            final DeployedWorkflow deployedWorkflow = iterator.next();
            deploymentPositionByWorkflowKey.set(deployedWorkflow.getKey(), event.getPosition());
            workflowKeyByProcessIdAndVersion.set(deployedWorkflow.getBpmnProcessId(), deployedWorkflow.getVersion(), deployedWorkflow.getKey());
        }
    }
}

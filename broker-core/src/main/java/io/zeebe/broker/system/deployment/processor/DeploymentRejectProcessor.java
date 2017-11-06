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

import static io.zeebe.broker.workflow.data.DeploymentState.DEPLOYMENT_REJECTED;

import io.zeebe.broker.logstreams.processor.*;
import io.zeebe.broker.system.deployment.data.PendingDeployments;
import io.zeebe.broker.workflow.data.DeploymentEvent;

public class DeploymentRejectProcessor implements TypedEventProcessor<DeploymentEvent>
{
    private final PendingDeployments pendingDeployments;

    public DeploymentRejectProcessor(PendingDeployments pendingDeployments)
    {
        this.pendingDeployments = pendingDeployments;
    }

    @Override
    public void processEvent(TypedEvent<DeploymentEvent> event)
    {
        event.getValue().setState(DEPLOYMENT_REJECTED);

        if (!event.getMetadata().hasRequestMetadata())
        {
            throw new RuntimeException("missing request metadata of deployment");
        }
    }

    @Override
    public boolean executeSideEffects(TypedEvent<DeploymentEvent> event, TypedResponseWriter responseWriter)
    {
        return responseWriter.write(event);
    }

    @Override
    public long writeEvent(TypedEvent<DeploymentEvent> event, TypedStreamWriter writer)
    {
        return writer.writeFollowupEvent(event.getKey(), event.getValue());
    }

    @Override
    public void updateState(TypedEvent<DeploymentEvent> event)
    {
        pendingDeployments.remove(event.getKey());
    }
}

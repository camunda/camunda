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

import static io.zeebe.util.buffer.BufferUtil.bufferAsString;

import org.agrona.DirectBuffer;
import org.slf4j.Logger;

import io.zeebe.broker.Loggers;
import io.zeebe.broker.logstreams.processor.TypedEvent;
import io.zeebe.broker.logstreams.processor.TypedEventProcessor;
import io.zeebe.broker.system.deployment.data.PendingDeployments;
import io.zeebe.broker.system.deployment.handler.DeploymentTimer;
import io.zeebe.broker.workflow.data.DeploymentEvent;

public class DeploymentValidatedProcessor implements TypedEventProcessor<DeploymentEvent>
{
    private static final Logger LOG = Loggers.SYSTEM_LOGGER;

    private final PendingDeployments pendingDeployments;
    private final DeploymentTimer timer;

    public DeploymentValidatedProcessor(PendingDeployments pendingDeployments, DeploymentTimer timer)
    {
        this.pendingDeployments = pendingDeployments;
        this.timer = timer;
    }

    @Override
    public void processEvent(TypedEvent<DeploymentEvent> event)
    {
        // just add the event position to the index

        LOG.debug("Create deployment with key '{}' on topic '{}'.", event.getKey(), bufferAsString(event.getValue().getTopicName()));
    }

    @Override
    public void updateState(TypedEvent<DeploymentEvent> event)
    {
        final long deploymentKey = event.getKey();
        final DirectBuffer topicName = event.getValue().getTopicName();

        pendingDeployments.put(deploymentKey, event.getPosition(), topicName);
        timer.onDeploymentValidated(deploymentKey);
    }

}

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

import java.util.function.Consumer;

import io.zeebe.broker.logstreams.processor.StreamProcessorLifecycleAware;
import io.zeebe.broker.logstreams.processor.TypedEvent;
import io.zeebe.broker.logstreams.processor.TypedStreamProcessor;
import io.zeebe.broker.logstreams.processor.TypedStreamReader;
import io.zeebe.broker.logstreams.processor.TypedStreamWriter;
import io.zeebe.broker.workflow.data.DeploymentEvent;
import io.zeebe.broker.workflow.data.DeploymentState;
import io.zeebe.protocol.impl.BrokerEventMetadata;
import io.zeebe.util.sched.ActorControl;

public class DeploymentEventWriter implements StreamProcessorLifecycleAware
{

    private final TypedStreamWriter writer;
    private final TypedStreamReader reader;

    private ActorControl actor;

    public DeploymentEventWriter(
            TypedStreamWriter writer,
            TypedStreamReader reader)
    {
        this.writer = writer;
        this.reader = reader;
    }

    @Override
    public void onOpen(TypedStreamProcessor streamProcessor)
    {
        this.actor = streamProcessor.getActor();
    }

    @Override
    public void onClose()
    {
        this.reader.close();
    }

    /**
     * Writes a follow-up event copying all properties of the source event and updating the state.
     */
    public void writeDeploymentEvent(final long sourceEventPosition, DeploymentState newState)
    {
        final TypedEvent<DeploymentEvent> event = reader.readValue(sourceEventPosition, DeploymentEvent.class);

        final DeploymentEvent deploymentEvent = event.getValue().setState(newState);

        actor.runUntilDone(() ->
        {
            final long position = writer.writeFollowupEvent(event.getKey(), deploymentEvent, copyRequestMetadata(event));
            if (position >= 0)
            {
                actor.done();
            }
            else
            {
                actor.yield();
            }
        });
    }

    private Consumer<BrokerEventMetadata> copyRequestMetadata(TypedEvent<DeploymentEvent> event)
    {
        final BrokerEventMetadata metadata = event.getMetadata();
        return m -> m
                .requestId(metadata.getRequestId())
                .requestStreamId(metadata.getRequestStreamId());
    }
}

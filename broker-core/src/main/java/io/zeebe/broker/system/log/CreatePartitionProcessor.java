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
package io.zeebe.broker.system.log;

import java.time.Duration;

import io.zeebe.broker.Loggers;
import io.zeebe.broker.clustering.api.CreatePartitionRequest;
import io.zeebe.broker.clustering.base.topology.TopologyDto.BrokerDto;
import io.zeebe.broker.logstreams.processor.*;
import io.zeebe.transport.*;
import io.zeebe.util.buffer.BufferUtil;
import io.zeebe.util.sched.ActorControl;
import io.zeebe.util.sched.clock.ActorClock;
import io.zeebe.util.sched.future.ActorFuture;
import org.agrona.DirectBuffer;

public class CreatePartitionProcessor implements TypedEventProcessor<PartitionEvent>
{
    private final ClientTransport clientTransport;
    protected final PendingPartitionsIndex partitions;
    protected final long creationTimeoutMillis;
    protected final SocketAddress creatorAddress = new SocketAddress();
    private ActorControl actor;

    public CreatePartitionProcessor(
            ClientTransport clientTransport,
            PendingPartitionsIndex partitions,
            Duration creationTimeout)
    {
        this.clientTransport = clientTransport;
        this.partitions = partitions;
        this.creationTimeoutMillis = creationTimeout.toMillis();
    }

    @Override
    public void onOpen(TypedStreamProcessor streamProcessor)
    {
        this.actor = streamProcessor.getActor();
    }

    @Override
    public void processEvent(TypedEvent<PartitionEvent> event)
    {
        final PartitionEvent value = event.getValue();
        value.setState(PartitionState.CREATING);

        final long now = ActorClock.currentTimeMillis();
        value.setCreationTimeout(now + creationTimeoutMillis);
    }

    @Override
    public boolean executeSideEffects(TypedEvent<PartitionEvent> event, TypedResponseWriter responseWriter)
    {
        final PartitionEvent value = event.getValue();
        final BrokerDto creator = value.getCreator();
        final DirectBuffer creatorHost = creator.getHost();

        creatorAddress.host(creatorHost, 0, creatorHost.capacity());
        creatorAddress.port(creator.getPort());

        final DirectBuffer topicNameBuffer = BufferUtil.cloneBuffer(value.getTopicName());
        final int partitionId = value.getPartitionId();
        final int replicationFactor = value.getReplicationFactor();

        final RemoteAddress remoteAddress = clientTransport.registerRemoteAddress(creatorAddress);

        final CreatePartitionRequest messageWriter = new CreatePartitionRequest();

        messageWriter
            .partitionId(partitionId)
            .replicationFactor(replicationFactor)
            .topicName(topicNameBuffer);

        final ActorFuture<ClientResponse> response = clientTransport.getOutput().sendRequest(remoteAddress, messageWriter, Duration.ofSeconds(5));

        actor.runOnCompletion(response, ((clientRequest, throwable) ->
        {
            if (throwable == null)
            {
                clientRequest.close();
            }
            else
            {
                Loggers.SYSTEM_LOGGER.error("Failed to create partitions request.", throwable);
            }
        }));

        return true;
    }

    @Override
    public long writeEvent(TypedEvent<PartitionEvent> event, TypedStreamWriter writer)
    {
        return writer.writeFollowupEvent(event.getKey(), event.getValue());
    }

    @Override
    public void updateState(TypedEvent<PartitionEvent> event)
    {
        final PartitionEvent value = event.getValue();

        // adds the position of the CREATE event to the index. This is not the latest event
        // (which is CREATING), but this should be ok since these events are the same apart from the state.
        partitions.putPartition(
                value.getPartitionId(),
                event.getPosition(),
                value.getCreationTimeout());
    }

}

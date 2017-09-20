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

import io.zeebe.broker.clustering.management.PartitionManager;
import io.zeebe.broker.logstreams.processor.TypedEvent;
import io.zeebe.broker.logstreams.processor.TypedEventProcessor;
import io.zeebe.broker.logstreams.processor.TypedResponseWriter;
import io.zeebe.broker.logstreams.processor.TypedStreamWriter;
import io.zeebe.transport.SocketAddress;

public class CreatePartitionProcessor implements TypedEventProcessor<PartitionEvent>
{

    protected final PartitionCreatorSelectionStrategy creatorStrategy;
    protected final PartitionManager partitionManager;
    protected final PartitionsIndex partitions;

    public CreatePartitionProcessor(
            PartitionManager partitionManager,
            PartitionsIndex partitions)
    {
        this.partitionManager = partitionManager;
        this.partitions = partitions;
        this.creatorStrategy = new RoundRobinSelectionStrategy(partitionManager);
    }

    @Override
    public void processEvent(TypedEvent<PartitionEvent> event)
    {
        event.getValue().setState(PartitionState.CREATING);
    }

    @Override
    public boolean executeSideEffects(TypedEvent<PartitionEvent> event, TypedResponseWriter responseWriter)
    {
        final PartitionEvent value = event.getValue();
        final SocketAddress nextBroker = creatorStrategy.selectBrokerForNewPartition();

        if (nextBroker == null)
        {
            return false;
        }
        else
        {
            return partitionManager.createPartitionRemote(nextBroker, value.getTopicName(), value.getId());
        }
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

        partitions.putPartitionKey(
                value.getTopicName(),
                value.getId(),
                event.getKey());
    }

}

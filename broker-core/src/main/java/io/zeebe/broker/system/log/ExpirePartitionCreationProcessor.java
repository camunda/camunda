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

import io.zeebe.broker.logstreams.processor.TypedBatchWriter;
import io.zeebe.broker.logstreams.processor.TypedEvent;
import io.zeebe.broker.logstreams.processor.TypedEventProcessor;
import io.zeebe.broker.logstreams.processor.TypedResponseWriter;
import io.zeebe.broker.logstreams.processor.TypedStreamWriter;
import io.zeebe.broker.system.log.PendingPartitionsIndex.PendingPartition;
import io.zeebe.transport.SocketAddress;

public class ExpirePartitionCreationProcessor implements TypedEventProcessor<PartitionEvent>
{
    protected final PendingPartitionsIndex partitions;
    protected final PartitionIdGenerator idGenerator;
    protected final PartitionCreatorSelectionStrategy creatorStrategy;

    protected final PartitionEvent newEvent = new PartitionEvent();

    public ExpirePartitionCreationProcessor(PendingPartitionsIndex partitions, PartitionIdGenerator idGenerator, PartitionCreatorSelectionStrategy creatorStrategy)
    {
        this.partitions = partitions;
        this.idGenerator = idGenerator;
        this.creatorStrategy = creatorStrategy;
    }

    @Override
    public void processEvent(TypedEvent<PartitionEvent> event)
    {
        final PartitionEvent value = event.getValue();

        final PendingPartition partition = partitions.get(value.getPartitionId());

        if (partition != null)
        {
            value.setState(PartitionState.CREATE_EXPIRED);
        }
        else
        {
            value.setState(PartitionState.CREATE_EXPIRE_REJECTED);
        }
    }

    @Override
    public boolean executeSideEffects(TypedEvent<PartitionEvent> event, TypedResponseWriter responseWriter)
    {
        return true;
    }

    @Override
    public long writeEvent(TypedEvent<PartitionEvent> event, TypedStreamWriter writer)
    {
        final PartitionEvent value = event.getValue();
        final TypedBatchWriter batchWriter = writer.newBatch()
            .addFollowUpEvent(event.getKey(), value);

        if (value.getState() == PartitionState.CREATE_EXPIRED)
        {
            // create a new partition
            final SocketAddress nextCreator = creatorStrategy.selectBrokerForNewPartition();
            if (nextCreator == null)
            {
                return -1;
            }

            newEvent.reset();
            newEvent.setState(PartitionState.CREATE);
            newEvent.setTopicName(value.getTopicName());
            newEvent.setParitionId(idGenerator.currentId());
            newEvent.setReplicationFactor(value.getReplicationFactor());
            newEvent.setCreator(nextCreator.getHostBuffer(), nextCreator.port());

            batchWriter.addNewEvent(newEvent);
        }

        return batchWriter.write();
    }

    @Override
    public void updateState(TypedEvent<PartitionEvent> event)
    {
        final PartitionEvent value = event.getValue();
        // removing the partition from the index to avoid expiration being triggered multiple times
        // => should the partition become available after this point, then the system partition will
        //    not recognize this and will not write a partition CREATED event. If this is required in the future,
        //    we can include the partition state in the index.
        partitions.removePartitionKey(value.getPartitionId());

        idGenerator.moveToNextId();

    }

}

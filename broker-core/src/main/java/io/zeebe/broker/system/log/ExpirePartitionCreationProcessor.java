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
import scala.util.Random;

public class ExpirePartitionCreationProcessor implements TypedEventProcessor<PartitionEvent>
{

    protected final PendingPartitionsIndex partitions;
    protected final PartitionEvent newEvent = new PartitionEvent();
    protected final Random idGenerator = new Random();

    public ExpirePartitionCreationProcessor(PendingPartitionsIndex partitions)
    {
        this.partitions = partitions;
    }

    @Override
    public void processEvent(TypedEvent<PartitionEvent> event)
    {
        final PartitionEvent value = event.getValue();

        final PendingPartition partition = partitions.get(value.getTopicName(), value.getId());

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
            newEvent.reset();
            newEvent.setState(PartitionState.CREATE);

            // TODO: random does not guarantee uniqueness => goes away with https://github.com/zeebe-io/zeebe/issues/414
            newEvent.setId(idGenerator.nextInt() & 0xFF);
            newEvent.setTopicName(value.getTopicName());
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
        partitions.removePartitionKey(value.getTopicName(), value.getId());

    }

}

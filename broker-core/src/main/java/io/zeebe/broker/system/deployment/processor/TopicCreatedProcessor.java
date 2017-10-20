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

import io.zeebe.broker.logstreams.processor.TypedEvent;
import io.zeebe.broker.logstreams.processor.TypedEventProcessor;
import io.zeebe.broker.system.deployment.data.TopicPartitions;
import io.zeebe.broker.system.deployment.data.TopicPartitions.TopicPartition;
import io.zeebe.broker.system.deployment.data.TopicPartitions.TopicPartitionIterator;
import io.zeebe.broker.system.log.TopicEvent;
import io.zeebe.util.buffer.BufferUtil;
import org.agrona.DirectBuffer;
import org.agrona.collections.IntArrayList;

public class TopicCreatedProcessor implements TypedEventProcessor<TopicEvent>
{
    private final TopicPartitions topicPartitions;

    private final IntArrayList partitionIds = new IntArrayList();

    public TopicCreatedProcessor(TopicPartitions topicPartitions)
    {
        this.topicPartitions = topicPartitions;
    }

    @Override
    public void processEvent(TypedEvent<TopicEvent> event)
    {
        partitionIds.clear();

        final DirectBuffer topicName = event.getValue().getName();

        final TopicPartitionIterator iterator = topicPartitions.iterator();
        while (iterator.hasNext())
        {
            final TopicPartition partition = iterator.next();

            if (BufferUtil.equals(topicName, partition.getTopicName()))
            {
                partitionIds.addInt(partition.getPartitionId());
            }
        }
    }

    @Override
    public void updateState(TypedEvent<TopicEvent> event)
    {
        final DirectBuffer topicName = event.getValue().getName();

        for (int partitionId : partitionIds)
        {
            topicPartitions.put(partitionId, topicName, TopicPartitions.STATE_CREATED);
        }
    }

}

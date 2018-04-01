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

import org.agrona.DirectBuffer;
import org.agrona.collections.IntArrayList;

import io.zeebe.broker.logstreams.processor.TypedEvent;
import io.zeebe.broker.logstreams.processor.TypedEventProcessor;
import io.zeebe.broker.logstreams.processor.TypedEventStreamProcessorBuilder;
import io.zeebe.broker.system.deployment.data.TopicPartitions;
import io.zeebe.broker.system.deployment.data.TopicPartitions.TopicPartition;
import io.zeebe.broker.system.deployment.data.TopicPartitions.TopicPartitionIterator;
import io.zeebe.broker.system.log.PartitionEvent;
import io.zeebe.broker.system.log.PartitionState;
import io.zeebe.broker.system.log.TopicEvent;
import io.zeebe.broker.system.log.TopicState;
import io.zeebe.protocol.clientapi.EventType;
import io.zeebe.util.IntObjectBiConsumer;
import io.zeebe.util.buffer.BufferUtil;

public class PartitionCollector
{
    protected final TopicPartitions partitions = new TopicPartitions();
    protected final IntObjectBiConsumer<DirectBuffer> partitionConsumer;

    public PartitionCollector()
    {
        this((p, t) ->
        {
        });
    }

    public PartitionCollector(IntObjectBiConsumer<DirectBuffer> partitionConsumer)
    {
        this.partitionConsumer = partitionConsumer;
    }


    public void registerWith(TypedEventStreamProcessorBuilder builder)
    {
        builder
            .onEvent(EventType.PARTITION_EVENT, PartitionState.CREATED, new PartitionCreatedProcessor())
            .onEvent(EventType.TOPIC_EVENT, TopicState.CREATED, new TopicCreatedProcessor())
            .withStateResource(partitions.getRawMap());
    }

    public TopicPartitions getPartitions()
    {
        return partitions;
    }

    protected class TopicCreatedProcessor implements TypedEventProcessor<TopicEvent>
    {
        private final IntArrayList partitionIds = new IntArrayList();

        @Override
        public void processEvent(TypedEvent<TopicEvent> event)
        {
            partitionIds.clear();

            final DirectBuffer topicName = event.getValue().getName();

            final TopicPartitionIterator iterator = partitions.iterator();
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
                partitions.put(partitionId, topicName, TopicPartitions.STATE_CREATED);
                partitionConsumer.accept(partitionId, topicName);
            }
        }

    }

    protected class PartitionCreatedProcessor implements TypedEventProcessor<PartitionEvent>
    {
        @Override
        public void processEvent(TypedEvent<PartitionEvent> event)
        {
            // just add the created partition to the index
        }

        @Override
        public void updateState(TypedEvent<PartitionEvent> event)
        {
            final PartitionEvent partitionEvent = event.getValue();
            final DirectBuffer topicName = partitionEvent.getTopicName();
            final int partitionId = partitionEvent.getPartitionId();

            partitions.put(partitionId, topicName, TopicPartitions.STATE_CREATING);
        }

    }
}

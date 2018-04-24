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

import io.zeebe.broker.clustering.orchestration.topic.TopicEvent;
import io.zeebe.broker.clustering.orchestration.topic.TopicState;
import io.zeebe.broker.logstreams.processor.TypedEvent;
import io.zeebe.broker.logstreams.processor.TypedEventProcessor;
import io.zeebe.broker.logstreams.processor.TypedEventStreamProcessorBuilder;
import io.zeebe.broker.system.deployment.data.TopicPartitions;
import io.zeebe.protocol.clientapi.EventType;
import io.zeebe.util.IntObjectBiConsumer;
import org.agrona.DirectBuffer;
import org.agrona.collections.IntArrayList;

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
            final TopicEvent topicEvent = event.getValue();

            partitionIds.clear();
            topicEvent.getPartitionIds().forEach(id -> partitionIds.addInt(id.getValue()));
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

}

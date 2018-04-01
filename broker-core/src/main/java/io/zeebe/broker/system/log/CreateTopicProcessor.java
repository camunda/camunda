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

import org.agrona.DirectBuffer;

import io.zeebe.broker.logstreams.processor.TypedBatchWriter;
import io.zeebe.broker.logstreams.processor.TypedEvent;
import io.zeebe.broker.logstreams.processor.TypedEventProcessor;
import io.zeebe.broker.logstreams.processor.TypedResponseWriter;
import io.zeebe.broker.logstreams.processor.TypedStreamProcessor;
import io.zeebe.broker.logstreams.processor.TypedStreamWriter;
import io.zeebe.protocol.Protocol;
import io.zeebe.transport.SocketAddress;

public class CreateTopicProcessor implements TypedEventProcessor<TopicEvent>
{
    protected final TopicsIndex topics;
    protected final PartitionIdGenerator idGenerator;
    protected final PartitionEvent partitionEvent = new PartitionEvent();
    protected final PartitionCreatorSelectionStrategy creatorStrategy;

    public CreateTopicProcessor(
            TopicsIndex topics,
            PartitionIdGenerator idGenerator,
            PartitionCreatorSelectionStrategy creatorStrategy)
    {
        this.topics = topics;
        this.idGenerator = idGenerator;
        this.creatorStrategy = creatorStrategy;
    }

    @Override
    public void onOpen(TypedStreamProcessor streamProcessor)
    {
        this.topics.put(Protocol.SYSTEM_TOPIC_BUF, 0, -1); // ensure that the system topic cannot be created
    }

    @Override
    public void processEvent(TypedEvent<TopicEvent> event)
    {
        final TopicEvent value = event.getValue();

        final DirectBuffer nameBuffer = value.getName();
        final boolean topicExists = topics.moveTo(nameBuffer);

        if (topicExists || value.getPartitions() <= 0)
        {
            value.setState(TopicState.CREATE_REJECTED);
        }
    }

    @Override
    public boolean executeSideEffects(TypedEvent<TopicEvent> event, TypedResponseWriter responseWriter)
    {
        if (event.getValue().getState() == TopicState.CREATE_REJECTED)
        {
            return responseWriter.write(event);
        }
        else
        {
            return true;
        }
    }

    @Override
    public long writeEvent(TypedEvent<TopicEvent> event, TypedStreamWriter writer)
    {
        final TopicEvent value = event.getValue();
        if (value.getState() == TopicState.CREATE_REJECTED)
        {
            return writer.writeFollowupEvent(event.getKey(), event.getValue());
        }
        else
        {
            final TypedBatchWriter batchWriter = writer.newBatch();

            for (int i = 0; i < value.getPartitions(); i++)
            {
                // in contrast to choosing the partition ID, choosing the creator
                // does not have to be deterministic (e.g. when this method is invoked multiple times due to backpressure),
                // so it is ok to choose the creator here and not in #processEvent
                final SocketAddress nextCreator = creatorStrategy.selectBrokerForNewPartition();
                if (nextCreator == null)
                {
                    return -1;
                }

                partitionEvent.reset();
                partitionEvent.setState(PartitionState.CREATE);
                partitionEvent.setTopicName(value.getName());
                partitionEvent.setParitionId(idGenerator.currentId(i));
                partitionEvent.setReplicationFactor(value.getReplicationFactor());
                partitionEvent.setCreator(nextCreator.getHostBuffer(), nextCreator.port());

                batchWriter.addNewEvent(partitionEvent);
            }

            return batchWriter.write();
        }
    }

    @Override
    public void updateState(TypedEvent<TopicEvent> event)
    {
        final TopicEvent value = event.getValue();
        if (value.getState() != TopicState.CREATE_REJECTED)
        {
            topics.put(value.getName(), value.getPartitions(), event.getPosition());
            idGenerator.moveToNextIds(value.getPartitions());
        }
    }
}

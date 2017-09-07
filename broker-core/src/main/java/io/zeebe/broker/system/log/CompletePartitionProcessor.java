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

import io.zeebe.broker.logstreams.processor.TypedEvent;
import io.zeebe.broker.logstreams.processor.TypedEventProcessor;
import io.zeebe.broker.logstreams.processor.TypedResponseWriter;
import io.zeebe.broker.logstreams.processor.TypedStreamWriter;

public class CompletePartitionProcessor implements TypedEventProcessor<PartitionEvent>
{

    protected final TopicsIndex topics;
    protected final PartitionsIndex partitions;

    public CompletePartitionProcessor(TopicsIndex topics, PartitionsIndex partitions)
    {
        this.topics = topics;
        this.partitions = partitions;
    }

    @Override
    public void processEvent(TypedEvent<PartitionEvent> event)
    {
        final PartitionEvent value = event.getValue();

        final DirectBuffer topicName = value.getTopicName();
        final long pendingPartitionKey = partitions.getPartitionKey(topicName, value.getId());

        if (pendingPartitionKey >= 0)
        {
            value.setState(PartitionState.CREATED);
        }
        else
        {
            value.setState(PartitionState.CREATE_COMPLETE_REJECTED);
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
        return writer.writeFollowupEvent(event.getKey(), event.getValue());
    }

    @Override
    public void updateState(TypedEvent<PartitionEvent> event)
    {
        final PartitionEvent value = event.getValue();

        final DirectBuffer topicName = value.getTopicName();

        if (value.getState() == PartitionState.CREATED)
        {
            topics.moveTo(topicName);
            final int remainingPartitions = topics.getRemainingPartitions();

            if (remainingPartitions > 0)
            {
                topics.putRemainingPartitions(topicName, remainingPartitions - 1);
            }

            partitions.removePartitionKey(topicName, value.getId());
        }

    }

}

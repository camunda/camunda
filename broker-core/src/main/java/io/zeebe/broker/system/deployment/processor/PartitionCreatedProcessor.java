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
import io.zeebe.broker.system.log.PartitionEvent;
import org.agrona.DirectBuffer;

public class PartitionCreatedProcessor implements TypedEventProcessor<PartitionEvent>
{
    private final TopicPartitions topicPartitions;

    public PartitionCreatedProcessor(TopicPartitions topicPartitions)
    {
        this.topicPartitions = topicPartitions;
    }

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
        final int partitionId = partitionEvent.getId();

        topicPartitions.put(partitionId, topicName, TopicPartitions.STATE_CREATING);
    }

}

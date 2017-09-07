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
import io.zeebe.broker.logstreams.processor.TypedStreamEnvironment;
import io.zeebe.broker.logstreams.processor.TypedStreamProcessor;
import io.zeebe.protocol.clientapi.EventType;

public class BrokerStreamProcessors
{

    public static TypedStreamProcessor buildSystemStreamProcessor(TypedStreamEnvironment environment, PartitionManager partitionManager)
    {
        final PartitionsIndex partitionsIndex = new PartitionsIndex();
        final TopicsIndex topicsIndex = new TopicsIndex();

        final ResolvePendingPartitionsCommand cmd =
                new ResolvePendingPartitionsCommand(partitionsIndex, partitionManager, environment.buildStreamWriter());

        final TypedStreamProcessor streamProcessor = environment.newStreamProcessor()
            .onEvent(EventType.TOPIC_EVENT, TopicState.CREATE, new CreateTopicProcessor(topicsIndex))
            .onEvent(EventType.PARTITION_EVENT, PartitionState.CREATE, new CreatePartitionProcessor(partitionManager, partitionsIndex))
            .onEvent(EventType.PARTITION_EVENT, PartitionState.CREATE_COMPLETE, new CompletePartitionProcessor(topicsIndex, partitionsIndex))
            .onEvent(EventType.PARTITION_EVENT, PartitionState.CREATED, new PartitionCreatedProcessor(topicsIndex, environment.buildStreamReader()))
            .withStateResource(topicsIndex.getRawMap())
            .withStateResource(partitionsIndex.getRawMap())
            .build();

        return streamProcessor;
    }
}

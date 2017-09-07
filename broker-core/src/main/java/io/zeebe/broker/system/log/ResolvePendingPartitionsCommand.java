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

import java.util.Iterator;

import org.agrona.DirectBuffer;

import io.zeebe.broker.clustering.management.Partition;
import io.zeebe.broker.clustering.management.PartitionManager;
import io.zeebe.broker.logstreams.processor.TypedStreamWriter;

public class ResolvePendingPartitionsCommand implements Runnable
{
    protected final PartitionsIndex partitions;
    protected final PartitionManager partitionManager;

    protected final TypedStreamWriter writer;
    protected final PartitionEvent partitionEvent = new PartitionEvent();

    public ResolvePendingPartitionsCommand(
            PartitionsIndex partitions,
            PartitionManager partitionManager,
            TypedStreamWriter writer)
    {
        this.partitions = partitions;
        this.partitionManager = partitionManager;
        this.writer = writer;
    }

    @Override
    public void run()
    {
        if (partitions.isEmpty())
        {
            // no pending partitions
            return;
        }

        final Iterator<Partition> currentPartitions = partitionManager.getKnownPartitions();
        while (currentPartitions.hasNext())
        {
            final Partition nextPartition = currentPartitions.next();
            final DirectBuffer topicName = nextPartition.getTopicName();
            final int partitionId = nextPartition.getPartitionId();

            final long key = partitions.getPartitionKey(topicName, partitionId);

            if (key >= 0)
            {
                partitionEvent.reset();
                partitionEvent.setTopicName(topicName);
                partitionEvent.setId(partitionId);
                partitionEvent.setState(PartitionState.CREATE_COMPLETE);

                // it is ok if writing fails,
                // we will then try it again with the next command execution (there are no other side effects)
                writer.writeFollowupEvent(key, partitionEvent);
            }
        }
    }
}

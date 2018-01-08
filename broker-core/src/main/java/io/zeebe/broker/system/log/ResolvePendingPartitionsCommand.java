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

import io.zeebe.broker.clustering.management.PartitionManager;
import io.zeebe.broker.clustering.member.Member;
import io.zeebe.broker.logstreams.processor.TypedEvent;
import io.zeebe.broker.logstreams.processor.TypedStreamReader;
import io.zeebe.broker.logstreams.processor.TypedStreamWriter;
import io.zeebe.broker.system.log.PendingPartitionsIndex.PendingPartition;
import io.zeebe.util.CloseableSilently;
import io.zeebe.util.collection.IntIterator;
import io.zeebe.util.time.ClockUtil;

public class ResolvePendingPartitionsCommand implements Runnable, CloseableSilently
{
    protected final PendingPartitionsIndex partitions;
    protected final PartitionManager partitionManager;

    protected final TypedStreamWriter writer;
    protected final TypedStreamReader reader;

    public ResolvePendingPartitionsCommand(
            PendingPartitionsIndex partitions,
            PartitionManager partitionManager,
            TypedStreamReader reader,
            TypedStreamWriter writer)
    {
        this.partitions = partitions;
        this.partitionManager = partitionManager;
        this.reader = reader;
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

        checkCompletedCreation();
        checkExpiredCreation();
    }

    @Override
    public void close()
    {
        reader.close();
    }

    private void checkExpiredCreation()
    {
        final Iterator<PendingPartition> partitionIt = partitions.iterator();
        final long now = ClockUtil.getCurrentTimeInMillis();

        while (partitionIt.hasNext())
        {
            final PendingPartition partition = partitionIt.next();
            if (partition.getCreationTimeout() < now)
            {
                final TypedEvent<PartitionEvent> event =
                        reader.readValue(partition.getPosition(), PartitionEvent.class);

                event.getValue().setState(PartitionState.CREATE_EXPIRE);

                // it is ok if writing fails,
                // we will then try it again with the next command execution (there are no other side effects of expiration)
                writer.writeFollowupEvent(event.getKey(), event.getValue());
            }
        }
    }

    private void checkCompletedCreation()
    {
        final Iterator<Member> currentMembers = partitionManager.getKnownMembers();

        while (currentMembers.hasNext())
        {
            final Member currentMember = currentMembers.next();

            final IntIterator partitionsLeadByMember = currentMember.getLeadingPartitions();


            while (partitionsLeadByMember.hasNext())
            {

                final int currentPartition = partitionsLeadByMember.nextInt();
//                Loggers.CLUSTERING_LOGGER.debug("Member {} leads partition {}.", currentMember.getManagementAddress(), currentPartition);
                final PendingPartition partition = partitions.get(currentPartition);

                if (partition != null)
                {

//                    Loggers.CLUSTERING_LOGGER.debug("Found partition {}.", partition);
                    final TypedEvent<PartitionEvent> event =
                            reader.readValue(partition.getPosition(), PartitionEvent.class);

                    event.getValue().setState(PartitionState.CREATE_COMPLETE);

                    // it is ok if writing fails,
                    // we will then try it again with the next command execution (there are no other side effects of completion)
                    writer.writeFollowupEvent(event.getKey(), event.getValue());
                }
            }
        }
    }
}

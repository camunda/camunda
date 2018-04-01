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

import java.time.Duration;
import java.util.Iterator;

import io.zeebe.broker.Loggers;
import io.zeebe.broker.clustering.base.topology.*;
import io.zeebe.broker.clustering.base.topology.Topology.NodeInfo;
import io.zeebe.broker.clustering.base.topology.Topology.PartitionInfo;
import io.zeebe.broker.logstreams.processor.*;
import io.zeebe.broker.system.log.PendingPartitionsIndex.PendingPartition;
import io.zeebe.util.CloseableSilently;
import io.zeebe.util.sched.ActorControl;
import io.zeebe.util.sched.clock.ActorClock;
import org.slf4j.Logger;

public class ResolvePendingPartitionsCommand implements CloseableSilently, TopologyPartitionListener
{
    private static final Logger LOG = Loggers.CLUSTERING_LOGGER;

    protected final PendingPartitionsIndex partitions;
    private ActorControl actorControl;

    protected final TypedStreamWriter writer;
    protected final TypedStreamReader reader;

    public ResolvePendingPartitionsCommand(PendingPartitionsIndex partitions,
            TypedStreamReader reader,
            TypedStreamWriter writer)
    {
        this.partitions = partitions;
        this.reader = reader;
        this.writer = writer;
    }

    public void init(ActorControl actorControl)
    {
        this.actorControl = actorControl;
        this.actorControl.runAtFixedRate(Duration.ofSeconds(1), this::checkExpiredCreation);
    }

    @Override
    public void close()
    {
        reader.close();
    }

    private void checkExpiredCreation()
    {
        if (partitions.isEmpty())
        {
            return;
        }

        final Iterator<PendingPartition> partitionIt = partitions.iterator();
        final long now = ActorClock.currentTimeMillis();

        while (partitionIt.hasNext())
        {
            final PendingPartition partition = partitionIt.next();
            if (partition.getCreationTimeout() < now)
            {
                final TypedEvent<PartitionEvent> event = reader.readValue(partition.getPosition(), PartitionEvent.class);

                event.getValue().setState(PartitionState.CREATE_EXPIRE);

                // it is ok if writing fails,
                // we will then try it again with the next command execution (there are no other side effects of expiration)
                writer.writeFollowupEvent(event.getKey(), event.getValue());
            }
        }
    }

    @Override
    public void onPartitionUpdated(PartitionInfo partitionInfo, Topology topology)
    {
        final int partitionId = partitionInfo.getPartitionId();
        final NodeInfo leader = topology.getLeader(partitionId);

        actorControl.run(() ->
        {
            actorControl.runUntilDone(() ->
            {
                final PendingPartition pendingPartition = partitions.get(partitionId);
                if (pendingPartition != null && leader != null)
                {
                    final TypedEvent<PartitionEvent> event = reader.readValue(pendingPartition.getPosition(), PartitionEvent.class);

                    event.getValue().setState(PartitionState.CREATE_COMPLETE);

                    if (writer.writeFollowupEvent(event.getKey(), event.getValue()) >= 0)
                    {
                        actorControl.done();
                    }
                    else
                    {
                        actorControl.yield();
                    }
                }
                else
                {
                    actorControl.done();
                }
            });
        });
    }
}

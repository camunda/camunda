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
package io.zeebe.broker.clustering.management.memberList;

import io.zeebe.broker.Loggers;
import io.zeebe.broker.clustering.management.ClusterManagerContext;
import io.zeebe.gossip.GossipSyncRequestHandler;
import io.zeebe.gossip.dissemination.GossipSyncRequest;
import io.zeebe.util.sched.ActorControl;
import io.zeebe.util.sched.future.ActorFuture;
import org.agrona.ExpandableArrayBuffer;
import org.slf4j.Logger;

import java.util.Iterator;
import java.util.List;

import static io.zeebe.broker.clustering.management.memberList.GossipEventCreationHelper.writeRaftsIntoBuffer;

public final class MemberRaftStatesSyncHandler implements GossipSyncRequestHandler
{
    private static final boolean IS_TRACE_ON = Loggers.CLUSTERING_LOGGER.isTraceEnabled();

    public static final Logger LOG = Loggers.CLUSTERING_LOGGER;

    private final ClusterManagerContext clusterManagerContext;
    private final ExpandableArrayBuffer memberRaftStatesBuffer;
    private final ActorControl actor;

    public MemberRaftStatesSyncHandler(ActorControl actorControl, ClusterManagerContext clusterManagerContext)
    {
        this.actor = actorControl;
        this.clusterManagerContext = clusterManagerContext;
        this.memberRaftStatesBuffer = new ExpandableArrayBuffer();
    }

    @Override
    public ActorFuture<Void> onSyncRequest(GossipSyncRequest request)
    {
        return actor.call(() ->
        {
            if (IS_TRACE_ON)
            {
                LOG.trace("Got RAFT state sync request.");
            }
            final Iterator<MemberRaftComposite> iterator = clusterManagerContext.getMemberListService()
                                                                                .iterator();
            while (iterator.hasNext())
            {
                final MemberRaftComposite next = iterator.next();

                final List<RaftStateComposite> rafts = next.getRafts();
                if (!rafts.isEmpty())
                {
                    final int length = writeRaftsIntoBuffer(rafts, memberRaftStatesBuffer);

                    request.addPayload(next.getMember(), memberRaftStatesBuffer, 0, length);
                }
            }

            if (IS_TRACE_ON)
            {
                LOG.trace("Send RAFT state sync response.");
            }
        });
    }
}

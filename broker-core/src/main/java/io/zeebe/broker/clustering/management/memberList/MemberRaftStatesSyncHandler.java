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

import static io.zeebe.broker.clustering.management.memberList.GossipEventCreationHelper.writeRaftsIntoBuffer;

import java.util.Iterator;

import io.zeebe.broker.Loggers;
import io.zeebe.broker.clustering.management.ClusterManagerContext;
import io.zeebe.gossip.GossipSyncRequestHandler;
import io.zeebe.gossip.dissemination.GossipSyncRequest;
import io.zeebe.util.DeferredCommandContext;
import org.agrona.DirectBuffer;
import org.agrona.ExpandableArrayBuffer;
import org.slf4j.Logger;

public final class MemberRaftStatesSyncHandler implements GossipSyncRequestHandler
{
    public static final Logger LOG = Loggers.CLUSTERING_LOGGER;

    private final DeferredCommandContext clusterManagerCmdQueue;
    private final ClusterManagerContext clusterManagerContext;
    private final ExpandableArrayBuffer memberRaftStatesBuffer;

    public MemberRaftStatesSyncHandler(DeferredCommandContext clusterManagerCmdQueue, ClusterManagerContext clusterManagerContext)
    {
        this.clusterManagerCmdQueue = clusterManagerCmdQueue;
        this.clusterManagerContext = clusterManagerContext;
        this.memberRaftStatesBuffer = new ExpandableArrayBuffer();
    }

    @Override
    public void onSyncRequest(GossipSyncRequest request)
    {
        clusterManagerCmdQueue.runAsync(() ->
        {
            LOG.debug("Got RAFT state sync request.");
            final Iterator<MemberRaftComposite> iterator = clusterManagerContext.getMemberListService()
                                                                                .iterator();
            while (iterator.hasNext())
            {
                final MemberRaftComposite next = iterator.next();

                final DirectBuffer payload = writeRaftsIntoBuffer(next.getRafts(), memberRaftStatesBuffer);
                request.addPayload(next.getMember()
                                       .getAddress(), payload);
            }
            request.done();

            LOG.debug("Send RAFT state sync response.");
        });
    }
}

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
package io.zeebe.broker.clustering.gossip;

import static io.zeebe.clustering.gossip.PeerState.ALIVE;
import static io.zeebe.clustering.gossip.PeerState.SUSPECT;
import static io.zeebe.util.buffer.BufferUtil.cloneBuffer;

import java.util.concurrent.CompletableFuture;

import org.agrona.DirectBuffer;

import io.zeebe.broker.clustering.gossip.data.Peer;
import io.zeebe.broker.clustering.gossip.data.PeerList;
import io.zeebe.broker.clustering.gossip.data.PeerListIterator;
import io.zeebe.broker.clustering.gossip.data.RaftMembership;
import io.zeebe.broker.clustering.gossip.handler.GossipFragmentHandler;
import io.zeebe.broker.clustering.gossip.protocol.GossipController;
import io.zeebe.broker.clustering.handler.Topology;
import io.zeebe.clustering.gossip.PeerState;
import io.zeebe.clustering.gossip.RaftMembershipState;
import io.zeebe.transport.BufferingServerTransport;
import io.zeebe.transport.ServerInputSubscription;
import io.zeebe.transport.SocketAddress;
import io.zeebe.util.DeferredCommandContext;
import io.zeebe.util.actor.Actor;

public class Gossip implements Actor
{
    public static final String GOSSIP_FILE_NAME = "gossip.zeebe";

    private final Peer peer;
    private final PeerList peers;

    protected final ServerInputSubscription inputSubscription;

    private final GossipController gossipController;

    private final DeferredCommandContext commandContext;

    public Gossip(final GossipContext context)
    {
        this.peer = context.getLocalPeer();
        this.peers = context.getPeers();

        this.gossipController = new GossipController(context);
        final BufferingServerTransport serverTransport = context.getServerTransport();
        final GossipFragmentHandler fragmentHandler = new GossipFragmentHandler(gossipController);
        inputSubscription = serverTransport
                .openSubscription("gossip", fragmentHandler, fragmentHandler)
                .join();

        this.commandContext = new DeferredCommandContext();
    }

    @Override
    public String name()
    {
        return "gossip";
    }

    @Override
    public int getPriority(long now)
    {
        return PRIORITY_LOW;
    }

    public void open()
    {
        final PeerListIterator iterator = peers.iterator();
        while (iterator.hasNext())
        {
            final Peer peer = iterator.next();
            if (peer.state() == SUSPECT)
            {
                peer.state(ALIVE);
            }

            peers.set(iterator.position(), peer);
        }

        gossipController.open();
    }

    public void close()
    {
        gossipController.close();
    }

    @Override
    public int doWork() throws Exception
    {
        int workcount = 0;

        workcount += commandContext.doWork();

        workcount += gossipController.doWork();
        workcount += inputSubscription.poll();

        return workcount;
    }

    public Peer peer()
    {
        return peer;
    }

    public PeerList peers()
    {
        return peers;
    }

    public static String fileName(String directory)
    {
        return directory + GOSSIP_FILE_NAME;
    }

    public CompletableFuture<Topology> getTopology()
    {
        return commandContext.runAsync(future ->
        {
            final Topology topology = new Topology();

            // force update local peer in peer list to sync local raft changes
            peers.update(peer);

            for (final Peer peer : peers)
            {
                if (PeerState.ALIVE == peer.state())
                {
                    final SocketAddress clientEndpoint = peer.clientEndpoint();

                    topology.brokers().add()
                            .setHost(clientEndpoint.getHostBuffer(), 0, clientEndpoint.hostLength())
                            .setPort(clientEndpoint.port());

                    for (final RaftMembership raftMembership : peer.raftMemberships())
                    {
                        if (RaftMembershipState.LEADER == raftMembership.state())
                        {
                            // TODO(menski): creates garbage
                            final DirectBuffer topicName = cloneBuffer(raftMembership.topicNameBuffer(), 0, raftMembership.topicNameLength());

                            topology.topicLeaders().add()
                                    .setTopicName(topicName, 0, topicName.capacity())
                                    .setPartitionId(raftMembership.partitionId())
                                    .setHost(clientEndpoint.getHostBuffer(), 0, clientEndpoint.hostLength())
                                    .setPort(clientEndpoint.port());
                        }
                    }
                }
            }

            future.complete(topology);
        });
    }

}

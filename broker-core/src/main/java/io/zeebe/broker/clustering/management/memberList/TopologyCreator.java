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

import java.util.Iterator;
import java.util.concurrent.CompletableFuture;

import io.zeebe.broker.Loggers;
import io.zeebe.broker.clustering.handler.TopologyBroker;
import io.zeebe.broker.clustering.handler.Topology;
import io.zeebe.broker.clustering.management.ClusterManagerContext;
import io.zeebe.msgpack.value.ValueArray;
import io.zeebe.transport.SocketAddress;
import io.zeebe.util.buffer.BufferUtil;
import org.agrona.DirectBuffer;
import org.slf4j.Logger;

public class TopologyCreator
{
    public static final Logger LOG = Loggers.CLUSTERING_LOGGER;

    private final ClusterManagerContext clusterManagerContext;

    public TopologyCreator(ClusterManagerContext clusterManagerContext)
    {
        this.clusterManagerContext = clusterManagerContext;
    }

    public void createTopology(CompletableFuture<Topology> future)
    {
        LOG.debug("Received topology request.");
        final Iterator<MemberRaftComposite> iterator = clusterManagerContext.getMemberListService()
                                                                            .iterator();
        final Topology topology = new Topology();
        while (iterator.hasNext())
        {
            final MemberRaftComposite next = iterator.next();

            final ValueArray<TopologyBroker> brokers = topology.brokers();

            final SocketAddress clientApi = next.getClientApi();

            if (clientApi != null)
            {
                final TopologyBroker nextTopologyBroker = brokers.add();
                nextTopologyBroker.setHost(clientApi.getHostBuffer(), 0, clientApi.hostLength())
                                  .setPort(clientApi.port());

                final Iterator<RaftStateComposite> raftTupleIt = next.getRaftIterator();
                while (raftTupleIt.hasNext())
                {
                    final RaftStateComposite nextRaftState = raftTupleIt.next();
                    final DirectBuffer topicName = BufferUtil.cloneBuffer(nextRaftState.getTopicName());

                    nextTopologyBroker.partitionStates()
                                      .add()
                                      .setPartitionId(nextRaftState.getPartition())
                                      .setTopicName(topicName, 0, topicName.capacity())
                                      .setState(nextRaftState.getRaftState());
                }
            }
        }

        // DO NOT LOG TOPOLOGY SEE https://github.com/zeebe-io/zeebe/issues/616
        // LOG.debug("Send topology {} as response.", topology);
        future.complete(topology);
    }
}

/**
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
package io.zeebe.broker.clustering;

import io.zeebe.broker.clustering.gossip.Gossip;
import io.zeebe.broker.clustering.gossip.GossipContext;
import io.zeebe.broker.clustering.gossip.data.Peer;
import io.zeebe.broker.clustering.gossip.data.PeerList;
import io.zeebe.broker.clustering.gossip.data.PeerSelector;
import io.zeebe.broker.clustering.management.ClusterManager;
import io.zeebe.broker.clustering.management.ClusterManagerContext;
import io.zeebe.broker.clustering.raft.Raft;
import io.zeebe.broker.clustering.raft.RaftContext;
import io.zeebe.servicecontainer.ServiceName;

public class ClusterServiceNames
{
    public static final ServiceName<Peer> PEER_LOCAL_SERVICE = ServiceName.newServiceName("cluster.peer.local", Peer.class);
    public static final ServiceName<PeerList> PEER_LIST_SERVICE = ServiceName.newServiceName("cluster.peer.list", PeerList.class);

    public static final ServiceName<Gossip> GOSSIP_SERVICE = ServiceName.newServiceName("cluster.gossip", Gossip.class);
    public static final ServiceName<GossipContext> GOSSIP_CONTEXT_SERVICE = ServiceName.newServiceName("cluster.gossip.context", GossipContext.class);
    public static final ServiceName<PeerSelector> GOSSIP_PEER_SELECTOR_SERVICE = ServiceName.newServiceName("cluster.gossip.peer.selector", PeerSelector.class);

    public static final ServiceName<Raft> RAFT_SERVICE_GROUP = ServiceName.newServiceName("cluster.raft.service", Raft.class);

    public static final ServiceName<ClusterManager> CLUSTER_MANAGER_SERVICE = ServiceName.newServiceName("cluster.manager", ClusterManager.class);
    public static final ServiceName<ClusterManagerContext> CLUSTER_MANAGER_CONTEXT_SERVICE = ServiceName.newServiceName("cluster.manager.context", ClusterManagerContext.class);

    public static ServiceName<RaftContext> raftContextServiceName(final String name)
    {
        return ServiceName.newServiceName(String.format("cluster.raft.%s.context", name), RaftContext.class);
    }

    public static ServiceName<Raft> raftServiceName(final String name)
    {
        return ServiceName.newServiceName(String.format("cluster.raft.%s", name), Raft.class);
    }

}

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

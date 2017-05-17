package org.camunda.tngp.broker.clustering;

import org.camunda.tngp.broker.clustering.gossip.Gossip;
import org.camunda.tngp.broker.clustering.gossip.GossipContext;
import org.camunda.tngp.broker.clustering.gossip.data.Peer;
import org.camunda.tngp.broker.clustering.gossip.data.PeerList;
import org.camunda.tngp.broker.clustering.gossip.data.PeerSelector;
import org.camunda.tngp.broker.clustering.management.ClusterManager;
import org.camunda.tngp.broker.clustering.management.ClusterManagerContext;
import org.camunda.tngp.broker.clustering.raft.Raft;
import org.camunda.tngp.broker.clustering.raft.RaftContext;
import org.camunda.tngp.dispatcher.Subscription;
import org.camunda.tngp.servicecontainer.ServiceName;
import org.camunda.tngp.transport.ChannelManager;
import org.camunda.tngp.transport.requestresponse.client.TransportConnectionPool;

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

    public static ServiceName<Subscription> subscriptionServiceName(final String name)
    {
        return ServiceName.newServiceName(String.format("cluster.%s.subscription", name), Subscription.class);
    }

    public static ServiceName<ChannelManager> clientChannelManagerName(final String name)
    {
        return ServiceName.newServiceName(String.format("cluster.%s.client.channel.manager", name), ChannelManager.class);
    }

    public static ServiceName<TransportConnectionPool> transportConnectionPoolName(final String name)
    {
        return ServiceName.newServiceName(String.format("cluster.%s.connection.pool", name), TransportConnectionPool.class);
    }

}

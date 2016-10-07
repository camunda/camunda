package org.camunda.tngp.broker.clustering.worker;

import org.camunda.tngp.broker.clustering.gossip.GossipProtocol;
import org.camunda.tngp.broker.clustering.gossip.channel.ClientChannelManager;
import org.camunda.tngp.broker.clustering.management.ClusterManager;
import org.camunda.tngp.broker.clustering.raft.RaftProtocol;
import org.camunda.tngp.transport.Transport;
import org.camunda.tngp.transport.requestresponse.server.AsyncRequestWorkerContext;

public class ManagementWorkerContext extends AsyncRequestWorkerContext
{
    protected Transport transport;
    protected ClientChannelManager clientChannelManager;
    protected GossipProtocol gossipProtocol;
    protected RaftProtocol raftProtocol;
    protected ClusterManager clusterManager;

    public Transport getTransport()
    {
        return transport;
    }

    public void setTransport(final Transport transport)
    {
        this.transport = transport;
    }

    public ClientChannelManager getClientChannelManager()
    {
        return clientChannelManager;
    }

    public void setClientChannelManager(final ClientChannelManager clientChannelManager)
    {
        this.clientChannelManager = clientChannelManager;
    }

    public GossipProtocol getGossipProtocol()
    {
        return gossipProtocol;
    }

    public void setGossipProtocol(final GossipProtocol gossipProtocol)
    {
        this.gossipProtocol = gossipProtocol;
    }

    public RaftProtocol getRaftProtocol()
    {
        return raftProtocol;
    }

    public void setRaftProtocol(final RaftProtocol raftProtocol)
    {
        this.raftProtocol = raftProtocol;
    }

    public ClusterManager getClusterManager()
    {
        return clusterManager;
    }

    public void setClusterManager(final ClusterManager clusterManager)
    {
        this.clusterManager = clusterManager;
    }
}

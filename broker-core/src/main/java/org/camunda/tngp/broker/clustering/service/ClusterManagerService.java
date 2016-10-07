package org.camunda.tngp.broker.clustering.service;

import org.camunda.tngp.broker.clustering.gossip.GossipProtocol;
import org.camunda.tngp.broker.clustering.gossip.channel.ClientChannelManager;
import org.camunda.tngp.broker.clustering.management.ClusterManager;
import org.camunda.tngp.broker.clustering.raft.RaftProtocol;
import org.camunda.tngp.logstreams.log.LogStream;
import org.camunda.tngp.servicecontainer.Injector;
import org.camunda.tngp.servicecontainer.Service;
import org.camunda.tngp.servicecontainer.ServiceGroupReference;
import org.camunda.tngp.servicecontainer.ServiceName;
import org.camunda.tngp.servicecontainer.ServiceStartContext;
import org.camunda.tngp.servicecontainer.ServiceStopContext;
import org.camunda.tngp.transport.requestresponse.client.TransportConnectionPool;
import org.camunda.tngp.transport.singlemessage.DataFramePool;

public class ClusterManagerService implements Service<ClusterManager>
{
    public static final ServiceName<ClusterManager> CLUSTER_MANAGER = ServiceName.newServiceName("cluster.manager", ClusterManager.class);

    protected ClusterManager clusterManager;
    protected Injector<GossipProtocol> gossipProtocolInjector = new Injector<>();
    protected Injector<RaftProtocol> raftProtocolInjector = new Injector<>();
    protected final Injector<TransportConnectionPool> transportConnectionPoolInjector = new Injector<>();
    protected final Injector<ClientChannelManager> clientChannelManagerInjector = new Injector<>();
    protected final Injector<DataFramePool> dataFramePoolInjector = new Injector<>();

    protected ServiceGroupReference<LogStream> logStreamsGroupReference = ServiceGroupReference.<LogStream>create()
            .onAdd((name, stream) -> {
                clusterManager.addStream(stream);
            })
            .onRemove((name, stream) -> clusterManager.removeStream(stream))
            .build();

    @Override
    public void start(final ServiceStartContext startContext)
    {
        final GossipProtocol gossipProtocol = gossipProtocolInjector.getValue();
        final RaftProtocol raftProtocol = raftProtocolInjector.getValue();
        final TransportConnectionPool connectionPool = transportConnectionPoolInjector.getValue();
        final ClientChannelManager clientChannelManager = clientChannelManagerInjector.getValue();
        final DataFramePool dataFramePool = dataFramePoolInjector.getValue();

        clusterManager = new ClusterManager(gossipProtocol, raftProtocol, clientChannelManager, connectionPool, dataFramePool);
        clusterManager.start();
    }

    @Override
    public void stop(final ServiceStopContext stopContext)
    {
    }

    @Override
    public ClusterManager get()
    {
        return clusterManager;
    }

    public Injector<GossipProtocol> getGossipProtocolInjector()
    {
        return gossipProtocolInjector;
    }

    public Injector<RaftProtocol> getRaftProtocolInjector()
    {
        return raftProtocolInjector;
    }

    public Injector<ClientChannelManager> getClientChannelManagerInjector()
    {
        return clientChannelManagerInjector;
    }

    public Injector<TransportConnectionPool> getTransportConnectionPoolInjector()
    {
        return transportConnectionPoolInjector;
    }

    public Injector<DataFramePool> getDataFramePoolInjector()
    {
        return dataFramePoolInjector;
    }

    public ServiceGroupReference<LogStream> getLogStreamsGroupReference()
    {
        return logStreamsGroupReference;
    }
}

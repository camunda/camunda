package org.camunda.tngp.broker.clustering.worker;

import org.camunda.tngp.broker.clustering.gossip.GossipProtocol;
import org.camunda.tngp.broker.clustering.gossip.channel.ClientChannelManager;
import org.camunda.tngp.broker.clustering.management.ClusterManager;
import org.camunda.tngp.broker.clustering.raft.RaftProtocol;
import org.camunda.tngp.broker.transport.worker.BrokerRequestWorkerContextService;
import org.camunda.tngp.servicecontainer.Injector;
import org.camunda.tngp.servicecontainer.ServiceStartContext;
import org.camunda.tngp.transport.Transport;

public class ManagementWorkerContextService extends BrokerRequestWorkerContextService
{
    protected final Injector<Transport> transportInjector = new Injector<>();
    protected final Injector<GossipProtocol> gossipProtocolInjector = new Injector<>();
    protected final Injector<RaftProtocol> raftProtocolInjector = new Injector<>();
    protected final Injector<ClusterManager> clusterManagerInjector = new Injector<>();
    protected final Injector<ClientChannelManager> clientChannelManagerInjector = new Injector<>();

    protected ManagementWorkerContext managementContext;

    public ManagementWorkerContextService(final ManagementWorkerContext context)
    {
        super(context);
        managementContext = context;
    }

    @Override
    public void start(final ServiceStartContext serviceContext)
    {
        super.start(serviceContext);
        managementContext.setTransport(transportInjector.getValue());
        managementContext.setGossipProtocol(gossipProtocolInjector.getValue());
        managementContext.setRaftProtocol(raftProtocolInjector.getValue());
        managementContext.setClusterManager(clusterManagerInjector.getValue());
        managementContext.setClientChannelManager(clientChannelManagerInjector.getValue());
    }

    public Injector<Transport> getTransportInjector()
    {
        return transportInjector;
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

    public Injector<ClusterManager> getClusterManagerInjector()
    {
        return clusterManagerInjector;
    }

}

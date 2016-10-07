package org.camunda.tngp.broker.clustering.service;

import org.camunda.tngp.broker.clustering.gossip.GossipProtocol;
import org.camunda.tngp.broker.clustering.gossip.channel.ClientChannelManager;
import org.camunda.tngp.broker.clustering.worker.cfg.ManagementComponentCfg;
import org.camunda.tngp.servicecontainer.Injector;
import org.camunda.tngp.servicecontainer.Service;
import org.camunda.tngp.servicecontainer.ServiceName;
import org.camunda.tngp.servicecontainer.ServiceStartContext;
import org.camunda.tngp.servicecontainer.ServiceStopContext;
import org.camunda.tngp.transport.Transport;
import org.camunda.tngp.transport.requestresponse.client.TransportConnectionPool;

public class GossipProtocolService implements Service<GossipProtocol>
{
    public static final ServiceName<GossipProtocol> GOSSIP_PROTOCOL = ServiceName.newServiceName("gossip.protocol", GossipProtocol.class);

    protected final Injector<TransportConnectionPool> transportConnectionPoolInjector = new Injector<TransportConnectionPool>();
    protected final Injector<Transport> transportInjector = new Injector<Transport>();
    protected final Injector<ClientChannelManager> clientChannelManagerInjector = new Injector<ClientChannelManager>();

    protected final ManagementComponentCfg cfg;
    protected GossipProtocol gossipProtocol;

    public GossipProtocolService(final ManagementComponentCfg cfg)
    {
        this.cfg = cfg;
    }

    @Override
    public void start(ServiceStartContext serviceContext)
    {
        final Transport transport = transportInjector.getValue();
        final ClientChannelManager clientChannelManager = clientChannelManagerInjector.getValue();
        final TransportConnectionPool connectionPool = transportConnectionPoolInjector.getValue();

        gossipProtocol = new GossipProtocol(cfg, transport, clientChannelManager, connectionPool);

        gossipProtocol.start();
    }

    @Override
    public void stop(ServiceStopContext stopContext)
    {
        gossipProtocol.stop();
    }

    @Override
    public GossipProtocol get()
    {
        return gossipProtocol;
    }

    public Injector<Transport> getTransportInjector()
    {
        return transportInjector;
    }

    public Injector<ClientChannelManager> getClientChannelManagerInjector()
    {
        return clientChannelManagerInjector;
    }

    public Injector<TransportConnectionPool> getTransportConnectionPool()
    {
        return transportConnectionPoolInjector;
    }
}

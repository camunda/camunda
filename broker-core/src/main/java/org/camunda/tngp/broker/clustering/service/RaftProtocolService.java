package org.camunda.tngp.broker.clustering.service;

import org.camunda.tngp.broker.clustering.gossip.channel.ClientChannelManager;
import org.camunda.tngp.broker.clustering.raft.RaftProtocol;
import org.camunda.tngp.broker.system.ConfigurationManager;
import org.camunda.tngp.servicecontainer.Injector;
import org.camunda.tngp.servicecontainer.Service;
import org.camunda.tngp.servicecontainer.ServiceName;
import org.camunda.tngp.servicecontainer.ServiceStartContext;
import org.camunda.tngp.servicecontainer.ServiceStopContext;
import org.camunda.tngp.transport.Transport;
import org.camunda.tngp.transport.requestresponse.client.TransportConnectionPool;
import org.camunda.tngp.transport.singlemessage.DataFramePool;

public class RaftProtocolService implements Service<RaftProtocol>
{
    public static final ServiceName<RaftProtocol> RAFT_PROTOCOL = ServiceName.newServiceName("raft.protocol", RaftProtocol.class);

    protected final Injector<TransportConnectionPool> transportConnectionPoolInjector = new Injector<TransportConnectionPool>();
    protected final Injector<Transport> transportInjector = new Injector<Transport>();
    protected final Injector<ClientChannelManager> clientChannelManagerInjector = new Injector<ClientChannelManager>();
    protected final Injector<DataFramePool> dataFramePoolInjector = new Injector<DataFramePool>();

    protected final ConfigurationManager cfg;
    protected RaftProtocol raftProtocol;

    public RaftProtocolService(final ConfigurationManager cfg)
    {
        this.cfg = cfg;
    }

    @Override
    public void start(final ServiceStartContext serviceContext)
    {
        final Transport transport = transportInjector.getValue();
        final ClientChannelManager clientChannelManager = clientChannelManagerInjector.getValue();
        final TransportConnectionPool connectionPool = transportConnectionPoolInjector.getValue();
        final DataFramePool dataFramePool = dataFramePoolInjector.getValue();

        raftProtocol = new RaftProtocol(cfg, transport, clientChannelManager, connectionPool, dataFramePool);

        raftProtocol.start();
    }

    @Override
    public void stop(final ServiceStopContext stopContext)
    {
        raftProtocol.stop();
    }

    @Override
    public RaftProtocol get()
    {
        return raftProtocol;
    }

    public Injector<Transport> getTransportInjector()
    {
        return transportInjector;
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

}

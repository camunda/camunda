package org.camunda.tngp.transport;

import org.camunda.tngp.transport.impl.ClientChannelPoolImpl;
import org.camunda.tngp.transport.impl.CompositeChannelHandler;
import org.camunda.tngp.transport.impl.TransportContext;
import org.camunda.tngp.transport.protocol.Protocols;
import org.camunda.tngp.transport.requestresponse.client.RequestResponseChannelHandler;
import org.camunda.tngp.transport.requestresponse.client.TransportConnectionPool;
import org.camunda.tngp.transport.requestresponse.client.TransportConnectionPoolImpl;
import org.camunda.tngp.transport.spi.TransportChannelHandler;

public class ClientChannelPoolBuilder
{
    protected TransportContext transportContext;
    protected int initialCapacity = 32;
    protected CompositeChannelHandler channelHandler = new CompositeChannelHandler();

    public ClientChannelPoolBuilder(TransportContext transportContext)
    {
        this.transportContext = transportContext;
    }

    public ClientChannelPoolBuilder transportChannelHandler(short protocolId, TransportChannelHandler channelHandler)
    {
        this.channelHandler.addHandler(protocolId, channelHandler);
        return this;
    }

    public ClientChannelPoolBuilder requestResponseProtocol(TransportConnectionPool connectionPool)
    {
        return transportChannelHandler(Protocols.REQUEST_RESPONSE,
                new RequestResponseChannelHandler((TransportConnectionPoolImpl) connectionPool));
    }

    public ClientChannelPoolBuilder initialCapacity(int initialCapacity)
    {
        this.initialCapacity = initialCapacity;
        return this;
    }

    public ClientChannelPool build()
    {
        final ClientChannelPoolImpl pool = new ClientChannelPoolImpl(
                initialCapacity,
                64,
                transportContext,
                channelHandler);
        transportContext.getConductorCmdQueue().add((c) -> c.registerClientChannelPool(pool));
        return pool;
    }
}

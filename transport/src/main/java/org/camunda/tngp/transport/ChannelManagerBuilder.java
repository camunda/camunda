package org.camunda.tngp.transport;

import org.camunda.tngp.transport.impl.CompositeChannelHandler;
import org.camunda.tngp.transport.impl.agent.Conductor;
import org.camunda.tngp.transport.protocol.Protocols;
import org.camunda.tngp.transport.requestresponse.client.RequestResponseChannelHandler;
import org.camunda.tngp.transport.requestresponse.client.TransportConnectionPool;
import org.camunda.tngp.transport.requestresponse.client.TransportConnectionPoolImpl;
import org.camunda.tngp.transport.spi.TransportChannelHandler;

public class ChannelManagerBuilder
{
    protected Conductor conductor;
    protected int initialCapacity = 32;
    protected CompositeChannelHandler channelHandler = new CompositeChannelHandler();

    public ChannelManagerBuilder(Conductor conductor)
    {
        this.conductor = conductor;
    }

    public ChannelManagerBuilder transportChannelHandler(short protocolId, TransportChannelHandler channelHandler)
    {
        this.channelHandler.addHandler(protocolId, channelHandler);
        return this;
    }

    public ChannelManagerBuilder requestResponseProtocol(TransportConnectionPool connectionPool)
    {
        return transportChannelHandler(Protocols.REQUEST_RESPONSE,
                new RequestResponseChannelHandler((TransportConnectionPoolImpl) connectionPool));
    }

    public ChannelManagerBuilder initialCapacity(int initialCapacity)
    {
        this.initialCapacity = initialCapacity;
        return this;
    }

    public ChannelManager build()
    {
        return conductor.newChannelManager(channelHandler, initialCapacity);
    }
}

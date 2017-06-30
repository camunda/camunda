package io.zeebe.transport;

import io.zeebe.transport.impl.CompositeChannelHandler;
import io.zeebe.transport.impl.agent.Conductor;
import io.zeebe.transport.protocol.Protocols;
import io.zeebe.transport.requestresponse.client.RequestResponseChannelHandler;
import io.zeebe.transport.requestresponse.client.TransportConnectionPool;
import io.zeebe.transport.requestresponse.client.TransportConnectionPoolImpl;
import io.zeebe.transport.spi.TransportChannelHandler;

public class ChannelManagerBuilder
{
    protected Conductor conductor;
    protected int initialCapacity = 32;
    protected CompositeChannelHandler channelHandler = new CompositeChannelHandler();
    protected boolean reopenChannelsOnException = true;

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

    /**
     * Set to true, if channels should be reopenend whenever they close unexpectedly; true is default.
     */
    public ChannelManagerBuilder reopenChannelsOnException(boolean reopenChannelsOnException)
    {
        this.reopenChannelsOnException = reopenChannelsOnException;
        return this;
    }

    public ChannelManagerBuilder initialCapacity(int initialCapacity)
    {
        this.initialCapacity = initialCapacity;
        return this;
    }

    public ChannelManager build()
    {
        return conductor.newChannelManager(
                channelHandler,
                initialCapacity,
                reopenChannelsOnException);
    }
}

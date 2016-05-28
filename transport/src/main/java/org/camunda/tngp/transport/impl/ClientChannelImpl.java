package org.camunda.tngp.transport.impl;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.StandardSocketOptions;
import java.nio.channels.SocketChannel;
import java.util.concurrent.atomic.AtomicLong;

import org.camunda.tngp.dispatcher.Dispatcher;
import org.camunda.tngp.transport.ClientChannel;
import org.camunda.tngp.transport.impl.agent.TransportConductorCmd;
import org.camunda.tngp.transport.spi.TransportChannelHandler;

import uk.co.real_logic.agrona.LangUtil;
import uk.co.real_logic.agrona.concurrent.ManyToOneConcurrentArrayQueue;

public class ClientChannelImpl extends TransportChannelImpl implements ClientChannel
{
    protected final ManyToOneConcurrentArrayQueue<TransportConductorCmd> toConcuctorCmdQue;

    protected final Dispatcher sendBuffer;

    protected final AtomicLong requestIdGenerator = new AtomicLong();

    protected InetSocketAddress remoteAddress;

    public ClientChannelImpl(
            final TransportContext transportContext,
            final TransportChannelHandler channelHandler,
            final InetSocketAddress remoteAddress)
    {
        super(transportContext, channelHandler);
        this.toConcuctorCmdQue = transportContext.getConductorCmdQueue();
        this.remoteAddress = remoteAddress;
        sendBuffer = transportContext.getSendBuffer();
    }

    public void finishConnect()
    {
        try
        {
            media.finishConnect();
        }
        catch (IOException e)
        {
            e.printStackTrace();
            closeForcibly(e);
        }
    }

    public void startConnect()
    {
        try
        {
            media = SocketChannel.open();
            media.setOption(StandardSocketOptions.TCP_NODELAY, true);
            media.configureBlocking(false);
            media.connect(remoteAddress);
        }
        catch (Exception e)
        {
            LangUtil.rethrowUnchecked(e);
        }
    }

    public InetSocketAddress getRemoteAddress()
    {
        return remoteAddress;
    }

    public void setConnected()
    {
        STATE_FIELD.compareAndSet(this, STATE_CONNECTING, STATE_CONNECTED);
    }
}
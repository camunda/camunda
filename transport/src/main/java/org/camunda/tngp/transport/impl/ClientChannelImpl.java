package org.camunda.tngp.transport.impl;

import java.io.IOException;
import java.net.StandardSocketOptions;
import java.nio.channels.SocketChannel;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.agrona.LangUtil;
import org.agrona.concurrent.ManyToOneConcurrentArrayQueue;
import org.camunda.tngp.dispatcher.Dispatcher;
import org.camunda.tngp.transport.ClientChannel;
import org.camunda.tngp.transport.SocketAddress;
import org.camunda.tngp.transport.impl.agent.TransportConductorCmd;
import org.camunda.tngp.transport.spi.TransportChannelHandler;
import org.camunda.tngp.util.time.ClockUtil;

public class ClientChannelImpl extends TransportChannelImpl implements ClientChannel
{
    protected final ManyToOneConcurrentArrayQueue<TransportConductorCmd> toConcuctorCmdQue;

    protected final Dispatcher sendBuffer;

    protected SocketAddress remoteAddress = new SocketAddress();

    protected AtomicLong lastUsed = new AtomicLong(Long.MIN_VALUE);
    protected AtomicInteger references = new AtomicInteger(0);

    public ClientChannelImpl(
            final TransportContext transportContext,
            final TransportChannelHandler channelHandler,
            final SocketAddress remoteAddress)
    {
        super(transportContext, channelHandler);
        this.toConcuctorCmdQue = transportContext.getConductorCmdQueue();
        this.remoteAddress.wrap(remoteAddress);
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
            media.connect(remoteAddress.toInetSocketAddress());
        }
        catch (Exception e)
        {
            LangUtil.rethrowUnchecked(e);
        }
    }

    public SocketAddress getRemoteAddress()
    {
        return remoteAddress;
    }

    public void setConnected()
    {
        STATE_FIELD.compareAndSet(this, STATE_CONNECTING, STATE_CONNECTED);
    }

    public void countUsageBegin()
    {
        references.incrementAndGet();
        lastUsed.set(ClockUtil.getCurrentTimeInMillis());
    }

    public void countUsageEnd()
    {
        references.decrementAndGet();
        lastUsed.set(ClockUtil.getCurrentTimeInMillis());
    }

    public boolean isInUse()
    {
        return references.get() > 0;
    }

    public long getLastUsed()
    {
        return lastUsed.get();
    }

}
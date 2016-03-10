package org.camunda.tngp.transport.impl;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.SocketChannel;

import org.camunda.tngp.transport.ChannelErrorHandler;
import org.camunda.tngp.transport.ChannelFrameHandler;
import org.camunda.tngp.transport.ClientChannel;
import org.camunda.tngp.transport.impl.agent.TransportConductorCmd;

import net.long_running.dispatcher.AsyncCompletionCallback;
import uk.co.real_logic.agrona.LangUtil;
import uk.co.real_logic.agrona.concurrent.ManyToOneConcurrentArrayQueue;

public class ClientChannelImpl extends BaseChannelImpl implements ClientChannel
{

    protected final ManyToOneConcurrentArrayQueue<TransportConductorCmd> toConcuctorCmdQue;

    protected InetSocketAddress remoteAddress;

    public ClientChannelImpl(
            final TransportContext transportContext,
            final ChannelFrameHandler frameHandler,
            final ChannelErrorHandler errorHandler,
            final InetSocketAddress remoteAddress,
            final AsyncCompletionCallback<ClientChannel> callback)
    {
        super(transportContext, frameHandler, errorHandler);
        this.toConcuctorCmdQue = transportContext.getConductorCmdQueue();
        this.remoteAddress = remoteAddress;

        toConcuctorCmdQue.add((cc) ->
        {
            cc.doConnectChannel(this, callback);
        });
    }

    public void finishConnect()
    {
        try
        {
            media.finishConnect();
            state = State.CONNECTED;
        }
        catch (IOException e)
        {
            LangUtil.rethrowUnchecked(e);
        }
    }

    public void startConnect()
    {
        try
        {
            media = SocketChannel.open();
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

}
package net.long_running.transport.impl;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.SocketChannel;

import net.long_running.dispatcher.AsyncCompletionCallback;
import net.long_running.transport.ChannelFrameHandler;
import net.long_running.transport.ClientChannel;
import net.long_running.transport.impl.agent.TransportConductorCmd;
import uk.co.real_logic.agrona.LangUtil;
import uk.co.real_logic.agrona.concurrent.ManyToOneConcurrentArrayQueue;

public class ClientChannelImpl extends BaseChannelImpl implements ClientChannel
{

    protected final ManyToOneConcurrentArrayQueue<TransportConductorCmd> toConcuctorCmdQue;

    protected InetSocketAddress remoteAddress;

    public ClientChannelImpl(
            ChannelFrameHandler channelReader,
            TransportContext transportContext,
            final InetSocketAddress remoteAddress,
            final AsyncCompletionCallback<ClientChannel> callback)
    {
        super(channelReader, transportContext);
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

    @Override
    public void close()
    {
        // TODO
    }

    public InetSocketAddress getRemoteAddress()
    {
        return remoteAddress;
    }

}
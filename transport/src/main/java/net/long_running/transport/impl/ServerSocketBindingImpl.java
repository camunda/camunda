package net.long_running.transport.impl;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;

import net.long_running.dispatcher.AsyncCompletionCallback;
import net.long_running.transport.ChannelErrorHandler;
import net.long_running.transport.ChannelFrameHandler;
import net.long_running.transport.ServerChannelHandler;
import net.long_running.transport.ServerSocketBinding;
import net.long_running.transport.impl.agent.TransportConductorCmd;
import uk.co.real_logic.agrona.LangUtil;
import uk.co.real_logic.agrona.concurrent.ManyToOneConcurrentArrayQueue;

public class ServerSocketBindingImpl implements ServerSocketBinding
{

    protected final ServerChannelHandler channelHander;
    protected final ManyToOneConcurrentArrayQueue<TransportConductorCmd> conductorCmdQueue;
    protected final TransportContext transportContext;

    protected final InetSocketAddress bindAddress;
    protected ServerSocketChannel media;

    public ServerSocketBindingImpl(
            final TransportContext transportContext,
            final ServerChannelHandler channelHander,
            final InetSocketAddress bindAddress,
            AsyncCompletionCallback<ServerSocketBinding> completionCallback)
    {
        this.transportContext = transportContext;
        this.channelHander = channelHander;
        this.conductorCmdQueue = transportContext.getConductorCmdQueue();
        this.bindAddress = bindAddress;

        if(bindAddress == null)
        {
            throw new IllegalArgumentException("bindAddress cannot be null");
        }

        conductorCmdQueue.add((cc) ->
        {
           cc.doBindServerSocket(this, completionCallback);
        });
    }

    public void doBind()
    {
        try
        {
            media = ServerSocketChannel.open();
            media.bind(bindAddress);
            media.configureBlocking(false);
        }
        catch (IOException e)
        {
            LangUtil.rethrowUnchecked(e);
        }
    }

    public void registerSelector(Selector selector, int op)
    {
        try
        {
            final SelectionKey key = media.register(selector, op);
            key.attach(this);
        }
        catch (ClosedChannelException e)
        {
            LangUtil.rethrowUnchecked(e);
        }
    }

    public void removeSelector(Selector selector)
    {
        final SelectionKey key = media.keyFor(selector);
        key.cancel();
    }

    public ServerChannelImpl accept()
    {
        ServerChannelImpl channel = null;
        try
        {
            final SocketChannel socketChannel = media.accept();
            socketChannel.configureBlocking(false);

            channel = new ServerChannelImpl(
                    transportContext, socketChannel,
                    ChannelFrameHandler.DISCARD_HANDLER,
                    ChannelErrorHandler.DEFAULT_ERROR_HANDLER);

            if(channelHander != null)
            {
                try
                {
                    channelHander.onChannelAccepted(channel);
                }
                catch(Exception e)
                {
                    // TODO
                    e.printStackTrace();
                }
            }

        }
        catch (IOException e)
        {
            LangUtil.rethrowUnchecked(e);
        }

        return channel;
    }

    @Override
    public void close()
    {
        // TODO
    }

    @Override
    public InetSocketAddress getBindAddress()
    {
        return bindAddress;
    }

}

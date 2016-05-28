package org.camunda.tngp.transport.impl;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.StandardSocketOptions;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;

import org.camunda.tngp.transport.ServerSocketBinding;
import org.camunda.tngp.transport.impl.agent.TransportConductorCmd;
import org.camunda.tngp.transport.spi.TransportChannelHandler;

import uk.co.real_logic.agrona.LangUtil;
import uk.co.real_logic.agrona.concurrent.ManyToOneConcurrentArrayQueue;

public class ServerSocketBindingImpl implements ServerSocketBinding
{
    public final static int STATE_NEW = -1;
    public final static int STATE_OPEN = 0;
    public final static int STATE_CLOSING = 1;
    public final static int STATE_CLOSED = 2;

    private final static AtomicIntegerFieldUpdater<ServerSocketBindingImpl> STATE_UPDATER
        = AtomicIntegerFieldUpdater.newUpdater(ServerSocketBindingImpl.class, "state");

    protected volatile int state;

    protected final ManyToOneConcurrentArrayQueue<TransportConductorCmd> conductorCmdQueue;
    protected final TransportContext transportContext;
    protected final InetSocketAddress bindAddress;

    protected List<ServerChannelImpl> openChannels = new ArrayList<>();

    protected ServerSocketChannel media;
    protected TransportChannelHandler transportChannelHandler;

    public ServerSocketBindingImpl(
            final TransportContext transportContext,
            final InetSocketAddress bindAddress,
            final TransportChannelHandler transportChannelHandler)
    {
        this.transportContext = transportContext;
        this.transportChannelHandler = transportChannelHandler;
        this.conductorCmdQueue = transportContext.getConductorCmdQueue();
        this.bindAddress = bindAddress;

        if(bindAddress == null)
        {
            throw new IllegalArgumentException("bindAddress cannot be null");
        }

        STATE_UPDATER.set(this, STATE_NEW);
    }

    public void doBind()
    {
        try
        {
            media = ServerSocketChannel.open();
            media.bind(bindAddress);
            media.configureBlocking(false);

            STATE_UPDATER.set(this, STATE_OPEN);
        }
        catch (IOException e)
        {
            LangUtil.rethrowUnchecked(e);
        }
    }

    public void onChannelClosed(ServerChannelImpl serverChannelImpl)
    {
        openChannels.remove(serverChannelImpl);
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
        if(key != null)
        {
            key.cancel();
        }
    }

    public ServerChannelImpl accept()
    {
        ServerChannelImpl channel = null;

        if(STATE_UPDATER.get(this) == STATE_OPEN)
        {
            try
            {
                final SocketChannel socketChannel = media.accept();
                socketChannel.setOption(StandardSocketOptions.TCP_NODELAY, true);
                socketChannel.configureBlocking(false);

                channel = new ServerChannelImpl(
                        transportContext,
                        this,
                        socketChannel,
                        transportChannelHandler);

                openChannels.add(channel);

            }
            catch (IOException e)
            {
                LangUtil.rethrowUnchecked(e);
            }
        }

        return channel;
    }

    public CompletableFuture<ServerSocketBinding> closeAsync()
    {
        if(STATE_UPDATER.compareAndSet(this, STATE_OPEN, STATE_CLOSING))
        {
            final CompletableFuture<ServerSocketBinding> completableFuture = new CompletableFuture<>();

            conductorCmdQueue.add((c) ->
            {
               c.closeServerSocketBinding(this, completableFuture);
            });

            return completableFuture;
        }
        else
        {
            return CompletableFuture.completedFuture(this);
        }
    }

    @Override
    public void close()
    {
        closeAsync().join();
    }

    @Override
    public InetSocketAddress getBindAddress()
    {
        return bindAddress;
    }

    public void closeMedia()
    {
        try
        {
            media.close();
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }
    }

    @SuppressWarnings("rawtypes")
    public void closeAllChannels()
    {
        try
        {
            ArrayList<ServerChannelImpl> openChannelsCopy = new ArrayList<>(openChannels);

            final CompletableFuture[] completableFutures = new CompletableFuture[openChannelsCopy.size()];

            for (int i = 0; i < openChannelsCopy.size(); i++)
            {
                completableFutures[i] = openChannelsCopy.get(i).closeAsync();
            }

            CompletableFuture.allOf(completableFutures);
        }
        catch(Exception e)
        {
            e.printStackTrace();
        }
    }

}

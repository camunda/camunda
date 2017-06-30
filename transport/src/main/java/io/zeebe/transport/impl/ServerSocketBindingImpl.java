package io.zeebe.transport.impl;

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

import io.zeebe.transport.ServerSocketBinding;
import io.zeebe.transport.impl.agent.Conductor;
import io.zeebe.transport.spi.TransportChannelHandler;
import io.zeebe.util.DeferredCommandContext;
import io.zeebe.util.LangUtil;
import io.zeebe.util.state.concurrent.SharedStateMachineBlueprint;

public class ServerSocketBindingImpl implements ServerSocketBinding
{
    public static final int STATE_NEW = -1;
    public static final int STATE_OPEN = 0;
    public static final int STATE_CLOSING = 1;
    public static final int STATE_CLOSED = 2;

    private static final AtomicIntegerFieldUpdater<ServerSocketBindingImpl> STATE_UPDATER = AtomicIntegerFieldUpdater.newUpdater(ServerSocketBindingImpl.class, "state");

    protected volatile int state;

    protected final InetSocketAddress bindAddress;
    protected final Conductor conductor;

    protected List<ChannelImpl> openChannels = new ArrayList<>();

    protected final SharedStateMachineBlueprint<ChannelImpl> channelLifecycle;

    protected ServerSocketChannel media;
    protected TransportChannelHandler channelHandler;
    protected final DeferredCommandContext asyncContext;

    public ServerSocketBindingImpl(
            final InetSocketAddress bindAddress,
            final TransportChannelHandler channelHandler,
            final Conductor conductor,
            DeferredCommandContext asyncContext,
            SharedStateMachineBlueprint<ChannelImpl> defaultLifecycle)
    {
        this.channelHandler = channelHandler;
        this.bindAddress = bindAddress;
        this.conductor = conductor;
        this.channelLifecycle = defaultLifecycle.copy()
                .onState(ChannelImpl.STATE_READY, this::onChannelConnected)
                .onState(ChannelImpl.STATE_INTERRUPTED, this::onChannelInterrupted)
                .onState(ChannelImpl.STATE_CLOSED | ChannelImpl.STATE_CLOSED_UNEXPECTEDLY, this::onChannelClosed);
        this.asyncContext = asyncContext;

        if (bindAddress == null)
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
            throw new RuntimeException(e);
        }
    }

    protected void onChannelClosed(ChannelImpl serverChannelImpl)
    {
        openChannels.remove(serverChannelImpl);
    }

    protected void onChannelConnected(ChannelImpl channel)
    {
        openChannels.add(channel);
    }

    protected void onChannelInterrupted(ChannelImpl channel)
    {
        channel.setClosedUnexpectedly();
    }

    public SharedStateMachineBlueprint<ChannelImpl> getChannelLifecycle()
    {
        return channelLifecycle;
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
            throw new RuntimeException(e);
        }
    }

    public void removeSelector(Selector selector)
    {
        final SelectionKey key = media.keyFor(selector);
        if (key != null)
        {
            key.cancel();

            try
            {
                // required to reuse socket on windows, see https://github.com/kaazing/nuklei/issues/20
                selector.select(1);
            }
            catch (IOException e)
            {
                throw new RuntimeException(e);
            }
        }
    }

    public SocketChannel accept()
    {
        if (STATE_UPDATER.get(this) == STATE_OPEN)
        {
            try
            {
                final SocketChannel socketChannel = media.accept();
                socketChannel.setOption(StandardSocketOptions.TCP_NODELAY, true);
                socketChannel.configureBlocking(false);
                return socketChannel;
            }
            catch (IOException e)
            {
                throw new RuntimeException(e);
            }
        }
        else
        {
            return null;
        }
    }

    public CompletableFuture<ServerSocketBinding> closeAsync()
    {
        if (STATE_UPDATER.compareAndSet(this, STATE_OPEN, STATE_CLOSING))
        {
            return conductor.closeServerSocketAsync(this);
        }
        else
        {
            return CompletableFuture.completedFuture(this);
        }
    }

    /**
     * For testing
     */
    public CompletableFuture<Void> interruptAllChannels()
    {
        return asyncContext.runAsync((future) ->
        {
            for (int i = 0; i < openChannels.size(); i++)
            {
                try
                {
                    openChannels.get(i).getSocketChannel().shutdownInput();
                }
                catch (IOException e)
                {
                    // ignore
                    System.err.println("Could not interrupt channel");
                }
            }

            future.complete(null);
        });
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

    public CompletableFuture<Void> closeAllChannelsAsync()
    {
        try
        {
            final ArrayList<ChannelImpl> openChannelsCopy = new ArrayList<>(openChannels);

            final List<CompletableFuture> completableFutures = new ArrayList<>(openChannelsCopy.size());
            for (int i = 0; i < openChannelsCopy.size(); i++)
            {
                final ChannelImpl channel = openChannelsCopy.get(i);
                final boolean closeInitiated = conductor.closeChannel(channel);

                if (closeInitiated)
                {
                    final CompletableFuture<ChannelImpl> closeFuture = new CompletableFuture<>();
                    channel.listenForClose(closeFuture);

                    completableFutures.add(closeFuture);
                }
            }

            return LangUtil.allOf(completableFutures);
        }
        catch (Exception e)
        {
            e.printStackTrace();
            return CompletableFuture.completedFuture(null);
        }
    }

    public TransportChannelHandler getChannelHandler()
    {
        return channelHandler;
    }

}

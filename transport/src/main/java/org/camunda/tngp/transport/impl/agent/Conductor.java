package org.camunda.tngp.transport.impl.agent;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

import org.agrona.concurrent.UnsafeBuffer;
import org.agrona.concurrent.ringbuffer.ManyToOneRingBuffer;
import org.agrona.concurrent.ringbuffer.RingBufferDescriptor;
import org.camunda.tngp.transport.ServerSocketBinding;
import org.camunda.tngp.transport.SocketAddress;
import org.camunda.tngp.transport.TransportChannelListener;
import org.camunda.tngp.transport.impl.ChannelImpl;
import org.camunda.tngp.transport.impl.ChannelManagerImpl;
import org.camunda.tngp.transport.impl.ServerSocketBindingImpl;
import org.camunda.tngp.transport.impl.media.AcceptTransportPoller;
import org.camunda.tngp.transport.impl.media.ConnectTransportPoller;
import org.camunda.tngp.transport.spi.TransportChannelHandler;
import org.camunda.tngp.transport.util.SharedStateMachineBlueprint;
import org.camunda.tngp.transport.util.SharedStateMachineManager;
import org.camunda.tngp.util.DeferredCommandContext;
import org.camunda.tngp.util.LangUtil;
import org.camunda.tngp.util.actor.Actor;

public class Conductor implements Actor
{

    protected final SharedStateMachineBlueprint<ChannelImpl> channelLifecycle;
    protected final SharedStateMachineManager<ChannelImpl> channelStateManager;

    protected DeferredCommandContext commandContext = new DeferredCommandContext();
    protected List<ServerSocketBinding> serverSocketBindings = new ArrayList<>();

    protected List<ChannelManagerImpl> managers = new ArrayList<>();
    protected List<TransportChannelListener> listeners = new ArrayList<>();

    protected final ManyToOneRingBuffer controlFramesBuffer;
    protected final ConnectTransportPoller connectTransportPoller;
    protected final AcceptTransportPoller acceptTransportPoller;
    protected final Receiver receiver;
    protected final Sender sender;

    protected final int maxMessageLength;

    protected int nextStreamId = 1;
    protected final long channelKeepAlivePeriod;

    protected AtomicBoolean isOpen = new AtomicBoolean(true);

    public Conductor(
            Sender sender,
            Receiver receiver,
            ManyToOneRingBuffer controlFramesBuffer,
            int maxMessageLength,
            long channelKeepAlivePeriod,
            int stateDispatchBufferSize)
    {
        this.connectTransportPoller = new ConnectTransportPoller();
        this.acceptTransportPoller = new AcceptTransportPoller(this);
        this.sender = sender;
        this.receiver = receiver;
        this.controlFramesBuffer = controlFramesBuffer;
        this.channelKeepAlivePeriod = channelKeepAlivePeriod;
        this.maxMessageLength = maxMessageLength;

        // one entry requires RecordDescriptor.HEADER_LENGTH + 4 = 12 byte
        final ManyToOneRingBuffer channelStateChangeBuffer =
                new ManyToOneRingBuffer(new UnsafeBuffer(new byte[RingBufferDescriptor.TRAILER_LENGTH + stateDispatchBufferSize]));

        this.channelLifecycle = new SharedStateMachineBlueprint<>(channelStateChangeBuffer);
        this.channelLifecycle
            .onState(ChannelImpl.STATE_CONNECTED, this::makeChannelReady)
            .onState(ChannelImpl.STATE_READY, c -> c.getHandler().onChannelOpened(c))
            .onState(ChannelImpl.STATE_CLOSED | ChannelImpl.STATE_CLOSED_UNEXPECTEDLY, this::onChannelClose);

        this.channelStateManager = new SharedStateMachineManager<>(channelStateChangeBuffer);
    }

    protected void onChannelClose(ChannelImpl channel)
    {
        makeChannelUnready(channel);
        notifyChannelListenerClosed(channel);
        channel.getHandler().onChannelClosed(channel);
        channelStateManager.unregister(channel.getStateMachine());
    }

    protected void notifyChannelListenerClosed(ChannelImpl channel)
    {
        for (int i = 0; i < listeners.size(); i++)
        {
            listeners.get(i).onChannelClosed(channel);
        }
    }

    public CompletableFuture<Void> registerChannelListenerAsync(TransportChannelListener listener)
    {
        return this.commandContext.runAsync((future) ->
        {
            listeners.add(listener);
            future.complete(null);
        });
    }

    public CompletableFuture<Void> removeChannelListenerAsync(TransportChannelListener listener)
    {
        return this.commandContext.runAsync((future) ->
        {
            listeners.remove(listener);
            future.complete(null);
        });
    }

    @Override
    public int doWork() throws Exception
    {
        int workCount = 0;

        workCount += commandContext.doWork();

        if (isOpen())
        {
            workCount += connectTransportPoller.pollNow();
            workCount += acceptTransportPoller.pollNow();
        }

        workCount += maintainChannels();
        workCount += maintainChannelManagers();

        return workCount;
    }

    protected int maintainChannelManagers()
    {
        int workCount = 0;
        for (int i = 0; i < managers.size(); i++)
        {
            workCount += managers.get(i).maintainState();
        }
        return workCount;
    }

    protected int maintainChannels()
    {
        return channelStateManager.dispatchTransitionEvents();
    }

    public ChannelImpl newChannel(
            SocketAddress remoteAddress,
            TransportChannelHandler handler,
            SharedStateMachineBlueprint<ChannelImpl> channelLifecycle)
    {
        final ChannelImpl channel = new ChannelImpl(
            ++nextStreamId,
            remoteAddress,
            maxMessageLength,
            controlFramesBuffer,
            handler,
            channelLifecycle);
        connectChannel(channel);

        return channel;
    }

    public void connectChannel(ChannelImpl channel)
    {
        channelStateManager.register(channel.getStateMachine());

        final boolean success = channel.startConnect();

        if (success)
        {
            connectTransportPoller.addChannel(channel);
        }
    }

    public ChannelImpl newChannel(
            SocketChannel connectedChannel,
            TransportChannelHandler handler,
            SharedStateMachineBlueprint<ChannelImpl> channelLifecycle)
    {
        final InetSocketAddress remoteAddress;
        try
        {
            remoteAddress = (InetSocketAddress) connectedChannel.getRemoteAddress();
        }
        catch (IOException e)
        {
            throw new RuntimeException(e);
        }


        final ChannelImpl channel = new ChannelImpl(
            ++nextStreamId,
            new SocketAddress(remoteAddress),
            connectedChannel,
            maxMessageLength,
            controlFramesBuffer,
            handler,
            channelLifecycle);

        channelStateManager.register(channel.getStateMachine());

        return channel;
    }

    public ChannelManagerImpl newChannelManager(TransportChannelHandler handler, int initialCapacity, boolean reopenChannelsOnException)
    {
        final ChannelManagerImpl manager = new ChannelManagerImpl(
                this,
                handler,
                initialCapacity,
                channelKeepAlivePeriod,
                reopenChannelsOnException,
                channelLifecycle);

        commandContext.runAsync(() ->
        {
            managers.add(manager);
        });

        return manager;
    }

    protected void makeChannelReady(ChannelImpl channel)
    {
        final CompletableFuture<Void> senderRegistration = sender.registerChannelAsync(channel);
        final CompletableFuture<Void> receiverRegistration = receiver.registerChannelAsync(channel);

        CompletableFuture.allOf(senderRegistration, receiverRegistration)
            .whenComplete((v, t) ->
            {
                if (t == null)
                {
                    channel.setReady();
                }
                else
                {
                    channel.initiateClose();
                }
            });
    }

    protected void makeChannelUnready(ChannelImpl c)
    {
        sender.removeChannelAsync(c);
        receiver.removeChannelAsync(c);
    }

    public boolean closeChannel(ChannelImpl channel)
    {
        if (channel.isConnecting())
        {
            channel.listenFor(ChannelImpl.STATE_CONNECTED | ChannelImpl.STATE_CLOSED | ChannelImpl.STATE_CLOSED_UNEXPECTEDLY,
                (s, c) ->
                {
                    if (s == ChannelImpl.STATE_CONNECTED)
                    {
                        c.initiateClose();
                    }
                    // ignore if channel is already closed
                });
            return true;
        }
        else
        {
            return channel.initiateClose();
        }
    }

    public CompletableFuture<ServerSocketBinding> bindServerSocketAsync(InetSocketAddress localAddress, TransportChannelHandler handler)
    {
        final ServerSocketBindingImpl binding = new ServerSocketBindingImpl(
                localAddress,
                handler,
                this,
                channelLifecycle);

        return commandContext.runAsync((future) ->
        {
            try
            {
                binding.doBind();
                acceptTransportPoller.addServerSocketBinding(binding);
                future.complete(binding);
                serverSocketBindings.add(binding);
            }
            catch (Exception e)
            {
                future.completeExceptionally(e);
            }
        });
    }

    public CompletableFuture<ServerSocketBinding> closeServerSocketAsync(ServerSocketBindingImpl binding)
    {
        return commandContext.runAsync((future) ->
        {
            // 1. stop accepting new channels
            acceptTransportPoller.removeServerSocketBinding(binding);

            // 2. close open channels
            binding.closeAllChannelsAsync()
                // 3. close server binding
                .whenComplete((v, t) -> binding.closeMedia())
                .whenComplete((v, t) ->
                {
                    serverSocketBindings.remove(binding);
                    future.complete(binding);
                });
        });
    }

    public CompletableFuture<Void> closeAsync()
    {
        if (isOpen.compareAndSet(true, false))
        {
            return commandContext.runAsync((future) ->
            {
                acceptTransportPoller.close();
                connectTransportPoller.close();

                final List<ChannelManagerImpl> channelManagers = new ArrayList<>(managers);
                final List<ServerSocketBinding> serverBindings = new ArrayList<>(serverSocketBindings);

                final List<CompletableFuture<Void>> clientCloseFutures = new ArrayList<>();
                for (int i = 0; i < channelManagers.size(); i++)
                {
                    clientCloseFutures.add(channelManagers.get(i).closeAllChannelsAsync());
                }

                LangUtil.allOf(clientCloseFutures)
                    .whenComplete((v, t) ->
                    {
                        final List<CompletableFuture<ServerSocketBinding>> serverCloseFutures = new ArrayList<>();
                        for (int i = 0; i < serverBindings.size(); i++)
                        {
                            serverCloseFutures.add(serverBindings.get(i).closeAsync());
                        }

                        LangUtil.allOf(serverCloseFutures).whenComplete((v1, t1) ->
                        {
                            future.complete(null);
                        });

                        receiver.closeSelectors();
                    });
            });
        }
        else
        {
            return CompletableFuture.completedFuture(null);
        }
    }

    public boolean isOpen()
    {
        return isOpen.get();
    }

    @Override
    public String name()
    {
        return "transport-conductor";
    }

    @Override
    public int getPriority(long now)
    {
        return PRIORITY_LOW;
    }

}

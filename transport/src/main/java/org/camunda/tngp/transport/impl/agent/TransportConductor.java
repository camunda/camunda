package org.camunda.tngp.transport.impl.agent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import org.camunda.tngp.transport.TransportChannel;
import org.camunda.tngp.transport.ClientChannel;
import org.camunda.tngp.transport.ServerSocketBinding;
import org.camunda.tngp.transport.Transport;
import org.camunda.tngp.transport.impl.TransportChannelImpl;
import org.camunda.tngp.transport.impl.ClientChannelImpl;
import org.camunda.tngp.transport.impl.ServerChannelImpl;
import org.camunda.tngp.transport.impl.ServerSocketBindingImpl;
import org.camunda.tngp.transport.impl.TransportContext;
import org.camunda.tngp.transport.impl.media.AcceptTransportPoller;
import org.camunda.tngp.transport.impl.media.ConnectTransportPoller;

import uk.co.real_logic.agrona.concurrent.Agent;
import uk.co.real_logic.agrona.concurrent.AgentRunner;
import uk.co.real_logic.agrona.concurrent.ManyToOneConcurrentArrayQueue;

public class TransportConductor implements Agent, Consumer<TransportConductorCmd>
{
    protected final ManyToOneConcurrentArrayQueue<TransportConductorCmd> cmdQueue;
    protected final ManyToOneConcurrentArrayQueue<SenderCmd> toSenderCmdQueue;
    protected final ManyToOneConcurrentArrayQueue<ReceiverCmd> toReceiverCmdQueue;

    protected final ConnectTransportPoller connectTransportPoller;
    protected final AcceptTransportPoller acceptTransportPoller;
    protected final TransportContext context;

    protected final List<ClientChannelImpl> clientChannels = new ArrayList<>();
    protected final List<ServerSocketBinding> serverSocketBindings = new ArrayList<>();

    protected final Map<ClientChannelImpl, CompletableFuture<TransportChannel>> connectFutures = new HashMap<>();

    protected int lastChannelId = 0;

    protected boolean isClosing = false;

    public TransportConductor(TransportContext context)
    {
        this.context = context;
        cmdQueue = context.getConductorCmdQueue();
        toSenderCmdQueue = context.getSenderCmdQueue();
        toReceiverCmdQueue = context.getReceiverCmdQueue();

        connectTransportPoller = new ConnectTransportPoller(this);
        acceptTransportPoller = new AcceptTransportPoller(this);
    }

    @Override
    public int doWork() throws Exception
    {
        int workCount = 0;

        workCount += cmdQueue.drain(this);

        if(!isClosing)
        {
            workCount += connectTransportPoller.pollNow();
            workCount += acceptTransportPoller.pollNow();
        }

        return workCount;
    }

    @Override
    public String roleName()
    {
        return "transport-conductor";
    }

    @Override
    public void accept(TransportConductorCmd t)
    {
        t.execute(this);
    }

    public ManyToOneConcurrentArrayQueue<TransportConductorCmd> getCmdQueue()
    {
        return cmdQueue;
    }

    public void onClientChannelConnected(final ClientChannelImpl channel)
    {
        channel.setConnected();
        connectTransportPoller.removeChannel(channel);

        final CompletableFuture<TransportChannel> connectFuture = connectFutures.get(channel);

        if(!isClosing)
        {
            clientChannels.add(channel);

            CompletableFuture<Void> openFuture = openAndRegisterChannel(channel);

            openFuture.whenComplete((v,t) ->
            {
                notifyChannelHandlerOpen(channel);
                connectFuture.complete(channel);
            });
        }
        else
        {
            channel.closeForcibly(null);
            connectFuture.cancel(true);
        }
    }

    public void onServerChannelOpened(ServerChannelImpl serverChannel)
    {
        if(!isClosing)
        {
            openAndRegisterChannel(serverChannel)
            .whenComplete((c,t) ->
            {
                notifyChannelHandlerOpen(serverChannel);
            });
        }
        else
        {
            serverChannel.closeForcibly(null);
        }
    }

    public CompletableFuture<Void> openAndRegisterChannel(final TransportChannelImpl channel)
    {
        channel.setId(++lastChannelId);

        final CompletableFuture<Void> receiverFuture = new CompletableFuture<>();
        final CompletableFuture<Void> senderFuture = new CompletableFuture<>();

        toReceiverCmdQueue.add((r) ->
        {
            r.registerChannel(channel, receiverFuture);
        });

        toSenderCmdQueue.add((s) ->
        {
            s.registerChannel(channel, senderFuture);
        });

        return CompletableFuture.allOf(receiverFuture, senderFuture);
    }

    private void notifyChannelHandlerOpen(final TransportChannelImpl channel)
    {
        try
        {
            channel.getChannelHandler().onChannelOpened(channel);
        }
        catch(Exception e)
        {
            e.printStackTrace();
        }
    }

    protected void notifyChannelHandlerClosed(TransportChannelImpl channel)
    {
        try
        {
            channel.getChannelHandler().onChannelClosed(channel);
        }
        catch(Exception e)
        {
            e.printStackTrace();
        }
    }

    public void doConnectChannel(
            final ClientChannelImpl channel,
            final CompletableFuture<TransportChannel> future)
    {
        if(!isClosing)
        {
            try
            {
                channel.startConnect();
                connectTransportPoller.addChannel(channel);
                connectFutures.put(channel, future);
            }
            catch(Exception e)
            {
                future.completeExceptionally(e);
            }
        }
        else
        {
            future.completeExceptionally(new IllegalStateException("Transport is closing."));
        }
    }

    public void doBindServerSocket(
            final ServerSocketBindingImpl serverSocketBinding,
            final CompletableFuture<ServerSocketBinding> bindFuture)
    {
        if(!isClosing)
        {
            try
            {
                serverSocketBinding.doBind();
                acceptTransportPoller.addServerSocketBinding(serverSocketBinding);
                bindFuture.complete(serverSocketBinding);
                serverSocketBindings.add(serverSocketBinding);
            }
            catch(Exception e)
            {
                bindFuture.completeExceptionally(e);
            }
        }
        else
        {
            bindFuture.completeExceptionally(new IllegalStateException("Transport is closing"));
        }
    }

    protected void removeChannel(TransportChannelImpl channel)
    {
        if (channel instanceof ClientChannelImpl)
        {
            this.clientChannels.remove(channel);
        }
        else
        {
            final ServerChannelImpl serverChannelImpl = (ServerChannelImpl) channel;
            final ServerSocketBindingImpl serverSocketBinding = serverChannelImpl.getServerSocketBinding();
            serverSocketBinding.onChannelClosed(serverChannelImpl);
        }

        notifyChannelHandlerClosed(channel);
    }

    public void onChannelClosed(
            TransportChannelImpl channel,
            CompletableFuture<TransportChannel> closeFuture)
    {
        removeChannel(channel);

        if(closeFuture != null)
        {
            closeFuture.complete(channel);
        }
    }

    public void onChannelClosedExceptionally(
            TransportChannelImpl channel,
            CompletableFuture<TransportChannel> closeFuture,
            Exception e)
    {
        removeChannel(channel);

        if(closeFuture == null)
        {
            closeFuture = connectFutures.remove(channel);
        }

        if(closeFuture != null)
        {
            if(e == null)
            {
                e = new RuntimeException("Channel closed exceptionally.");
            }
            closeFuture.completeExceptionally(e);
        }
    }

    /**
     * perform an orderly close of a server socket binding
     */
    public void closeServerSocketBinding(
            final ServerSocketBindingImpl serverSocketBinding,
            final CompletableFuture<ServerSocketBinding> completableFuture)
    {
        // 1. stop accepting new channels
        acceptTransportPoller.removeServerSocketBinding(serverSocketBinding);

        CompletableFuture.runAsync(() ->
        {
            // 2. gracefully close all open server channels
            serverSocketBinding.closeAllChannels();
        })
        .thenRun(() ->
        {
            // 3. close the socket binding
            serverSocketBinding.closeMedia();
        })
        .whenComplete((v,t) ->
        {
            if(t != null)
            {
                t.printStackTrace();
            }
            serverSocketBindings.remove(serverSocketBinding);
            completableFuture.complete(serverSocketBinding);
        });
    }

    @SuppressWarnings("rawtypes")
    public void close(final Transport transport, final CompletableFuture<Transport> transportCloseFuture)
    {
        this.isClosing = true;

        final List<TransportChannelImpl> channels = new ArrayList<>(this.clientChannels);
        final ArrayList<ServerSocketBinding> serverSocketBindings = new ArrayList<>(this.serverSocketBindings);

        acceptTransportPoller.close();
        connectTransportPoller.close();

        new Thread("transport-close-thread")
        {
            @Override
            public void run()
            {
                try
                {
                    final CompletableFuture[] channelCloseFutures = new CompletableFuture[channels.size()];

                    for (int i = 0; i < channelCloseFutures.length; i++)
                    {
                        channelCloseFutures[i] = channels.get(i).closeAsync();
                    }
                    CompletableFuture.allOf(channelCloseFutures).join();
                }
                catch(Exception e)
                {
                    e.printStackTrace();
                }

                for (ServerSocketBinding serverSocketBinding : serverSocketBindings)
                {
                    try
                    {
                        serverSocketBinding.close();
                    }
                    catch(Exception e)
                    {
                        e.printStackTrace();
                    }
                }

                final AgentRunner[] agentRunners = context.getAgentRunners();
                if(agentRunners != null)
                {
                    for (AgentRunner agentRunner : agentRunners)
                    {
                        agentRunner.close();
                    }

                    transportCloseFuture.complete(transport);
                }
            }
        }
        .start();
    }

}

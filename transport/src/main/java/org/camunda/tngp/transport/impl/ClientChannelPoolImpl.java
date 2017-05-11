package org.camunda.tngp.transport.impl;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.ToIntFunction;

import org.agrona.concurrent.ManyToOneConcurrentArrayQueue;
import org.camunda.tngp.transport.ClientChannel;
import org.camunda.tngp.transport.ClientChannelPool;
import org.camunda.tngp.transport.PooledFuture;
import org.camunda.tngp.transport.SocketAddress;
import org.camunda.tngp.transport.TransportChannel;
import org.camunda.tngp.transport.impl.GrowablePool.PoolIterator;
import org.camunda.tngp.transport.spi.TransportChannelHandler;
import org.camunda.tngp.util.time.ClockUtil;

public class ClientChannelPoolImpl implements ClientChannelPool
{

    protected final ObjectPool<ChannelRequest> channelRequestPool;
    protected final ManyToOneConcurrentArrayQueue<ChannelRequest> pendingChannelRequests;
    protected final List<ChannelRequest> channelRequestsInProgress;

    protected final TransportContext transportContext;
    protected final TransportChannelHandler channelHandler;
    protected final GrowablePool<ClientChannelImpl> clientChannels;

    protected final long keepAlivePeriod;
    protected long lastKeepAlive = -1;

    public ClientChannelPoolImpl(
            int initialCapacity,
            int numConcurrentRequests,
            TransportContext transportContext,
            TransportChannelHandler channelHandler)
    {
        this.channelRequestPool = new ObjectPool<>(numConcurrentRequests, ChannelRequest::new);
        this.pendingChannelRequests = new ManyToOneConcurrentArrayQueue<>(numConcurrentRequests);
        this.channelRequestsInProgress = new ArrayList<>(numConcurrentRequests);
        this.channelHandler = channelHandler;
        this.transportContext = transportContext;
        this.keepAlivePeriod = transportContext.getChannelKeepAlivePeriod();

        final Predicate<ClientChannelImpl> removalFunction = c -> !(c.isConnecting() || c.isOpen());
        final ToIntFunction<PoolIterator<ClientChannelImpl>> evictionDecider = it ->
        {
            final long minimalLastUsedTime = Long.MAX_VALUE;
            int channelToEvict = -1;

            while (it.hasNext())
            {
                final ClientChannelImpl channel = it.next();
                if (!channel.isInUse() && channel.getLastUsed() < minimalLastUsedTime)
                {
                    channelToEvict = it.getCurrentElementIndex();
                }
            }

            return channelToEvict;
        };
        final Consumer<ClientChannelImpl> evictionHandler = c -> c.closeAsync();

        this.clientChannels = new GrowablePool<>(
                ClientChannelImpl.class,
                initialCapacity,
                removalFunction,
                evictionDecider,
                evictionHandler);
    }

    @Override
    public PooledFuture<ClientChannel> requestChannelAsync(SocketAddress remoteAddress)
    {
        final ChannelRequest channelRequest = scheduleChannelRequest(remoteAddress, null);

        if (channelRequest == null)
        {
            return null; // for backpressure
        }

        return channelRequest;
    }

    @Override
    public ClientChannel requestChannel(SocketAddress remoteAddress)
    {
        final CompletableFuture<ClientChannel> completionFuture = new CompletableFuture<>();
        final ChannelRequest channelRequest = scheduleChannelRequest(remoteAddress, completionFuture);

        if (channelRequest == null)
        {
            throw new RuntimeException("Could not schedule opening a channel; capacities are exhausted");
        }
        else
        {
            return completionFuture
                .whenComplete((c, t) -> channelRequest.release())
                .join();
        }
    }

    @Override
    public void returnChannel(ClientChannel channel)
    {
        ((ClientChannelImpl) channel).countUsageEnd();
    }

    protected ChannelRequest scheduleChannelRequest(SocketAddress remoteAddress, CompletableFuture<ClientChannel> completionFuture)
    {
        final ChannelRequest channelRequest = channelRequestPool.request();

        if (channelRequest == null)
        {
            return null; // for backpressure
        }

        channelRequest.init(remoteAddress, completionFuture);
        pendingChannelRequests.add(channelRequest);

        return channelRequest;
    }

    protected void handleChannelRequest(ChannelRequest request)
    {
        final SocketAddress remoteAddress = request.getRemoteAddress();
        final ClientChannelImpl channel = findExistingChannel(remoteAddress);

        if (channel != null)
        {
            request.resolve(channel);
        }
        else
        {
            channelRequestsInProgress.add(request);
            openNewChannel(remoteAddress)
                // the returned future is completed in the context of the conductor
                // agent, so we should be fine with accessing internal state
                // in the following callback
                .whenComplete((c, e) ->
                {
                    if (e == null)
                    {
                        resolveRequestsForAddress(remoteAddress, (ClientChannelImpl) c);
                    }
                    else
                    {
                        e.printStackTrace();
                        failRequestsForAddress(remoteAddress);
                    }
                });
        }
    }

    protected void failRequestsForAddress(SocketAddress address)
    {
        for (int i = 0; i < channelRequestsInProgress.size(); i++)
        {
            final ChannelRequest request = channelRequestsInProgress.get(i);
            if (request.getRemoteAddress().equals(address))
            {
                request.fail();
                channelRequestsInProgress.remove(i);
            }
        }
    }

    protected void resolveRequestsForAddress(SocketAddress address, ClientChannelImpl channel)
    {
        for (int i = 0; i < channelRequestsInProgress.size(); i++)
        {
            final ChannelRequest request = channelRequestsInProgress.get(i);
            if (request.getRemoteAddress().equals(address))
            {
                request.resolve(channel);
                channelRequestsInProgress.remove(i);
            }
        }
    }

    protected ClientChannelImpl findExistingChannel(SocketAddress remoteAddress)
    {
        final Iterator<ClientChannelImpl> it = clientChannels.iterator();

        while (it.hasNext())
        {
            final ClientChannelImpl channel = it.next();

            if (channel.getRemoteAddress().equals(remoteAddress))
            {
                return channel;
            }
        }

        return null;
    }

    protected CompletableFuture<TransportChannel> openNewChannel(SocketAddress remoteAddress)
    {
        final CompletableFuture<TransportChannel> completionFuture = new CompletableFuture<>();
        final ClientChannelImpl channel = new ClientChannelImpl(transportContext, channelHandler, remoteAddress);
        clientChannels.add(channel);

        transportContext.getConductorCmdQueue().add((c) ->
        {
            c.doConnectChannel(channel, completionFuture);
        });

        return completionFuture;
    }

    public int doWork()
    {
        int workCount = pendingChannelRequests.drain(this::handleChannelRequest);
        workCount += scheduleKeepAliveMessages();
        return workCount;
    }

    protected int scheduleKeepAliveMessages()
    {
        final long now = ClockUtil.getCurrentTimeInMillis();
        if (now < lastKeepAlive + keepAlivePeriod)
        {
            return 0;
        }
        else
        {
            lastKeepAlive = now;
            final Iterator<ClientChannelImpl> it = clientChannels.iterator();
            int keepAlivesSent = 0;

            while (it.hasNext())
            {
                final ClientChannel clientChannel = it.next();

                if (clientChannel.isOpen())
                {
                    clientChannel.scheduleControlFrame(StaticControlFrames.KEEP_ALIVE_FRAME);
                    keepAlivesSent++;
                }
            }

            return keepAlivesSent;
        }
    }

    public CompletableFuture<Void> closeAsync()
    {
        final List<CompletableFuture> futures = new ArrayList<>();
        final Iterator<ClientChannelImpl> it = clientChannels.iterator();

        while (it.hasNext())
        {
            futures.add(it.next().closeAsync());
        }

        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[futures.size()]));
    }

    public void onChannelRemove(ClientChannelImpl channel)
    {
        clientChannels.remove(channel);
    }

}

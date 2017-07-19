/*
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.zeebe.transport.impl.actor;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

import io.zeebe.transport.TransportListener;
import io.zeebe.transport.impl.TransportChannelFactory;
import io.zeebe.transport.impl.ClientRequestPool;
import io.zeebe.transport.impl.RemoteAddressList;
import io.zeebe.transport.impl.TransportChannel;
import io.zeebe.transport.impl.TransportChannel.ChannelLifecycleListener;
import io.zeebe.transport.impl.TransportContext;
import io.zeebe.util.DeferredCommandContext;
import io.zeebe.util.actor.Actor;

public class Conductor implements Actor, ChannelLifecycleListener
{
    protected final DeferredCommandContext deferred = new DeferredCommandContext();
    protected final RemoteAddressList remoteAddressList;
    protected final TransportContext transportContext;

    private final List<TransportListener> transportListeners = new ArrayList<>();
    private final List<TransportChannel> transportChannels = new ArrayList<>();
    private final ActorContext actorContext;
    protected final AtomicBoolean closing = new AtomicBoolean(false);
    protected final TransportChannelFactory channelFactory;

    public Conductor(ActorContext actorContext, TransportContext context)
    {
        this.actorContext = actorContext;
        this.transportContext = context;
        this.remoteAddressList = context.getRemoteAddressList();
        this.channelFactory = context.getChannelFactory();

        actorContext.setConductor(this);
    }

    @Override
    public int getPriority(long now)
    {
        return PRIORITY_LOW;
    }

    @Override
    public int doWork() throws Exception
    {
        int workCount = 0;

        workCount += deferred.doWork();

        return workCount;
    }

    public CompletableFuture<Void> registerListener(TransportListener channelListener)
    {
        return deferred.runAsync((future) ->
        {
            transportListeners.add(channelListener);
            future.complete(null);
        });
    }

    public void removeListener(TransportListener channelListener)
    {
        deferred.runAsync(() ->
        {
            transportListeners.remove(channelListener);
        });
    }

    // channel lifecycle

    @Override
    public void onChannelConnected(TransportChannel ch)
    {
        deferred.runAsync(() ->
        {
            transportChannels.add(ch);
            actorContext.registerChannel(ch);

            transportListeners.forEach(l ->
            {
                try
                {
                    l.onConnectionEstablished(ch.getRemoteAddress());
                }
                catch (Exception e)
                {
                    e.printStackTrace();
                }
            });
        });
    }

    public CompletableFuture<Void> interruptAllChannels()
    {
        return deferred.runAsync((future) ->
        {
            for (int i = 0; i < transportChannels.size(); i++)
            {
                final TransportChannel channel = transportChannels.get(i);
                channel.shutdownInput();
            }

            future.complete(null);
        });
    }

    @Override
    public void onChannelDisconnected(TransportChannel ch)
    {

        deferred.runAsync(() ->
        {
            transportChannels.remove(ch);
            failRequestsOnChannel(ch, "Socket channel has been disconnected");
            actorContext.removeChannel(ch);

            transportListeners.forEach(l ->
            {
                try
                {
                    l.onConnectionClosed(ch.getRemoteAddress());
                }
                catch (Exception e)
                {
                    e.printStackTrace();
                }
            });
        });
    }

    protected void failRequestsOnChannel(TransportChannel ch, String reason)
    {
        final ClientRequestPool clientRequestPool = transportContext.getClientRequestPool();
        if (clientRequestPool != null)
        {
            clientRequestPool.failPendingRequestsToRemote(ch.getRemoteAddress(), reason);
        }
    }

    public CompletableFuture<Void> onClose()
    {
        if (closing.compareAndSet(false, true))
        {
            return closeCurrentChannels();
        }
        else
        {
            return CompletableFuture.completedFuture(null);
        }
    }

    public CompletableFuture<Void> closeCurrentChannels()
    {
        return deferred.runAsync((f) ->
        {
            final ArrayList<TransportChannel> listCopy = new ArrayList<>(transportChannels);

            for (TransportChannel transportChannel : listCopy)
            {
                transportChannel.close();
            }

            f.complete(null);
        });
    }
}

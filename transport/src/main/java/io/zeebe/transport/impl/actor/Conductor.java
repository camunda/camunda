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

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

import io.zeebe.transport.Loggers;
import io.zeebe.transport.TransportListener;
import io.zeebe.transport.impl.*;
import io.zeebe.transport.impl.TransportChannel.ChannelLifecycleListener;
import io.zeebe.util.sched.ZbActor;
import io.zeebe.util.sched.future.ActorFuture;
import org.slf4j.Logger;

public abstract class Conductor extends ZbActor implements ChannelLifecycleListener
{
    private static final Logger LOG = Loggers.TRANSPORT_LOGGER;

    protected final RemoteAddressListImpl remoteAddressList;
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

    public ActorFuture<Void> registerListener(TransportListener channelListener)
    {
        return actor.call(() ->
        {
            transportListeners.add(channelListener);
        });
    }

    public void removeListener(TransportListener channelListener)
    {
        actor.call(() ->
        {
            transportListeners.remove(channelListener);
        });
    }

    // channel lifecycle

    @Override
    public void onChannelConnected(TransportChannel ch)
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
                LOG.debug("Failed to call transport listener {} on channel connect", l, e);
            }
        });
    }

    public ActorFuture<Void> interruptAllChannels()
    {
        return actor.call(() ->
        {
            for (int i = 0; i < transportChannels.size(); i++)
            {
                final TransportChannel channel = transportChannels.get(i);
                channel.shutdownInput();
            }
        });
    }

    @Override
    public void onChannelClosed(TransportChannel ch, boolean wasConnected)
    {
        actor.run(() ->
        {
            if (wasConnected)
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
                        LOG.debug("Failed to call transport listener {} on disconnect", l, e);
                    }
                });
            }
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

    @Override
    protected void onActorClosing()
    {
        remoteAddressList.deactivateAll();

        final ArrayList<TransportChannel> listCopy = new ArrayList<>(transportChannels);

        for (TransportChannel transportChannel : listCopy)
        {
            transportChannel.close();
        }

        final ActorFuture<Void> senderClose = actorContext.closeSender();
        final ActorFuture<Void> receiverClose = actorContext.closeReceiver();

        actor.awaitAll(Arrays.asList(senderClose, receiverClose), (t) ->
        {
            onSenderAndReceiverClosed();
        });
    }

    protected void onSenderAndReceiverClosed()
    {
        // empty
    }

    public ActorFuture<Void> close()
    {
        return actor.close();
    }

    public ActorFuture<Void> closeCurrentChannels()
    {
        return actor.call(() ->
        {
            final ArrayList<TransportChannel> listCopy = new ArrayList<>(transportChannels);

            for (TransportChannel transportChannel : listCopy)
            {
                transportChannel.close();
            }
        });
    }
}

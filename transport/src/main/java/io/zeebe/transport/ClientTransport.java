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
package io.zeebe.transport;

import java.time.Duration;

import io.zeebe.dispatcher.Dispatcher;
import io.zeebe.transport.impl.TransportContext;
import io.zeebe.transport.impl.actor.ActorContext;
import io.zeebe.util.sched.future.ActorFuture;

public class ClientTransport implements AutoCloseable
{
    private final ClientOutput output;
    private final RemoteAddressList remoteAddressList;
    private final ActorContext transportActorContext;
    private final Dispatcher receiveBuffer;
    private final TransportContext transportContext;

    public ClientTransport(ActorContext transportActorContext, TransportContext transportContext)
    {
        this.transportActorContext = transportActorContext;
        this.transportContext = transportContext;
        this.output = transportContext.getClientOutput();
        this.remoteAddressList = transportContext.getRemoteAddressList();
        this.receiveBuffer = transportContext.getReceiveBuffer();
    }

    /**
     * @return interface to stage outbound data
     */
    public ClientOutput getOutput()
    {
        return output;
    }

    /**
     * Resolve a socket address as a remote to which data can be sent. The return value identifies
     * the remote and remains stable throughout the lifetime of this {@link ClientTransport} object, i.e.
     * can be cached. Transport will make sure to keep an open channel to this remote until the address
     * is deactivated or retired.
     */
    public RemoteAddress registerRemoteAddress(SocketAddress addr)
    {
        return remoteAddressList.register(addr);
    }

    /**
     * Signals that the remote is no longer in use for the time being. A transport channel will no longer
     * be managed. A remote address is reactivated when the endpoint is registered again.
     */
    public void deactivateRemoteAddress(RemoteAddress remote)
    {
        remoteAddressList.deactivate(remote);
    }

    /**
     * Signals that the remote is no longer used and that the stream should not be reused on reactivation. That means,
     * when the endpoint is registered again, it is assigned a different stream id (=> a new remote address is returned).
     * @param remote
     */
    public void retireRemoteAddress(RemoteAddress remote)
    {
        remoteAddressList.retire(remote);
    }

    /**
     * <p>DO NOT USE in production code as it involves blocking the current thread.
     *
     * <p>Not thread-safe
     *
     * <p>Like {@link #registerRemoteAddress(SocketAddress)} but blockingly waits for the corresponding channel
     * to be opened such that it is probable that subsequent requests/messages can be sent. This saves test code
     * the need to retry sending.
     */
    public RemoteAddress registerRemoteAndAwaitChannel(SocketAddress addr)
    {
        final RemoteAddress remoteAddress = getRemoteAddress(addr);

        if (remoteAddress != null)
        {
            // already registered; assuming a channel is open then
            return remoteAddress;
        }
        else
        {
            final Object monitor = new Object();

            final TransportListener listener = new TransportListener()
            {
                @Override
                public void onConnectionEstablished(RemoteAddress remoteAddress)
                {
                    synchronized (monitor)
                    {
                        if (remoteAddress.getAddress().equals(addr))
                        {
                            monitor.notifyAll();
                            removeChannelListener(this);
                        }
                    }
                }

                @Override
                public void onConnectionClosed(RemoteAddress remoteAddress)
                {
                }
            };

            transportActorContext.registerListener(listener).join();

            synchronized (monitor)
            {
                final RemoteAddress registeredAddress = registerRemoteAddress(addr);
                try
                {
                    monitor.wait(Duration.ofSeconds(10).toMillis());
                }
                catch (InterruptedException e)
                {
                    throw new RuntimeException(e);
                }

                return registeredAddress;
            }

        }
    }

    public RemoteAddress getRemoteAddress(SocketAddress addr)
    {
        return remoteAddressList.getByAddress(addr);
    }

    public RemoteAddress getRemoteAddress(int streamId)
    {
        return remoteAddressList.getByStreamId(streamId);
    }

    /**
     * Creates a subscription on the receive buffer for single messages.
     *
     * @throws RuntimeException if this client was not created with a receive buffer for single-messages
     */
    public ActorFuture<ClientInputMessageSubscription> openSubscription(String subscriptionName, ClientMessageHandler messageHandler)
    {
        if (receiveBuffer == null)
        {
            throw new RuntimeException("Cannot throw exception. No receive buffer in use");
        }

        return transportActorContext
                .getClientConductor()
                .openClientInputMessageSubscription(subscriptionName, messageHandler, output, remoteAddressList);
    }

    /**
     * Registers a listener with callbacks for whenever a connection to a remote gets established or closed.
     */
    public ActorFuture<Void> registerChannelListener(TransportListener channelListener)
    {
        return transportActorContext.registerListener(channelListener);
    }

    public void removeChannelListener(TransportListener listener)
    {
        transportActorContext.removeListener(listener);
    }

    public ActorFuture<Void> closeAsync()
    {
        return transportActorContext.onClose();
    }

    @Override
    public void close()
    {
        closeAsync().join();
    }

    public void interruptAllChannels()
    {
        transportActorContext.interruptAllChannels();
    }

    public ActorFuture<Void> closeAllChannels()
    {
        return transportActorContext.closeAllOpenChannels();
    }

    public Duration getChannelKeepAlivePeriod()
    {
        return transportContext.getChannelKeepAlivePeriod();
    }
}

/**
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

import java.util.Arrays;
import java.util.concurrent.CompletableFuture;

import io.zeebe.dispatcher.Dispatcher;
import io.zeebe.dispatcher.Subscription;
import io.zeebe.transport.impl.ClientRequestPool;
import io.zeebe.transport.impl.RemoteAddressList;
import io.zeebe.transport.impl.TransportContext;
import io.zeebe.transport.impl.actor.ActorContext;

public class ClientTransport implements AutoCloseable
{
    private final ClientOutput output;
    private final ClientRequestPool requestPool;
    private final RemoteAddressList remoteAddressList;
    private final ActorContext transportActorContext;
    private final Dispatcher receiveBuffer;
    private final TransportContext transportContext;

    public ClientTransport(ActorContext transportActorContext, TransportContext transportContext)
    {
        this.transportActorContext = transportActorContext;
        this.transportContext = transportContext;
        this.output = transportContext.getClientOutput();
        this.requestPool = transportContext.getClientRequestPool();
        this.remoteAddressList = transportContext.getRemoteAddressList();
        this.receiveBuffer = transportContext.getReceiveBuffer();
    }

    public ClientOutput getOutput()
    {
        return output;
    }

    public RemoteAddress registerRemoteAddress(SocketAddress addr)
    {
        return remoteAddressList.register(addr);
    }

    public RemoteAddress getRemoteAddress(SocketAddress addr)
    {
        return remoteAddressList.getByAddress(addr);
    }

    public RemoteAddress getRemoteAddress(int streamId)
    {
        return remoteAddressList.getByStreamId(streamId);
    }

    public CompletableFuture<Subscription> openSubscription(String subscriptionName)
    {
        if (receiveBuffer == null)
        {
            throw new RuntimeException("Cannot throw exception. No receive buffer in use");
        }

        return receiveBuffer.openSubscriptionAsync(subscriptionName);
    }

    public void registerChannelListener(TransportListener channelListener)
    {
        transportActorContext.registerListener(channelListener);
    }

    public void removeChannelListener(TransportListener listener)
    {
        transportActorContext.removeListener(listener);
    }

    public CompletableFuture<Void> closeAsync()
    {
        return transportActorContext.onClose()
            .whenComplete((v, t) ->
            {
                requestPool.close();

                Arrays.asList(transportContext.getActorReferences())
                    .forEach(r -> r.close());
            });
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

    public CompletableFuture<Void> closeAllChannels()
    {
        return transportActorContext.closeAllOpenChannels();
    }
}

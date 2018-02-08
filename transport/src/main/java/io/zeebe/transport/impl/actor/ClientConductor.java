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

import io.zeebe.dispatcher.Subscription;
import io.zeebe.transport.*;
import io.zeebe.transport.impl.*;
import io.zeebe.transport.impl.selector.ConnectTransportPoller;
import io.zeebe.util.sched.future.ActorFuture;
import io.zeebe.util.sched.future.CompletableActorFuture;

public class ClientConductor extends Conductor
{
    private final ConnectTransportPoller connectTransportPoller;
    private final ClientChannelManager channelManager;

    public ClientConductor(ActorContext actorContext, TransportContext context)
    {
        super(actorContext, context);

        this.connectTransportPoller = new ConnectTransportPoller();
        this.channelManager = new ClientChannelManager(this);

        remoteAddressList.setOnAddressAddedConsumer(this::onRemoteAddressAdded);
    }

    @Override
    protected void onActorStarted()
    {
        super.onActorStarted();
        actor.pollBlocking(connectTransportPoller::pollBlocking, connectTransportPoller::processKeys);
    }

    @Override
    protected void onActorClosing()
    {
        connectTransportPoller.close();
        super.onActorClosing();
    }

    public TransportChannel openChannel(RemoteAddressImpl address)
    {
        final TransportChannel channel =
            channelFactory.buildClientChannel(
                this,
                address,
                transportContext.getMessageMaxLength(),
                transportContext.getReceiveHandler());

        if (channel.beginConnect())
        {
            connectTransportPoller.addChannel(channel);
            return channel;
        }
        else
        {
            return null;
        }

    }

    @Override
    public void onChannelDisconnected(TransportChannel ch)
    {
        actor.run(() ->
        {
            channelManager.onChannelClosed(ch);
        });

        super.onChannelDisconnected(ch);
    }

    private void onRemoteAddressAdded(RemoteAddressImpl remoteAddress)
    {
        actor.call(() ->
        {
            channelManager.onRemoteAddressAdded(remoteAddress);
        });
    }

    public ActorFuture<ClientInputMessageSubscription> openClientInputMessageSubscription(String subscriptionName,
            ClientMessageHandler messageHandler,
            ClientOutput output,
            RemoteAddressList remoteAddressList)
    {
        final CompletableActorFuture<ClientInputMessageSubscription> future = new CompletableActorFuture<>();

        actor.call(() ->
        {
            final ActorFuture<Subscription> subscriptionFuture = transportContext.getReceiveBuffer().openSubscriptionAsync(subscriptionName);

            actor.await(subscriptionFuture, (s, t) ->
            {
                if (t != null)
                {
                    future.completeExceptionally(t);
                }
                else
                {
                    future.complete(new ClientInputMessageSubscriptionImpl(s, messageHandler, output, remoteAddressList));
                }
            });
        });

        return future;
    }
}

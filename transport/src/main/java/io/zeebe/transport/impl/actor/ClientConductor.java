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

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import io.zeebe.dispatcher.Subscription;
import io.zeebe.transport.ClientInputMessageSubscription;
import io.zeebe.transport.ClientMessageHandler;
import io.zeebe.transport.ClientOutput;
import io.zeebe.transport.Loggers;
import io.zeebe.transport.RemoteAddressList;
import io.zeebe.transport.impl.ClientInputMessageSubscriptionImpl;
import io.zeebe.transport.impl.ClientRequestRetryController;
import io.zeebe.transport.impl.RemoteAddressImpl;
import io.zeebe.transport.impl.TransportChannel;
import io.zeebe.transport.impl.TransportContext;
import io.zeebe.transport.impl.selector.ConnectTransportPoller;
import io.zeebe.util.sched.future.ActorFuture;
import io.zeebe.util.sched.future.CompletableActorFuture;

public class ClientConductor extends Conductor
{
    private final ConnectTransportPoller connectTransportPoller;
    private final Set<ClientRequestRetryController> activeManagedRequests = new HashSet<>();

    public ClientConductor(ActorContext actorContext, TransportContext context)
    {
        super(actorContext, context);
        connectTransportPoller = new ConnectTransportPoller();
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

        // do not close request pool here
        // the conductor does not own the requests and it is perfectly fine
        // to leave requests open => we could fail the requests eventually, but nothing more
//        transportContext.getClientRequestPool().close();

        final List<ActorFuture<Void>> controllerFutures = new ArrayList<>();
        Loggers.TRANSPORT_LOGGER.debug("Closing conductor");
        activeManagedRequests.forEach(c -> controllerFutures.add(c.close()));

        if (!controllerFutures.isEmpty())
        {
            actor.runOnCompletion(controllerFutures, (t) ->
            {
                Loggers.TRANSPORT_LOGGER.debug("All controllers closed");
                super.onActorClosing();
            });
        }
        else
        {
            super.onActorClosing();
        }
    }

    public void onManagedRequestStarted(ClientRequestRetryController requestController)
    {
        actor.call(() ->
        {
            this.activeManagedRequests.add(requestController);
        });
    }

    public void onManagedRequestFinished(ClientRequestRetryController requestController)
    {
        actor.call(() ->
        {
            this.activeManagedRequests.remove(requestController);
        });
    }

    public void openChannel(RemoteAddressImpl address, int connectAttempt)
    {
        final TransportChannel channel =
            channelFactory.buildClientChannel(
                this,
                address,
                transportContext.getMessageMaxLength(),
                transportContext.getReceiveHandler());


        if (channel.beginConnect(connectAttempt))
        {
            // backoff connecton attempts
            actor.runDelayed(Duration.ofMillis(Math.min(1000, 50 * connectAttempt)), () ->
            {
                connectTransportPoller.addChannel(channel);
            });

            channels.put(address.getStreamId(), channel);
        }
    }

    @Override
    public void onChannelClosed(TransportChannel channel, boolean wasConnected)
    {
        actor.run(() ->
        {
            final RemoteAddressImpl remoteAddress = channel.getRemoteAddress();

            if (remoteAddress.isActive())
            {
                final int openAttempt = channel.getOpenAttempt() + 1;
                openChannel(remoteAddress, openAttempt);
            }

            super.onChannelClosed(channel, wasConnected);
        });
    }

    private void onRemoteAddressAdded(RemoteAddressImpl remoteAddress)
    {
        actor.call(() ->
        {
            final TransportChannel channel = channels.get(remoteAddress.getStreamId());

            if (channel == null)
            {
                openChannel(remoteAddress, 0);
            }
            else
            {
                if (channel.isClosed())
                {
                    openChannel(remoteAddress, 0);
                }
            }
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

            actor.runOnCompletion(subscriptionFuture, (s, t) ->
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

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

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.SocketChannel;

import io.zeebe.transport.*;
import io.zeebe.transport.impl.*;
import io.zeebe.transport.impl.selector.AcceptTransportPoller;
import io.zeebe.util.sched.future.ActorFuture;
import io.zeebe.util.sched.future.CompletableActorFuture;

public class ServerConductor extends Conductor
{
    private final AcceptTransportPoller acceptTransportPoller;
    private ServerSocketBinding serverSocketBinding;

    public ServerConductor(ServerActorContext actorContext, TransportContext context)
    {
        super(actorContext, context);
        this.serverSocketBinding = context.getServerSocketBinding();
        this.acceptTransportPoller = new AcceptTransportPoller(this);
        this.acceptTransportPoller.addServerSocketBinding(serverSocketBinding);
    }

    @Override
    protected void onActorStarted()
    {
        super.onActorStarted();

        actor.pollBlocking(acceptTransportPoller::pollBlocking, acceptTransportPoller::processKeys);
    }

    @Override
    protected void onActorClosing()
    {
        acceptTransportPoller.close();
        super.onActorClosing();
    }

    @Override
    protected void onSenderAndReceiverClosed()
    {
        serverSocketBinding.close();
    }

    public void onServerChannelOpened(SocketChannel serverChannel)
    {
        SocketAddress socketAddress = null;

        try
        {
            socketAddress = new SocketAddress((InetSocketAddress) serverChannel.getRemoteAddress());
        }
        catch (IOException e)
        {
            try
            {
                serverChannel.close();
            }
            catch (IOException e1)
            {
                return;
            }
        }

        RemoteAddressImpl remoteAddress = remoteAddressList.getByAddress(socketAddress);

        if (remoteAddress != null)
        {
            // make sure to generate a new stream id
            remoteAddressList.retire(remoteAddress);
        }

        remoteAddress = remoteAddressList.register(socketAddress);

        final TransportChannel ch = channelFactory.buildServerChannel(
            this,
            remoteAddress,
            transportContext.getMessageMaxLength(),
            transportContext.getReceiveHandler(),
            serverChannel);

        onChannelConnected(ch);
    }

    public ActorFuture<ServerInputSubscription> openInputSubscription(String subscriptionName,
            ServerOutput output,
            RemoteAddressListImpl remoteAddressList,
            ServerMessageHandler messageHandler,
            ServerRequestHandler requestHandler)
    {
        final CompletableActorFuture<ServerInputSubscription> future = new CompletableActorFuture<>();

        actor.call(() ->
        {
            actor.await(transportContext.getReceiveBuffer().openSubscriptionAsync(subscriptionName), (s, t) ->
            {
                if (t == null)
                {
                    future.complete(new ServerInputSubscriptionImpl(output, s, remoteAddressList, messageHandler, requestHandler));
                }
                else
                {
                    future.completeExceptionally(t);
                }
            });
        });

        return future;
    }

}

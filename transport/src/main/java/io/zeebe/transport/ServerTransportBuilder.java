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

import java.net.InetSocketAddress;
import java.util.Objects;

import io.zeebe.dispatcher.Dispatcher;
import io.zeebe.dispatcher.FragmentHandler;
import io.zeebe.transport.impl.ReceiveBufferHandler;
import io.zeebe.transport.impl.RemoteAddressList;
import io.zeebe.transport.impl.ServerOutputImpl;
import io.zeebe.transport.impl.ServerReceiveHandler;
import io.zeebe.transport.impl.ServerSocketBinding;
import io.zeebe.transport.impl.TransportContext;
import io.zeebe.transport.impl.actor.Receiver;
import io.zeebe.transport.impl.actor.Sender;
import io.zeebe.transport.impl.actor.ServerActorContext;
import io.zeebe.transport.impl.actor.ServerConductor;
import io.zeebe.util.actor.ActorScheduler;

public class ServerTransportBuilder
{
    private int messageMaxLength = 1024 * 512;

    private Dispatcher sendBuffer;
    private ServerOutput output;
    private ActorScheduler scheduler;
    private InetSocketAddress bindAddress;
    protected FragmentHandler receiveHandler;
    protected RemoteAddressList remoteAddressList;

    public ServerTransportBuilder bindAddress(InetSocketAddress address)
    {
        this.bindAddress = address;
        return this;
    }

    public ServerTransportBuilder scheduler(ActorScheduler scheduler)
    {
        this.scheduler = scheduler;
        return this;
    }

    public ServerTransportBuilder sendBuffer(Dispatcher sendBuffer)
    {
        this.sendBuffer = sendBuffer;
        this.output = new ServerOutputImpl(sendBuffer);
        return this;
    }

    public ServerTransportBuilder messageMaxLength(int messageMaxLength)
    {
        this.messageMaxLength = messageMaxLength;
        return this;
    }

    protected ServerTransportBuilder receiveHandler(FragmentHandler receiveHandler)
    {
        this.receiveHandler = receiveHandler;
        return this;
    }

    public ServerTransport build(ServerMessageHandler messageHandler, ServerRequestHandler requestHandler)
    {
        remoteAddressList = new RemoteAddressList();

        receiveHandler(new ServerReceiveHandler(output, remoteAddressList, messageHandler, requestHandler));

        validate();

        final TransportContext context = buildTransportContext();
        final ServerActorContext actorContext = new ServerActorContext();

        buildActors(context, actorContext);

        return new ServerTransport(actorContext, context);
    }

    public BufferingServerTransport buildBuffering(Dispatcher receiveBuffer)
    {
        remoteAddressList = new RemoteAddressList();
        receiveHandler(new ReceiveBufferHandler(receiveBuffer));

        validate();

        final TransportContext context = buildTransportContext();
        final ServerActorContext actorContext = new ServerActorContext();

        context.setReceiveBuffer(receiveBuffer);

        buildActors(context, actorContext);

        return new BufferingServerTransport(actorContext, context);
    }

    protected TransportContext buildTransportContext()
    {
        final ServerSocketBinding serverSocketBinding = new ServerSocketBinding(bindAddress);
        serverSocketBinding.doBind();

        final TransportContext context = new TransportContext();

        context.setServerOutput(output);
        context.setMessageMaxLength(messageMaxLength);
        context.setRemoteAddressList(remoteAddressList);
        context.setReceiveHandler(receiveHandler);
        context.setServerSocketBinding(serverSocketBinding);
        context.setSenderSubscription(sendBuffer.getSubscriptionByName("sender"));

        return context;
    }

    protected void buildActors(TransportContext context, ServerActorContext actorContext)
    {
        final ServerConductor conductor = new ServerConductor(actorContext, context);
        final Sender sender = new Sender(actorContext, context);
        final Receiver receiver = new Receiver(actorContext, context);

        context.setActorReferences(
                scheduler.schedule(conductor),
                scheduler.schedule(sender),
                scheduler.schedule(receiver));
    }

    protected void validate()
    {
        Objects.requireNonNull(scheduler, "Scheduler must be provided");
        Objects.requireNonNull(sendBuffer, "Send buffer must be provided");
        Objects.requireNonNull(bindAddress, "Bind Address must be provided");
        Objects.requireNonNull(receiveHandler, "Receive Handler must be defined");
    }


}

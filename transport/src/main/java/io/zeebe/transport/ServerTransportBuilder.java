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

import java.net.InetSocketAddress;
import java.util.Objects;

import io.zeebe.dispatcher.Dispatcher;
import io.zeebe.dispatcher.FragmentHandler;
import io.zeebe.transport.impl.*;
import io.zeebe.transport.impl.actor.*;
import io.zeebe.util.sched.ActorScheduler;

public class ServerTransportBuilder
{
    private int messageMaxLength = 1024 * 512;

    private Dispatcher sendBuffer;
    private ServerOutput output;
    private ActorScheduler scheduler;
    private InetSocketAddress bindAddress;
    protected FragmentHandler receiveHandler;
    protected RemoteAddressListImpl remoteAddressList;
    protected ServerControlMessageListener controlMessageListener;

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

    public ServerTransportBuilder controlMessageListener(ServerControlMessageListener controlMessageListener)
    {
        this.controlMessageListener = controlMessageListener;
        return this;
    }

    public ServerTransport build(ServerMessageHandler messageHandler, ServerRequestHandler requestHandler)
    {
        remoteAddressList = new RemoteAddressListImpl();

        receiveHandler(new ServerReceiveHandler(output, remoteAddressList, messageHandler, requestHandler, controlMessageListener));

        validate();

        final TransportContext context = buildTransportContext();
        final ServerActorContext actorContext = new ServerActorContext();
        actorContext.setMetricsManager(scheduler.getMetricsManager());

        buildActors(context, actorContext);

        return new ServerTransport(actorContext, context);
    }

    public BufferingServerTransport buildBuffering(Dispatcher receiveBuffer)
    {
        remoteAddressList = new RemoteAddressListImpl();
        receiveHandler(new ReceiveBufferHandler(receiveBuffer));

        validate();

        final TransportContext context = buildTransportContext();
        final ServerActorContext actorContext = new ServerActorContext();

        context.setReceiveBuffer(receiveBuffer);
        actorContext.setMetricsManager(scheduler.getMetricsManager());

        buildActors(context, actorContext);

        return new BufferingServerTransport(actorContext, context);
    }

    protected TransportContext buildTransportContext()
    {
        final ServerSocketBinding serverSocketBinding = new ServerSocketBinding(bindAddress);
        serverSocketBinding.doBind();

        final TransportContext context = new TransportContext();

        context.setName("server");
        context.setServerOutput(output);
        context.setMessageMaxLength(messageMaxLength);
        context.setRemoteAddressList(remoteAddressList);
        context.setReceiveHandler(receiveHandler);
        context.setServerSocketBinding(serverSocketBinding);
        context.setSendBuffer(sendBuffer);
        context.setChannelFactory(new DefaultChannelFactory(scheduler.getMetricsManager(), context.getName()));

        return context;
    }

    protected void buildActors(TransportContext context, ServerActorContext actorContext)
    {
        final ServerConductor conductor = new ServerConductor(actorContext, context);
        final Sender sender = new Sender(actorContext, context);
        final Receiver receiver = new Receiver(actorContext, context);

        scheduler.submitActor(conductor, true);
        scheduler.submitActor(sender, true);
        scheduler.submitActor(receiver, true);
    }

    protected void validate()
    {
        Objects.requireNonNull(scheduler, "Scheduler must be provided");
        Objects.requireNonNull(sendBuffer, "Send buffer must be provided");
        Objects.requireNonNull(bindAddress, "Bind Address must be provided");
        Objects.requireNonNull(receiveHandler, "Receive Handler must be defined");
    }


}

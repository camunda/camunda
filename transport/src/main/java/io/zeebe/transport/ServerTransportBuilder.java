/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.transport;

import io.zeebe.dispatcher.Dispatcher;
import io.zeebe.dispatcher.FragmentHandler;
import io.zeebe.transport.impl.DefaultChannelFactory;
import io.zeebe.transport.impl.ReceiveBufferHandler;
import io.zeebe.transport.impl.RemoteAddressListImpl;
import io.zeebe.transport.impl.ServerOutputImpl;
import io.zeebe.transport.impl.ServerReceiveHandler;
import io.zeebe.transport.impl.ServerSocketBinding;
import io.zeebe.transport.impl.TransportContext;
import io.zeebe.transport.impl.actor.Receiver;
import io.zeebe.transport.impl.actor.ServerActorContext;
import io.zeebe.transport.impl.actor.ServerConductor;
import io.zeebe.transport.impl.memory.NonBlockingMemoryPool;
import io.zeebe.transport.impl.memory.TransportMemoryPool;
import io.zeebe.transport.impl.sender.Sender;
import io.zeebe.util.ByteValue;
import io.zeebe.util.sched.ActorScheduler;
import java.net.InetSocketAddress;
import java.util.Objects;

public final class ServerTransportBuilder {

  protected FragmentHandler receiveHandler;
  protected RemoteAddressListImpl remoteAddressList;
  protected ServerControlMessageListener controlMessageListener;

  private String name = "server";
  private ServerOutput output;
  private ActorScheduler scheduler;
  private InetSocketAddress bindAddress;

  private int messageMaxLength = (int) ByteValue.ofKilobytes(512).toBytes();
  private TransportMemoryPool messageMemoryPool =
      new NonBlockingMemoryPool(ByteValue.ofMegabytes(4));

  public ServerTransportBuilder name(final String name) {
    this.name = name;
    return this;
  }

  public ServerTransportBuilder messageMemoryPool(final TransportMemoryPool messageMemoryPool) {
    this.messageMemoryPool = messageMemoryPool;
    return this;
  }

  public ServerTransportBuilder bindAddress(final InetSocketAddress address) {
    bindAddress = address;
    return this;
  }

  public ServerTransportBuilder scheduler(final ActorScheduler scheduler) {
    this.scheduler = scheduler;
    return this;
  }

  public ServerTransportBuilder messageMaxLength(final ByteValue messageMaxLength) {
    this.messageMaxLength = (int) messageMaxLength.toBytes();
    return this;
  }

  protected ServerTransportBuilder receiveHandler(final FragmentHandler receiveHandler) {
    this.receiveHandler = receiveHandler;
    return this;
  }

  public ServerTransportBuilder controlMessageListener(
      final ServerControlMessageListener controlMessageListener) {
    this.controlMessageListener = controlMessageListener;
    return this;
  }

  public ServerTransport build(
      final ServerMessageHandler messageHandler, final ServerRequestHandler requestHandler) {
    remoteAddressList = new RemoteAddressListImpl();

    final ServerActorContext actorContext = new ServerActorContext();

    final Sender sender = new Sender(actorContext, messageMemoryPool, null, null);

    output = new ServerOutputImpl(sender);

    receiveHandler(
        new ServerReceiveHandler(
            output, remoteAddressList, messageHandler, requestHandler, controlMessageListener));

    validate();

    final TransportContext context = buildTransportContext();

    buildActors(context, actorContext);

    return new ServerTransport(actorContext, context);
  }

  public BufferingServerTransport buildBuffering(final Dispatcher receiveBuffer) {
    remoteAddressList = new RemoteAddressListImpl();
    receiveHandler(new ReceiveBufferHandler(receiveBuffer));

    validate();

    final ServerActorContext actorContext = new ServerActorContext();

    final Sender sender = new Sender(actorContext, messageMemoryPool, null, null);

    output = new ServerOutputImpl(sender);

    final TransportContext context = buildTransportContext();

    context.setReceiveBuffer(receiveBuffer);

    buildActors(context, actorContext);

    return new BufferingServerTransport(actorContext, context);
  }

  protected TransportContext buildTransportContext() {
    final ServerSocketBinding serverSocketBinding = new ServerSocketBinding(bindAddress);
    serverSocketBinding.doBind();

    final TransportContext context = new TransportContext();

    context.setName(name);
    context.setServerOutput(output);
    context.setMessageMaxLength(messageMaxLength);
    context.setRemoteAddressList(remoteAddressList);
    context.setReceiveHandler(receiveHandler);
    context.setServerSocketBinding(serverSocketBinding);
    context.setChannelFactory(new DefaultChannelFactory());

    return context;
  }

  protected void buildActors(
      final TransportContext context, final ServerActorContext actorContext) {
    final ServerConductor conductor = new ServerConductor(actorContext, context);
    final Sender sender = actorContext.getSender();
    final Receiver receiver = new Receiver(actorContext, context);

    scheduler.submitActor(conductor);
    scheduler.submitActor(sender);
    scheduler.submitActor(receiver);
  }

  protected void validate() {
    Objects.requireNonNull(scheduler, "Scheduler must be provided");
    Objects.requireNonNull(bindAddress, "Bind Address must be provided");
    Objects.requireNonNull(receiveHandler, "Receive Handler must be defined");

    if (messageMaxLength > messageMemoryPool.capacity()) {
      throw new IllegalArgumentException(
          String.format(
              "Expected the message memory pool size to be greater than the max message length of %d, but was %d.",
              messageMaxLength, messageMemoryPool.capacity()));
    }
  }
}

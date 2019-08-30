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
import io.zeebe.transport.impl.ClientOutputImpl;
import io.zeebe.transport.impl.ClientReceiveHandler;
import io.zeebe.transport.impl.DefaultChannelFactory;
import io.zeebe.transport.impl.EndpointRegistryImpl;
import io.zeebe.transport.impl.RemoteAddressListImpl;
import io.zeebe.transport.impl.TransportChannelFactory;
import io.zeebe.transport.impl.TransportContext;
import io.zeebe.transport.impl.actor.ClientActorContext;
import io.zeebe.transport.impl.actor.ClientConductor;
import io.zeebe.transport.impl.actor.Receiver;
import io.zeebe.transport.impl.memory.NonBlockingMemoryPool;
import io.zeebe.transport.impl.memory.TransportMemoryPool;
import io.zeebe.transport.impl.sender.Sender;
import io.zeebe.util.ByteValue;
import io.zeebe.util.sched.ActorScheduler;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class ClientTransportBuilder {
  /** In the same order of magnitude of what apache and nginx use. */
  private static final Duration DEFAULT_CHANNEL_KEEP_ALIVE_PERIOD = Duration.ofSeconds(5);

  private final String name;
  private Duration keepAlivePeriod = DEFAULT_CHANNEL_KEEP_ALIVE_PERIOD;
  private Dispatcher receiveBuffer;
  private List<ClientInputListener> listeners;
  private TransportChannelFactory channelFactory;
  private Duration defaultRequestRetryTimeout = Duration.ofSeconds(15);
  private Duration defaultMessageRetryTimeout = Duration.ofSeconds(1);
  private ActorScheduler scheduler;

  private int messageMaxLength = (int) ByteValue.ofKilobytes(512).toBytes();
  private TransportMemoryPool requestMemoryPool =
      new NonBlockingMemoryPool(ByteValue.ofMegabytes(4));
  private TransportMemoryPool messageMemoryPool =
      new NonBlockingMemoryPool(ByteValue.ofMegabytes(4));

  public ClientTransportBuilder(final String name) {
    this.name = name;
  }

  public ClientTransportBuilder scheduler(final ActorScheduler scheduler) {
    this.scheduler = scheduler;
    return this;
  }

  /**
   * Optional. If set, all incoming messages (single-message protocol) are put onto the provided
   * buffer. {@link ClientTransport#openSubscription(String, ClientMessageHandler)} can be used to
   * consume from this buffer.
   */
  public ClientTransportBuilder messageReceiveBuffer(final Dispatcher receiveBuffer) {
    this.receiveBuffer = receiveBuffer;
    return this;
  }

  public ClientTransportBuilder requestMemoryPool(final TransportMemoryPool requestMemoryPool) {
    this.requestMemoryPool = requestMemoryPool;
    return this;
  }

  public ClientTransportBuilder messageMemoryPool(final TransportMemoryPool messageMemoryPool) {
    this.messageMemoryPool = messageMemoryPool;
    return this;
  }

  public ClientTransportBuilder inputListener(final ClientInputListener listener) {
    if (listeners == null) {
      listeners = new ArrayList<>();
    }
    listeners.add(listener);
    return this;
  }

  public ClientTransportBuilder messageMaxLength(final int messageMaxLength) {
    this.messageMaxLength = messageMaxLength;
    return this;
  }

  /** The period in which a dummy message is sent to keep the underlying TCP connection open. */
  public ClientTransportBuilder keepAlivePeriod(final Duration keepAlivePeriod) {
    if (keepAlivePeriod.getSeconds() < 1) {
      throw new RuntimeException("Min value for keepalive period is 1s.");
    }
    this.keepAlivePeriod = keepAlivePeriod;
    return this;
  }

  public ClientTransportBuilder channelFactory(final TransportChannelFactory channelFactory) {
    this.channelFactory = channelFactory;
    return this;
  }

  public ClientTransportBuilder defaultRequestRetryTimeout(final Duration duration) {
    defaultRequestRetryTimeout = duration;
    return this;
  }

  public ClientTransportBuilder defaultMessageRetryTimeout(final Duration duration) {
    defaultMessageRetryTimeout = duration;
    return this;
  }

  public ClientTransport build() {
    validate();

    final ClientActorContext actorContext = new ClientActorContext();

    final Sender sender =
        new Sender(actorContext, messageMemoryPool, requestMemoryPool, keepAlivePeriod);

    final RemoteAddressListImpl remoteAddressList = new RemoteAddressListImpl();
    final EndpointRegistry endpointRegistry = new EndpointRegistryImpl(name, remoteAddressList);

    final TransportContext transportContext =
        buildTransportContext(
            remoteAddressList,
            endpointRegistry,
            new ClientReceiveHandler(sender, receiveBuffer, listeners),
            receiveBuffer);

    return build(actorContext, transportContext);
  }

  protected TransportContext buildTransportContext(
      final RemoteAddressListImpl addressList,
      final EndpointRegistry endpointRegistry,
      final FragmentHandler receiveHandler,
      final Dispatcher receiveBuffer) {
    final TransportContext context = new TransportContext();
    context.setName("client");
    context.setReceiveBuffer(receiveBuffer);
    context.setMessageMaxLength(messageMaxLength);
    context.setRemoteAddressList(addressList);
    context.setEndpointRegistry(endpointRegistry);
    context.setReceiveHandler(receiveHandler);
    context.setChannelKeepAlivePeriod(keepAlivePeriod);

    if (channelFactory != null) {
      context.setChannelFactory(channelFactory);
    } else {
      context.setChannelFactory(new DefaultChannelFactory());
    }

    return context;
  }

  protected ClientTransport build(
      final ClientActorContext actorContext, final TransportContext context) {
    final ClientConductor conductor = new ClientConductor(actorContext, context);
    final Receiver receiver = new Receiver(actorContext, context);
    final Sender sender = actorContext.getSender();

    final ClientOutput output =
        new ClientOutputImpl(
            context.getEndpointRegistry(),
            sender,
            defaultRequestRetryTimeout,
            defaultMessageRetryTimeout);

    context.setClientOutput(output);

    scheduler.submitActor(conductor);
    scheduler.submitActor(receiver);
    scheduler.submitActor(sender);

    return new ClientTransport(actorContext, context);
  }

  private void validate() {
    Objects.requireNonNull(scheduler, "Scheduler must be provided");

    if (messageMaxLength > messageMemoryPool.capacity()) {
      throw new IllegalArgumentException(
          String.format(
              "Expected the message memory pool size to be greater than the max message length of %d, but was %d.",
              messageMaxLength, messageMemoryPool.capacity()));
    }

    if (messageMaxLength > requestMemoryPool.capacity()) {
      throw new IllegalArgumentException(
          String.format(
              "Expected the request memory pool size to be greater than the max message length of %d, but was %d.",
              messageMaxLength, requestMemoryPool.capacity()));
    }
  }
}

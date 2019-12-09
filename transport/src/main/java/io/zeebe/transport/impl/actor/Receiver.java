/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.transport.impl.actor;

import io.zeebe.transport.impl.TransportChannel;
import io.zeebe.transport.impl.TransportContext;
import io.zeebe.transport.impl.selector.ReadTransportPoller;
import io.zeebe.util.sched.Actor;
import io.zeebe.util.sched.future.ActorFuture;

public class Receiver extends Actor {
  protected final ReadTransportPoller transportPoller;
  private final String name;

  public Receiver(ActorContext actorContext, TransportContext context) {
    this.transportPoller = new ReadTransportPoller(actor);
    this.name = String.format("%s-receiver", context.getName());
    actorContext.setReceiver(this);
  }

  @Override
  public String getName() {
    return name;
  }

  @Override
  protected void onActorStarted() {
    actor.runBlocking(transportPoller::pollBlocking, transportPoller::pollBlockingEnded);
  }

  @Override
  protected void onActorClosing() {
    transportPoller.close();
    transportPoller.clearChannels();
  }

  public ActorFuture<Void> removeChannel(TransportChannel c) {
    return actor.call(
        () -> {
          transportPoller.removeChannel(c);
        });
  }

  public ActorFuture<Void> registerChannel(TransportChannel c) {
    return actor.call(
        () -> {
          transportPoller.addChannel(c);
        });
  }
}

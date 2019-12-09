/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.transport;

import io.zeebe.transport.impl.ServerSocketBinding;
import io.zeebe.transport.impl.TransportContext;
import io.zeebe.transport.impl.actor.ActorContext;
import io.zeebe.util.sched.future.ActorFuture;

public class ServerTransport implements AutoCloseable {
  protected final ServerOutput output;
  protected final ActorContext transportActorContext;
  protected final TransportContext transportContext;
  protected final ServerSocketBinding serverSocketBinding;

  public ServerTransport(ActorContext transportActorContext, TransportContext transportContext) {
    this.transportActorContext = transportActorContext;
    this.transportContext = transportContext;
    this.output = transportContext.getServerOutput();
    this.serverSocketBinding = transportContext.getServerSocketBinding();
  }

  /** @return interface to stage outbound data */
  public ServerOutput getOutput() {
    return output;
  }

  /**
   * Registers a listener with callbacks for whenever a connection to a remote gets established or
   * closed.
   */
  public ActorFuture<Void> registerChannelListener(TransportListener channelListener) {
    return transportActorContext.registerListener(channelListener);
  }

  public void removeChannelListener(TransportListener listener) {
    transportActorContext.removeListener(listener);
  }

  public ActorFuture<Void> closeAsync() {
    return transportActorContext.onClose();
  }

  public ActorFuture<Void> interruptAllChannels() {
    return transportActorContext.interruptAllChannels();
  }

  @Override
  public void close() {
    closeAsync().join();
  }

  public void releaseResources() {
    transportActorContext.getConductor().close();
  }
}

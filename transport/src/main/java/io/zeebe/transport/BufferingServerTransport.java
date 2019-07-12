/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.transport;

import io.zeebe.dispatcher.Dispatcher;
import io.zeebe.transport.impl.TransportContext;
import io.zeebe.transport.impl.actor.ActorContext;
import io.zeebe.util.sched.future.ActorFuture;

public class BufferingServerTransport extends ServerTransport {
  protected final Dispatcher receiveBuffer;

  public BufferingServerTransport(
      ActorContext transportActorContext, TransportContext transportContext) {
    super(transportActorContext, transportContext);
    receiveBuffer = transportContext.getReceiveBuffer();
  }

  public ActorFuture<ServerInputSubscription> openSubscription(
      String subscriptionName,
      ServerMessageHandler messageHandler,
      ServerRequestHandler requestHandler) {
    return transportActorContext
        .getServerConductor()
        .openInputSubscription(
            subscriptionName,
            output,
            transportContext.getRemoteAddressList(),
            messageHandler,
            requestHandler);
  }
}

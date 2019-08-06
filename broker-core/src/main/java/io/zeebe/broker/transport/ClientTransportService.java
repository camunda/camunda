/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.broker.transport;

import io.zeebe.servicecontainer.Service;
import io.zeebe.servicecontainer.ServiceStartContext;
import io.zeebe.servicecontainer.ServiceStopContext;
import io.zeebe.transport.ClientTransport;
import io.zeebe.transport.ClientTransportBuilder;
import io.zeebe.transport.SocketAddress;
import io.zeebe.transport.Transports;
import io.zeebe.transport.impl.memory.NonBlockingMemoryPool;
import io.zeebe.transport.impl.memory.UnboundedMemoryPool;
import io.zeebe.util.ByteValue;
import io.zeebe.util.collection.IntTuple;
import io.zeebe.util.sched.ActorScheduler;
import java.util.Collection;

public class ClientTransportService implements Service<ClientTransport> {

  protected final Collection<IntTuple<SocketAddress>> defaultEndpoints;
  private final String name;
  private final ByteValue messageBufferSize;

  protected ClientTransport transport;

  public ClientTransportService(
      String name,
      Collection<IntTuple<SocketAddress>> defaultEndpoints,
      ByteValue messageBufferSize) {
    this.name = name;
    this.defaultEndpoints = defaultEndpoints;
    this.messageBufferSize = messageBufferSize;
  }

  @Override
  public void start(ServiceStartContext startContext) {
    final ActorScheduler scheduler = startContext.getScheduler();

    final ClientTransportBuilder transportBuilder = Transports.newClientTransport(name);

    transport =
        transportBuilder
            .messageMemoryPool(new NonBlockingMemoryPool(messageBufferSize))
            // client transport in broker should no do any high volume interactions using
            // request/resp
            .requestMemoryPool(new UnboundedMemoryPool())
            .scheduler(scheduler)
            .build();

    if (defaultEndpoints != null) {
      // make transport open and manage channels to the default endpoints
      defaultEndpoints.forEach(s -> transport.registerEndpoint(s.getInt(), s.getRight()));
    }
  }

  @Override
  public void stop(ServiceStopContext stopContext) {
    stopContext.async(transport.closeAsync());
  }

  @Override
  public ClientTransport get() {
    return transport;
  }
}

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.broker.transport;

import io.zeebe.servicecontainer.Injector;
import io.zeebe.servicecontainer.Service;
import io.zeebe.servicecontainer.ServiceStartContext;
import io.zeebe.servicecontainer.ServiceStopContext;
import io.zeebe.transport.Loggers;
import io.zeebe.transport.ServerMessageHandler;
import io.zeebe.transport.ServerRequestHandler;
import io.zeebe.transport.ServerTransport;
import io.zeebe.transport.Transports;
import io.zeebe.transport.impl.memory.NonBlockingMemoryPool;
import io.zeebe.util.ByteValue;
import io.zeebe.util.sched.ActorScheduler;
import java.io.Closeable;
import java.net.InetSocketAddress;
import org.slf4j.Logger;

public class ServerTransportService implements Service<ServerTransport> {
  public static final Logger LOG = Loggers.TRANSPORT_LOGGER;

  protected final Injector<ServerRequestHandler> requestHandlerInjector = new Injector<>();
  protected final Injector<ServerMessageHandler> messageHandlerInjector = new Injector<>();

  protected final String readableName;
  protected final InetSocketAddress bindAddress;
  private final ByteValue sendBufferSize;

  protected ServerTransport serverTransport;

  public ServerTransportService(
      String readableName, InetSocketAddress bindAddress, ByteValue sendBufferSize) {
    this.readableName = readableName;
    this.bindAddress = bindAddress;
    this.sendBufferSize = sendBufferSize;
  }

  @Override
  public void start(ServiceStartContext serviceContext) {
    final ActorScheduler scheduler = serviceContext.getScheduler();
    final ServerRequestHandler requestHandler = requestHandlerInjector.getValue();
    final ServerMessageHandler messageHandler = messageHandlerInjector.getValue();

    serverTransport =
        Transports.newServerTransport()
            .name(readableName)
            .bindAddress(bindAddress)
            .scheduler(scheduler)
            .messageMemoryPool(new NonBlockingMemoryPool(sendBufferSize))
            .build(messageHandler, requestHandler);

    LOG.info("Bound {} to {}", readableName, bindAddress);
  }

  @Override
  public void stop(ServiceStopContext serviceStopContext) {
    serviceStopContext.async(serverTransport.closeAsync());
  }

  @Override
  public ServerTransport get() {
    return serverTransport;
  }

  public Closeable getReleasingResourcesDelegate() {
    return () -> {
      if (serverTransport != null) {
        serverTransport.releaseResources();
      }
    };
  }

  public Injector<ServerRequestHandler> getRequestHandlerInjector() {
    return requestHandlerInjector;
  }

  public Injector<ServerMessageHandler> getMessageHandlerInjector() {
    return messageHandlerInjector;
  }
}

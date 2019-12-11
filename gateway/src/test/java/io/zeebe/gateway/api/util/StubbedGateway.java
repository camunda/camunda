/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.gateway.api.util;

import io.grpc.ManagedChannel;
import io.grpc.Server;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.zeebe.gateway.EndpointManager;
import io.zeebe.gateway.impl.job.LongPollingActivateJobsHandler;
import io.zeebe.gateway.protocol.GatewayGrpc;
import io.zeebe.gateway.protocol.GatewayGrpc.GatewayBlockingStub;
import io.zeebe.util.sched.ActorScheduler;
import java.io.IOException;

@SuppressWarnings({"rawtypes", "unchecked"})
public class StubbedGateway {

  private static final String SERVER_NAME = "server";

  private final StubbedBrokerClient brokerClient;
  private final LongPollingActivateJobsHandler longPollingHandler;
  private final ActorScheduler actorScheduler;
  private Server server;

  public StubbedGateway(
      final ActorScheduler actorScheduler,
      final StubbedBrokerClient brokerClient,
      final LongPollingActivateJobsHandler longPollingHandler) {
    this.actorScheduler = actorScheduler;
    this.brokerClient = brokerClient;
    this.longPollingHandler = longPollingHandler;
  }

  public void start() throws IOException {
    actorScheduler.submitActor(longPollingHandler);
    final EndpointManager endpointManager = new EndpointManager(brokerClient, longPollingHandler);
    final InProcessServerBuilder serverBuilder =
        InProcessServerBuilder.forName(SERVER_NAME).addService(endpointManager);
    server = serverBuilder.build();
    server.start();
  }

  public void stop() {
    if (server != null && !server.isShutdown()) {
      server.shutdownNow();
      try {
        server.awaitTermination();
      } catch (final InterruptedException e) {
        e.printStackTrace();
      }
    }
  }

  public GatewayBlockingStub buildClient() {
    final ManagedChannel channel =
        InProcessChannelBuilder.forName(SERVER_NAME).directExecutor().build();
    return GatewayGrpc.newBlockingStub(channel);
  }
}

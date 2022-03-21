/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.gateway.api.util;

import io.camunda.zeebe.gateway.EndpointManager;
import io.camunda.zeebe.gateway.GatewayGrpcService;
import io.camunda.zeebe.gateway.impl.job.ActivateJobsHandler;
import io.camunda.zeebe.gateway.impl.job.LongPollingActivateJobsHandler;
import io.camunda.zeebe.gateway.protocol.GatewayGrpc;
import io.camunda.zeebe.gateway.protocol.GatewayGrpc.GatewayBlockingStub;
import io.camunda.zeebe.util.sched.Actor;
import io.camunda.zeebe.util.sched.ActorControl;
import io.camunda.zeebe.util.sched.ActorScheduler;
import io.grpc.ManagedChannel;
import io.grpc.Server;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import java.io.IOException;
import java.util.concurrent.CompletableFuture;

@SuppressWarnings({"rawtypes", "unchecked"})
public final class StubbedGateway {

  private static final String SERVER_NAME = "server";

  private final StubbedBrokerClient brokerClient;
  private final ActivateJobsHandler activateJobsHandler;
  private final ActorScheduler actorScheduler;
  private Server server;

  public StubbedGateway(
      final ActorScheduler actorScheduler,
      final StubbedBrokerClient brokerClient,
      final ActivateJobsHandler activateJobsHandler) {
    this.actorScheduler = actorScheduler;
    this.brokerClient = brokerClient;
    this.activateJobsHandler = activateJobsHandler;
  }

  public void start() throws IOException {
    if (activateJobsHandler instanceof LongPollingActivateJobsHandler handler) {
      submitLongPollingActor(handler);
    }

    final EndpointManager endpointManager = new EndpointManager(brokerClient, activateJobsHandler);
    final GatewayGrpcService gatewayGrpcService = new GatewayGrpcService(endpointManager);

    final InProcessServerBuilder serverBuilder =
        InProcessServerBuilder.forName(SERVER_NAME).addService(gatewayGrpcService);
    server = serverBuilder.build();
    server.start();
  }

  private void submitLongPollingActor(final LongPollingActivateJobsHandler handler) {
    final var actorStartedFuture = new CompletableFuture<ActorControl>();
    final var actor =
        Actor.newActor()
            .name(handler.getName())
            .actorStartedHandler(handler.andThen(actorStartedFuture::complete))
            .build();
    actorScheduler.submitActor(actor);
    actorStartedFuture.join();
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

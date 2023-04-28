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
import io.camunda.zeebe.gateway.impl.broker.BrokerClient;
import io.camunda.zeebe.gateway.impl.configuration.GatewayCfg;
import io.camunda.zeebe.gateway.impl.job.ActivateJobsHandler;
import io.camunda.zeebe.gateway.impl.job.LongPollingActivateJobsHandler;
import io.camunda.zeebe.gateway.impl.job.RoundRobinActivateJobsHandler;
import io.camunda.zeebe.gateway.impl.stream.JobActivationProperties;
import io.camunda.zeebe.gateway.protocol.GatewayGrpc;
import io.camunda.zeebe.gateway.protocol.GatewayGrpc.GatewayBlockingStub;
import io.camunda.zeebe.scheduler.Actor;
import io.camunda.zeebe.scheduler.ActorControl;
import io.camunda.zeebe.scheduler.ActorScheduler;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.transport.stream.api.ClientStreamConsumer;
import io.camunda.zeebe.transport.stream.api.ClientStreamId;
import io.camunda.zeebe.transport.stream.api.ClientStreamer;
import io.grpc.ManagedChannel;
import io.grpc.Server;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import org.agrona.DirectBuffer;

@SuppressWarnings({"unchecked"})
public final class StubbedGateway {

  private static final String SERVER_NAME = "server";

  private final StubbedBrokerClient brokerClient;
  private final ActorScheduler actorScheduler;
  private final GatewayCfg config;
  private Server server;

  public StubbedGateway(
      final ActorScheduler actorScheduler,
      final StubbedBrokerClient brokerClient,
      final GatewayCfg config) {
    this.actorScheduler = actorScheduler;
    this.brokerClient = brokerClient;
    this.config = config;
  }

  public void start() throws IOException {
    final var activateJobsHandler = buildActivateJobsHandler(brokerClient);
    submitActorToActivateJobs(activateJobsHandler);

    final EndpointManager endpointManager =
        new EndpointManager(brokerClient, activateJobsHandler, new NoopJobStreamer());
    final GatewayGrpcService gatewayGrpcService = new GatewayGrpcService(endpointManager);

    final InProcessServerBuilder serverBuilder =
        InProcessServerBuilder.forName(SERVER_NAME).addService(gatewayGrpcService);
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

  private void submitActorToActivateJobs(final Consumer<ActorControl> consumer) {
    final var future = new CompletableFuture<>();
    final var actor =
        Actor.newActor()
            .name("ActivateJobsHandler")
            .actorStartedHandler(consumer.andThen(future::complete))
            .build();
    actorScheduler.submitActor(actor);
    future.join();
  }

  private ActivateJobsHandler buildActivateJobsHandler(final BrokerClient brokerClient) {
    if (config.getLongPolling().isEnabled()) {
      return buildLongPollingHandler(brokerClient);
    } else {
      return new RoundRobinActivateJobsHandler(brokerClient);
    }
  }

  private LongPollingActivateJobsHandler buildLongPollingHandler(final BrokerClient brokerClient) {
    return LongPollingActivateJobsHandler.newBuilder().setBrokerClient(brokerClient).build();
  }

  private static final class NoopJobStreamer implements ClientStreamer<JobActivationProperties> {

    @Override
    public ActorFuture<ClientStreamId> add(
        final DirectBuffer streamType,
        final JobActivationProperties metadata,
        final ClientStreamConsumer clientStreamConsumer) {
      throw new UnsupportedOperationException("Not yet implemented; implement when needed");
    }

    @Override
    public ActorFuture<Void> remove(final ClientStreamId streamId) {
      throw new UnsupportedOperationException("Not yet implemented; implement when needed");
    }

    @Override
    public void close() {}
  }
}

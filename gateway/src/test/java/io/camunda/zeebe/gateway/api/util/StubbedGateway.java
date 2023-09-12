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
import io.camunda.zeebe.gateway.impl.configuration.MultiTenancyCfg;
import io.camunda.zeebe.gateway.impl.job.ActivateJobsHandler;
import io.camunda.zeebe.gateway.impl.job.LongPollingActivateJobsHandler;
import io.camunda.zeebe.gateway.impl.job.RoundRobinActivateJobsHandler;
import io.camunda.zeebe.gateway.protocol.GatewayGrpc;
import io.camunda.zeebe.gateway.protocol.GatewayGrpc.GatewayBlockingStub;
import io.camunda.zeebe.gateway.protocol.GatewayGrpc.GatewayStub;
import io.camunda.zeebe.protocol.impl.stream.job.ActivatedJobImpl;
import io.camunda.zeebe.protocol.impl.stream.job.JobActivationProperties;
import io.camunda.zeebe.scheduler.Actor;
import io.camunda.zeebe.scheduler.ActorControl;
import io.camunda.zeebe.scheduler.ActorScheduler;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import io.camunda.zeebe.transport.stream.api.ClientStreamConsumer;
import io.camunda.zeebe.transport.stream.api.ClientStreamId;
import io.camunda.zeebe.transport.stream.api.ClientStreamer;
import io.camunda.zeebe.util.buffer.BufferUtil;
import io.grpc.ManagedChannel;
import io.grpc.Server;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Consumer;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.awaitility.Awaitility;

public final class StubbedGateway {

  private static final String SERVER_NAME = "server";

  private final StubbedBrokerClient brokerClient;
  private final StubbedJobStreamer jobStreamer;
  private final ActorScheduler actorScheduler;
  private final GatewayCfg config;
  private Server server;

  public StubbedGateway(
      final ActorScheduler actorScheduler,
      final StubbedBrokerClient brokerClient,
      final StubbedJobStreamer jobStreamer,
      final GatewayCfg config) {
    this.actorScheduler = actorScheduler;
    this.brokerClient = brokerClient;
    this.jobStreamer = jobStreamer;
    this.config = config;
  }

  public void start() throws IOException {
    final var activateJobsHandler = buildActivateJobsHandler(brokerClient);
    submitActorToActivateJobs(activateJobsHandler);

    final EndpointManager endpointManager =
        new EndpointManager(
            brokerClient, activateJobsHandler, jobStreamer, Runnable::run, new MultiTenancyCfg());
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

  public GatewayStub buildAsyncClient() {
    final ManagedChannel channel =
        InProcessChannelBuilder.forName(SERVER_NAME).directExecutor().build();
    return GatewayGrpc.newStub(channel);
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

  public static final class StubbedJobStreamer implements ClientStreamer<JobActivationProperties> {
    private final ConcurrentMap<DirectBuffer, StreamTypeConsumer> registeredStreams =
        new ConcurrentHashMap<>();
    private final ConcurrentMap<ClientStreamId, StreamTypeConsumer> streamIdToConsumer =
        new ConcurrentHashMap<>();

    @Override
    public ActorFuture<ClientStreamId> add(
        final DirectBuffer streamType,
        final JobActivationProperties metadata,
        final ClientStreamConsumer clientStreamConsumer) {
      final StubbedClientStreamId streamId = new StubbedClientStreamId(UUID.randomUUID());
      final StreamTypeConsumer consumer =
          new StreamTypeConsumer(streamType, metadata, clientStreamConsumer);
      registeredStreams.put(streamType, consumer);
      streamIdToConsumer.put(streamId, consumer);

      return CompletableActorFuture.completed(streamId);
    }

    @Override
    public ActorFuture<Void> remove(final ClientStreamId streamId) {
      final var consumer = streamIdToConsumer.remove(streamId);
      registeredStreams.remove(consumer.streamType);

      return CompletableActorFuture.completed(null);
    }

    @Override
    public void close() {}

    public CompletableFuture<Void> push(final ActivatedJobImpl activatedJob) {
      final StreamTypeConsumer streamTypeConsumer =
          registeredStreams.get(activatedJob.jobRecord().getTypeBuffer());

      if (streamTypeConsumer != null) {
        final UnsafeBuffer serializedJob = new UnsafeBuffer(new byte[activatedJob.getLength()]);
        activatedJob.write(serializedJob, 0);
        return streamTypeConsumer.clientStreamConsumer.push(serializedJob);
      }

      return CompletableFuture.failedFuture(new RuntimeException("No stream exists with given id"));
    }

    public boolean containsStreamFor(final String streamType) {
      return registeredStreams.containsKey(BufferUtil.wrapString(streamType));
    }

    public void waitStreamToBeAvailable(final DirectBuffer jobType) {
      // since we are using an async client, we need some time before the test request executes
      // below, we reserve some time for a client to register for a job type
      Awaitility.await("wait until stream is registered")
          .until(() -> registeredStreams.containsKey(jobType));
    }
  }

  private record StreamTypeConsumer(
      DirectBuffer streamType,
      JobActivationProperties metadata,
      ClientStreamConsumer clientStreamConsumer) {}

  private record StubbedClientStreamId(UUID serverStreamId) implements ClientStreamId {}
}

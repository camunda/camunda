/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.api.util;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import io.camunda.security.configuration.MultiTenancyConfiguration;
import io.camunda.security.configuration.SecurityConfiguration;
import io.camunda.zeebe.broker.client.api.BrokerClient;
import io.camunda.zeebe.gateway.EndpointManager;
import io.camunda.zeebe.gateway.Gateway;
import io.camunda.zeebe.gateway.GatewayGrpcService;
import io.camunda.zeebe.gateway.ResponseMapper;
import io.camunda.zeebe.gateway.impl.configuration.GatewayCfg;
import io.camunda.zeebe.gateway.impl.job.ActivateJobsHandler;
import io.camunda.zeebe.gateway.impl.job.LongPollingActivateJobsHandler;
import io.camunda.zeebe.gateway.impl.job.RoundRobinActivateJobsHandler;
import io.camunda.zeebe.gateway.impl.stream.StreamJobsHandler;
import io.camunda.zeebe.gateway.metrics.LongPollingMetrics;
import io.camunda.zeebe.gateway.protocol.GatewayGrpc;
import io.camunda.zeebe.gateway.protocol.GatewayGrpc.GatewayBlockingStub;
import io.camunda.zeebe.gateway.protocol.GatewayGrpc.GatewayStub;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.ActivateJobsResponse;
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
import io.grpc.CallCredentials;
import io.grpc.ManagedChannel;
import io.grpc.Metadata;
import io.grpc.Metadata.Key;
import io.grpc.Server;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import java.io.IOException;
import java.util.Date;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;
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
  private final SecurityConfiguration securityConfiguration;
  private Server server;

  public StubbedGateway(
      final ActorScheduler actorScheduler,
      final StubbedBrokerClient brokerClient,
      final StubbedJobStreamer jobStreamer,
      final GatewayCfg config,
      final SecurityConfiguration securityConfiguration) {
    this.actorScheduler = actorScheduler;
    this.brokerClient = brokerClient;
    this.jobStreamer = jobStreamer;
    this.config = config;
    this.securityConfiguration = securityConfiguration;
  }

  public void start() throws IOException {
    final var activateJobsHandler = buildActivateJobsHandler(brokerClient);
    submitActorToActivateJobs(activateJobsHandler);
    final var clientStreamAdapter = new StreamJobsHandler(jobStreamer);
    actorScheduler.submitActor(clientStreamAdapter).join();

    final MultiTenancyConfiguration multiTenancy = securityConfiguration.getMultiTenancy();
    final EndpointManager endpointManager =
        new EndpointManager(brokerClient, activateJobsHandler, clientStreamAdapter, multiTenancy);
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
    return GatewayGrpc.newBlockingStub(channel).withCallCredentials(new FakeOAuthCallCredentials());
  }

  public GatewayStub buildAsyncClient() {
    final ManagedChannel channel =
        InProcessChannelBuilder.forName(SERVER_NAME).directExecutor().build();
    return GatewayGrpc.newStub(channel).withCallCredentials(new FakeOAuthCallCredentials());
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

  private ActivateJobsHandler<ActivateJobsResponse> buildActivateJobsHandler(
      final BrokerClient brokerClient) {
    if (config.getLongPolling().isEnabled()) {
      return buildLongPollingHandler(brokerClient);
    } else {
      return new RoundRobinActivateJobsHandler<>(
          brokerClient,
          config.getNetwork().getMaxMessageSize().toBytes(),
          ResponseMapper::toActivateJobsResponse,
          Gateway.REQUEST_CANCELED_EXCEPTION_PROVIDER);
    }
  }

  private LongPollingActivateJobsHandler<ActivateJobsResponse> buildLongPollingHandler(
      final BrokerClient brokerClient) {
    return LongPollingActivateJobsHandler.<ActivateJobsResponse>newBuilder()
        .setBrokerClient(brokerClient)
        .setMaxMessageSize(config.getNetwork().getMaxMessageSize().toBytes())
        .setActivationResultMapper(ResponseMapper::toActivateJobsResponse)
        .setNoJobsReceivedExceptionProvider(Gateway.NO_JOBS_RECEIVED_EXCEPTION_PROVIDER)
        .setRequestCanceledExceptionProvider(Gateway.REQUEST_CANCELED_EXCEPTION_PROVIDER)
        .setMetrics(LongPollingMetrics.noop())
        .build();
  }

  public static final class StubbedJobStreamer implements ClientStreamer<JobActivationProperties> {
    private final ConcurrentMap<DirectBuffer, StreamTypeConsumer> registeredStreams =
        new ConcurrentHashMap<>();
    private final ConcurrentMap<ClientStreamId, StreamTypeConsumer> streamIdToConsumer =
        new ConcurrentHashMap<>();

    private final AtomicReference<Throwable> failOnAdd = new AtomicReference<>();

    @Override
    public ActorFuture<ClientStreamId> add(
        final DirectBuffer streamType,
        final JobActivationProperties metadata,
        final ClientStreamConsumer clientStreamConsumer) {
      final var failure = failOnAdd.get();
      if (failure != null) {
        return CompletableActorFuture.completedExceptionally(failure);
      }

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

    public ActorFuture<Void> push(final ActivatedJobImpl activatedJob) {
      final StreamTypeConsumer streamTypeConsumer =
          registeredStreams.get(activatedJob.jobRecord().getTypeBuffer());

      if (streamTypeConsumer != null) {
        final UnsafeBuffer serializedJob = new UnsafeBuffer(new byte[activatedJob.getLength()]);
        activatedJob.write(serializedJob, 0);
        return streamTypeConsumer.clientStreamConsumer.push(serializedJob);
      }

      return CompletableActorFuture.completedExceptionally(
          new RuntimeException("No stream exists with given id"));
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

    public void setFailOnAdd(final Throwable failure) {
      failOnAdd.set(failure);
    }
  }

  private record StreamTypeConsumer(
      DirectBuffer streamType,
      JobActivationProperties metadata,
      ClientStreamConsumer clientStreamConsumer) {}

  private record StubbedClientStreamId(UUID serverStreamId) implements ClientStreamId {}

  private static final class FakeOAuthCallCredentials extends CallCredentials {

    /** Can be adjusted to test different token values. */
    private String token = "token";

    public void setToken(final String token) {
      this.token = token;
    }

    @Override
    public void applyRequestMetadata(
        final RequestInfo requestInfo, final Executor appExecutor, final MetadataApplier applier) {
      final Metadata headers = new Metadata();
      final Key<String> key = Key.of("Authorization", Metadata.ASCII_STRING_MARSHALLER);
      headers.put(key, generateToken());
      applier.apply(headers);
    }

    private String generateToken() {
      final Algorithm algorithm = Algorithm.HMAC256("secret-key");

      return JWT.create()
          .withIssuer("test-issuer")
          .withSubject("test-user")
          .withAudience("test-audience")
          .withClaim("role", "admin")
          .withClaim("foo", "bar")
          .withClaim("baz", "qux")
          .withExpiresAt(new Date(System.currentTimeMillis() + 60 * 60 * 1000)) // Expires in 1 hour
          .sign(algorithm);
    }
  }
}

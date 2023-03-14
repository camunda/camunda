/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker.bootstrap;

import io.camunda.zeebe.broker.jobstream.JobStreamService;
import io.camunda.zeebe.scheduler.ConcurrencyControl;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.stream.api.ActivatedJob;
import io.camunda.zeebe.stream.api.GatewayStreamer;
import io.camunda.zeebe.stream.api.GatewayStreamer.ErrorHandler;
import io.camunda.zeebe.stream.api.GatewayStreamer.GatewayStream;
import io.camunda.zeebe.stream.api.JobActivationProperties;
import io.camunda.zeebe.transport.TransportFactory;
import io.camunda.zeebe.transport.stream.api.RemoteStream;
import io.camunda.zeebe.transport.stream.api.RemoteStreamServer;
import io.camunda.zeebe.transport.stream.api.RemoteStreamService;
import java.util.Collection;
import java.util.Collections;
import java.util.Optional;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

/**
 * Sets up the {@link JobStreamService}, which manages the lifecycle of the job specific stream API
 * server, pusher, and registry.
 */
@SuppressWarnings("resource")
public final class JobStreamServiceStep extends AbstractBrokerStartupStep {

  @Override
  void startupInternal(
      final BrokerStartupContext brokerStartupContext,
      final ConcurrencyControl concurrencyControl,
      final ActorFuture<BrokerStartupContext> startupFuture) {
    final RemoteStreamService<JobActivationProperties, ActivatedJob> remoteStreamService =
        new TransportFactory(brokerStartupContext.getActorSchedulingService())
            .createRemoteStreamServer(
                brokerStartupContext.getClusterServices().getCommunicationService(),
                DummyActivationProperties::new,
                brokerStartupContext.getClusterServices().getEventService());

    final var startFuture = remoteStreamService.start();
    concurrencyControl.runOnCompletion(
        startFuture,
        (streamer, error) -> {
          // TODO: Put remoteStreamService into context
          final var jobStreamService = new JobStreamService(null, new JobGatewayStreamer(streamer));
          brokerStartupContext.setJobStreamService(jobStreamService);
        });

    startFuture.onComplete(startFuture);
  }

  @Override
  void shutdownInternal(
      final BrokerStartupContext brokerShutdownContext,
      final ConcurrencyControl concurrencyControl,
      final ActorFuture<BrokerStartupContext> shutdownFuture) {
    final var clusterServices = brokerShutdownContext.getClusterServices();
    final var service = brokerShutdownContext.getJobStreamService();
    if (service == null) {
      return;
    }

    // TODO: close remoteStreamService
  }

  @Override
  public String getName() {
    return "JobStreamService";
  }

  // TODO: replace with real activation properties
  private record DummyActivationProperties(
      DirectBuffer worker, long timeout, Collection<DirectBuffer> fetchVariables)
      implements JobActivationProperties {

    private DummyActivationProperties() {
      this(new UnsafeBuffer(), -1L, Collections.emptyList());
    }

    @Override
    public void wrap(final DirectBuffer buffer, final int offset, final int length) {}
  }

  private static final class JobGatewayStream
      implements GatewayStream<
          io.camunda.zeebe.stream.api.JobActivationProperties,
          io.camunda.zeebe.stream.api.ActivatedJob> {

    private final RemoteStream<JobActivationProperties, ActivatedJob> remoteStream;

    private JobGatewayStream(
        final RemoteStream<JobActivationProperties, ActivatedJob> remoteStream) {
      this.remoteStream = remoteStream;
    }

    @Override
    public JobActivationProperties metadata() {
      return remoteStream.metadata();
    }

    @Override
    public void push(final ActivatedJob p, final ErrorHandler<ActivatedJob> errorHandler) {
      remoteStream.push(p, errorHandler::handleError);
    }
  }

  private static final class JobGatewayStreamer
      implements GatewayStreamer<JobActivationProperties, ActivatedJob> {

    private final RemoteStreamServer<JobActivationProperties, ActivatedJob> delegate;

    private JobGatewayStreamer(
        final RemoteStreamServer<JobActivationProperties, ActivatedJob> delegate) {
      this.delegate = delegate;
    }

    @Override
    public Optional<GatewayStream<JobActivationProperties, ActivatedJob>> streamFor(
        final DirectBuffer streamId) {
      return delegate.streamFor(streamId).map(JobGatewayStream::new);
    }
  }
}

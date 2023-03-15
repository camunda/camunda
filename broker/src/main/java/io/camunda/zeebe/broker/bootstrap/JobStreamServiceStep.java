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
import io.camunda.zeebe.stream.api.GatewayStreamer.ErrorHandler;
import io.camunda.zeebe.stream.api.JobActivationProperties;
import io.camunda.zeebe.transport.TransportFactory;
import io.camunda.zeebe.transport.stream.api.RemoteStream;
import io.camunda.zeebe.transport.stream.api.RemoteStreamService;
import io.camunda.zeebe.transport.stream.api.RemoteStreamer;
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
                DummyActivationProperties::new);

    final var startFuture = remoteStreamService.start();
    concurrencyControl.runOnCompletion(
        startFuture,
        (streamer, error) -> {
          if (error != null) {
            startupFuture.completeExceptionally(error);
          } else {
            final var jobStreamService =
                new JobStreamService(remoteStreamService, new JobGatewayStreamer(streamer));
            brokerStartupContext.setJobStreamService(jobStreamService);
            startupFuture.complete(brokerStartupContext);
          }
        });
  }

  @Override
  void shutdownInternal(
      final BrokerStartupContext brokerShutdownContext,
      final ConcurrencyControl concurrencyControl,
      final ActorFuture<BrokerStartupContext> shutdownFuture) {
    final var service = brokerShutdownContext.getJobStreamService();
    if (service == null) {
      service
          .server()
          .closeAsync()
          .onComplete(
              (ok, error) -> {
                if (error != null) {
                  shutdownFuture.completeExceptionally(error);
                } else {
                  brokerShutdownContext.setJobStreamService(null);
                  shutdownFuture.complete(brokerShutdownContext);
                }
              });
    }
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
      implements io.camunda.zeebe.stream.api.GatewayStreamer.GatewayStream<
          JobActivationProperties, ActivatedJob> {

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
      implements io.camunda.zeebe.stream.api.GatewayStreamer<
          JobActivationProperties, ActivatedJob> {

    private final RemoteStreamer<JobActivationProperties, ActivatedJob> delegate;

    private JobGatewayStreamer(
        final RemoteStreamer<JobActivationProperties, ActivatedJob> delegate) {
      this.delegate = delegate;
    }

    @Override
    public Optional<GatewayStream<JobActivationProperties, ActivatedJob>> streamFor(
        final DirectBuffer streamId) {
      return delegate.streamFor(streamId).map(JobGatewayStream::new);
    }
  }
}

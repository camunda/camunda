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
import io.camunda.zeebe.scheduler.future.ActorFutureCollector;
import io.camunda.zeebe.stream.api.ActivatedJob;
import io.camunda.zeebe.stream.api.JobActivationProperties;
import io.camunda.zeebe.transport.TransportFactory;
import io.camunda.zeebe.transport.stream.api.RemoteStreamService;
import java.util.Collection;
import java.util.Collections;
import java.util.stream.Stream;
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
    final var clusterServices = brokerStartupContext.getClusterServices();
    final var scheduler = brokerStartupContext.getActorSchedulingService();
    final RemoteStreamService<JobActivationProperties, ActivatedJob> remoteStreamService =
        new TransportFactory(brokerStartupContext.getActorSchedulingService())
            .createRemoteStreamServer(
                brokerStartupContext.getClusterServices().getCommunicationService(),
                DummyActivationProperties::new,
                eventService);

    final var startFuture = remoteStreamService.start();
    concurrencyControl.runOnCompletion(
        startFuture,
        (result, error) -> {
          // TODO: Put it into context

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

    clusterServices.getMembershipService().removeListener(service.server());
    final var result =
        Stream.of(service.server().closeAsync(), service.jobStreamer().closeAsync())
            .collect(new ActorFutureCollector<>(concurrencyControl));
    concurrencyControl.runOnCompletion(
        result,
        (ok, error) -> {
          if (error != null) {
            shutdownFuture.completeExceptionally(error);
            return;
          }

          brokerShutdownContext.setJobStreamService(null);
          shutdownFuture.complete(brokerShutdownContext);
        });
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
}

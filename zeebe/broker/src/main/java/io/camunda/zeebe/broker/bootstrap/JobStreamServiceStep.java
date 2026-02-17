/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.bootstrap;

import io.camunda.zeebe.broker.jobstream.JobStreamMetrics;
import io.camunda.zeebe.broker.jobstream.JobStreamService;
import io.camunda.zeebe.broker.jobstream.RemoteJobStreamErrorHandlerService;
import io.camunda.zeebe.broker.jobstream.RemoteJobStreamer;
import io.camunda.zeebe.broker.jobstream.YieldingJobStreamErrorHandler;
import io.camunda.zeebe.protocol.impl.stream.job.ActivatedJob;
import io.camunda.zeebe.protocol.impl.stream.job.JobActivationProperties;
import io.camunda.zeebe.protocol.impl.stream.job.JobActivationPropertiesImpl;
import io.camunda.zeebe.scheduler.ConcurrencyControl;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.transport.TransportFactory;
import io.camunda.zeebe.transport.stream.api.RemoteStreamService;
import io.camunda.zeebe.util.VisibleForTesting;
import java.util.Collection;
import java.util.Map;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;

/**
 * Sets up the {@link JobStreamService}, which manages the lifecycle of the job specific stream API
 * remoteStreamService, pusher, and registry.
 */
public final class JobStreamServiceStep extends AbstractBrokerStartupStep {

  @Override
  void startupInternal(
      final BrokerStartupContext brokerStartupContext,
      final ConcurrencyControl concurrencyControl,
      final ActorFuture<BrokerStartupContext> startupFuture) {
    final var clusterServices = brokerStartupContext.getClusterServices();
    final var errorHandlerService =
        new RemoteJobStreamErrorHandlerService(new YieldingJobStreamErrorHandler());

    final var scheduler = brokerStartupContext.getActorSchedulingService();
    final RemoteStreamService<JobActivationProperties, ActivatedJob> remoteStreamService =
        new TransportFactory(scheduler)
            .createRemoteStreamServer(
                clusterServices.getCommunicationService(),
                JobStreamServiceStep::readJobActivationProperties,
                errorHandlerService,
                new JobStreamMetrics(brokerStartupContext.getMeterRegistry()));
    final var errorHandlerStarted = scheduler.submitActor(errorHandlerService);

    errorHandlerStarted.onComplete(
        (ok, err) -> {
          if (err != null) {
            startupFuture.completeExceptionally(err);
            return;
          }

          remoteStreamService
              .start(scheduler, concurrencyControl)
              .onComplete(
                  (streamer, error) -> {
                    if (error != null) {
                      startupFuture.completeExceptionally(error);
                      return;
                    }

                    final var jobStreamService =
                        new JobStreamService(
                            remoteStreamService,
                            new RemoteJobStreamer(streamer, clusterServices.getEventService()),
                            errorHandlerService);
                    clusterServices.getMembershipService().addListener(remoteStreamService);
                    brokerStartupContext.addPartitionListener(errorHandlerService);
                    brokerStartupContext.setJobStreamService(jobStreamService);
                    brokerStartupContext
                        .getSpringBrokerBridge()
                        .registerJobStreamServiceSupplier(() -> jobStreamService);
                    startupFuture.complete(brokerStartupContext);
                  });
        });
  }

  @Override
  void shutdownInternal(
      final BrokerStartupContext brokerShutdownContext,
      final ConcurrencyControl concurrencyControl,
      final ActorFuture<BrokerStartupContext> shutdownFuture) {
    final var service = brokerShutdownContext.getJobStreamService();

    if (service != null) {
      brokerShutdownContext
          .getClusterServices()
          .getMembershipService()
          .removeListener(service.remoteStreamService());
      brokerShutdownContext.removePartitionListener(service.errorHandlerService());

      service
          .closeAsync(concurrencyControl)
          .onComplete(
              (ok, error) -> {
                if (error != null) {
                  shutdownFuture.completeExceptionally(error);
                } else {
                  brokerShutdownContext
                      .getClusterServices()
                      .getMembershipService()
                      .removeListener(service.remoteStreamService());
                  brokerShutdownContext.setJobStreamService(null);
                  brokerShutdownContext
                      .getSpringBrokerBridge()
                      .registerJobStreamServiceSupplier(null);
                  shutdownFuture.complete(brokerShutdownContext);
                }
              });
    }
  }

  @VisibleForTesting("https://github.com/camunda/camunda/issues/14624")
  static JobActivationProperties readJobActivationProperties(final DirectBuffer buffer) {
    final var mutable = new JobActivationPropertiesImpl();
    mutable.wrap(buffer);

    return new ImmutableJobActivationPropertiesImpl(
        mutable.worker(),
        mutable.timeout(),
        mutable.fetchVariables(),
        mutable.tenantIds(),
        mutable.claims());
  }

  @Override
  public String getName() {
    return "JobStreamService";
  }

  private record ImmutableJobActivationPropertiesImpl(
      DirectBuffer worker,
      long timeout,
      Collection<DirectBuffer> fetchVariables,
      Collection<String> tenantIds,
      Map<String, Object> claims)
      implements JobActivationProperties {

    @Override
    public int getLength() {
      throw new UnsupportedOperationException();
    }

    @Override
    public int write(final MutableDirectBuffer buffer, final int offset) {
      throw new UnsupportedOperationException();
    }
  }
}

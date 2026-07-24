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
import io.camunda.zeebe.protocol.record.value.TenantFilter;
import io.camunda.zeebe.scheduler.ActorSchedulingService;
import io.camunda.zeebe.scheduler.ConcurrencyControl;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.future.ActorFutureCollector;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import io.camunda.zeebe.transport.TransportFactory;
import io.camunda.zeebe.transport.stream.api.RemoteStreamService;
import io.camunda.zeebe.util.VisibleForTesting;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;

/**
 * Sets up one {@link JobStreamService} per physical tenant and registers it in the broker startup
 * context's job-stream-service map. Each service subscribes on its group's transport topics so that
 * streams registered for one physical tenant are never matched against jobs from another.
 */
public final class JobStreamServiceStep extends AbstractBrokerStartupStep {

  @Override
  void startupInternal(
      final BrokerStartupContext brokerStartupContext,
      final ConcurrencyControl concurrencyControl,
      final ActorFuture<BrokerStartupContext> startupFuture) {

    final var scheduler = brokerStartupContext.getActorSchedulingService();
    final var tenantIds = brokerStartupContext.getPhysicalTenantIds().known();

    final var futures =
        tenantIds.stream()
            .map(
                tenantId ->
                    startTenantService(
                        brokerStartupContext, tenantId, scheduler, concurrencyControl))
            .collect(new ActorFutureCollector<>(concurrencyControl));

    concurrencyControl.runOnCompletion(
        futures,
        (services, err) -> {
          if (err != null) {
            // Shut down any services that started successfully before completing exceptionally.
            // shutdownTenantService is a no-op for tenants whose service was never stored.
            final var cleanupFutures =
                tenantIds.stream()
                    .map(id -> shutdownTenantService(brokerStartupContext, id, concurrencyControl))
                    .collect(new ActorFutureCollector<>(concurrencyControl));
            concurrencyControl.runOnCompletion(
                cleanupFutures, (ignored, cleanupErr) -> startupFuture.completeExceptionally(err));
            return;
          }
          brokerStartupContext
              .getSpringBrokerBridge()
              .registerJobStreamServicesSupplier(
                  () ->
                      tenantIds.stream()
                          .map(brokerStartupContext::getJobStreamService)
                          .filter(Objects::nonNull)
                          .toList());
          startupFuture.complete(brokerStartupContext);
        });
  }

  @Override
  void shutdownInternal(
      final BrokerStartupContext brokerShutdownContext,
      final ConcurrencyControl concurrencyControl,
      final ActorFuture<BrokerStartupContext> shutdownFuture) {

    final var tenantIds = brokerShutdownContext.getPhysicalTenantIds().known();

    final var futures =
        tenantIds.stream()
            .map(
                tenantId ->
                    shutdownTenantService(brokerShutdownContext, tenantId, concurrencyControl))
            .collect(new ActorFutureCollector<>(concurrencyControl));

    concurrencyControl.runOnCompletion(
        futures,
        (ok, err) -> {
          brokerShutdownContext.getSpringBrokerBridge().registerJobStreamServicesSupplier(null);
          if (err != null) {
            shutdownFuture.completeExceptionally(err);
          } else {
            shutdownFuture.complete(brokerShutdownContext);
          }
        });
  }

  private ActorFuture<JobStreamService> startTenantService(
      final BrokerStartupContext brokerStartupContext,
      final String physicalTenantId,
      final ActorSchedulingService scheduler,
      final ConcurrencyControl concurrencyControl) {

    final var clusterServices = brokerStartupContext.getClusterServices();
    final var errorHandlerService =
        new RemoteJobStreamErrorHandlerService(new YieldingJobStreamErrorHandler());
    final RemoteStreamService<JobActivationProperties, ActivatedJob> remoteStreamService =
        new TransportFactory(scheduler)
            .createRemoteStreamServer(
                clusterServices.getCommunicationService(),
                JobStreamServiceStep::readJobActivationProperties,
                errorHandlerService,
                new JobStreamMetrics(brokerStartupContext.getMeterRegistry(), physicalTenantId),
                physicalTenantId);

    final var result = concurrencyControl.<JobStreamService>createFuture();
    final var errorHandlerStarted = scheduler.submitActor(errorHandlerService);

    errorHandlerStarted.onComplete(
        (ok, err) -> {
          if (err != null) {
            remoteStreamService.closeAsync(concurrencyControl);
            result.completeExceptionally(err);
            return;
          }

          remoteStreamService
              .start(scheduler, concurrencyControl)
              .onComplete(
                  (streamer, error) -> {
                    if (error != null) {
                      errorHandlerService.closeAsync();
                      remoteStreamService.closeAsync(concurrencyControl);
                      result.completeExceptionally(error);
                      return;
                    }

                    final var jobStreamService =
                        new JobStreamService(
                            remoteStreamService,
                            new RemoteJobStreamer(
                                streamer, clusterServices.getEventService(), physicalTenantId),
                            errorHandlerService);
                    clusterServices.getMembershipService().addListener(remoteStreamService);
                    brokerStartupContext.addJobStreamService(physicalTenantId, jobStreamService);
                    result.complete(jobStreamService);
                  });
        });

    return result;
  }

  private ActorFuture<Void> shutdownTenantService(
      final BrokerStartupContext ctx,
      final String physicalTenantId,
      final ConcurrencyControl concurrencyControl) {

    final var service = ctx.getJobStreamService(physicalTenantId);
    if (service == null) {
      return CompletableActorFuture.completed(null);
    }

    ctx.getClusterServices().getMembershipService().removeListener(service.remoteStreamService());
    ctx.removeJobStreamService(physicalTenantId);

    final var result = concurrencyControl.<Void>createFuture();
    service
        .closeAsync(concurrencyControl)
        .onComplete(
            (ignored, err) -> {
              if (err != null) {
                result.completeExceptionally(err);
              } else {
                result.complete(null);
              }
            });
    return result;
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
        mutable.tenantFilter(),
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
      TenantFilter tenantFilter,
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

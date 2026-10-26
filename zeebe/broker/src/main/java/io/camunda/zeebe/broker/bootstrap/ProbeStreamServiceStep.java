/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.bootstrap;

import io.camunda.zeebe.broker.jobstream.JobStreamMetrics;
import io.camunda.zeebe.broker.probestream.ProbeStreamErrorHandlerService;
import io.camunda.zeebe.broker.probestream.ProbeStreamService;
import io.camunda.zeebe.broker.probestream.RemoteProbeStreamer;
import io.camunda.zeebe.protocol.impl.stream.probe.ActivatedProbe;
import io.camunda.zeebe.protocol.impl.stream.probe.ProbeActivationProperties;
import io.camunda.zeebe.protocol.impl.stream.probe.ProbeActivationPropertiesImpl;
import io.camunda.zeebe.scheduler.ActorSchedulingService;
import io.camunda.zeebe.scheduler.ConcurrencyControl;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.future.ActorFutureCollector;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import io.camunda.zeebe.transport.TransportFactory;
import io.camunda.zeebe.transport.stream.api.RemoteStreamService;
import io.camunda.zeebe.transport.stream.api.StreamTopicNamespace;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

/**
 * Sets up one {@link ProbeStreamService} per physical tenant, as {@link JobStreamServiceStep} does
 * for job streams. Probe streams use their own topic namespace, so they never meet job streams.
 */
public final class ProbeStreamServiceStep extends AbstractBrokerStartupStep {

  private static final StreamTopicNamespace NAMESPACE = StreamTopicNamespace.PROBES;

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
            startupFuture.completeExceptionally(err);
          } else {
            startupFuture.complete(brokerStartupContext);
          }
        });
  }

  @Override
  void shutdownInternal(
      final BrokerStartupContext brokerShutdownContext,
      final ConcurrencyControl concurrencyControl,
      final ActorFuture<BrokerStartupContext> shutdownFuture) {
    final var futures =
        brokerShutdownContext.getPhysicalTenantIds().known().stream()
            .map(tenantId -> shutdownTenantService(brokerShutdownContext, tenantId))
            .collect(new ActorFutureCollector<>(concurrencyControl));

    concurrencyControl.runOnCompletion(
        futures,
        (ok, err) -> {
          if (err != null) {
            shutdownFuture.completeExceptionally(err);
          } else {
            shutdownFuture.complete(brokerShutdownContext);
          }
        });
  }

  @Override
  public String getName() {
    return "ProbeStreamService";
  }

  private ActorFuture<ProbeStreamService> startTenantService(
      final BrokerStartupContext brokerStartupContext,
      final String physicalTenantId,
      final ActorSchedulingService scheduler,
      final ConcurrencyControl concurrencyControl) {
    final var clusterServices = brokerStartupContext.getClusterServices();
    final var errorHandlerService = new ProbeStreamErrorHandlerService();
    final RemoteStreamService<ProbeActivationProperties, ActivatedProbe> remoteStreamService =
        new TransportFactory(scheduler)
            .createRemoteStreamServer(
                clusterServices.getCommunicationService(),
                ProbeStreamServiceStep::readProbeActivationProperties,
                errorHandlerService,
                // the job stream metrics are reused under the probe tenant tag; a POC shortcut
                new JobStreamMetrics(
                    brokerStartupContext.getMeterRegistry(),
                    NAMESPACE.prefix() + "-" + physicalTenantId),
                physicalTenantId,
                NAMESPACE);

    final var result = concurrencyControl.<ProbeStreamService>createFuture();
    scheduler
        .submitActor(errorHandlerService)
        .onComplete(
            (ok, err) -> {
              if (err != null) {
                result.completeExceptionally(err);
                return;
              }
              remoteStreamService
                  .start(scheduler, concurrencyControl)
                  .onComplete(
                      (streamer, error) -> {
                        if (error != null) {
                          errorHandlerService.closeAsync();
                          result.completeExceptionally(error);
                          return;
                        }
                        final var service =
                            new ProbeStreamService(
                                remoteStreamService,
                                new RemoteProbeStreamer(streamer),
                                errorHandlerService);
                        clusterServices.getMembershipService().addListener(remoteStreamService);
                        brokerStartupContext.addProbeStreamService(physicalTenantId, service);
                        result.complete(service);
                      });
            });
    return result;
  }

  private ActorFuture<?> shutdownTenantService(
      final BrokerStartupContext ctx, final String physicalTenantId) {
    final var service = ctx.getProbeStreamService(physicalTenantId);
    if (service == null) {
      return CompletableActorFuture.completed(null);
    }
    ctx.getClusterServices().getMembershipService().removeListener(service.remoteStreamService());
    ctx.removeProbeStreamService(physicalTenantId);
    return service.closeAsync(ctx.getConcurrencyControl());
  }

  /** Copies the metadata: the buffer belongs to the request and is reused after this returns. */
  private static ProbeActivationProperties readProbeActivationProperties(
      final DirectBuffer buffer) {
    final var bytes = new byte[buffer.capacity()];
    buffer.getBytes(0, bytes);
    final var properties = new ProbeActivationPropertiesImpl();
    properties.wrap(new UnsafeBuffer(bytes));
    return properties;
  }
}

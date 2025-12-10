/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.bootstrap;

import io.camunda.zeebe.broker.jobstream.ProcessInstanceStreamService;
import io.camunda.zeebe.broker.jobstream.RemoteProcessInstanceStreamer;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceRecord;
import io.camunda.zeebe.scheduler.ConcurrencyControl;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.transport.TransportFactory;
import io.camunda.zeebe.transport.stream.api.RemoteStreamErrorHandler;
import io.camunda.zeebe.transport.stream.api.RemoteStreamMetrics;
import io.camunda.zeebe.transport.stream.api.RemoteStreamService;

public class ProcessInstanceStreamServiceStep extends AbstractBrokerStartupStep {

  @Override
  void startupInternal(
      final BrokerStartupContext brokerStartupContext,
      final ConcurrencyControl concurrencyControl,
      final ActorFuture<BrokerStartupContext> startupFuture) {
    final var clusterServices = brokerStartupContext.getClusterServices();

    final var scheduler = brokerStartupContext.getActorSchedulingService();
    final RemoteStreamService<Long, ProcessInstanceRecord> remoteStreamService =
        new TransportFactory(scheduler)
            .createRemoteStreamServer(
                clusterServices.getCommunicationService(),
                b -> b.getLong(0),
                new RemoteProcessInstanceStreamErrorHandlerService(),
                RemoteStreamMetrics.noop());

    remoteStreamService
        .start(scheduler, concurrencyControl)
        .onComplete(
            (streamer, error) -> {
              if (error != null) {
                startupFuture.completeExceptionally(error);
                return;
              }

              final var processInstanceStreamService =
                  new ProcessInstanceStreamService(
                      remoteStreamService,
                      new RemoteProcessInstanceStreamer(
                          streamer, clusterServices.getEventService()));
              clusterServices.getMembershipService().addListener(remoteStreamService);
              brokerStartupContext.setProcessInstanceStreamService(processInstanceStreamService);
              startupFuture.complete(brokerStartupContext);
            });
  }

  @Override
  void shutdownInternal(
      final BrokerStartupContext brokerShutdownContext,
      final ConcurrencyControl concurrencyControl,
      final ActorFuture<BrokerStartupContext> shutdownFuture) {}

  @Override
  public String getName() {
    return "ProcessInstanceStreamService";
  }

  static final class RemoteProcessInstanceStreamErrorHandlerService
      implements RemoteStreamErrorHandler<ProcessInstanceRecord> {
    @Override
    public void handleError(final Throwable error, final ProcessInstanceRecord data) {
      // noop
    }
  }
}

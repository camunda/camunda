/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker.bootstrap;

import io.camunda.zeebe.broker.system.configuration.DataCfg;
import io.camunda.zeebe.broker.system.monitoring.DiskSpaceUsageMonitorActor;
import io.camunda.zeebe.scheduler.ConcurrencyControl;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.util.FileUtil;
import java.io.IOException;
import java.nio.file.Paths;

class DiskSpaceUsageMonitorStep extends AbstractBrokerStartupStep {

  @Override
  void startupInternal(
      final BrokerStartupContext brokerStartupContext,
      final ConcurrencyControl concurrencyControl,
      final ActorFuture<BrokerStartupContext> startupFuture) {

    final var data = brokerStartupContext.getBrokerConfiguration().getData();

    startDiskUsageMonitorActor(brokerStartupContext, startupFuture, data);
  }

  @Override
  void shutdownInternal(
      final BrokerStartupContext brokerShutdownContext,
      final ConcurrencyControl concurrencyControl,
      final ActorFuture<BrokerStartupContext> shutdownFuture) {

    final var diskSpaceUsageMonitor = brokerShutdownContext.getDiskSpaceUsageMonitor();
    if (diskSpaceUsageMonitor instanceof DiskSpaceUsageMonitorActor actor) {
      stopDiskUsageMonitorActor(brokerShutdownContext, concurrencyControl, shutdownFuture, actor);
    } else {
      shutdownFuture.complete(brokerShutdownContext);
    }
  }

  private static void startDiskUsageMonitorActor(
      final BrokerStartupContext brokerStartupContext,
      final ActorFuture<BrokerStartupContext> startupFuture,
      final DataCfg data) {
    try {
      FileUtil.ensureDirectoryExists(Paths.get(data.getDirectory()));
    } catch (final IOException e) {
      startupFuture.completeExceptionally(e);
      return;
    }

    final var diskSpaceUsageMonitor = new DiskSpaceUsageMonitorActor(data);

    final var actorStartFuture =
        brokerStartupContext.getActorSchedulingService().submitActor(diskSpaceUsageMonitor);

    brokerStartupContext
        .getConcurrencyControl()
        .runOnCompletion(
            actorStartFuture,
            (ok, error) -> {
              if (error != null) {
                startupFuture.completeExceptionally(error);
                return;
              }

              brokerStartupContext.setDiskSpaceUsageMonitor(diskSpaceUsageMonitor);
              startupFuture.complete(brokerStartupContext);
            });
  }

  private void stopDiskUsageMonitorActor(
      final BrokerStartupContext brokerShutdownContext,
      final ConcurrencyControl concurrencyControl,
      final ActorFuture<BrokerStartupContext> shutdownFuture,
      final DiskSpaceUsageMonitorActor actor) {
    final var closeFuture = actor.closeAsync();
    concurrencyControl.runOnCompletion(
        closeFuture,
        (ok, error) -> {
          if (error != null) {
            shutdownFuture.completeExceptionally(error);
            return;
          }

          forwardExceptions(
              () ->
                  concurrencyControl.run(
                      () ->
                          forwardExceptions(
                              () -> {
                                brokerShutdownContext.setDiskSpaceUsageMonitor(null);
                                shutdownFuture.complete(brokerShutdownContext);
                              },
                              shutdownFuture)),
              shutdownFuture);
        });
  }

  @Override
  public String getName() {
    return "Disk Space Usage Monitor";
  }
}

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker.bootstrap;

import io.camunda.zeebe.broker.system.monitoring.DiskSpaceUsageMonitor;
import io.camunda.zeebe.util.FileUtil;
import io.camunda.zeebe.util.sched.ConcurrencyControl;
import io.camunda.zeebe.util.sched.future.ActorFuture;
import java.io.IOException;
import java.nio.file.Paths;

class DiskSpaceUsageMonitorStep extends AbstractBrokerStartupStep {

  @Override
  void startupInternal(
      final BrokerStartupContext brokerStartupContext,
      final ConcurrencyControl concurrencyControl,
      final ActorFuture<BrokerStartupContext> startupFuture) {

    final var data = brokerStartupContext.getBrokerConfiguration().getData();
    try {
      FileUtil.ensureDirectoryExists(Paths.get(data.getDirectory()));
    } catch (final IOException e) {
      startupFuture.completeExceptionally(e);
      return;
    }

    final var diskSpaceUsageMonitor = new DiskSpaceUsageMonitor(data);

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

  @Override
  void shutdownInternal(
      final BrokerStartupContext brokerShutdownContext,
      final ConcurrencyControl concurrencyControl,
      final ActorFuture<BrokerStartupContext> shutdownFuture) {

    final var diskSpaceUsageMonitor = brokerShutdownContext.getDiskSpaceUsageMonitor();

    if (diskSpaceUsageMonitor != null) {
      final var closeFuture = diskSpaceUsageMonitor.closeAsync();
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
    } else {
      shutdownFuture.complete(brokerShutdownContext);
    }
  }

  @Override
  public String getName() {
    return "Disk Space Usage Monitor";
  }
}

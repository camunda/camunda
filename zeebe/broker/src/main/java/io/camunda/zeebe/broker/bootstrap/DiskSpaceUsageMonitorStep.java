/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.bootstrap;

import io.camunda.zeebe.broker.system.configuration.DataCfg;
import io.camunda.zeebe.broker.system.monitoring.DiskSpaceUsageListener;
import io.camunda.zeebe.broker.system.monitoring.DiskSpaceUsageMonitor;
import io.camunda.zeebe.broker.system.monitoring.DiskSpaceUsageMonitorActor;
import io.camunda.zeebe.scheduler.ConcurrencyControl;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import io.camunda.zeebe.util.FileUtil;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.function.LongSupplier;

class DiskSpaceUsageMonitorStep extends AbstractBrokerStartupStep {

  @Override
  void startupInternal(
      final BrokerStartupContext brokerStartupContext,
      final ConcurrencyControl concurrencyControl,
      final ActorFuture<BrokerStartupContext> startupFuture) {

    final var data = brokerStartupContext.getBrokerConfiguration().getData();

    if (!data.getDisk().isEnableMonitoring()) {
      brokerStartupContext.setDiskSpaceUsageMonitor(new DisabledDiskUsageMonitor());
      startupFuture.complete(brokerStartupContext);
      return;
    }

    startDiskUsageMonitorActor(brokerStartupContext, startupFuture, data);
  }

  @Override
  void shutdownInternal(
      final BrokerStartupContext brokerShutdownContext,
      final ConcurrencyControl concurrencyControl,
      final ActorFuture<BrokerStartupContext> shutdownFuture) {

    if (brokerShutdownContext.getDiskSpaceUsageMonitor() == null) {
      shutdownFuture.complete(brokerShutdownContext);
      return;
    }

    concurrencyControl.runOnCompletion(
        brokerShutdownContext.getDiskSpaceUsageMonitor().closeAsync(),
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

  private static void startDiskUsageMonitorActor(
      final BrokerStartupContext brokerStartupContext,
      final ActorFuture<BrokerStartupContext> startupFuture,
      final DataCfg data) {
    try {
      FileUtil.ensureDirectoryExists(Paths.get(data.getRootDirectory()));
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

  @Override
  public String getName() {
    return "Disk Space Usage Monitor";
  }

  private static final class DisabledDiskUsageMonitor implements DiskSpaceUsageMonitor {

    @Override
    public void addDiskUsageListener(final DiskSpaceUsageListener listener) {}

    @Override
    public void removeDiskUsageListener(final DiskSpaceUsageListener listener) {}

    @Override
    public void setFreeDiskSpaceSupplier(final LongSupplier freeDiskSpaceSupplier) {}

    @Override
    public ActorFuture<Void> closeAsync() {
      return CompletableActorFuture.completed(null);
    }
  }
}

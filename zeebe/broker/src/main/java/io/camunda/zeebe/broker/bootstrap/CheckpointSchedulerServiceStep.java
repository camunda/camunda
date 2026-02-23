/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.bootstrap;

import io.camunda.zeebe.broker.system.management.CheckpointSchedulingService;
import io.camunda.zeebe.scheduler.ConcurrencyControl;
import io.camunda.zeebe.scheduler.future.ActorFuture;

public class CheckpointSchedulerServiceStep extends AbstractBrokerStartupStep {

  @Override
  void startupInternal(
      final BrokerStartupContext brokerStartupContext,
      final ConcurrencyControl concurrencyControl,
      final ActorFuture<BrokerStartupContext> startupFuture) {

    final var backupCfg = brokerStartupContext.getBrokerConfiguration().getData().getBackup();
    final var scheduler = brokerStartupContext.getActorSchedulingService();
    final var meterRegistry = brokerStartupContext.getMeterRegistry();

    concurrencyControl.run(
        () -> {
          final CheckpointSchedulingService schedulingService =
              new CheckpointSchedulingService(
                  brokerStartupContext.getClusterServices().getMembershipService(),
                  scheduler,
                  backupCfg,
                  brokerStartupContext.getBrokerClient(),
                  meterRegistry);

          concurrencyControl.runOnCompletion(
              scheduler.submitActor(schedulingService),
              proceed(
                  () -> {
                    brokerStartupContext.setCheckpointSchedulingService(schedulingService);
                    startupFuture.complete(brokerStartupContext);
                  },
                  startupFuture));
        });
  }

  @Override
  void shutdownInternal(
      final BrokerStartupContext brokerShutdownContext,
      final ConcurrencyControl concurrencyControl,
      final ActorFuture<BrokerStartupContext> shutdownFuture) {
    final var schedulingService = brokerShutdownContext.getCheckpointSchedulingService();

    if (schedulingService != null) {
      concurrencyControl.runOnCompletion(
          schedulingService.closeAsync(),
          (ignored, error) -> {
            brokerShutdownContext.setCheckpointSchedulingService(null);
            shutdownFuture.complete(brokerShutdownContext);
          });
    } else {
      shutdownFuture.complete(brokerShutdownContext);
    }
  }

  @Override
  public String getName() {
    return "Checkpoint Scheduler";
  }
}

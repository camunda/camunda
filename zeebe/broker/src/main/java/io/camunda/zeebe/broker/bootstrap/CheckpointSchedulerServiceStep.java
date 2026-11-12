/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.bootstrap;

import io.camunda.zeebe.broker.system.configuration.backup.BackupCfg;
import io.camunda.zeebe.broker.system.management.CheckpointSchedulingService;
import io.camunda.zeebe.scheduler.ConcurrencyControl;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import java.util.LinkedHashMap;
import java.util.Map;

public class CheckpointSchedulerServiceStep extends AbstractBrokerStartupStep {

  @Override
  void startupInternal(
      final BrokerStartupContext brokerStartupContext,
      final ConcurrencyControl concurrencyControl,
      final ActorFuture<BrokerStartupContext> startupFuture) {

    final var scheduler = brokerStartupContext.getActorSchedulingService();
    final var meterRegistry = brokerStartupContext.getMeterRegistry();

    final Map<String, BackupCfg> backupCfgByPhysicalTenant = new LinkedHashMap<>();
    for (final var physicalTenantId : brokerStartupContext.getPhysicalTenantIds().known()) {
      backupCfgByPhysicalTenant.put(
          physicalTenantId,
          brokerStartupContext
              .getPhysicalTenantContext(physicalTenantId)
              .config()
              .getData()
              .getBackup());
    }

    concurrencyControl.run(
        () -> {
          final CheckpointSchedulingService schedulingService =
              new CheckpointSchedulingService(
                  brokerStartupContext.getClusterServices().getMembershipService(),
                  scheduler,
                  backupCfgByPhysicalTenant,
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

  /**
   * Schedules the checkpoints, backups and backup retention of a tenant whose module starts while
   * the broker runs, replacing what it had before; the tenants of the broker's start are scheduled
   * by this step.
   */
  static ActorFuture<Void> startTenantSchedulers(
      final BrokerStartupContext brokerStartupContext, final String physicalTenantId) {
    final var schedulingService = brokerStartupContext.getCheckpointSchedulingService();
    if (schedulingService == null) {
      return CompletableActorFuture.completed(null);
    }
    return schedulingService.putPhysicalTenant(
        physicalTenantId,
        brokerStartupContext
            .getPhysicalTenantContext(physicalTenantId)
            .config()
            .getData()
            .getBackup());
  }

  /** Stops the scheduled jobs of a tenant whose module stops while the broker runs. */
  static ActorFuture<Void> stopTenantSchedulers(
      final BrokerStartupContext brokerStartupContext, final String physicalTenantId) {
    final var schedulingService = brokerStartupContext.getCheckpointSchedulingService();
    if (schedulingService == null) {
      return CompletableActorFuture.completed(null);
    }
    return schedulingService.removePhysicalTenant(physicalTenantId);
  }

  @Override
  public String getName() {
    return "Checkpoint Scheduler";
  }
}

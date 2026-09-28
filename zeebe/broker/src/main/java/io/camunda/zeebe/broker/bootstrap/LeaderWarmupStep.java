/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.bootstrap;

import io.camunda.zeebe.broker.partitioning.PartitionManager;
import io.camunda.zeebe.broker.warmup.LeaderWarmup;
import io.camunda.zeebe.scheduler.ConcurrencyControl;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Starts the {@link LeaderWarmup}. It must run before the partition managers are created, since
 * they take a copy of the partition listeners.
 */
@NullMarked
final class LeaderWarmupStep extends AbstractBrokerStartupStep {

  private @Nullable LeaderWarmup warmup;

  @Override
  public String getName() {
    return "Leader Warm-up";
  }

  @Override
  void startupInternal(
      final BrokerStartupContext brokerStartupContext,
      final ConcurrencyControl concurrencyControl,
      final ActorFuture<BrokerStartupContext> startupFuture) {
    final var brokerCfg = brokerStartupContext.getBrokerConfiguration();
    warmup =
        new LeaderWarmup(
            brokerCfg.getExperimental().getLeaderWarmup(),
            brokerCfg,
            brokerStartupContext.getPhysicalTenantContext(PartitionManager.DEFAULT_GROUP_NAME),
            brokerStartupContext.getHealthCheckService(),
            brokerStartupContext.getMeterRegistry());
    brokerStartupContext.addPartitionRaftListener(warmup);
    warmup.start();
    startupFuture.complete(brokerStartupContext);
  }

  @Override
  void shutdownInternal(
      final BrokerStartupContext brokerShutdownContext,
      final ConcurrencyControl concurrencyControl,
      final ActorFuture<BrokerStartupContext> shutdownFuture) {
    final var current = warmup;
    if (current == null) {
      shutdownFuture.complete(brokerShutdownContext);
      return;
    }
    brokerShutdownContext.removePartitionRaftListener(current);
    warmup = null;
    current
        .closeAsync()
        .whenComplete(
            (ok, error) ->
                concurrencyControl.run(() -> shutdownFuture.complete(brokerShutdownContext)));
  }
}

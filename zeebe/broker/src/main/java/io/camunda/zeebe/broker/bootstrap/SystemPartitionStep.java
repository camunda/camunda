/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.bootstrap;

import io.atomix.primitive.partition.impl.DefaultPartitionManagementService;
import io.camunda.zeebe.broker.Loggers;
import io.camunda.zeebe.broker.system.syspartition.SystemPartition;
import io.camunda.zeebe.broker.system.syspartition.SystemPartitionFactory;
import io.camunda.zeebe.scheduler.ConcurrencyControl;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import org.slf4j.Logger;

/**
 * Bootstraps the {@link SystemPartition} when it is enabled. The step does not wait for the Raft
 * group to elect a leader, as that requires a quorum of brokers, which may still be starting.
 */
final class SystemPartitionStep extends AbstractBrokerStartupStep {
  private static final Logger LOG = Loggers.SYSTEM_LOGGER;

  @Override
  public String getName() {
    return "System Partition";
  }

  @Override
  void startupInternal(
      final BrokerStartupContext brokerStartupContext,
      final ConcurrencyControl concurrencyControl,
      final ActorFuture<BrokerStartupContext> startupFuture) {
    final var brokerCfg = brokerStartupContext.getBrokerConfiguration();
    if (!brokerCfg.getCluster().getSystemPartition().isEnabled()) {
      startupFuture.complete(brokerStartupContext);
      return;
    }

    final var clusterServices = brokerStartupContext.getClusterServices();
    final var factory =
        new SystemPartitionFactory(
            brokerCfg,
            brokerStartupContext.getMeterRegistry(),
            clusterServices.getCommunicationService(),
            clusterServices.getMembershipService().getLocalMember().id());
    concurrencyControl.runOnCompletion(
        factory.create(brokerStartupContext.getActorSchedulingService()),
        (systemPartition, error) -> {
          if (error != null) {
            startupFuture.completeExceptionally(error);
            return;
          }

          brokerStartupContext.setSystemPartition(systemPartition);
          systemPartition
              .bootstrap(
                  new DefaultPartitionManagementService(
                      clusterServices.getMembershipService(),
                      clusterServices.getCommunicationService()))
              .whenComplete(
                  (ok, bootstrapError) -> {
                    if (bootstrapError != null) {
                      LOG.error("Failed to bootstrap the system partition", bootstrapError);
                    } else {
                      LOG.info("Bootstrapped the system partition");
                      systemPartition.start(brokerStartupContext.getActorSchedulingService());
                    }
                  });
          startupFuture.complete(brokerStartupContext);
        });
  }

  @Override
  void shutdownInternal(
      final BrokerStartupContext brokerShutdownContext,
      final ConcurrencyControl concurrencyControl,
      final ActorFuture<BrokerStartupContext> shutdownFuture) {
    final var systemPartition = brokerShutdownContext.getSystemPartition();
    if (systemPartition == null) {
      shutdownFuture.complete(brokerShutdownContext);
      return;
    }

    concurrencyControl.runOnCompletion(
        SystemPartitionFactory.close(systemPartition, concurrencyControl),
        (ok, error) -> {
          brokerShutdownContext.setSystemPartition(null);
          if (error != null) {
            shutdownFuture.completeExceptionally(error);
          } else {
            shutdownFuture.complete(brokerShutdownContext);
          }
        });
  }
}

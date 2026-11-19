/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.bootstrap;

import io.camunda.zeebe.broker.Loggers;
import io.camunda.zeebe.dynamic.config.state.CurrentClusterConfiguration;
import io.camunda.zeebe.scheduler.ConcurrencyControl;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import java.util.Set;
import java.util.TreeSet;
import org.slf4j.Logger;

/**
 * Warns about partition groups in the persisted cluster topology that no configured physical tenant
 * matches. Partition groups can never be removed from the topology, so a renamed tenant leaves its
 * old group behind. The check only warns: failing would block broker startup for a situation that
 * is otherwise harmless.
 */
final class OrphanedPartitionGroupCheckStep extends AbstractBrokerStartupStep {
  private static final Logger LOGGER = Loggers.SYSTEM_LOGGER;

  @Override
  public String getName() {
    return "Orphaned Partition Group Check";
  }

  @Override
  void startupInternal(
      final BrokerStartupContext brokerStartupContext,
      final ConcurrencyControl concurrencyControl,
      final ActorFuture<BrokerStartupContext> startupFuture) {
    try {
      final var configuration =
          brokerStartupContext.getClusterConfigurationService().getInitialClusterConfiguration();
      warnAboutOrphanedGroups(
          LOGGER, configuration, brokerStartupContext.getPhysicalTenantIds().known());
    } catch (final RuntimeException e) {
      LOGGER.debug("Skipping the check for orphaned partition groups", e);
    }
    startupFuture.complete(brokerStartupContext);
  }

  @Override
  void shutdownInternal(
      final BrokerStartupContext brokerShutdownContext,
      final ConcurrencyControl concurrencyControl,
      final ActorFuture<BrokerStartupContext> shutdownFuture) {
    shutdownFuture.complete(brokerShutdownContext);
  }

  static void warnAboutOrphanedGroups(
      final Logger logger,
      final CurrentClusterConfiguration configuration,
      final Set<String> configuredTenantIds) {
    if (configuration == null) {
      return;
    }
    final var orphaned = new TreeSet<>(configuration.partitionGroups().keySet());
    orphaned.removeAll(configuredTenantIds);
    for (final var group : orphaned) {
      logger.warn(
          "The cluster topology contains partition group '{}' but no configured physical tenant"
              + " matches it (configured: {}). This is likely caused by a renamed physical tenant."
              + " Partition groups cannot be removed from the topology, so the group stays in the"
              + " topology. If the tenant was renamed, restore the previous name.",
          group,
          new TreeSet<>(configuredTenantIds));
    }
  }
}

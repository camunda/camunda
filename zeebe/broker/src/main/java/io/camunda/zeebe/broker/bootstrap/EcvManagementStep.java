/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.bootstrap;

import io.camunda.zeebe.broker.Loggers;
import io.camunda.zeebe.broker.ecv.EcvManagement;
import io.camunda.zeebe.broker.ecv.LocalEnabledCapabilitiesView;
import io.camunda.zeebe.broker.ecv.UpgradeReadinessRegistry;
import io.camunda.zeebe.broker.partitioning.topology.DynamicClusterConfigurationService;
import io.camunda.zeebe.broker.system.syspartition.SystemPartition;
import io.camunda.zeebe.scheduler.ConcurrencyControl;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.util.ecv.Capability;
import io.camunda.zeebe.util.ecv.CapabilitySet;
import java.nio.file.Path;
import org.slf4j.Logger;

/**
 * Starts the ECV tracking and commit. It needs the system partition, so it runs only when the
 * system partition is enabled. Without it, the broker starts its partitions without a check, as
 * before.
 */
final class EcvManagementStep extends AbstractBrokerStartupStep {
  static final String DIRECTORY = "ecv";
  private static final Logger LOG = Loggers.SYSTEM_LOGGER;

  @Override
  public String getName() {
    return "ECV Management";
  }

  @Override
  void startupInternal(
      final BrokerStartupContext context,
      final ConcurrencyControl concurrencyControl,
      final ActorFuture<BrokerStartupContext> startupFuture) {
    final var brokerCfg = context.getBrokerConfiguration();
    final var systemPartitionCfg = brokerCfg.getCluster().getSystemPartition();
    if (!systemPartitionCfg.isEnabled()) {
      startupFuture.complete(context);
      return;
    }

    final var cfg = systemPartitionCfg.getEcv();
    final var supported =
        cfg.getSupportedCapabilities() == null
            ? Capability.all()
            : CapabilitySet.of(
                cfg.getSupportedCapabilities().stream().map(Capability::valueOf).toList());
    final var clusterServices = context.getClusterServices();
    final var view =
        new LocalEnabledCapabilitiesView(
            Path.of(brokerCfg.getData().getDirectory()).resolve(DIRECTORY));
    final var ecvManagement =
        new EcvManagement(
            cfg,
            clusterServices.getMembershipService().getLocalMember().id(),
            clusterServices.getMembershipService(),
            clusterServices.getCommunicationService(),
            SystemPartition.members(brokerCfg),
            context.getSystemPartition(),
            supported,
            view,
            new UpgradeReadinessRegistry());
    final var configurationService = context.getClusterConfigurationService();
    if (configurationService instanceof final DynamicClusterConfigurationService dynamic) {
      ecvManagement.setClusterConfiguration(dynamic.getCurrentClusterConfiguration());
    }
    configurationService.addUpdateListener(ecvManagement.configurationListener());
    LOG.info(
        "Starting the ECV management; this broker supports {}, its local view of the enabled"
            + " capabilities is {}",
        supported,
        view.enabled());

    concurrencyControl.runOnCompletion(
        context.getActorSchedulingService().submitActor(ecvManagement),
        (ok, error) -> {
          if (error != null) {
            startupFuture.completeExceptionally(error);
            return;
          }
          context.setEcvManagement(ecvManagement);
          context.getSpringBrokerBridge().registerEcvManagementSupplier(context::getEcvManagement);
          startupFuture.complete(context);
        });
  }

  @Override
  void shutdownInternal(
      final BrokerStartupContext context,
      final ConcurrencyControl concurrencyControl,
      final ActorFuture<BrokerStartupContext> shutdownFuture) {
    final var ecvManagement = context.getEcvManagement();
    if (ecvManagement == null) {
      shutdownFuture.complete(context);
      return;
    }
    context
        .getClusterConfigurationService()
        .removeUpdateListener(ecvManagement.configurationListener());
    concurrencyControl.runOnCompletion(
        ecvManagement.closeAsync(),
        (ok, error) -> {
          context.setEcvManagement(null);
          if (error != null) {
            shutdownFuture.completeExceptionally(error);
          } else {
            shutdownFuture.complete(context);
          }
        });
  }
}

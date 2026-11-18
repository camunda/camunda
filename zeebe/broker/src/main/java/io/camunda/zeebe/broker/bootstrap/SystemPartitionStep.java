/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.bootstrap;

import io.camunda.zeebe.broker.systempartition.SystemPartitionService;
import io.camunda.zeebe.scheduler.ConcurrencyControl;
import io.camunda.zeebe.scheduler.future.ActorFuture;

/**
 * Starts the system partition (design 5.4). It runs before the dynamic configuration starts, thus
 * it uses only local data, the static configuration and the probe. It does not block the start of
 * the broker: the system partition starts in the background.
 */
final class SystemPartitionStep extends AbstractBrokerStartupStep {

  @Override
  public String getName() {
    return "System Partition";
  }

  @Override
  void startupInternal(
      final BrokerStartupContext brokerStartupContext,
      final ConcurrencyControl concurrencyControl,
      final ActorFuture<BrokerStartupContext> startupFuture) {
    final var clusterServices = brokerStartupContext.getClusterServices();
    final var service =
        new SystemPartitionService(
            brokerStartupContext.getBrokerConfiguration(),
            clusterServices.getMembershipService(),
            clusterServices.getCommunicationService(),
            brokerStartupContext.getMeterRegistry(),
            brokerStartupContext::getClusterConfigurationService);
    service.start();
    brokerStartupContext.setSystemPartitionService(service);
    startupFuture.complete(brokerStartupContext);
  }

  @Override
  void shutdownInternal(
      final BrokerStartupContext brokerStartupContext,
      final ConcurrencyControl concurrencyControl,
      final ActorFuture<BrokerStartupContext> shutdownFuture) {
    final var service = brokerStartupContext.getSystemPartitionService();
    if (service != null) {
      service.close();
      brokerStartupContext.setSystemPartitionService(null);
    }
    shutdownFuture.complete(brokerStartupContext);
  }
}

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config;

import io.atomix.cluster.ClusterMembershipService;
import io.atomix.cluster.messaging.ClusterCommunicationService;
import io.camunda.zeebe.dynamic.config.gossip.ClusterConfigurationGossiperConfig;
import io.camunda.zeebe.scheduler.ActorScheduler;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.micrometer.core.instrument.MeterRegistry;

/**
 * Minimal service required by the gateway: it only listens to cluster configuration changes and
 * relays them via gossip, it never makes changes to the configuration itself.
 */
public interface GatewayClusterConfigurationService
    extends ClusterConfigurationUpdateNotifier, AutoCloseable {

  /** Submits the service to the scheduler; the future completes once gossiping has started. */
  ActorFuture<Void> start(ActorScheduler scheduler);

  @Override
  void close();

  static GatewayClusterConfigurationService create(
      final ClusterCommunicationService communicationService,
      final ClusterMembershipService membershipService,
      final ClusterConfigurationGossiperConfig config,
      final MeterRegistry meterRegistry) {
    return ClusterConfigurationServiceProvider.load()
        .newGatewayService(communicationService, membershipService, config, meterRegistry);
  }
}

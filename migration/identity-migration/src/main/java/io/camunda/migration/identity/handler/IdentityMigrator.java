/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.identity.handler;

import io.camunda.migration.api.MigrationException;
import io.camunda.migration.api.Migrator;
import io.camunda.migration.identity.config.IdentityMigrationProperties;
import io.camunda.migration.identity.config.cluster.ClusterProperties;
import io.camunda.zeebe.broker.client.api.BrokerTopologyManager;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

@EnableConfigurationProperties(IdentityMigrationProperties.class)
@Component("identity-migrator")
public class IdentityMigrator implements Migrator {
  private static final Logger LOG = LoggerFactory.getLogger(IdentityMigrator.class);

  private final List<MigrationHandler<?>> handlers;
  private final BrokerTopologyManager brokerTopologyManager;
  private final IdentityMigrationProperties identityMigrationProperties;

  public IdentityMigrator(
      final List<MigrationHandler<?>> handlers,
      final BrokerTopologyManager brokerTopologyManager,
      final IdentityMigrationProperties identityMigrationProperties) {
    this.handlers = handlers;
    this.brokerTopologyManager = brokerTopologyManager;
    this.identityMigrationProperties = identityMigrationProperties;
  }

  @Override
  public Void call() {
    awaitClusterJoin();
    migrate();
    return null;
  }

  private void migrate() {
    for (final MigrationHandler<?> handler : handlers) {
      LOG.info("Starting {}", handler.getName());
      try {
        handler.migrate();
      } catch (final Exception e) {
        throw new MigrationException("Execution of %s failed".formatted(handler.getName()), e);
      }
      handler.logSummary();
    }
  }

  private void awaitClusterJoin() {
    final ClusterProperties clusterConfig = identityMigrationProperties.getCluster();
    int remainingClusterStateAttempts = clusterConfig.getAwaitClusterJoinMaxAttempts();
    boolean joinedCluster = false;
    do {
      joinedCluster = brokerTopologyManager.getTopology().isInitialized();
      LOG.info("Waiting for Orchestration Cluster Broker availability...");
      try {
        Thread.sleep(clusterConfig.getAwaitClusterJoinRetryInterval().toMillis());
      } catch (final InterruptedException e) {
        throw new RuntimeException(e);
      }
      remainingClusterStateAttempts--;
    } while (!joinedCluster && remainingClusterStateAttempts > 0);

    if (joinedCluster) {
      LOG.info("Successfully connected to Orchestration Cluster, starting migration.");
      LOG.info(
          "List of brokers in the cluster: {}", brokerTopologyManager.getTopology().getBrokers());
      LOG.info(
          "List of brokers addresses: {}",
          brokerTopologyManager.getTopology().getBrokers().stream()
              .map(i -> brokerTopologyManager.getTopology().getBrokerAddress(i))
              .toList());
    } else {
      final String message =
          "Failed to connect to Orchestration Cluster within %s."
              .formatted(
                  clusterConfig
                      .getAwaitClusterJoinRetryInterval()
                      .multipliedBy(clusterConfig.getAwaitClusterJoinMaxAttempts()));
      LOG.error(message);
      throw new MigrationException(message);
    }
  }
}

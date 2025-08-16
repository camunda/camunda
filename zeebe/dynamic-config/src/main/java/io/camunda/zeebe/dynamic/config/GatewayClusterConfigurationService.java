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
import io.camunda.zeebe.dynamic.config.gossip.ClusterConfigurationGossiper;
import io.camunda.zeebe.dynamic.config.gossip.ClusterConfigurationGossiperConfig;
import io.camunda.zeebe.dynamic.config.metrics.TopologyMetrics;
import io.camunda.zeebe.dynamic.config.serializer.ProtoBufSerializer;
import io.camunda.zeebe.dynamic.config.state.ClusterConfiguration;
import io.camunda.zeebe.scheduler.Actor;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The GatewayClusterConfigurationService contains minimal functionality required for the Gateway.
 * The Gateway only listens to ClusterConfiguration changes. It cannot make changes to the
 * configuration. So the service does not run ClusterConfigurationManager, but only contains the
 * ClusterConfigurationGossiper.
 */
public class GatewayClusterConfigurationService extends Actor
    implements ClusterConfigurationUpdateNotifier {
  private static final Logger LOG =
      LoggerFactory.getLogger(GatewayClusterConfigurationService.class);
  private final ClusterConfigurationGossiper clusterConfigurationGossiper;

  // Keep an in memory copy of the configuration. No need to persist it.
  private ClusterConfiguration clusterConfiguration = ClusterConfiguration.uninitialized();
  private final TopologyMetrics topologyMetrics;

  public GatewayClusterConfigurationService(
      final ClusterCommunicationService communicationService,
      final ClusterMembershipService memberShipService,
      final ClusterConfigurationGossiperConfig config,
      final MeterRegistry meterRegistry) {
    topologyMetrics = new TopologyMetrics(meterRegistry);
    clusterConfigurationGossiper =
        new ClusterConfigurationGossiper(
            this,
            communicationService,
            memberShipService,
            new ProtoBufSerializer(),
            config,
            this::updateClusterTopology,
            topologyMetrics);
  }

  @Override
  public String getName() {
    return "GatewayClusterConfigurationService";
  }

  @Override
  protected void onActorStarting() {
    LOG.info("Starting Cluster Configuration Manager");
    clusterConfigurationGossiper
        .start()
        .onComplete(
            (ignore, error) -> {
              if (error != null) {
                LOG.error("Failed to start cluster configuration gossiper", error);
              } else {
                LOG.info("Started Cluster Configuration Manager");
              }
            });
  }

  @Override
  protected void onActorClosing() {
    clusterConfigurationGossiper.close();
  }

  private void updateClusterTopology(final ClusterConfiguration clusterConfiguration) {

    actor.run(
        () -> {
          if (clusterConfiguration == null || clusterConfiguration.isUninitialized()) {
            return;
          }

          try {
            final var mergedTopology = this.clusterConfiguration.merge(clusterConfiguration);
            if (mergedTopology.equals(this.clusterConfiguration)) {
              return;
            }
            LOG.debug(
                "Received new configuration {}. Updating local configuration to {}",
                clusterConfiguration,
                mergedTopology);
            this.clusterConfiguration = mergedTopology;
            clusterConfigurationGossiper.updateClusterConfiguration(this.clusterConfiguration);
          } catch (final Exception updateFailed) {
            LOG.warn(
                "Failed to process received configuration update {}",
                clusterConfiguration,
                updateFailed);
          }
        });
  }

  @Override
  public void addUpdateListener(final ClusterConfigurationUpdateListener listener) {
    clusterConfigurationGossiper.addUpdateListener(listener);
  }

  @Override
  public void removeUpdateListener(final ClusterConfigurationUpdateListener listener) {
    clusterConfigurationGossiper.removeUpdateListener(listener);
  }
}

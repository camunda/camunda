/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.topology;

import io.atomix.cluster.ClusterMembershipService;
import io.atomix.cluster.messaging.ClusterCommunicationService;
import io.camunda.zeebe.scheduler.Actor;
import io.camunda.zeebe.topology.gossip.ClusterConfigurationGossiper;
import io.camunda.zeebe.topology.gossip.ClusterConfigurationGossiperConfig;
import io.camunda.zeebe.topology.serializer.ProtoBufSerializer;
import io.camunda.zeebe.topology.state.ClusterConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The GatewayClusterTopologyService contains minimal functionality required for the Gateway. The
 * Gateway only listens to ClusterTopology changes. It cannot make changes to the topology. So the
 * service does not run ClusterTopologyManager, but only contains the ClusterTopologyGossiper.
 */
public class GatewayClusterConfigurationService extends Actor implements
    ClusterConfigurationUpdateNotifier {
  private static final Logger LOG =
      LoggerFactory.getLogger(GatewayClusterConfigurationService.class);
  private final ClusterConfigurationGossiper clusterConfigurationGossiper;

  // Keep an in memory copy of the topology. No need to persist it.
  private ClusterConfiguration clusterConfiguration = ClusterConfiguration.uninitialized();

  public GatewayClusterConfigurationService(
      final ClusterCommunicationService communicationService,
      final ClusterMembershipService memberShipService,
      final ClusterConfigurationGossiperConfig config) {
    clusterConfigurationGossiper =
        new ClusterConfigurationGossiper(
            this,
            communicationService,
            memberShipService,
            new ProtoBufSerializer(),
            config,
            this::updateClusterTopology);
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
                "Received new topology {}. Updating local topology to {}",
                clusterConfiguration,
                mergedTopology);
            this.clusterConfiguration = mergedTopology;
            clusterConfigurationGossiper.updateClusterTopology(this.clusterConfiguration);
          } catch (final Exception updateFailed) {
            LOG.warn(
                "Failed to process received topology update {}",
                clusterConfiguration,
                updateFailed);
          }
        });
  }

  @Override
  protected void onActorStarting() {
    clusterConfigurationGossiper.start();
  }

  @Override
  protected void onActorClosing() {
    clusterConfigurationGossiper.close();
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

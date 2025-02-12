/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.topology;

import io.atomix.cluster.ClusterMembershipService;
import io.atomix.cluster.messaging.ClusterCommunicationService;
import io.camunda.zeebe.scheduler.Actor;
import io.camunda.zeebe.topology.gossip.ClusterTopologyGossiper;
import io.camunda.zeebe.topology.gossip.ClusterTopologyGossiperConfig;
import io.camunda.zeebe.topology.metrics.TopologyMetrics;
import io.camunda.zeebe.topology.serializer.ProtoBufSerializer;
import io.camunda.zeebe.topology.state.ClusterTopology;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The GatewayClusterTopologyService contains minimal functionality required for the Gateway. The
 * Gateway only listens to ClusterTopology changes. It cannot make changes to the topology. So the
 * service does not run ClusterTopologyManager, but only contains the ClusterTopologyGossiper.
 */
public class GatewayClusterTopologyService extends Actor implements TopologyUpdateNotifier {
  private static final Logger LOG = LoggerFactory.getLogger(GatewayClusterTopologyService.class);
  private final ClusterTopologyGossiper clusterTopologyGossiper;

  // Keep an in memory copy of the topology. No need to persist it.
  private ClusterTopology clusterTopology = ClusterTopology.uninitialized();
  private final TopologyMetrics topologyMetrics;

  public GatewayClusterTopologyService(
      final ClusterCommunicationService communicationService,
      final ClusterMembershipService memberShipService,
      final ClusterTopologyGossiperConfig config,
      final MeterRegistry meterRegistry) {
    topologyMetrics = new TopologyMetrics(meterRegistry);
    clusterTopologyGossiper =
        new ClusterTopologyGossiper(
            this,
            communicationService,
            memberShipService,
            new ProtoBufSerializer(),
            config,
            this::updateClusterTopology,
            topologyMetrics);
  }

  private void updateClusterTopology(final ClusterTopology clusterTopology) {

    actor.run(
        () -> {
          if (clusterTopology == null || clusterTopology.isUninitialized()) {
            return;
          }

          try {
            final var mergedTopology = this.clusterTopology.merge(clusterTopology);
            if (mergedTopology.equals(this.clusterTopology)) {
              return;
            }
            LOG.debug(
                "Received new topology {}. Updating local topology to {}",
                clusterTopology,
                mergedTopology);
            this.clusterTopology = mergedTopology;
            clusterTopologyGossiper.updateClusterTopology(this.clusterTopology);
          } catch (final Exception updateFailed) {
            LOG.warn(
                "Failed to process received topology update {}", clusterTopology, updateFailed);
          }
        });
  }

  @Override
  protected void onActorStarting() {
    clusterTopologyGossiper.start();
  }

  @Override
  protected void onActorClosing() {
    clusterTopologyGossiper.close();
  }

  @Override
  public void addUpdateListener(final TopologyUpdateListener listener) {
    clusterTopologyGossiper.addUpdateListener(listener);
  }

  @Override
  public void removeUpdateListener(final TopologyUpdateListener listener) {
    clusterTopologyGossiper.removeUpdateListener(listener);
  }
}

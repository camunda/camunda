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
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import io.camunda.zeebe.topology.gossip.ClusterTopologyGossiper;
import io.camunda.zeebe.topology.gossip.ClusterTopologyGossiperConfig;
import io.camunda.zeebe.topology.serializer.ProtoBufSerializer;
import io.camunda.zeebe.topology.state.ClusterTopology;
import java.util.HashSet;
import java.util.Set;

/**
 * The GatewayClusterTopologyService contains minimal functionality required for the Gateway. The
 * Gateway only listens to ClusterTopology changes. It cannot make changes to the topology. So the
 * service does not run ClusterTopologyManager, but only contains the ClusterTopologyGossiper.
 */
public class GatewayClusterTopologyService extends Actor {

  private final Set<Listener> topologyChangeListeners = new HashSet<>();
  private final ClusterTopologyGossiper clusterTopologyGossiper;

  // Keep an in memory copy of the topology. No need to persist it.
  private ClusterTopology clusterTopology = ClusterTopology.uninitialized();

  public GatewayClusterTopologyService(
      final ClusterCommunicationService communicationService,
      final ClusterMembershipService memberShipService,
      final ClusterTopologyGossiperConfig config) {
    clusterTopologyGossiper =
        new ClusterTopologyGossiper(
            this,
            communicationService,
            memberShipService,
            new ProtoBufSerializer(),
            config,
            this::updateClusterTopology);
  }

  private ActorFuture<ClusterTopology> updateClusterTopology(
      final ClusterTopology clusterTopology) {
    final CompletableActorFuture<ClusterTopology> updated = new CompletableActorFuture<>();
    actor.run(
        () -> {
          try {
            final var updateTopology = this.clusterTopology.merge(clusterTopology);
            if (!updateTopology.equals(this.clusterTopology)) {
              this.clusterTopology = updateTopology;
              topologyChangeListeners.forEach(
                  listener -> listener.onClusterTopologyChanged(updateTopology));
            }
            updated.complete(updateTopology);
          } catch (final Exception updateFailed) {
            updated.completeExceptionally(updateFailed);
          }
        });

    return updated;
  }

  @Override
  protected void onActorStarting() {
    clusterTopologyGossiper.start();
  }

  @Override
  protected void onActorClosing() {
    clusterTopologyGossiper.closeAsync();
  }

  public void registerClusterTopologyChangeListener(final Listener listener) {
    actor.run(
        () -> {
          topologyChangeListeners.add(listener);
          if (!clusterTopology.isUninitialized()) {
            listener.onClusterTopologyChanged(clusterTopology);
          }
        });
  }

  public void removeClusterTopologyChangeListener(final Listener listener) {
    actor.run(() -> topologyChangeListeners.remove(listener));
  }

  public interface Listener {
    void onClusterTopologyChanged(ClusterTopology clusterTopology);
  }
}

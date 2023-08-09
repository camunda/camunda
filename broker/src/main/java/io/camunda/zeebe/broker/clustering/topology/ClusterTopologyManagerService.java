/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker.clustering.topology;

import io.camunda.zeebe.broker.system.configuration.BrokerCfg;
import io.camunda.zeebe.scheduler.Actor;
import io.camunda.zeebe.scheduler.ActorSchedulingService;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import java.nio.file.Path;

public final class ClusterTopologyManagerService extends Actor {

  private final ClusterTopologyManager clusterTopologyManager;

  public ClusterTopologyManagerService(final Path topologyFile) {
    clusterTopologyManager =
        new ClusterTopologyManager(this, new PersistedClusterTopology(topologyFile));
  }

  /**
   * NOTE: Do not integrate this into Broker startup steps until data layout and serialization
   * `ClusterTopology` is finalized. This is to prevent any backward incompatible changes to be
   * included in a released version.
   *
   * <p>Starts ClusterTopologyManager which initializes ClusterTopology
   */
  ActorFuture<Void> start(
      final ActorSchedulingService actorSchedulingService, final BrokerCfg brokerCfg) {
    final var startFuture = new CompletableActorFuture<Void>();
    actorSchedulingService
        .submitActor(this)
        .onComplete(
            (ignore, error) -> {
              if (error == null) {
                actor.run(() -> startClusterTopologyManager(brokerCfg, startFuture));
              } else {
                startFuture.completeExceptionally(error);
              }
            });

    return startFuture;
  }

  private void startClusterTopologyManager(
      final BrokerCfg brokerCfg, final CompletableActorFuture<Void> startFuture) {
    clusterTopologyManager.start(brokerCfg).onComplete(startFuture);
  }
}

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.topology;

import io.camunda.zeebe.scheduler.ConcurrencyControl;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.topology.state.ClusterTopology;
import java.io.IOException;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Responsible for initializing ClusterTopology and managing ClusterTopology changes. */
final class ClusterTopologyManager {
  private static final Logger LOG = LoggerFactory.getLogger(ClusterTopologyManager.class);

  private final ConcurrencyControl executor;
  private final PersistedClusterTopology persistedClusterTopology;

  private Consumer<ClusterTopology> topologyGossiper;
  private final ActorFuture<Void> startFuture;

  ClusterTopologyManager(
      final ConcurrencyControl executor, final PersistedClusterTopology persistedClusterTopology) {
    this.executor = executor;
    this.persistedClusterTopology = persistedClusterTopology;
    startFuture = executor.createFuture();
  }

  ActorFuture<ClusterTopology> getClusterTopology() {
    return executor.call(persistedClusterTopology::getTopology);
  }

  ActorFuture<Void> start(final TopologyInitializer topologyInitializer) {
    executor.run(
        () -> {
          if (startFuture.isDone()) {
            return;
          }
          initialize(topologyInitializer);
        });

    return startFuture;
  }

  ActorFuture<ClusterTopology> onGossipReceived(final ClusterTopology receivedTopology) {
    final ActorFuture<ClusterTopology> result = executor.createFuture();
    executor.run(
        () -> {
          try {
            if (receivedTopology != null) {
              final var mergedTopology =
                  persistedClusterTopology.getTopology().merge(receivedTopology);
              updateLocalTopology(mergedTopology);
            }
            result.complete(persistedClusterTopology.getTopology());
          } catch (final IOException error) {
            result.completeExceptionally(error);
          }
        });

    return result;
  }

  public void setTopologyGossiper(final Consumer<ClusterTopology> topologyGossiper) {
    this.topologyGossiper = topologyGossiper;
  }

  private void initialize(final TopologyInitializer topologyInitializer) {
    topologyInitializer
        .initialize()
        .onComplete(
            (topology, error) -> {
              if (error != null) {
                LOG.error("Failed to initialize topology", error);
                startFuture.completeExceptionally(error);
              } else if (topology.isUninitialized()) {
                final String errorMessage =
                    "Expected to initialize topology, but got uninitialized topology";
                LOG.error(errorMessage);
                startFuture.completeExceptionally(new IllegalStateException(errorMessage));
              } else {
                try {
                  persistedClusterTopology.update(topology);
                  topologyGossiper.accept(persistedClusterTopology.getTopology());
                  setStarted();
                } catch (final IOException e) {
                  startFuture.completeExceptionally("Failed to start update cluster topology", e);
                }
              }
            });
  }

  private void setStarted() {
    if (!startFuture.isDone()) {
      startFuture.complete(null);
    }
  }

  private void updateLocalTopology(final ClusterTopology topology) throws IOException {
    persistedClusterTopology.update(topology);
  }
}

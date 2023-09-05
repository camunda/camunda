/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.topology;

import io.atomix.cluster.MemberId;
import io.camunda.zeebe.scheduler.ConcurrencyControl;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.topology.state.ClusterTopology;
import io.camunda.zeebe.topology.state.MemberState;
import io.camunda.zeebe.topology.state.TopologyChangeOperation;
import io.camunda.zeebe.util.Either;
import java.io.IOException;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Responsible for initializing ClusterTopology and managing ClusterTopology changes. */
final class ClusterTopologyManager {
  private static final Logger LOG = LoggerFactory.getLogger(ClusterTopologyManager.class);

  private final ConcurrencyControl executor;
  private final PersistedClusterTopology persistedClusterTopology;

  private Consumer<ClusterTopology> topologyGossiper;
  private final ActorFuture<Void> startFuture;

  private final TopologyChangeAppliers operationsAppliers;
  private final MemberId localMemberId;

  ClusterTopologyManager(
      final ConcurrencyControl executor,
      final MemberId localMemberId,
      final PersistedClusterTopology persistedClusterTopology,
      final TopologyChangeAppliers operationsAppliers) {
    this.executor = executor;
    this.persistedClusterTopology = persistedClusterTopology;
    startFuture = executor.createFuture();
    this.operationsAppliers = operationsAppliers;
    this.localMemberId = localMemberId;
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
                  // merge in case there was a concurrent update via gossip
                  persistedClusterTopology.update(
                      topology.merge(persistedClusterTopology.getTopology()));
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

  ActorFuture<ClusterTopology> onGossipReceived(final ClusterTopology receivedTopology) {
    final ActorFuture<ClusterTopology> result = executor.createFuture();
    executor.run(
        () -> {
          try {
            if (receivedTopology != null) {
              final var mergedTopology =
                  persistedClusterTopology.getTopology().merge(receivedTopology);
              if (!mergedTopology.equals(persistedClusterTopology.getTopology())) {
                persistedClusterTopology.update(mergedTopology);
                if (mergedTopology.hasPendingChangesFor(localMemberId)) {
                  applyTopologyChangeOperation(mergedTopology);
                }
              }
            }
            result.complete(persistedClusterTopology.getTopology());
          } catch (final IOException error) {
            result.completeExceptionally(error);
          }
        });

    return result;
  }

  private void applyTopologyChangeOperation(final ClusterTopology mergedTopology)
      throws IOException {
    final var operation = mergedTopology.pendingChangerFor(localMemberId);
    final var operationApplier = operationsAppliers.getApplier(operation);
    final var initialized =
        operationApplier
            .init()
            .map(
                transformer ->
                    persistedClusterTopology.getTopology().updateMember(localMemberId, transformer))
            .map(this::updateLocalTopology);

    if (initialized.isLeft()) {
      // TODO: What should we do here? Retry?
      LOG.error(
          "Failed to initialize topology change operation {}", operation, initialized.getLeft());
      return;
    }

    operationApplier
        .apply()
        .onComplete((transformer, error) -> onOperationApplied(operation, transformer, error));
  }

  private void onOperationApplied(
      final TopologyChangeOperation operation,
      final UnaryOperator<MemberState> transformer,
      final Throwable error) {
    if (error == null) {
      updateLocalTopology(
          persistedClusterTopology.getTopology().advanceTopologyChange(localMemberId, transformer));
    } else {
      // TODO: Retry after a fixed delay. The failure is most likely due to timeouts such
      // as when joining a raft partition.
      LOG.error("Failed to apply topology change operation {}", operation, error);
    }
  }

  private Either<Exception, ClusterTopology> updateLocalTopology(final ClusterTopology topology) {
    if (topology.equals(persistedClusterTopology.getTopology())) {
      return Either.right(topology);
    }
    try {
      persistedClusterTopology.update(topology);
      topologyGossiper.accept(topology);
      return Either.right(topology);
    } catch (final Exception e) {
      return Either.left(e);
    }
  }
}

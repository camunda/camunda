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
import io.camunda.zeebe.topology.changes.TopologyChangeAppliers;
import io.camunda.zeebe.topology.state.ClusterTopology;
import io.camunda.zeebe.topology.state.MemberState;
import io.camunda.zeebe.topology.state.TopologyChangeOperation;
import io.camunda.zeebe.util.Either;
import java.io.IOException;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * ClusterTopologyManager is responsible for initializing ClusterTopology and managing
 * ClusterTopology changes.
 *
 * <p>On startup, ClusterTopologyManager initializes the topology using {@link
 * TopologyInitializer}s. The initialized topology is gossiped to other members.
 *
 * <p>When a topology is received via gossip, it is merged with the local topology. The merge
 * operation ensures that any concurrent update to the topology is not lost.
 *
 * <h4>Making configuration changes</h4>
 *
 * <p>Only a coordinator can start a topology change. The steps to make a configuration change are
 * added to the {@link ClusterTopology}. To make a topology change, the coordinator update the
 * topology with a list of operations that needs to be executed to achieve the target topology and
 * gossip the updated topology. These operations are expected to be executed in the order given.
 *
 * <p>When a member receives a topology with pending changes, it applies the change if it is
 * applicable to the member. Only a member can make changes to its own state in the topology. See
 * {@link TopologyChangeAppliers} to see how a change is applied locally.
 */
public final class ClusterTopologyManagerImpl implements ClusterTopologyManager {
  private static final Logger LOG = LoggerFactory.getLogger(ClusterTopologyManagerImpl.class);

  private final ConcurrencyControl executor;
  private final PersistedClusterTopology persistedClusterTopology;

  private Consumer<ClusterTopology> topologyGossiper;
  private final ActorFuture<Void> startFuture;

  private TopologyChangeAppliers changeAppliers;
  private final MemberId localMemberId;

  // Indicates whether there is a topology change operation in progress on this member.
  private boolean onGoingTopologyChangeOperation = false;

  ClusterTopologyManagerImpl(
      final ConcurrencyControl executor,
      final MemberId localMemberId,
      final PersistedClusterTopology persistedClusterTopology) {
    this.executor = executor;
    this.persistedClusterTopology = persistedClusterTopology;
    startFuture = executor.createFuture();
    this.localMemberId = localMemberId;
  }

  @Override
  public ActorFuture<ClusterTopology> getClusterTopology() {
    return executor.call(persistedClusterTopology::getTopology);
  }

  @Override
  public ActorFuture<ClusterTopology> updateClusterTopology(
      final UnaryOperator<ClusterTopology> topologyUpdated) {
    final ActorFuture<ClusterTopology> future = executor.createFuture();
    executor.run(
        () -> {
          try {
            final ClusterTopology updatedTopology =
                topologyUpdated.apply(persistedClusterTopology.getTopology());
            updateLocalTopology(updatedTopology)
                .ifRightOrLeft(
                    updated -> {
                      future.complete(updated);
                      applyTopologyChangeOperation(updatedTopology);
                    },
                    future::completeExceptionally);
          } catch (final Exception e) {
            LOG.error("Failed to update cluster topology", e);
            future.completeExceptionally(e);
          }
        });

    return future;
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
              // If receivedTopology is an older version, the merged topology will be same as the
              // local one. In that case, we can skip the next steps.
              if (!mergedTopology.equals(persistedClusterTopology.getTopology())) {
                LOG.debug(
                    "Received new topology {}. Updating local topology to {}",
                    receivedTopology,
                    mergedTopology);
                persistedClusterTopology.update(mergedTopology);
                applyTopologyChangeOperation(mergedTopology);
              }
            }
            result.complete(persistedClusterTopology.getTopology());
          } catch (final IOException error) {
            result.completeExceptionally(error);
          }
        });

    return result;
  }

  private boolean shouldApplyTopologyChangeOperation(final ClusterTopology mergedTopology) {
    // Topology change operation should be applied only once. The operation is removed
    // from the pending list only after the operation is completed. We should take care
    // not to repeatedly trigger the same operation while it is in progress. This
    // usually would not happen, because no other member will update the topology while
    // the current one is in progress. So the local topology is not changed. The topology change
    // operation is triggered locally only when the local topology is changes. However, as
    // an extra precaution we check if there is an ongoing operation before applying
    // one.
    return !onGoingTopologyChangeOperation
        && mergedTopology.pendingChangesFor(localMemberId).isPresent()
        // changeApplier is registered only after PartitionManager in the Broker is started.
        && changeAppliers != null;
  }

  private void applyTopologyChangeOperation(final ClusterTopology mergedTopology) {
    if (!shouldApplyTopologyChangeOperation(mergedTopology)) {
      return;
    }

    onGoingTopologyChangeOperation = true;
    final var operation = mergedTopology.pendingChangesFor(localMemberId).orElseThrow();
    LOG.info("Applying topology change operation {}", operation);
    final var operationApplier = changeAppliers.getApplier(operation);
    final var initialized =
        operationApplier
            .init(mergedTopology)
            .map(transformer -> mergedTopology.updateMember(localMemberId, transformer))
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
    onGoingTopologyChangeOperation = false;
    if (error == null) {
      updateLocalTopology(
          persistedClusterTopology.getTopology().advanceTopologyChange(localMemberId, transformer));
      LOG.info(
          "Operation {} applied. Updated local topology to {}",
          operation,
          persistedClusterTopology.getTopology());

      executor.run(
          () -> {
            // Continue applying topology change, if the next operation is for the local member
            applyTopologyChangeOperation(persistedClusterTopology.getTopology());
          });
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

  void registerTopologyChangeAppliers(final TopologyChangeAppliers topologyChangeAppliers) {
    executor.run(
        () -> {
          changeAppliers = topologyChangeAppliers;
          // Continue applying the topology change operation, after a broker restart.
          applyTopologyChangeOperation(persistedClusterTopology.getTopology());
        });
  }

  public void removeTopologyChangeAppliers() {
    executor.run(() -> changeAppliers = null);
  }
}

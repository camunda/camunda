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
import io.camunda.zeebe.topology.metrics.TopologyMetrics;
import io.camunda.zeebe.topology.metrics.TopologyMetrics.OperationObserver;
import io.camunda.zeebe.topology.state.ClusterTopology;
import io.camunda.zeebe.topology.state.MemberState.State;
import io.camunda.zeebe.topology.state.TopologyChangeOperation;
import io.camunda.zeebe.util.Either;
import io.camunda.zeebe.util.ExponentialBackoffRetryDelay;
import io.camunda.zeebe.util.VisibleForTesting;
import java.io.IOException;
import java.time.Duration;
import java.util.Objects;
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
  private static final Duration MIN_RETRY_DELAY = Duration.ofSeconds(10);
  private static final Duration MAX_RETRY_DELAY = Duration.ofMinutes(1);
  private final ConcurrencyControl executor;
  private final PersistedClusterTopology persistedClusterTopology;
  private Consumer<ClusterTopology> topologyGossiper;
  private final ActorFuture<Void> startFuture;
  private TopologyChangeAppliers changeAppliers;
  private InconsistentTopologyListener onInconsistentTopologyDetected;
  private final MemberId localMemberId;
  // Indicates whether there is a topology change operation in progress on this member.
  private boolean onGoingTopologyChangeOperation = false;
  private boolean shouldRetry = false;
  private final ExponentialBackoffRetryDelay backoffRetry;
  private boolean initialized = false;
  private final TopologyMetrics topologyMetrics;

  ClusterTopologyManagerImpl(
      final ConcurrencyControl executor,
      final MemberId localMemberId,
      final PersistedClusterTopology persistedClusterTopology,
      final TopologyMetrics topologyMetrics) {
    this(
        executor,
        localMemberId,
        persistedClusterTopology,
        topologyMetrics,
        MIN_RETRY_DELAY,
        MAX_RETRY_DELAY);
  }

  @VisibleForTesting
  ClusterTopologyManagerImpl(
      final ConcurrencyControl executor,
      final MemberId localMemberId,
      final PersistedClusterTopology persistedClusterTopology,
      final TopologyMetrics topologyMetrics,
      final Duration minRetryDelay,
      final Duration maxRetryDelay) {
    this.topologyMetrics = topologyMetrics;
    this.executor = executor;
    this.persistedClusterTopology = persistedClusterTopology;
    startFuture = executor.createFuture();
    this.localMemberId = localMemberId;
    backoffRetry = new ExponentialBackoffRetryDelay(maxRetryDelay, minRetryDelay);
  }

  @Override
  public ActorFuture<ClusterTopology> getClusterTopology() {
    final var future = executor.<ClusterTopology>createFuture();
    executor.run(() -> future.complete(persistedClusterTopology.getTopology()));
    return future;
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
                  LOG.debug("Initialized topology '{}'", persistedClusterTopology.getTopology());
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
      initialized = true;
      startFuture.complete(null);
    }
  }

  void onGossipReceived(final ClusterTopology receivedTopology) {
    executor.run(
        () -> {
          if (!initialized) {
            LOG.trace(
                "Received topology {} before ClusterTopologyManager is initialized.",
                receivedTopology);
            // When not started, do not update the local topology. This is to avoid any race
            // condition between FileInitializer and concurrently received topology via gossip.
            topologyGossiper.accept(receivedTopology);
            return;
          }
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

                final var oldTopology = persistedClusterTopology.getTopology();
                final var isConflictingTopology =
                    isConflictingTopology(mergedTopology, oldTopology);
                persistedClusterTopology.update(mergedTopology);

                if (isConflictingTopology && onInconsistentTopologyDetected != null) {
                  onInconsistentTopologyDetected.onInconsistentLocalTopology(
                      mergedTopology, oldTopology);
                }

                topologyGossiper.accept(mergedTopology);
                applyTopologyChangeOperation(mergedTopology);
              }
            }
          } catch (final IOException error) {
            LOG.warn(
                "Failed to process cluster topology received via gossip. '{}'",
                receivedTopology,
                error);
          }
        });
  }

  private boolean isConflictingTopology(
      final ClusterTopology mergedTopology, final ClusterTopology oldTopology) {
    if (!mergedTopology.hasMember(localMemberId)
        && oldTopology.hasMember(localMemberId)
        && oldTopology.getMember(localMemberId).state() == State.LEFT) {
      // If the member has left, it's state will be removed from the topology by another member. See
      // ClusterTopology#advance()
      return false;
    }
    return !Objects.equals(
        mergedTopology.getMember(localMemberId), oldTopology.getMember(localMemberId));
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
    return (!onGoingTopologyChangeOperation || shouldRetry)
        && mergedTopology.pendingChangesFor(localMemberId).isPresent()
        // changeApplier is registered only after PartitionManager in the Broker is started.
        && changeAppliers != null;
  }

  private void applyTopologyChangeOperation(final ClusterTopology mergedTopology) {
    if (!shouldApplyTopologyChangeOperation(mergedTopology)) {
      return;
    }

    onGoingTopologyChangeOperation = true;
    shouldRetry = false;
    final var operation = mergedTopology.pendingChangesFor(localMemberId).orElseThrow();
    final var observer = topologyMetrics.observeOperation(operation);
    LOG.info("Applying topology change operation {}", operation);
    final var operationApplier = changeAppliers.getApplier(operation);
    final var operationInitialized =
        operationApplier
            .init(mergedTopology)
            .map(transformer -> transformer.apply(mergedTopology))
            .flatMap(this::updateLocalTopology);

    if (operationInitialized.isLeft()) {
      // TODO: Mark ClusterChangePlan as failed
      observer.failed();
      onGoingTopologyChangeOperation = false;
      LOG.error(
          "Failed to initialize topology change operation {}",
          operation,
          operationInitialized.getLeft());
      return;
    }

    final var initializedTopology = operationInitialized.get();
    operationApplier
        .apply()
        .onComplete(
            (transformer, error) ->
                onOperationApplied(initializedTopology, operation, transformer, error, observer));
  }

  private void logAndScheduleRetry(final TopologyChangeOperation operation, final Throwable error) {
    shouldRetry = true;
    final Duration delay = backoffRetry.nextDelay();
    LOG.warn(
        "Failed to apply topology change operation {}. Will be retried in {}.",
        operation,
        delay,
        error);
    executor.schedule(
        delay,
        () -> {
          LOG.debug("Retrying last applied operation");
          applyTopologyChangeOperation(persistedClusterTopology.getTopology());
        });
  }

  private void onOperationApplied(
      final ClusterTopology topologyOnWhichOperationIsApplied,
      final TopologyChangeOperation operation,
      final UnaryOperator<ClusterTopology> transformer,
      final Throwable error,
      final OperationObserver observer) {
    onGoingTopologyChangeOperation = false;
    if (error == null) {
      observer.applied();
      backoffRetry.reset();
      if (persistedClusterTopology.getTopology().version()
          != topologyOnWhichOperationIsApplied.version()) {
        LOG.debug(
            "Topology changed while applying operation {}. Expected topology is {}. Current topology is {}. Most likely the change operation was cancelled.",
            operation,
            topologyOnWhichOperationIsApplied,
            persistedClusterTopology.getTopology());
        return;
      }
      updateLocalTopology(
          persistedClusterTopology.getTopology().advanceTopologyChange(transformer));
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
      observer.failed();
      // Retry after a delay. The failure is most likely due to timeouts such
      // as when joining a raft partition.
      logAndScheduleRetry(operation, error);
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

  void removeTopologyChangeAppliers() {
    executor.run(() -> changeAppliers = null);
  }

  void registerTopologyChangedListener(final InconsistentTopologyListener listener) {
    executor.run(() -> onInconsistentTopologyDetected = listener);
  }

  void removeTopologyChangedListener() {
    executor.run(() -> onInconsistentTopologyDetected = null);
  }
}

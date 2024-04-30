/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.configuration;

import io.atomix.cluster.MemberId;
import io.camunda.zeebe.dynamic.configuration.changes.ConfigurationChangeAppliers;
import io.camunda.zeebe.dynamic.configuration.metrics.TopologyMetrics;
import io.camunda.zeebe.dynamic.configuration.metrics.TopologyMetrics.OperationObserver;
import io.camunda.zeebe.dynamic.configuration.state.ClusterConfiguration;
import io.camunda.zeebe.dynamic.configuration.state.ClusterConfigurationChangeOperation;
import io.camunda.zeebe.dynamic.configuration.state.MemberState.State;
import io.camunda.zeebe.scheduler.ConcurrencyControl;
import io.camunda.zeebe.scheduler.future.ActorFuture;
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
 * ClusterConfigurationInitializer}s. The initialized topology is gossiped to other members.
 *
 * <p>When a topology is received via gossip, it is merged with the local topology. The merge
 * operation ensures that any concurrent update to the topology is not lost.
 *
 * <h4>Making configuration changes</h4>
 *
 * <p>Only a coordinator can start a topology change. The steps to make a configuration change are
 * added to the {@link ClusterConfiguration}. To make a topology change, the coordinator update the
 * topology with a list of operations that needs to be executed to achieve the target topology and
 * gossip the updated topology. These operations are expected to be executed in the order given.
 *
 * <p>When a member receives a topology with pending changes, it applies the change if it is
 * applicable to the member. Only a member can make changes to its own state in the topology. See
 * {@link ConfigurationChangeAppliers} to see how a change is applied locally.
 */
public final class ClusterConfigurationManagerImpl implements ClusterConfigurationManager {
  private static final Logger LOG = LoggerFactory.getLogger(ClusterConfigurationManagerImpl.class);
  private static final Duration MIN_RETRY_DELAY = Duration.ofSeconds(10);
  private static final Duration MAX_RETRY_DELAY = Duration.ofMinutes(1);
  private final ConcurrencyControl executor;
  private final PersistedClusterConfiguration persistedClusterConfiguration;
  private Consumer<ClusterConfiguration> topologyGossiper;
  private final ActorFuture<Void> startFuture;
  private ConfigurationChangeAppliers changeAppliers;
  private InconsistentConfigurationListener onInconsistentTopologyDetected;
  private final MemberId localMemberId;
  // Indicates whether there is a topology change operation in progress on this member.
  private boolean onGoingTopologyChangeOperation = false;
  private boolean shouldRetry = false;
  private final ExponentialBackoffRetryDelay backoffRetry;
  private boolean initialized = false;

  ClusterConfigurationManagerImpl(
      final ConcurrencyControl executor,
      final MemberId localMemberId,
      final PersistedClusterConfiguration persistedClusterConfiguration) {
    this(executor, localMemberId, persistedClusterConfiguration, MIN_RETRY_DELAY, MAX_RETRY_DELAY);
  }

  @VisibleForTesting
  ClusterConfigurationManagerImpl(
      final ConcurrencyControl executor,
      final MemberId localMemberId,
      final PersistedClusterConfiguration persistedClusterConfiguration,
      final Duration minRetryDelay,
      final Duration maxRetryDelay) {
    this.executor = executor;
    this.persistedClusterConfiguration = persistedClusterConfiguration;
    startFuture = executor.createFuture();
    this.localMemberId = localMemberId;
    backoffRetry = new ExponentialBackoffRetryDelay(maxRetryDelay, minRetryDelay);
  }

  @Override
  public ActorFuture<ClusterConfiguration> getClusterConfiguration() {
    final var future = executor.<ClusterConfiguration>createFuture();
    executor.run(() -> future.complete(persistedClusterConfiguration.getTopology()));
    return future;
  }

  @Override
  public ActorFuture<ClusterConfiguration> updateClusterConfiguration(
      final UnaryOperator<ClusterConfiguration> updatedConfiguration) {
    final ActorFuture<ClusterConfiguration> future = executor.createFuture();
    executor.run(
        () -> {
          try {
            final ClusterConfiguration updatedTopology =
                updatedConfiguration.apply(persistedClusterConfiguration.getTopology());
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

  ActorFuture<Void> start(final ClusterConfigurationInitializer clusterConfigurationInitializer) {
    executor.run(
        () -> {
          if (startFuture.isDone()) {
            return;
          }
          initialize(clusterConfigurationInitializer);
        });

    return startFuture;
  }

  public void setTopologyGossiper(final Consumer<ClusterConfiguration> topologyGossiper) {
    this.topologyGossiper = topologyGossiper;
  }

  private void initialize(final ClusterConfigurationInitializer clusterConfigurationInitializer) {
    clusterConfigurationInitializer
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
                  persistedClusterConfiguration.update(
                      topology.merge(persistedClusterConfiguration.getTopology()));
                  LOG.debug(
                      "Initialized topology '{}'", persistedClusterConfiguration.getTopology());
                  topologyGossiper.accept(persistedClusterConfiguration.getTopology());
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

  void onGossipReceived(final ClusterConfiguration receivedTopology) {
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
                  persistedClusterConfiguration.getTopology().merge(receivedTopology);
              // If receivedTopology is an older version, the merged topology will be same as the
              // local one. In that case, we can skip the next steps.
              if (!mergedTopology.equals(persistedClusterConfiguration.getTopology())) {
                LOG.debug(
                    "Received new topology {}. Updating local topology to {}",
                    receivedTopology,
                    mergedTopology);

                final var oldTopology = persistedClusterConfiguration.getTopology();
                final var isConflictingTopology =
                    isConflictingTopology(mergedTopology, oldTopology);
                persistedClusterConfiguration.update(mergedTopology);

                if (isConflictingTopology && onInconsistentTopologyDetected != null) {
                  onInconsistentTopologyDetected.onInconsistentConfiguration(
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
      final ClusterConfiguration mergedTopology, final ClusterConfiguration oldTopology) {
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

  private boolean shouldApplyTopologyChangeOperation(final ClusterConfiguration mergedTopology) {
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

  private void applyTopologyChangeOperation(final ClusterConfiguration mergedTopology) {
    if (!shouldApplyTopologyChangeOperation(mergedTopology)) {
      return;
    }

    onGoingTopologyChangeOperation = true;
    shouldRetry = false;
    final var operation = mergedTopology.pendingChangesFor(localMemberId).orElseThrow();
    final var observer = TopologyMetrics.observeOperation(operation);
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

  private void logAndScheduleRetry(
      final ClusterConfigurationChangeOperation operation, final Throwable error) {
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
          applyTopologyChangeOperation(persistedClusterConfiguration.getTopology());
        });
  }

  private void onOperationApplied(
      final ClusterConfiguration topologyOnWhichOperationIsApplied,
      final ClusterConfigurationChangeOperation operation,
      final UnaryOperator<ClusterConfiguration> transformer,
      final Throwable error,
      final OperationObserver observer) {
    onGoingTopologyChangeOperation = false;
    if (error == null) {
      observer.applied();
      backoffRetry.reset();
      if (persistedClusterConfiguration.getTopology().version()
          != topologyOnWhichOperationIsApplied.version()) {
        LOG.debug(
            "Topology changed while applying operation {}. Expected topology is {}. Current topology is {}. Most likely the change operation was cancelled.",
            operation,
            topologyOnWhichOperationIsApplied,
            persistedClusterConfiguration.getTopology());
        return;
      }
      updateLocalTopology(
          persistedClusterConfiguration.getTopology().advanceTopologyChange(transformer));
      LOG.info(
          "Operation {} applied. Updated local topology to {}",
          operation,
          persistedClusterConfiguration.getTopology());

      executor.run(
          () -> {
            // Continue applying topology change, if the next operation is for the local member
            applyTopologyChangeOperation(persistedClusterConfiguration.getTopology());
          });
    } else {
      observer.failed();
      // Retry after a delay. The failure is most likely due to timeouts such
      // as when joining a raft partition.
      logAndScheduleRetry(operation, error);
    }
  }

  private Either<Exception, ClusterConfiguration> updateLocalTopology(
      final ClusterConfiguration topology) {
    if (topology.equals(persistedClusterConfiguration.getTopology())) {
      return Either.right(topology);
    }
    try {
      persistedClusterConfiguration.update(topology);
      topologyGossiper.accept(topology);
      return Either.right(topology);
    } catch (final Exception e) {
      return Either.left(e);
    }
  }

  void registerTopologyChangeAppliers(
      final ConfigurationChangeAppliers configurationChangeAppliers) {
    executor.run(
        () -> {
          changeAppliers = configurationChangeAppliers;
          // Continue applying the topology change operation, after a broker restart.
          applyTopologyChangeOperation(persistedClusterConfiguration.getTopology());
        });
  }

  void removeTopologyChangeAppliers() {
    executor.run(() -> changeAppliers = null);
  }

  void registerTopologyChangedListener(final InconsistentConfigurationListener listener) {
    executor.run(() -> onInconsistentTopologyDetected = listener);
  }

  void removeTopologyChangedListener() {
    executor.run(() -> onInconsistentTopologyDetected = null);
  }
}

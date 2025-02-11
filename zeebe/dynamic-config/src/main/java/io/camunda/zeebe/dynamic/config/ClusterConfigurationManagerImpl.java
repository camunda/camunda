/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config;

import io.atomix.cluster.MemberId;
import io.camunda.zeebe.dynamic.config.changes.ConfigurationChangeAppliers;
import io.camunda.zeebe.dynamic.config.metrics.TopologyMetrics;
import io.camunda.zeebe.dynamic.config.metrics.TopologyMetrics.OperationObserver;
import io.camunda.zeebe.dynamic.config.state.ClusterConfiguration;
import io.camunda.zeebe.dynamic.config.state.ClusterConfigurationChangeOperation;
import io.camunda.zeebe.dynamic.config.state.MemberState.State;
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
 * ClusterConfigurationManager is responsible for initializing ClusterConfiguration and managing
 * ClusterConfiguration changes.
 *
 * <p>On startup, ClusterConfigurationManager initializes the configuration using {@link
 * ClusterConfigurationInitializer}s. The initialized configuration is gossiped to other members.
 *
 * <p>When a configuration is received via gossip, it is merged with the local configuration. The
 * merge operation ensures that any concurrent update to the configuration is not lost.
 *
 * <h4>Making configuration changes</h4>
 *
 * <p>Only a coordinator can start a configuration change. The steps to make a configuration change
 * are added to the {@link ClusterConfiguration}. To make a configuration change, the coordinator
 * update the configuration with a list of operations that needs to be executed to achieve the
 * target configuration and gossip the updated configuration. These operations are expected to be
 * executed in the order given.
 *
 * <p>When a member receives a configuration with pending changes, it applies the change if it is
 * applicable to the member. Only a member can make changes to its own state in the configuration.
 * See {@link ConfigurationChangeAppliers} to see how a change is applied locally.
 */
public final class ClusterConfigurationManagerImpl implements ClusterConfigurationManager {
  private static final Logger LOG = LoggerFactory.getLogger(ClusterConfigurationManagerImpl.class);
  private static final Duration MIN_RETRY_DELAY = Duration.ofSeconds(10);
  private static final Duration MAX_RETRY_DELAY = Duration.ofMinutes(1);
  private final ConcurrencyControl executor;
  private final PersistedClusterConfiguration persistedClusterConfiguration;
  private Consumer<ClusterConfiguration> configurationGossiper;
  private final ActorFuture<Void> startFuture;
  private ConfigurationChangeAppliers changeAppliers;
  private InconsistentConfigurationListener onInconsistentConfigurationDetected;
  private final MemberId localMemberId;
  // Indicates whether there is a configuration change operation in progress on this member.
  private boolean onGoingConfigurationChangeOperation = false;
  private boolean shouldRetry = false;
  private final ExponentialBackoffRetryDelay backoffRetry;
  private boolean initialized = false;
  private final TopologyMetrics topologyMetrics;

  ClusterConfigurationManagerImpl(
      final ConcurrencyControl executor,
      final MemberId localMemberId,
      final PersistedClusterConfiguration persistedClusterConfiguration,
      final TopologyMetrics topologyMetrics) {
    this(
        executor,
        localMemberId,
        persistedClusterConfiguration,
        topologyMetrics,
        MIN_RETRY_DELAY,
        MAX_RETRY_DELAY);
  }

  @VisibleForTesting
  ClusterConfigurationManagerImpl(
      final ConcurrencyControl executor,
      final MemberId localMemberId,
      final PersistedClusterConfiguration persistedClusterConfiguration,
      final TopologyMetrics topologyMetrics,
      final Duration minRetryDelay,
      final Duration maxRetryDelay) {
    this.executor = executor;
    this.persistedClusterConfiguration = persistedClusterConfiguration;
    startFuture = executor.createFuture();
    this.localMemberId = localMemberId;
    this.topologyMetrics = topologyMetrics;
    backoffRetry = new ExponentialBackoffRetryDelay(maxRetryDelay, minRetryDelay);
  }

  @Override
  public ActorFuture<ClusterConfiguration> getClusterConfiguration() {
    final var future = executor.<ClusterConfiguration>createFuture();
    executor.run(() -> future.complete(persistedClusterConfiguration.getConfiguration()));
    return future;
  }

  @Override
  public ActorFuture<ClusterConfiguration> updateClusterConfiguration(
      final UnaryOperator<ClusterConfiguration> configUpdater) {
    final ActorFuture<ClusterConfiguration> future = executor.createFuture();
    executor.run(
        () -> {
          try {
            final ClusterConfiguration updatedConfiguration =
                configUpdater.apply(persistedClusterConfiguration.getConfiguration());
            updateLocalConfiguration(updatedConfiguration)
                .ifRightOrLeft(
                    updated -> {
                      future.complete(updated);
                      applyConfigurationChangeOperation(updatedConfiguration);
                    },
                    future::completeExceptionally);
          } catch (final Exception e) {
            LOG.error("Failed to update cluster configuration", e);
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

  public void setConfigurationGossiper(final Consumer<ClusterConfiguration> configurationGossiper) {
    this.configurationGossiper = configurationGossiper;
  }

  private void initialize(final ClusterConfigurationInitializer clusterConfigurationInitializer) {
    clusterConfigurationInitializer
        .initialize()
        .onComplete(
            (configuration, error) -> {
              if (error != null) {
                LOG.error("Failed to initialize configuration", error);
                startFuture.completeExceptionally(error);
              } else if (configuration.isUninitialized()) {
                final String errorMessage =
                    "Expected to initialize configuration, but got uninitialized configuration";
                LOG.error(errorMessage);
                startFuture.completeExceptionally(new IllegalStateException(errorMessage));
              } else {
                try {
                  // merge in case there was a concurrent update via gossip
                  persistedClusterConfiguration.update(
                      configuration.merge(persistedClusterConfiguration.getConfiguration()));
                  LOG.debug(
                      "Initialized cluster configuration '{}'",
                      persistedClusterConfiguration.getConfiguration());
                  configurationGossiper.accept(persistedClusterConfiguration.getConfiguration());
                  setStarted();
                } catch (final IOException e) {
                  startFuture.completeExceptionally(
                      "Failed to start update cluster configuration", e);
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

  void onGossipReceived(final ClusterConfiguration receivedConfiguration) {
    executor.run(
        () -> {
          if (!initialized) {
            LOG.trace(
                "Received configuration {} before ClusterConfigurationManager is initialized.",
                receivedConfiguration);
            // When not started, do not update the local configuration. This is to avoid any race
            // condition between FileInitializer and concurrently received configuration via gossip.
            configurationGossiper.accept(receivedConfiguration);
            return;
          }
          try {
            if (receivedConfiguration != null) {
              final var mergedConfiguration =
                  persistedClusterConfiguration.getConfiguration().merge(receivedConfiguration);
              // If received configuration is an older version, the merged configuration will be
              // same as the
              // local one. In that case, we can skip the next steps.
              if (!mergedConfiguration.equals(persistedClusterConfiguration.getConfiguration())) {
                LOG.debug(
                    "Received new configuration {}. Updating local configuration to {}",
                    receivedConfiguration,
                    mergedConfiguration);

                final var oldConfiguration = persistedClusterConfiguration.getConfiguration();
                final var isConflictingConfiguration =
                    isConflictingConfiguration(mergedConfiguration, oldConfiguration);
                persistedClusterConfiguration.update(mergedConfiguration);

                if (isConflictingConfiguration && onInconsistentConfigurationDetected != null) {
                  onInconsistentConfigurationDetected.onInconsistentConfiguration(
                      mergedConfiguration, oldConfiguration);
                }

                configurationGossiper.accept(mergedConfiguration);
                applyConfigurationChangeOperation(mergedConfiguration);
              }
            }
          } catch (final IOException error) {
            LOG.warn(
                "Failed to process cluster configuration received via gossip. '{}'",
                receivedConfiguration,
                error);
          }
        });
  }

  private boolean isConflictingConfiguration(
      final ClusterConfiguration mergedConfiguration, final ClusterConfiguration oldConfiguration) {
    if (!mergedConfiguration.hasMember(localMemberId)
        && oldConfiguration.hasMember(localMemberId)
        && oldConfiguration.getMember(localMemberId).state() == State.LEFT) {
      // If the member has left, it's state will be removed from the configuration by another
      // member. See ClusterConfiguration#advance()
      return false;
    }
    return !Objects.equals(
        mergedConfiguration.getMember(localMemberId), oldConfiguration.getMember(localMemberId));
  }

  private boolean shouldApplyConfigurationChangeOperation(
      final ClusterConfiguration mergedConfiguration) {
    // Configuration change operation should be applied only once. The operation is removed
    // from the pending list only after the operation is completed. We should take care
    // not to repeatedly trigger the same operation while it is in progress. This
    // usually would not happen, because no other member will update the configuration while
    // the current one is in progress. So the local configuration is not changed. The configuration
    // change operation is triggered locally only when the local configuration is changes. However,
    // as
    // an extra precaution we check if there is an ongoing operation before applying one.
    return (!onGoingConfigurationChangeOperation || shouldRetry)
        && mergedConfiguration.pendingChangesFor(localMemberId).isPresent()
        // changeApplier is registered only after PartitionManager in the Broker is started.
        && changeAppliers != null;
  }

  private void applyConfigurationChangeOperation(final ClusterConfiguration mergedConfiguration) {
    if (!shouldApplyConfigurationChangeOperation(mergedConfiguration)) {
      return;
    }

    onGoingConfigurationChangeOperation = true;
    shouldRetry = false;
    final var operation = mergedConfiguration.pendingChangesFor(localMemberId).orElseThrow();
    final var observer = topologyMetrics.observeOperation(operation);
    LOG.info("Applying configuration change operation {}", operation);
    final var operationApplier = changeAppliers.getApplier(operation);
    final var operationInitialized =
        operationApplier
            .init(mergedConfiguration)
            .map(transformer -> transformer.apply(mergedConfiguration))
            .flatMap(this::updateLocalConfiguration);

    if (operationInitialized.isLeft()) {
      // TODO: Mark ClusterChangePlan as failed
      observer.failed();
      onGoingConfigurationChangeOperation = false;
      LOG.error(
          "Failed to initialize configuration change operation {}",
          operation,
          operationInitialized.getLeft());
      return;
    }

    final var initializedConfiguration = operationInitialized.get();
    operationApplier
        .apply()
        .onComplete(
            (transformer, error) ->
                onOperationApplied(
                    initializedConfiguration, operation, transformer, error, observer));
  }

  private void logAndScheduleRetry(
      final ClusterConfigurationChangeOperation operation, final Throwable error) {
    shouldRetry = true;
    final Duration delay = backoffRetry.nextDelay();
    LOG.warn(
        "Failed to apply configuration change operation {}. Will be retried in {}.",
        operation,
        delay,
        error);
    executor.schedule(
        delay,
        () -> {
          LOG.debug("Retrying last applied operation");
          applyConfigurationChangeOperation(persistedClusterConfiguration.getConfiguration());
        });
  }

  private void onOperationApplied(
      final ClusterConfiguration topologyOnWhichOperationIsApplied,
      final ClusterConfigurationChangeOperation operation,
      final UnaryOperator<ClusterConfiguration> transformer,
      final Throwable error,
      final OperationObserver observer) {
    onGoingConfigurationChangeOperation = false;
    if (error == null) {
      observer.applied();
      backoffRetry.reset();
      if (persistedClusterConfiguration.getConfiguration().version()
          != topologyOnWhichOperationIsApplied.version()) {
        LOG.debug(
            "Configuration changed while applying operation {}. Expected configuration is {}. Current configuration is {}. Most likely the change operation was cancelled.",
            operation,
            topologyOnWhichOperationIsApplied,
            persistedClusterConfiguration.getConfiguration());
        return;
      }
      updateLocalConfiguration(
          persistedClusterConfiguration.getConfiguration().advanceConfigurationChange(transformer));
      LOG.info(
          "Operation {} applied. Updated local configuration to {}",
          operation,
          persistedClusterConfiguration.getConfiguration());

      executor.run(
          () -> {
            // Continue applying configuration change, if the next operation is for the local member
            applyConfigurationChangeOperation(persistedClusterConfiguration.getConfiguration());
          });
    } else {
      observer.failed();
      // Retry after a delay. The failure is most likely due to timeouts such
      // as when joining a raft partition.
      logAndScheduleRetry(operation, error);
    }
  }

  private Either<Exception, ClusterConfiguration> updateLocalConfiguration(
      final ClusterConfiguration configuration) {
    if (configuration.equals(persistedClusterConfiguration.getConfiguration())) {
      return Either.right(configuration);
    }
    try {
      persistedClusterConfiguration.update(configuration);
      configurationGossiper.accept(configuration);
      return Either.right(configuration);
    } catch (final Exception e) {
      return Either.left(e);
    }
  }

  void registerTopologyChangeAppliers(
      final ConfigurationChangeAppliers configurationChangeAppliers) {
    executor.run(
        () -> {
          changeAppliers = configurationChangeAppliers;
          // Continue applying the configuration change operation, after a broker restart.
          applyConfigurationChangeOperation(persistedClusterConfiguration.getConfiguration());
        });
  }

  void removeTopologyChangeAppliers() {
    executor.run(() -> changeAppliers = null);
  }

  void registerTopologyChangedListener(final InconsistentConfigurationListener listener) {
    executor.run(() -> onInconsistentConfigurationDetected = listener);
  }

  void removeTopologyChangedListener() {
    executor.run(() -> onInconsistentConfigurationDetected = null);
  }
}

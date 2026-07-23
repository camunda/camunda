/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config;

import io.atomix.cluster.MemberId;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationCoordinatorSupplier;
import io.camunda.zeebe.dynamic.config.changes.ConfigurationChangeAppliers;
import io.camunda.zeebe.dynamic.config.changes.GlobalConfigurationChangeAppliers;
import io.camunda.zeebe.dynamic.config.changes.PartitionGroupConfigurationChangeAppliers;
import io.camunda.zeebe.dynamic.config.metrics.TopologyManagerMetrics;
import io.camunda.zeebe.dynamic.config.metrics.TopologyManagerMetrics.OperationObserver;
import io.camunda.zeebe.dynamic.config.state.ClusterConfiguration;
import io.camunda.zeebe.dynamic.config.state.ClusterConfigurationChangeOperation;
import io.camunda.zeebe.dynamic.config.state.CurrentClusterConfiguration;
import io.camunda.zeebe.dynamic.config.state.GlobalChangeOperation;
import io.camunda.zeebe.dynamic.config.state.GlobalConfiguration;
import io.camunda.zeebe.dynamic.config.state.MemberState.State;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupConfiguration;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation;
import io.camunda.zeebe.dynamic.config.state.PhasedChangePlan.GlobalPhase;
import io.camunda.zeebe.dynamic.config.state.PhasedChangePlan.PartitionGroupParallelPhase;
import io.camunda.zeebe.dynamic.config.state.PhasedChangePlanStatus;
import io.camunda.zeebe.scheduler.ConcurrencyControl;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.util.Either;
import io.camunda.zeebe.util.ExponentialBackoffRetryDelay;
import io.camunda.zeebe.util.VisibleForTesting;
import java.io.IOException;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;
import org.jspecify.annotations.Nullable;
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
  private final TopologyManagerMetrics topologyMetrics;
  private final boolean useNewConfig;

  // New-model state, only used when useNewConfig is true.
  private final @Nullable PersistedCurrentClusterConfiguration persistedCurrentConfiguration;
  private @Nullable Consumer<CurrentClusterConfiguration> currentConfigurationGossiper;
  private final @Nullable ClusterConfigurationCoordinatorSupplier coordinatorSupplier;
  private @Nullable GlobalConfigurationChangeAppliers globalChangeAppliers;
  private final Map<String, PartitionGroupConfigurationChangeAppliers>
      partitionGroupChangeAppliers = new HashMap<>();
  private boolean onGoingGlobalOperation = false;
  private boolean shouldRetryGlobal = false;
  private final Map<String, Boolean> onGoingGroupOperation = new HashMap<>();
  private final Map<String, Boolean> shouldRetryGroup = new HashMap<>();
  private final Map<String, ExponentialBackoffRetryDelay> groupBackoffRetry = new HashMap<>();
  private final Duration minRetryDelay;
  private final Duration maxRetryDelay;

  ClusterConfigurationManagerImpl(
      final ConcurrencyControl executor,
      final MemberId localMemberId,
      final PersistedClusterConfiguration persistedClusterConfiguration,
      final TopologyManagerMetrics topologyMetrics) {
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
      final TopologyManagerMetrics topologyMetrics,
      final Duration minRetryDelay,
      final Duration maxRetryDelay) {
    this.executor = executor;
    this.persistedClusterConfiguration = persistedClusterConfiguration;
    startFuture = executor.createFuture();
    this.localMemberId = localMemberId;
    this.topologyMetrics = topologyMetrics;
    this.minRetryDelay = minRetryDelay;
    this.maxRetryDelay = maxRetryDelay;
    backoffRetry = new ExponentialBackoffRetryDelay(maxRetryDelay, minRetryDelay);
    useNewConfig = false;
    persistedCurrentConfiguration = null;
    coordinatorSupplier = null;
  }

  /**
   * Constructs a manager operating on the new multi-partition-group model. Used when {@link
   * ClusterConfigurationManagerService#USE_NEW_CONFIG} is enabled. The legacy {@code
   * PersistedClusterConfiguration} is not used in this mode.
   */
  ClusterConfigurationManagerImpl(
      final ConcurrencyControl executor,
      final MemberId localMemberId,
      final PersistedCurrentClusterConfiguration persistedCurrentConfiguration,
      final TopologyManagerMetrics topologyMetrics) {
    this(
        executor,
        localMemberId,
        persistedCurrentConfiguration,
        topologyMetrics,
        MIN_RETRY_DELAY,
        MAX_RETRY_DELAY);
  }

  @VisibleForTesting
  ClusterConfigurationManagerImpl(
      final ConcurrencyControl executor,
      final MemberId localMemberId,
      final PersistedCurrentClusterConfiguration persistedCurrentConfiguration,
      final TopologyManagerMetrics topologyMetrics,
      final Duration minRetryDelay,
      final Duration maxRetryDelay) {
    this.executor = executor;
    persistedClusterConfiguration = null;
    this.persistedCurrentConfiguration = persistedCurrentConfiguration;
    startFuture = executor.createFuture();
    this.localMemberId = localMemberId;
    this.topologyMetrics = topologyMetrics;
    this.minRetryDelay = minRetryDelay;
    this.maxRetryDelay = maxRetryDelay;
    backoffRetry = new ExponentialBackoffRetryDelay(maxRetryDelay, minRetryDelay);
    useNewConfig = true;
    coordinatorSupplier =
        ClusterConfigurationCoordinatorSupplier.ofMembers(
            () ->
                this.persistedCurrentConfiguration
                    .getConfiguration()
                    .globalConfiguration()
                    .members()
                    .keySet());
  }

  @Override
  public ActorFuture<ClusterConfiguration> getClusterConfiguration() {
    final var future = executor.<ClusterConfiguration>createFuture();
    executor.run(
        () ->
            future.complete(
                useNewConfig
                    ? persistedCurrentConfiguration.getConfiguration().toLegacyDefault()
                    : persistedClusterConfiguration.getConfiguration()));
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

  @Override
  public boolean isUsingNewConfig() {
    return useNewConfig;
  }

  /** Returns the full multi-group configuration. Only valid when {@link #useNewConfig} is true. */
  @Override
  public ActorFuture<CurrentClusterConfiguration> getMultiConfiguration() {
    final var future = executor.<CurrentClusterConfiguration>createFuture();
    executor.run(() -> future.complete(persistedCurrentConfiguration.getConfiguration()));
    return future;
  }

  /**
   * Applies {@code updater} to the multi-group configuration, persists and gossips the result, then
   * triggers local operation application. Only valid when {@link #useNewConfig} is true.
   */
  @Override
  public ActorFuture<CurrentClusterConfiguration> updateMultiConfiguration(
      final UnaryOperator<CurrentClusterConfiguration> updater) {
    final var future = executor.<CurrentClusterConfiguration>createFuture();
    executor.run(
        () -> {
          try {
            final var updated = updater.apply(persistedCurrentConfiguration.getConfiguration());
            updateLocalCurrentConfiguration(updated)
                .ifRightOrLeft(
                    applied -> {
                      future.complete(applied);
                      applyNewConfigurationChangeOperation();
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

  /**
   * Starts the manager on the new multi-partition-group model using {@code initializer} (only
   * {@link CurrentClusterConfigurationInitializer.StaticInitializer} exists today — see that
   * interface's class doc for the migration status). Only valid when {@link #useNewConfig} is true.
   */
  ActorFuture<Void> start(final CurrentClusterConfigurationInitializer initializer) {
    executor.run(
        () -> {
          if (startFuture.isDone()) {
            return;
          }
          initializeNewModel(initializer);
        });
    return startFuture;
  }

  private void initializeNewModel(final CurrentClusterConfigurationInitializer initializer) {
    initializer
        .initialize()
        .onComplete(
            (configuration, error) -> {
              if (error != null) {
                LOG.error("Failed to initialize configuration", error);
                startFuture.completeExceptionally(error);
              } else if (configuration.globalConfiguration().members().isEmpty()) {
                final String errorMessage =
                    "Expected to initialize configuration, but got uninitialized configuration";
                LOG.error(errorMessage);
                startFuture.completeExceptionally(new IllegalStateException(errorMessage));
              } else {
                try {
                  // merge in case there was a concurrent update via gossip
                  final var merged =
                      configuration.merge(persistedCurrentConfiguration.getConfiguration());
                  persistedCurrentConfiguration.update(merged);
                  LOG.debug(
                      "Initialized cluster configuration '{}'",
                      persistedCurrentConfiguration.getConfiguration());
                  if (currentConfigurationGossiper != null) {
                    currentConfigurationGossiper.accept(
                        persistedCurrentConfiguration.getConfiguration());
                  }
                  setStarted();
                } catch (final IOException e) {
                  startFuture.completeExceptionally(
                      "Failed to start update cluster configuration", e);
                }
              }
            });
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

  // ---------------------------------------------------------------------------
  // New multi-partition-group model (used only when useNewConfig is true).
  // ---------------------------------------------------------------------------

  void removeTopologyChangeAppliers() {
    executor.run(() -> changeAppliers = null);
  }

  void registerTopologyChangedListener(final InconsistentConfigurationListener listener) {
    executor.run(() -> onInconsistentConfigurationDetected = listener);
  }

  void removeTopologyChangedListener() {
    executor.run(() -> onInconsistentConfigurationDetected = null);
  }

  void setCurrentConfigurationGossiper(
      final Consumer<CurrentClusterConfiguration> currentConfigurationGossiper) {
    this.currentConfigurationGossiper = currentConfigurationGossiper;
  }

  /**
   * Seeds a partition group configuration (e.g. a non-default physical tenant) if it does not exist
   * yet. Existing groups are left untouched.
   */
  ActorFuture<Void> setPartitionGroupConfig(
      final String groupId, final PartitionGroupConfiguration groupConfiguration) {
    final var future = executor.<Void>createFuture();
    executor.run(
        () -> {
          final var current = persistedCurrentConfiguration.getConfiguration();
          if (current.hasPartitionGroup(groupId)) {
            future.complete(null);
            return;
          }
          final Map<String, PartitionGroupConfiguration> groups =
              new HashMap<>(current.partitionGroups());
          groups.put(groupId, groupConfiguration);
          final var updated =
              new CurrentClusterConfiguration(
                  current.version(),
                  current.globalConfiguration(),
                  groups,
                  current.phasedChangeState());
          updateLocalCurrentConfiguration(updated)
              .ifRightOrLeft(applied -> future.complete(null), future::completeExceptionally);
        });
    return future;
  }

  void registerGlobalChangeAppliers(final GlobalConfigurationChangeAppliers appliers) {
    executor.run(
        () -> {
          globalChangeAppliers = appliers;
          applyNewConfigurationChangeOperation();
        });
  }

  void registerPartitionGroupChangeAppliers(
      final String groupId, final PartitionGroupConfigurationChangeAppliers appliers) {
    executor.run(
        () -> {
          partitionGroupChangeAppliers.put(groupId, appliers);
          applyNewConfigurationChangeOperation();
        });
  }

  void removePartitionGroupChangeAppliers(final String groupId) {
    executor.run(() -> partitionGroupChangeAppliers.remove(groupId));
  }

  /**
   * Merges a {@link CurrentClusterConfiguration} received via gossip into the local one. If the
   * merge changes the local configuration, it is persisted, re-gossiped, and local operation
   * application is triggered. Mirrors the legacy {@code onGossipReceived} merge behaviour.
   */
  void onGossipReceivedCurrent(final CurrentClusterConfiguration receivedConfiguration) {
    executor.run(
        () -> {
          if (receivedConfiguration == null) {
            return;
          }
          final var local = persistedCurrentConfiguration.getConfiguration();
          final var merged = local.merge(receivedConfiguration);
          if (!merged.equals(local)) {
            updateLocalCurrentConfiguration(merged)
                .ifRightOrLeft(
                    applied -> applyNewConfigurationChangeOperation(),
                    error ->
                        LOG.warn(
                            "Failed to process cluster configuration received via gossip. '{}'",
                            receivedConfiguration,
                            error));
          }
        });
  }

  private Either<Exception, CurrentClusterConfiguration> updateLocalCurrentConfiguration(
      final CurrentClusterConfiguration configuration) {
    if (configuration.equals(persistedCurrentConfiguration.getConfiguration())) {
      return Either.right(configuration);
    }
    try {
      persistedCurrentConfiguration.update(configuration);
      if (currentConfigurationGossiper != null) {
        currentConfigurationGossiper.accept(configuration);
      }
      maybeAdvancePhase(configuration);
      return Either.right(configuration);
    } catch (final Exception e) {
      return Either.left(e);
    }
  }

  /**
   * Drives phased-plan advancement. Invoked after every successful local configuration update. Only
   * the coordinator (the member with the lowest id, per {@link
   * ClusterConfigurationCoordinatorSupplier}) advances the plan, so that a single member is
   * responsible for the transition. When the current phase's sub-configuration(s) have drained
   * their pending changes, the plan is advanced to the next phase, or completed if it was the last
   * phase. The action is idempotent — re-firing on an already-advanced phase is a no-op.
   */
  private void maybeAdvancePhase(final CurrentClusterConfiguration config) {
    final var pending = config.phasedChangeState().pending();
    if (pending.isEmpty() || !isLocalMemberCoordinator()) {
      return;
    }
    final var plan = pending.get();
    final boolean currentPhaseComplete =
        switch (plan.currentPhase()) {
          case final GlobalPhase ignored -> !config.globalConfiguration().hasPendingChanges();
          case final PartitionGroupParallelPhase parallelPhase ->
              parallelPhase.groupOperations().keySet().stream()
                  .map(config::partitionGroup)
                  .allMatch(group -> group != null && !group.hasPendingChanges());
        };
    if (!currentPhaseComplete) {
      return;
    }

    if (plan.hasNextPhase()) {
      updateMultiConfiguration(CurrentClusterConfiguration::activateNextPhase);
    } else {
      updateMultiConfiguration(c -> c.completePlan(PhasedChangePlanStatus.COMPLETED));
    }
  }

  private boolean isLocalMemberCoordinator() {
    return coordinatorSupplier != null
        && localMemberId.equals(coordinatorSupplier.getDefaultCoordinator());
  }

  /**
   * Applies the next pending operation for the local member on the global configuration and on
   * every partition group. Operations across partition groups are applied concurrently — each group
   * has its own in-progress/retry state — while the operations within a single group (and within
   * the global configuration) are applied sequentially.
   */
  private void applyNewConfigurationChangeOperation() {
    applyGlobalConfigurationChangeOperation();
    // can apply operations to global and groups in parallel. The ordering is constrained by the
    // phases. So additional enforcement of ordering is not needed here.
    for (final String groupId :
        List.copyOf(persistedCurrentConfiguration.getConfiguration().partitionGroups().keySet())) {
      // Can apply operations to multiple groups in parallel, but only one operation per group at a
      // time.
      applyPartitionGroupConfigurationChangeOperation(groupId);
    }
  }

  private void applyGlobalConfigurationChangeOperation() {
    final var config = persistedCurrentConfiguration.getConfiguration();
    final var pending = config.globalConfiguration().pendingChangesFor(localMemberId);
    if ((onGoingGlobalOperation && !shouldRetryGlobal)
        || globalChangeAppliers == null
        || pending.isEmpty()) {
      return;
    }

    onGoingGlobalOperation = true;
    shouldRetryGlobal = false;
    final var operation = (GlobalChangeOperation) pending.orElseThrow();
    final var observer = topologyMetrics.observeOperation(operation);
    LOG.info("Applying global configuration change operation {}", operation);
    final var applier = globalChangeAppliers.getApplier(operation);
    final var initialized =
        applier
            .init(config)
            .map(config::updateGlobalConfiguration)
            .flatMap(this::updateLocalCurrentConfiguration);

    if (initialized.isLeft()) {
      observer.failed();
      onGoingGlobalOperation = false;
      LOG.error(
          "Failed to initialize global configuration change operation {}",
          operation,
          initialized.getLeft());
      return;
    }

    final var startedConfiguration = initialized.get();
    applier
        .apply()
        .onComplete(
            (transformer, error) ->
                onGlobalOperationApplied(
                    startedConfiguration, operation, transformer, error, observer));
  }

  private void onGlobalOperationApplied(
      final CurrentClusterConfiguration configurationOnWhichOperationIsApplied,
      final GlobalChangeOperation operation,
      final UnaryOperator<GlobalConfiguration> transformer,
      final Throwable error,
      final OperationObserver observer) {
    onGoingGlobalOperation = false;
    if (error != null) {
      observer.failed();
      shouldRetryGlobal = true;
      final Duration delay = backoffRetry.nextDelay();
      LOG.warn(
          "Failed to apply global configuration change operation {}. Will be retried in {}.",
          operation,
          delay,
          error);
      executor.schedule(delay, this::applyNewConfigurationChangeOperation);
      return;
    }

    observer.applied();
    backoffRetry.reset();
    if (persistedCurrentConfiguration.getConfiguration().globalConfiguration().version()
        != configurationOnWhichOperationIsApplied.globalConfiguration().version()) {
      LOG.debug(
          "Global configuration changed while applying operation {}. Most likely the change was cancelled.",
          operation);
      return;
    }
    final var advanced =
        persistedCurrentConfiguration
            .getConfiguration()
            .updateGlobalConfiguration(g -> g.advanceConfigurationChange(transformer));
    updateLocalCurrentConfiguration(advanced);
    LOG.info("Global operation {} applied.", operation);
    executor.run(this::applyNewConfigurationChangeOperation);
  }

  private void applyPartitionGroupConfigurationChangeOperation(final String groupId) {
    final var config = persistedCurrentConfiguration.getConfiguration();
    final var group = config.partitionGroup(groupId);
    final var appliers = partitionGroupChangeAppliers.get(groupId);
    if (group == null || appliers == null) {
      return;
    }
    final var pending = group.pendingChangesFor(localMemberId);
    if ((onGoingGroupOperation.getOrDefault(groupId, false)
            && !shouldRetryGroup.getOrDefault(groupId, false))
        || pending.isEmpty()) {
      return;
    }

    onGoingGroupOperation.put(groupId, true);
    shouldRetryGroup.put(groupId, false);
    final var operation = (PartitionGroupOperation) pending.orElseThrow();
    final var observer = topologyMetrics.observeOperation(operation);
    LOG.info("Applying partition group '{}' configuration change operation {}", groupId, operation);
    final var applier = appliers.getApplier(operation);
    final var initialized =
        applier
            .init(config.globalConfiguration(), group)
            .map(transformer -> config.updatePartitionGroupConfig(groupId, transformer))
            .flatMap(this::updateLocalCurrentConfiguration);

    if (initialized.isLeft()) {
      observer.failed();
      onGoingGroupOperation.put(groupId, false);
      LOG.error(
          "Failed to initialize partition group '{}' configuration change operation {}",
          groupId,
          operation,
          initialized.getLeft());
      return;
    }

    final var startedConfiguration = initialized.get();
    applier
        .apply()
        .onComplete(
            (transformer, error) ->
                onPartitionGroupOperationApplied(
                    groupId, startedConfiguration, operation, transformer, error, observer));
  }

  private void onPartitionGroupOperationApplied(
      final String groupId,
      final CurrentClusterConfiguration configurationOnWhichOperationIsApplied,
      final PartitionGroupOperation operation,
      final UnaryOperator<PartitionGroupConfiguration> transformer,
      final Throwable error,
      final OperationObserver observer) {
    onGoingGroupOperation.put(groupId, false);
    if (error != null) {
      observer.failed();
      shouldRetryGroup.put(groupId, true);
      final Duration delay =
          groupBackoffRetry
              .computeIfAbsent(
                  groupId,
                  ignored -> new ExponentialBackoffRetryDelay(maxRetryDelay, minRetryDelay))
              .nextDelay();
      LOG.warn(
          "Failed to apply partition group '{}' configuration change operation {}. Will be retried in {}.",
          groupId,
          operation,
          delay,
          error);
      executor.schedule(delay, this::applyNewConfigurationChangeOperation);
      return;
    }

    observer.applied();
    final var groupBackoff = groupBackoffRetry.get(groupId);
    if (groupBackoff != null) {
      groupBackoff.reset();
    }
    final var currentGroup =
        persistedCurrentConfiguration.getConfiguration().partitionGroup(groupId);
    if (currentGroup == null
        || currentGroup.version()
            != configurationOnWhichOperationIsApplied.partitionGroup(groupId).version()) {
      LOG.debug(
          "Partition group '{}' changed while applying operation {}. Most likely the change was cancelled.",
          groupId,
          operation);
      return;
    }
    final var advanced =
        persistedCurrentConfiguration
            .getConfiguration()
            .updatePartitionGroupConfig(groupId, g -> g.advanceConfigurationChange(transformer));
    updateLocalCurrentConfiguration(advanced);
    LOG.info("Partition group '{}' operation {} applied.", groupId, operation);
    executor.run(this::applyNewConfigurationChangeOperation);
  }
}

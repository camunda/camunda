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
import io.camunda.zeebe.dynamic.config.state.OperationId;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupConfiguration;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation;
import io.camunda.zeebe.dynamic.config.state.PhasedChangePlan.GlobalPhase;
import io.camunda.zeebe.dynamic.config.state.PhasedChangePlan.PartitionGroupGraphPhase;
import io.camunda.zeebe.dynamic.config.state.PhasedChangePlan.PartitionGroupParallelPhase;
import io.camunda.zeebe.dynamic.config.state.PhasedChangePlanStatus;
import io.camunda.zeebe.dynamic.config.state.PhasedChangeState;
import io.camunda.zeebe.scheduler.ConcurrencyControl;
import io.camunda.zeebe.scheduler.ScheduledTimer;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.util.Either;
import io.camunda.zeebe.util.ExponentialBackoffRetryDelay;
import io.camunda.zeebe.util.VisibleForTesting;
import java.io.IOException;
import java.time.Duration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
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
  // Dependency-graph execution runs several of a group's operations at once, so in-flight and
  // retry state are keyed per operation rather than per group as the queue path's are.
  private final Set<GraphOperationKey> inFlightGraphOperations = new HashSet<>();
  private final Map<GraphOperationKey, ExponentialBackoffRetryDelay> graphOperationBackoff =
      new HashMap<>();
  private final Duration minRetryDelay;
  private final Duration maxRetryDelay;
  // Keyed by plan id, since multiple plans can be advancing (and independently retrying) at once.
  private final Map<Long, ScheduledTimer> advancePhaseRetryTimers = new HashMap<>();
  private final int completedChangeHistoryLimit;

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
    completedChangeHistoryLimit = PhasedChangeState.DEFAULT_HISTORY_LIMIT;
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
    this(
        executor,
        localMemberId,
        persistedCurrentConfiguration,
        topologyMetrics,
        minRetryDelay,
        maxRetryDelay,
        PhasedChangeState.DEFAULT_HISTORY_LIMIT);
  }

  /**
   * @param completedChangeHistoryLimit maximum number of completed changes retained in {@link
   *     PhasedChangeState#history()}, oldest evicted first
   */
  @VisibleForTesting
  ClusterConfigurationManagerImpl(
      final ConcurrencyControl executor,
      final MemberId localMemberId,
      final PersistedCurrentClusterConfiguration persistedCurrentConfiguration,
      final TopologyManagerMetrics topologyMetrics,
      final Duration minRetryDelay,
      final Duration maxRetryDelay,
      final int completedChangeHistoryLimit) {
    this.executor = executor;
    persistedClusterConfiguration = null;
    this.persistedCurrentConfiguration = persistedCurrentConfiguration;
    startFuture = executor.createFuture();
    this.localMemberId = localMemberId;
    this.topologyMetrics = topologyMetrics;
    this.minRetryDelay = minRetryDelay;
    this.maxRetryDelay = maxRetryDelay;
    this.completedChangeHistoryLimit = completedChangeHistoryLimit;
    PhasedChangeState.setHistoryLimit(completedChangeHistoryLimit);
    backoffRetry = new ExponentialBackoffRetryDelay(maxRetryDelay, minRetryDelay);
    useNewConfig = true;
    coordinatorSupplier =
        ClusterConfigurationCoordinatorSupplier.from(
            () -> this.persistedCurrentConfiguration.getConfiguration());
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

  ActorFuture<Void> start(
      final ClusterConfigurationInitializer<? extends InitializableClusterConfiguration>
          clusterConfigurationInitializer) {
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

  private void initialize(
      final ClusterConfigurationInitializer<? extends InitializableClusterConfiguration>
          clusterConfigurationInitializer) {
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
                // temporary workaround to support both legacy and new configuration models.
                if (configuration instanceof final ClusterConfiguration clusterConfiguration) {
                  try {
                    // merge in case there was a concurrent update via gossip
                    persistedClusterConfiguration.update(
                        clusterConfiguration.merge(
                            persistedClusterConfiguration.getConfiguration()));
                    LOG.debug(
                        "Initialized (legacy) cluster configuration '{}'",
                        persistedClusterConfiguration.getConfiguration());
                    configurationGossiper.accept(persistedClusterConfiguration.getConfiguration());
                  } catch (final IOException e) {
                    startFuture.completeExceptionally(
                        "Failed to start update cluster configuration", e);
                  }
                } else if (configuration
                    instanceof final CurrentClusterConfiguration currentClusterConfiguration) {
                  try {
                    // merge in case there was a concurrent update via gossip
                    persistedCurrentConfiguration.update(
                        currentClusterConfiguration.merge(
                            persistedCurrentConfiguration.getConfiguration()));
                    LOG.debug(
                        "Initialized cluster configuration '{}'",
                        persistedCurrentConfiguration.getConfiguration());
                    if (currentConfigurationGossiper != null) {
                      currentConfigurationGossiper.accept(
                          persistedCurrentConfiguration.getConfiguration());
                    }
                  } catch (final IOException e) {
                    startFuture.completeExceptionally(
                        "Failed to start update cluster configuration", e);
                  }
                } else {
                  startFuture.completeExceptionally(
                      new IllegalStateException(
                          "Unexpected configuration type: " + configuration.getClass()));
                }
                setStarted();
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

  /**
   * New-model counterpart of {@link #isConflictingConfiguration(ClusterConfiguration,
   * ClusterConfiguration)}. The legacy model has a single partition group, so the local member's
   * state is checked once; the new model has one {@link PartitionGroupConfiguration} per physical
   * tenant, so the local member's state is checked in every group it appears in, in either
   * configuration.
   */
  private boolean isConflictingConfiguration(
      final CurrentClusterConfiguration mergedConfiguration,
      final CurrentClusterConfiguration oldConfiguration) {
    final var groupIds = new HashSet<>(oldConfiguration.partitionGroups().keySet());
    groupIds.addAll(mergedConfiguration.partitionGroups().keySet());
    return groupIds.stream()
        .anyMatch(groupId -> isConflictingGroup(groupId, mergedConfiguration, oldConfiguration));
  }

  private boolean isConflictingGroup(
      final String groupId,
      final CurrentClusterConfiguration mergedConfiguration,
      final CurrentClusterConfiguration oldConfiguration) {
    final var mergedGroup = mergedConfiguration.partitionGroup(groupId);
    final var oldGroup = oldConfiguration.partitionGroup(groupId);
    final var mergedMemberState =
        mergedGroup == null ? null : mergedGroup.members().get(localMemberId);
    final var oldMemberState = oldGroup == null ? null : oldGroup.members().get(localMemberId);
    if (mergedMemberState == null
        && oldMemberState != null
        && wasTargetedByCurrentPlan(oldConfiguration, groupId)) {
      // The member's zero-partition entry was pruned from this group as part of the current
      // plan's own operations targeting it (e.g. it is leaving this group as one of the plan's
      // steps). A faster peer's gossip can report that removal before the local apply catches up;
      // that race is expected, not a forced/unexpected change, so it's not a conflict. Mirrors the
      // LEFT-member exclusion in isConflictingConfiguration(ClusterConfiguration,
      // ClusterConfiguration) above. A removal the member was never an operation-target for (e.g. a
      // force-reconfigure applied by another member on its behalf) still falls through and
      // conflicts, since {@code wasTargetedByCurrentPlan} only returns true for the former.
      return false;
    }
    return !Objects.equals(mergedMemberState, oldMemberState);
  }

  /**
   * Returns {@code true} if the current (unmutated, per-phase) plan has an operation targeting
   * {@link #localMemberId} within {@code groupId}'s {@link PartitionGroupParallelPhase}s, whether
   * already completed or still pending. Phases are fixed at plan creation and never mutated, so
   * this reflects the member's original involvement in the plan regardless of how far it has since
   * progressed.
   */
  private boolean wasTargetedByCurrentPlan(
      final CurrentClusterConfiguration configuration, final String groupId) {
    return configuration.phasedChangeState().pending().values().stream()
        .flatMap(plan -> plan.phases().stream())
        .filter(PartitionGroupParallelPhase.class::isInstance)
        .map(PartitionGroupParallelPhase.class::cast)
        .map(phase -> phase.groupOperations().get(groupId))
        .filter(Objects::nonNull)
        .flatMap(List::stream)
        .anyMatch(operation -> operation.memberId().equals(localMemberId));
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
          try {
            final var local = persistedCurrentConfiguration.getConfiguration();
            final var merged = local.merge(receivedConfiguration);
            if (!merged.equals(local)) {
              final var isConflictingConfiguration = isConflictingConfiguration(merged, local);
              updateLocalCurrentConfiguration(merged)
                  .ifRightOrLeft(
                      applied -> {
                        if (isConflictingConfiguration
                            && onInconsistentConfigurationDetected != null) {
                          onInconsistentConfigurationDetected.onInconsistentConfiguration(
                              applied, local);
                        }
                        applyNewConfigurationChangeOperation();
                      },
                      error ->
                          LOG.warn(
                              "Failed to process cluster configuration received via gossip. '{}'",
                              receivedConfiguration,
                              error));
            } else {
              // The merge changed nothing locally, but a graph change may still be drained and
              // unfinished — most plausibly because an earlier attempt failed to persist. Retrying
              // on every gossip round rides the existing sync cadence and needs no timer of its
              // own.
              maybeCompleteGraphChanges(merged);
            }
          } catch (final Exception e) {
            LOG.warn(
                "Failed to process cluster configuration received via gossip. '{}'",
                receivedConfiguration,
                e);
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
      // Before maybeAdvancePhase: a phase counts as complete when its sub-configurations have no
      // pending changes, and finishing a drained graph change is what drains one.
      maybeCompleteGraphChanges(configuration);
      maybeAdvancePhase(configuration);
      return Either.right(configuration);
    } catch (final Exception e) {
      return Either.left(e);
    }
  }

  /**
   * Drives phased-plan advancement for every currently pending plan (multiple may be pending
   * concurrently, targeting disjoint sub-configurations). Invoked after every successful local
   * configuration update. Only the coordinator (the member with the lowest id, per {@link
   * ClusterConfigurationCoordinatorSupplier}) advances plans, so that a single member is
   * responsible for the transition. When a plan's current phase's sub-configuration(s) have drained
   * their pending changes, that plan is advanced to the next phase, or completed if it was the last
   * phase. The action is idempotent — re-firing on an already-advanced phase is a no-op. Plans are
   * advanced one at a time (each id re-reads the latest configuration before mutating it), so
   * advancing one plan never clobbers a concurrent update to another.
   */
  private void maybeAdvancePhase(final CurrentClusterConfiguration config) {
    if (config.phasedChangeState().pending().isEmpty() || !isLocalMemberCoordinator()) {
      return;
    }
    for (final var planId : List.copyOf(config.phasedChangeState().pending().keySet())) {
      maybeAdvancePhase(config, planId);
    }
  }

  private void maybeAdvancePhase(final CurrentClusterConfiguration config, final long planId) {
    final var plan = config.phasedChangeState().pending().get(planId);
    if (plan == null) {
      // Already advanced/completed by a previous call in this same batch, or by another trigger.
      return;
    }
    final boolean currentPhaseComplete =
        switch (plan.currentPhase()) {
          case final GlobalPhase ignored -> !config.globalConfiguration().hasPendingChanges();
          case final PartitionGroupParallelPhase parallelPhase ->
              parallelPhase.groupOperations().keySet().stream()
                  .map(config::partitionGroup)
                  .allMatch(group -> group != null && !group.hasPendingChanges());
          case final PartitionGroupGraphPhase graphPhase ->
              graphPhase.groupGraphs().keySet().stream()
                  .map(config::partitionGroup)
                  .allMatch(group -> group != null && !group.hasPendingChanges());
        };
    if (!currentPhaseComplete) {
      return;
    }

    if (plan.hasNextPhase()) {
      updateMultiConfiguration(c -> c.activateNextPhase(planId))
          .onComplete(
              (ignore, error) -> {
                if (error != null) {
                  LOG.warn(
                      "Failed to advance phased change plan '{}' to next phase", planId, error);
                  scheduleAdvancePhase(planId);
                } else {
                  advancePhaseRetryTimers.remove(planId);
                }
              });
    } else {
      updateMultiConfiguration(
              c ->
                  c.completePlan(
                      planId, PhasedChangePlanStatus.COMPLETED, completedChangeHistoryLimit))
          .onComplete(
              (ignore, error) -> {
                if (error != null) {
                  LOG.warn("Failed to complete phased change plan '{}'", planId, error);
                  scheduleAdvancePhase(planId);
                } else {
                  advancePhaseRetryTimers.remove(planId);
                }
              });
    }
  }

  private void scheduleAdvancePhase(final long planId) {
    advancePhaseRetryTimers.computeIfAbsent(
        planId,
        ignored ->
            executor.schedule(
                backoffRetry.nextDelay(),
                () -> {
                  // Cleared before firing (not in the success/failure branches of the retry
                  // itself), so a repeat failure can schedule a new timer instead of finding a
                  // stale, already-fired one still occupying this key.
                  advancePhaseRetryTimers.remove(planId);
                  maybeAdvancePhase(persistedCurrentConfiguration.getConfiguration(), planId);
                }));
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
      // Can apply operations to multiple groups in parallel. Within a group the queue path runs one
      // operation at a time; the dependency-graph path runs everything currently runnable. A group
      // holds one change of one model, so at most one of these does anything.
      applyPartitionGroupConfigurationChangeOperation(groupId);
      applyPartitionGroupGraphOperations(groupId);
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
    final var operation = pending.orElseThrow();
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
    final var operation = pending.orElseThrow();
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
      LOG.warn(
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

  // ---------------------------------------------------------------------------
  // Dependency-graph execution (see DependencyChangePlan). Only groups whose change was built by a
  // transformer that opted in take this path; everything else stays on the queue above.
  // ---------------------------------------------------------------------------

  /**
   * How many of a group's operations this broker will run at once.
   *
   * <p>The degree of concurrency is a runtime policy here rather than a property of the plan, which
   * is the point: it can be lowered to 1 to reproduce one-operation-at-a-time behaviour, or raised,
   * without changing a single transformer. Restore is I/O bound, so whether running several
   * partition restores at once on one broker actually helps is a question to settle by measurement.
   */
  private static final int MAX_CONCURRENT_GRAPH_OPERATIONS_PER_GROUP = 4;

  private void applyPartitionGroupGraphOperations(final String groupId) {
    final var config = persistedCurrentConfiguration.getConfiguration();
    final var group = config.partitionGroup(groupId);
    final var appliers = partitionGroupChangeAppliers.get(groupId);
    if (group == null || appliers == null) {
      return;
    }

    int started = 0;
    for (final var entry : group.runnableFor(localMemberId).entrySet()) {
      if (started >= MAX_CONCURRENT_GRAPH_OPERATIONS_PER_GROUP) {
        break;
      }
      if (inFlightGraphOperations.contains(new GraphOperationKey(groupId, entry.getKey()))) {
        continue;
      }
      startGraphOperation(groupId, entry.getKey(), entry.getValue(), appliers);
      started++;
    }
  }

  private void startGraphOperation(
      final String groupId,
      final OperationId operationId,
      final PartitionGroupOperation operation,
      final PartitionGroupConfigurationChangeAppliers appliers) {
    // Read the configuration fresh for each operation rather than once for the loop: init writes
    // through updateLocalCurrentConfiguration, which persists within this turn, so a hoisted read
    // would make the second operation of a batch overwrite the first one's init.
    final var config = persistedCurrentConfiguration.getConfiguration();
    final var group = config.partitionGroup(groupId);
    if (group == null) {
      return;
    }

    final var key = new GraphOperationKey(groupId, operationId);
    inFlightGraphOperations.add(key);
    final var observer = topologyMetrics.observeOperation(operation);
    LOG.info("Applying partition group '{}' operation {} ({})", groupId, operationId, operation);

    final var applier = appliers.getApplier(operation);
    final var initialized =
        applier
            .init(config.globalConfiguration(), group)
            .map(transformer -> config.updatePartitionGroupConfig(groupId, transformer))
            .flatMap(this::updateLocalCurrentConfiguration);

    if (initialized.isLeft()) {
      observer.failed();
      inFlightGraphOperations.remove(key);
      LOG.error(
          "Failed to initialize partition group '{}' operation {}",
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
                onGraphOperationApplied(
                    groupId,
                    operationId,
                    operation,
                    startedConfiguration,
                    transformer,
                    error,
                    observer));
  }

  private void onGraphOperationApplied(
      final String groupId,
      final OperationId operationId,
      final PartitionGroupOperation operation,
      final CurrentClusterConfiguration configurationOnWhichOperationIsApplied,
      final UnaryOperator<PartitionGroupConfiguration> transformer,
      final Throwable error,
      final OperationObserver observer) {
    final var key = new GraphOperationKey(groupId, operationId);
    inFlightGraphOperations.remove(key);

    if (error != null) {
      observer.failed();
      final Duration delay =
          graphOperationBackoff
              .computeIfAbsent(
                  key, ignored -> new ExponentialBackoffRetryDelay(maxRetryDelay, minRetryDelay))
              .nextDelay();
      LOG.warn(
          "Failed to apply partition group '{}' operation {}. Will be retried in {}.",
          groupId,
          operation,
          delay,
          error);
      // Only this operation retries; the group's other runnable operations are unaffected, which is
      // the point of tracking in-flight state per operation.
      executor.schedule(delay, () -> applyPartitionGroupGraphOperations(groupId));
      return;
    }

    observer.applied();
    graphOperationBackoff.remove(key);

    final var current = persistedCurrentConfiguration.getConfiguration();
    final var currentGroup = current.partitionGroup(groupId);
    final var startedGroup = configurationOnWhichOperationIsApplied.partitionGroup(groupId);
    if (currentGroup == null
        || startedGroup == null
        || currentGroup.version() != startedGroup.version()) {
      // Recording an operation moves no version, so a version change here means the change was
      // cancelled or already completed while this operation was running.
      LOG.warn(
          "Partition group '{}' changed while applying operation {}. Most likely the change was cancelled.",
          groupId,
          operation);
      return;
    }

    final var advanced =
        current.updatePartitionGroupConfig(
            groupId,
            g -> g.completeOperation(operationId, transformer).completeGraphChangeIfDrained());
    updateLocalCurrentConfiguration(advanced);
    LOG.info("Partition group '{}' operation {} applied.", groupId, operation);
    executor.run(this::applyNewConfigurationChangeOperation);
  }

  /**
   * Finishes any graph change whose operations have all completed.
   *
   * <p>Runs on every broker with no coordinator check: completion is a pure function of converged
   * state, so several brokers computing it agree. It must also run when a gossip merge changes
   * nothing locally — otherwise a completion whose persist failed is never retried once the cluster
   * converges, and the change sits drained but unfinished with nothing left to perturb it.
   */
  private void maybeCompleteGraphChanges(final CurrentClusterConfiguration config) {
    final var drained =
        config.partitionGroups().entrySet().stream()
            .anyMatch(
                entry ->
                    entry
                        .getValue()
                        .pendingGraphChanges()
                        .filter(plan -> !plan.hasPendingChanges())
                        .isPresent());
    if (!drained) {
      return;
    }
    updateMultiConfiguration(ClusterConfigurationManagerImpl::completeDrainedGraphChanges)
        .onComplete(
            (ignore, completionError) -> {
              if (completionError != null) {
                LOG.warn("Failed to complete a drained configuration change", completionError);
              }
            });
  }

  private static CurrentClusterConfiguration completeDrainedGraphChanges(
      final CurrentClusterConfiguration config) {
    var result = config;
    for (final String groupId : List.copyOf(config.partitionGroups().keySet())) {
      result =
          result.updatePartitionGroupConfig(
              groupId, PartitionGroupConfiguration::completeGraphChangeIfDrained);
    }
    return result;
  }

  /** Identifies one in-flight operation: operation ids are unique only within a group's plan. */
  private record GraphOperationKey(String groupId, OperationId operationId) {}
}

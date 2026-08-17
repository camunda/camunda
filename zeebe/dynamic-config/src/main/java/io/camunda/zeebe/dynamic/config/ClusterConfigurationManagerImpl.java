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
import io.camunda.zeebe.dynamic.config.changes.GlobalConfigurationChangeAppliers;
import io.camunda.zeebe.dynamic.config.changes.PartitionGroupConfigurationChangeAppliers;
import io.camunda.zeebe.dynamic.config.metrics.TopologyManagerMetrics;
import io.camunda.zeebe.dynamic.config.metrics.TopologyManagerMetrics.OperationObserver;
import io.camunda.zeebe.dynamic.config.state.ClusterConfiguration;
import io.camunda.zeebe.dynamic.config.state.CurrentClusterConfiguration;
import io.camunda.zeebe.dynamic.config.state.GlobalChangeOperation;
import io.camunda.zeebe.dynamic.config.state.GlobalConfiguration;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupConfiguration;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation;
import io.camunda.zeebe.dynamic.config.state.PhasedChangePlan.GlobalPhase;
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
 * See {@link GlobalConfigurationChangeAppliers} and {@link
 * PartitionGroupConfigurationChangeAppliers} to see how a change is applied locally.
 */
public final class ClusterConfigurationManagerImpl implements ClusterConfigurationManager {

  private static final Logger LOG = LoggerFactory.getLogger(ClusterConfigurationManagerImpl.class);
  private static final Duration MIN_RETRY_DELAY = Duration.ofSeconds(10);
  private static final Duration MAX_RETRY_DELAY = Duration.ofMinutes(1);
  private final ConcurrencyControl executor;
  private final ActorFuture<Void> startFuture;
  private InconsistentConfigurationListener onInconsistentConfigurationDetected;
  private final MemberId localMemberId;
  private final ExponentialBackoffRetryDelay backoffRetry;
  private boolean initialized = false;
  private final TopologyManagerMetrics topologyMetrics;
  private final boolean useNewConfig;

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
  // Keyed by plan id, since multiple plans can be advancing (and independently retrying) at once.
  private final Map<Long, ScheduledTimer> advancePhaseRetryTimers = new HashMap<>();
  private final int completedChangeHistoryLimit;

  /** Constructs a manager operating on the multi-partition-group model. */
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

  /** Legacy single-group projection of the multi-partition-group configuration. */
  @Override
  public ActorFuture<ClusterConfiguration> getClusterConfiguration() {
    final var future = executor.<ClusterConfiguration>createFuture();
    executor.run(
        () -> future.complete(persistedCurrentConfiguration.getConfiguration().toLegacyDefault()));
    return future;
  }

  /**
   * Not supported on the multi-partition-group model — the legacy single-group configuration cannot
   * be mutated in isolation. Use {@link #updateMultiConfiguration} instead.
   */
  @Override
  public ActorFuture<ClusterConfiguration> updateClusterConfiguration(
      final UnaryOperator<ClusterConfiguration> configUpdater) {
    throw new UnsupportedOperationException(
        "updateClusterConfiguration is not supported; use updateMultiConfiguration instead");
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
      final ClusterConfigurationInitializer<CurrentClusterConfiguration>
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

  private void initialize(
      final ClusterConfigurationInitializer<CurrentClusterConfiguration>
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
                try {
                  // merge in case there was a concurrent update via gossip
                  persistedCurrentConfiguration.update(
                      configuration.merge(persistedCurrentConfiguration.getConfiguration()));
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

  /**
   * The local member's state is checked in every {@link PartitionGroupConfiguration} (physical
   * tenant) it appears in, in either configuration.
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
      // that race is expected, not a forced/unexpected change, so it's not a conflict. A removal
      // the member was never an operation-target for (e.g. a force-reconfigure applied by another
      // member on its behalf) still falls through and conflicts, since {@code
      // wasTargetedByCurrentPlan} only returns true for the former.
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
   * application is triggered. Leaves local state unchanged until {@link #start} has completed, to
   * avoid a race between the configuration initializer and a concurrently received gossip update.
   */
  void onGossipReceivedCurrent(final CurrentClusterConfiguration receivedConfiguration) {
    executor.run(
        () -> {
          if (receivedConfiguration == null) {
            return;
          }
          if (!initialized) {
            LOG.trace(
                "Received configuration {} before ClusterConfigurationManager is initialized.",
                receivedConfiguration);
            // When not started, do not update the local configuration. This is to avoid any race
            // condition between FileInitializer and concurrently received configuration via gossip.
            if (currentConfigurationGossiper != null) {
              currentConfigurationGossiper.accept(receivedConfiguration);
            }
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
}

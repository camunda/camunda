/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config;

import io.atomix.cluster.MemberId;
import io.camunda.zeebe.dynamic.config.GraphScopeReconciler.Operation;
import io.camunda.zeebe.dynamic.config.GraphScopeReconciler.Scope;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationCoordinatorSupplier;
import io.camunda.zeebe.dynamic.config.changes.GlobalConfigurationChangeApplier;
import io.camunda.zeebe.dynamic.config.changes.GlobalConfigurationChangeAppliers;
import io.camunda.zeebe.dynamic.config.changes.PartitionGroupConfigurationChangeApplier;
import io.camunda.zeebe.dynamic.config.changes.PartitionGroupConfigurationChangeAppliers;
import io.camunda.zeebe.dynamic.config.changes.appliers.RemovePhysicalTenantApplier;
import io.camunda.zeebe.dynamic.config.metrics.TopologyManagerMetrics;
import io.camunda.zeebe.dynamic.config.state.ClusterConfiguration;
import io.camunda.zeebe.dynamic.config.state.ClusterConfigurationChangeOperation;
import io.camunda.zeebe.dynamic.config.state.CurrentClusterConfiguration;
import io.camunda.zeebe.dynamic.config.state.DependencyChangePlan;
import io.camunda.zeebe.dynamic.config.state.GlobalChangeOperation;
import io.camunda.zeebe.dynamic.config.state.GlobalConfiguration;
import io.camunda.zeebe.dynamic.config.state.OperationId;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupConfiguration;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.RemovePhysicalTenantOperation;
import io.camunda.zeebe.dynamic.config.state.PhasedChangePlan.GlobalPhase;
import io.camunda.zeebe.dynamic.config.state.PhasedChangePlan.PartitionGroupPhase;
import io.camunda.zeebe.dynamic.config.state.PhasedChangePlan.Phase;
import io.camunda.zeebe.dynamic.config.state.PhasedChangeState;
import io.camunda.zeebe.scheduler.ConcurrencyControl;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.util.Either;
import io.camunda.zeebe.util.VisibleForTesting;
import java.io.IOException;
import java.time.Duration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;
import org.jspecify.annotations.NonNull;
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
 *
 * <h4>Driving changes forward: scopes and plans</h4>
 *
 * <p>Applying the local member's pending operations and advancing a phased change plan are both
 * driven from one place: {@link #reconcile(CurrentClusterConfiguration)}, invoked after every
 * successful local configuration update (see {@link #updateLocalCurrentConfiguration}). It fans out
 * to two kinds of per-key workers, each independently retrying with its own backoff so that one
 * key's failures cannot delay another's:
 *
 * <ul>
 *   <li>A {@link GraphScopeReconciler} per scope — the global configuration, plus one per partition
 *       group — applies whichever of that scope's operations the local member may run right now.
 *       Several at once where the scope's graph allows it; the global configuration's graph is
 *       sequential, so there it is one at a time.
 *   <li>A {@link PlanAdvancer} per pending plan id advances that plan to its next phase, or
 *       completes it, once its current phase has fully drained.
 * </ul>
 */
public final class ClusterConfigurationManagerImpl implements ClusterConfigurationManager {

  private static final Logger LOG = LoggerFactory.getLogger(ClusterConfigurationManagerImpl.class);
  private static final Duration MIN_RETRY_DELAY = Duration.ofSeconds(10);
  private static final Duration MAX_RETRY_DELAY = Duration.ofMinutes(1);
  private final ConcurrencyControl executor;
  private final ActorFuture<Void> startFuture;
  private InconsistentConfigurationListener onInconsistentConfigurationDetected;
  private final MemberId localMemberId;
  private boolean initialized = false;
  private final TopologyManagerMetrics topologyMetrics;
  private final boolean useNewConfig;

  private final PersistedCurrentClusterConfiguration persistedCurrentConfiguration;
  private @Nullable Consumer<CurrentClusterConfiguration> currentConfigurationGossiper;
  private final ClusterConfigurationCoordinatorSupplier coordinatorSupplier;
  private @Nullable GlobalConfigurationChangeAppliers globalChangeAppliers;
  private final Map<String, PartitionGroupConfigurationChangeAppliers>
      partitionGroupChangeAppliers = new HashMap<>();
  private final Duration minRetryDelay;
  private final Duration maxRetryDelay;
  private @Nullable GraphScopeReconciler globalScopeReconciler;
  // Keyed by group id; the global configuration's reconciler is held separately above, since it is
  // created once and outlives any group.
  private final Map<String, GraphScopeReconciler> graphReconcilers = new HashMap<>();
  // Keyed by plan id, since multiple plans can be advancing (and independently retrying) at once.
  private final Map<Long, PlanAdvancer> planAdvancers = new HashMap<>();
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
    useNewConfig = true;
    coordinatorSupplier =
        ClusterConfigurationCoordinatorSupplier.from(
            () -> this.persistedCurrentConfiguration.getConfiguration());
  }

  /**
   * Not supported on the multi-partition-group model — the legacy single-group configuration cannot
   * be mutated in isolation. Use {@link #updateMultiConfiguration} instead.
   */
  @Deprecated
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
   * triggers reconciliation (see {@link #reconcile}). Only valid when {@link #useNewConfig} is
   * true.
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
                .ifRightOrLeft(future::complete, future::completeExceptionally);
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
                CurrentClusterConfiguration toPersist;
                try {
                  // merge in case there was a concurrent update via gossip
                  toPersist = configuration.merge(persistedCurrentConfiguration.getConfiguration());
                } catch (final RuntimeException e) {
                  // The merge can throw when a concurrently-gossiped peer's write genuinely
                  // conflicts with this member's own state at the same version (see
                  // BrokerPartitionState#merge) -- a real disagreement between brokers, not a
                  // transient error. Failing start over it would leave this broker down
                  // indefinitely against a condition later, unrelated writes are what actually
                  // resolve. Starting from this member's own freshly-initialized configuration
                  // instead -- skipping the merge, not skipping the update -- lets normal gossip
                  // reconciliation surface and retry against the same conflict once running,
                  // exactly as it does for one arriving after start via {@link
                  // #onGossipReceivedCurrent}.
                  LOG.warn(
                      "Failed to merge cluster configuration during startup; starting without "
                          + "the merge",
                      e);
                  toPersist = configuration;
                }
                try {
                  persistedCurrentConfiguration.update(toPersist);
                } catch (final IOException e) {
                  startFuture.completeExceptionally(
                      "Failed to start update cluster configuration", e);
                  return;
                }
                LOG.debug(
                    "Initialized cluster configuration '{}'",
                    persistedCurrentConfiguration.getConfiguration());
                if (currentConfigurationGossiper != null) {
                  currentConfigurationGossiper.accept(
                      persistedCurrentConfiguration.getConfiguration());
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
   * {@link #localMemberId} within {@code groupId}'s {@link PartitionGroupPhase}s, whether already
   * completed or still pending. Phases are fixed at plan creation and never mutated, so this
   * reflects the member's original involvement in the plan regardless of how far it has since
   * progressed.
   */
  private boolean wasTargetedByCurrentPlan(
      final CurrentClusterConfiguration configuration, final String groupId) {
    return configuration.phasedChangeState().pending().values().stream()
        .flatMap(plan -> plan.phases().stream())
        .map(phase -> groupOperationsOf(phase, groupId))
        .filter(Objects::nonNull)
        .flatMap(List::stream)
        .anyMatch(operation -> operation.memberId().equals(localMemberId));
  }

  /**
   * {@code phase}'s operations for {@code groupId}, or {@code null} if the phase targets no group
   * at all ({@link GlobalPhase}) or not this one.
   *
   * <p>Switches over every phase kind rather than filtering for one, for the same reason {@link
   * io.camunda.zeebe.dynamic.config.state.PhasedChangePlan#scopeOf} does: a phase kind added later
   * without a case here fails to compile, instead of silently making {@link
   * #wasTargetedByCurrentPlan} blind to it.
   */
  private static @Nullable List<PartitionGroupOperation> groupOperationsOf(
      final Phase phase, final String groupId) {
    return switch (phase) {
      case final GlobalPhase ignored -> null;
      case final PartitionGroupPhase groupPhase -> groupPhase.groupOperations().get(groupId);
    };
  }

  void registerTopologyChangedListener(final InconsistentConfigurationListener listener) {
    executor.run(() -> onInconsistentConfigurationDetected = listener);
  }

  void removeTopologyChangedListener() {
    executor.run(() -> onInconsistentConfigurationDetected = null);
  }

  /**
   * Drops every scope's and plan's reconciliation worker. Only meant to be called once, as part of
   * shutting down the whole manager (see {@link ClusterConfigurationManagerService#closeAsync} ) —
   * unlike {@link #removePartitionGroupChangeAppliers}, which must leave a group's {@link
   * GraphScopeReconciler} alone so it survives that group's own register/remove churn.
   */
  void close() {
    executor.run(
        () -> {
          globalScopeReconciler = null;
          graphReconcilers.clear();
          planAdvancers.clear();
        });
  }

  void setCurrentConfigurationGossiper(
      final Consumer<CurrentClusterConfiguration> currentConfigurationGossiper) {
    this.currentConfigurationGossiper = currentConfigurationGossiper;
  }

  void registerGlobalChangeAppliers(final GlobalConfigurationChangeAppliers appliers) {
    executor.run(
        () -> {
          globalChangeAppliers = appliers;
          if (globalScopeReconciler == null) {
            globalScopeReconciler = newGlobalScopeReconciler();
          }
          reconcile(persistedCurrentConfiguration.getConfiguration());
        });
  }

  void registerPartitionGroupChangeAppliers(
      final String groupId, final PartitionGroupConfigurationChangeAppliers appliers) {
    executor.run(
        () -> {
          partitionGroupChangeAppliers.put(groupId, appliers);
          reconcile(persistedCurrentConfiguration.getConfiguration());
        });
  }

  /**
   * The single place group-scoped {@link GraphScopeReconciler}s are created — registering a group's
   * appliers via {@link #registerPartitionGroupChangeAppliers} does not create one. Every live
   * group keeps one on every broker, whether or not the broker registered that group's appliers — a
   * broker can be named in a group operation without ever hosting the group (a disabled physical
   * tenant's forced removal executes on whichever broker received the request, and no broker runs a
   * disabled tenant). Removed groups are tombstones that can never have work again, and they
   * accumulate over a cluster's lifetime, so no new reconciler is created for one — an existing one
   * persisting is fine, since reconcilers are deliberately never removed except on {@link #close}.
   * A reconciler with nothing pending is a no-op per pass.
   */
  private void ensurePartitionGroupScopeReconcilers(final CurrentClusterConfiguration config) {
    config
        .partitionGroups()
        .forEach(
            (groupId, group) -> {
              if (!group.isRemoved()) {
                graphReconcilers.computeIfAbsent(
                    groupId, ignored -> newGraphScopeReconciler(groupId));
              }
            });
  }

  void removePartitionGroupChangeAppliers(final String groupId) {
    executor.run(
        () -> {
          partitionGroupChangeAppliers.remove(groupId);
          // Deliberately not removing this group's GraphScopeReconciler here: it is created solely
          // by ensurePartitionGroupScopeReconcilers, so it survives a remove/register round-trip
          // unmolested. PartitionManagerImpl/RecoveryPartitionManager re-register their change
          // appliers on every recovery/processing mode transition; discarding the reconciler here
          // would discard its in-flight retry/backoff state on every such transition, leaving a
          // broker stuck mid-transition (see
          // ModeChangeAcceptanceIT#shouldCycleBetweenRecoveryAndProcessing).
        });
  }

  /**
   * Merges a {@link CurrentClusterConfiguration} received via gossip into the local one. If the
   * merge changes the local configuration, it is persisted, re-gossiped, and reconciliation is
   * triggered. Leaves local state unchanged until {@link #start} has completed, to avoid a race
   * between the configuration initializer and a concurrently received gossip update.
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
                      },
                      error ->
                          LOG.warn(
                              "Failed to process cluster configuration received via gossip. '{}'",
                              receivedConfiguration,
                              error));
            } else {
              // The merge changed nothing locally, so reconcile() is not reached — but a graph
              // change may still be drained and unfinished, most plausibly because an earlier
              // attempt to finish it failed to persist. Retrying on every gossip round rides the
              // existing sync cadence and needs no timer of its own.
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
      reconcile(configuration);
      return Either.right(configuration);
    } catch (final Exception e) {
      return Either.left(e);
    }
  }

  /**
   * The single entry point that drives every pending plan and every scope's pending operation
   * forward, invoked after every successful local configuration update. Only the coordinator (the
   * member with the lowest id, per {@link ClusterConfigurationCoordinatorSupplier}) advances plans,
   * so that a single member is responsible for the transition; every member applies its own pending
   * operations regardless of coordinator status. Both are idempotent — re-firing while an action is
   * already in flight for a given plan or scope is a no-op (see {@link PlanAdvancer} and {@link
   * ScopeReconciler}).
   *
   * <p>This call chain is synchronous, not scheduled: staging an operation persists/gossips
   * immediately, which re-enters this method before returning (guarded by the in-flight flags above
   * from becoming a second concurrent attempt, not from recursing at all). Its stack depth is
   * therefore bounded by the number of registered scopes with a simultaneously-ready operation, not
   * by the number of phases or operations processed overall — acceptable at the scope counts
   * (global plus one per physical tenant) this module deals with today, but worth keeping in mind
   * if that count were ever to grow very large.
   */
  private void reconcile(final CurrentClusterConfiguration config) {
    final var pendingIds = config.phasedChangeState().pending().keySet();
    ensurePartitionGroupScopeReconcilers(config);
    if (isLocalMemberCoordinator()) {
      for (final var planId : List.copyOf(pendingIds)) {
        planAdvancers.computeIfAbsent(planId, this::newPlanAdvancer).maybeAdvance(config);
      }
    }
    // Drop advancers for plans that are no longer pending, so this map doesn't grow one entry per
    // plan for the process lifetime. Skipped when already empty (the common case: most updates
    // happen while no plan is pending at all) rather than diffing against pendingIds for nothing.
    if (!planAdvancers.isEmpty()) {
      planAdvancers.keySet().retainAll(pendingIds);
    }
    if (globalScopeReconciler != null) {
      globalScopeReconciler.reconcile();
    }
    graphReconcilers.values().forEach(GraphScopeReconciler::reconcile);
    maybeCompleteGraphChanges(config);
  }

  /**
   * Finishes any graph change whose operations have all completed.
   *
   * <p>Runs on every broker with no coordinator check: completion is a pure function of converged
   * state, so several brokers computing it agree. It must also run when a gossip merge changes
   * nothing locally (see {@link #onGossipReceivedCurrent}) — otherwise a completion whose persist
   * failed is never retried once the cluster converges, and the change sits drained but unfinished
   * with nothing left to perturb it.
   */
  private void maybeCompleteGraphChanges(final CurrentClusterConfiguration config) {
    final var drained =
        Stream.concat(
                Stream.of(config.globalConfiguration().pendingChanges()),
                config.partitionGroups().values().stream()
                    .map(PartitionGroupConfiguration::pendingChanges))
            .anyMatch(plan -> plan.filter(p -> !p.hasPendingChanges()).isPresent());
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

  /**
   * The global arm is belt-and-braces while a cluster-wide graph is a chain: its last operation
   * records and clears in one transition, and operation N only becomes runnable once N-1 is
   * complete locally, so no broker observes the change drained but unfinished. It is here because
   * "finish a drained change" is a property of the execution model, not of a scope, and the first
   * cluster-wide change that declares two independent operations would need it.
   */
  private static CurrentClusterConfiguration completeDrainedGraphChanges(
      final CurrentClusterConfiguration config) {
    var result =
        config.updateGlobalConfiguration(GlobalConfiguration::completeGraphChangeIfDrained);
    for (final String groupId : List.copyOf(config.partitionGroups().keySet())) {
      result =
          result.updatePartitionGroupConfig(
              groupId, PartitionGroupConfiguration::completeGraphChangeIfDrained);
    }
    return result;
  }

  private PlanAdvancer newPlanAdvancer(final long planId) {
    return new PlanAdvancer(
        planId,
        executor,
        this::updateMultiConfiguration,
        persistedCurrentConfiguration::getConfiguration,
        this::isLocalMemberCoordinator,
        minRetryDelay,
        maxRetryDelay,
        completedChangeHistoryLimit);
  }

  private GraphScopeReconciler newGlobalScopeReconciler() {
    return newScopeReconciler(new GlobalScope());
  }

  private GraphScopeReconciler newGraphScopeReconciler(final String groupId) {
    return newScopeReconciler(new PartitionGroupScope(groupId));
  }

  private GraphScopeReconciler newScopeReconciler(final Scope scope) {
    return new GraphScopeReconciler(
        scope,
        localMemberId,
        persistedCurrentConfiguration::getConfiguration,
        this::updateLocalCurrentConfiguration,
        executor,
        topologyMetrics,
        minRetryDelay,
        maxRetryDelay);
  }

  private boolean isLocalMemberCoordinator() {
    return coordinatorSupplier != null
        && localMemberId.equals(coordinatorSupplier.getDefaultCoordinator());
  }

  /**
   * A {@code RemovePhysicalTenantOperation} is dispatchable even with no {@code appliers}
   * registered for the group, since it is a pure configuration edit with no broker-side executor.
   * Every other operation still requires a registered appliers set, returning {@code
   * Optional.empty()} — leaving it pending — otherwise.
   */
  private static Optional<PartitionGroupConfigurationChangeApplier> applierFor(
      final PartitionGroupOperation operation,
      final @Nullable PartitionGroupConfigurationChangeAppliers appliers) {
    if (operation instanceof RemovePhysicalTenantOperation) {
      return Optional.of(new RemovePhysicalTenantApplier());
    }
    return Optional.ofNullable(appliers).map(a -> a.getApplier(operation));
  }

  private record GlobalOperation(GlobalConfigurationChangeApplier applier) implements Operation {

    @Override
    public Either<Exception, CurrentClusterConfiguration> initialize(
        final CurrentClusterConfiguration config) {
      return applier.init(config).map(config::updateGlobalConfiguration);
    }

    @Override
    public ActorFuture<UnaryOperator<CurrentClusterConfiguration>> apply(
        final @NonNull OperationId operationId) {
      return applier
          .apply()
          .thenApply(
              transformer ->
                  (UnaryOperator<CurrentClusterConfiguration>)
                      config ->
                          config.updateGlobalConfiguration(
                              g ->
                                  g.completeOperation(operationId, transformer)
                                      .completeGraphChangeIfDrained()));
    }
  }

  private record PartitionGroupOperationApplication(
      String groupId, PartitionGroupConfigurationChangeApplier applier) implements Operation {

    @Override
    public Either<Exception, CurrentClusterConfiguration> initialize(
        final CurrentClusterConfiguration config) {
      final var group = config.partitionGroup(groupId);
      if (group == null) {
        return Either.left(
            new IllegalStateException(
                "Expected to apply an operation of partition group '%s', but it does not exist"
                    .formatted(groupId)));
      }
      return applier
          .init(config.globalConfiguration(), group)
          .map(transformer -> config.updatePartitionGroupConfig(groupId, transformer));
    }

    @Override
    public ActorFuture<UnaryOperator<CurrentClusterConfiguration>> apply(
        final OperationId operationId) {
      return applier
          .apply()
          .thenApply(
              transformer ->
                  (UnaryOperator<CurrentClusterConfiguration>)
                      config ->
                          config.updatePartitionGroupConfig(
                              groupId,
                              group ->
                                  group
                                      .completeOperation(operationId, transformer)
                                      .completeGraphChangeIfDrained()));
    }
  }

  /** {@link GraphScopeReconciler.Scope} for the global configuration. */
  private final class GlobalScope implements Scope {

    @Override
    public @Nullable DependencyChangePlan plan(final CurrentClusterConfiguration config) {
      return config.globalConfiguration().pendingChanges().orElse(null);
    }

    @Override
    public long versionOf(final CurrentClusterConfiguration config) {
      return config.globalConfiguration().version();
    }

    @Override
    public String describe() {
      return "global configuration";
    }

    @Override
    public Optional<Operation> operationFor(final ClusterConfigurationChangeOperation operation) {
      if (globalChangeAppliers == null) {
        return Optional.empty();
      }
      final var globalOperation = (GlobalChangeOperation) operation;
      return Optional.of(new GlobalOperation(globalChangeAppliers.getApplier(globalOperation)));
    }
  }

  /** {@link GraphScopeReconciler.Scope} for one partition group. */
  private final class PartitionGroupScope implements Scope {

    private final String groupId;

    private PartitionGroupScope(final String groupId) {
      this.groupId = groupId;
    }

    @Override
    public @Nullable DependencyChangePlan plan(final CurrentClusterConfiguration config) {
      final var group = config.partitionGroup(groupId);
      return group == null ? null : group.pendingChanges().orElse(null);
    }

    @Override
    public long versionOf(final CurrentClusterConfiguration config) {
      final var group = config.partitionGroup(groupId);
      return group == null ? -1 : group.version();
    }

    @Override
    public String describe() {
      return "partition group '%s'".formatted(groupId);
    }

    @Override
    public Optional<Operation> operationFor(final ClusterConfigurationChangeOperation operation) {
      return applierFor(
              (PartitionGroupOperation) operation, partitionGroupChangeAppliers.get(groupId))
          .map(applier -> new PartitionGroupOperationApplication(groupId, applier));
    }
  }
}

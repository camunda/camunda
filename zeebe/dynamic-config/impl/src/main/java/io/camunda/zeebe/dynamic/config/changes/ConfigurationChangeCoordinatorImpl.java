/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config.changes;

import io.atomix.cluster.MemberId;
import io.camunda.zeebe.dynamic.config.ClusterConfigurationManager;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationRequestFailedException;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationRequestFailedException.ConcurrentModificationException;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationRequestFailedException.InvalidRequest;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationRequestFailedException.OperationNotAllowed;
import io.camunda.zeebe.dynamic.config.changes.ClusterChangeExecutor.NoopClusterChangeExecutor;
import io.camunda.zeebe.dynamic.config.changes.ModeChangeExecutor.NoopModeChangeExecutor;
import io.camunda.zeebe.dynamic.config.changes.PartitionScalingChangeExecutor.NoopPartitionScalingChangeExecutor;
import io.camunda.zeebe.dynamic.config.changes.RestoreChangeExecutor.NoopRestoreChangeExecutor;
import io.camunda.zeebe.dynamic.config.state.ClusterConfigurationChangeOperation;
import io.camunda.zeebe.dynamic.config.state.CompletedPhasedChange;
import io.camunda.zeebe.dynamic.config.state.CurrentClusterConfiguration;
import io.camunda.zeebe.dynamic.config.state.GlobalChangeOperation;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation;
import io.camunda.zeebe.dynamic.config.state.PhasedChangePlan;
import io.camunda.zeebe.dynamic.config.state.PhasedChangePlan.GlobalPhase;
import io.camunda.zeebe.dynamic.config.state.PhasedChangePlan.PartitionGroupPhase;
import io.camunda.zeebe.dynamic.config.state.PhasedChangePlan.Phase;
import io.camunda.zeebe.dynamic.config.state.PhasedChangePlanStatus;
import io.camunda.zeebe.dynamic.config.state.PhasedChangeState;
import io.camunda.zeebe.scheduler.ConcurrencyControl;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import org.agrona.collections.MutableLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ConfigurationChangeCoordinatorImpl implements ConfigurationChangeCoordinator {
  private static final Logger LOG =
      LoggerFactory.getLogger(ConfigurationChangeCoordinatorImpl.class);
  private final ClusterConfigurationManager clusterTopologyManager;
  private final ConcurrencyControl executor;
  private final MemberId localMemberId;
  private final int completedChangeHistoryLimit;

  public ConfigurationChangeCoordinatorImpl(
      final ClusterConfigurationManager clusterTopologyManager,
      final MemberId localMemberId,
      final ConcurrencyControl executor) {
    this(clusterTopologyManager, localMemberId, executor, PhasedChangeState.DEFAULT_HISTORY_LIMIT);
  }

  /**
   * @param completedChangeHistoryLimit maximum number of completed changes retained in {@link
   *     io.camunda.zeebe.dynamic.config.state.PhasedChangeState#history()}, oldest evicted first
   */
  public ConfigurationChangeCoordinatorImpl(
      final ClusterConfigurationManager clusterTopologyManager,
      final MemberId localMemberId,
      final ConcurrencyControl executor,
      final int completedChangeHistoryLimit) {
    this.clusterTopologyManager = clusterTopologyManager;
    this.executor = executor;
    this.localMemberId = localMemberId;
    this.completedChangeHistoryLimit = completedChangeHistoryLimit;
  }

  @Override
  public ActorFuture<CurrentClusterConfiguration> getClusterConfiguration() {
    return clusterTopologyManager.getMultiConfiguration();
  }

  @Override
  public ActorFuture<ConfigurationChangeResult> applyOperations(
      final ConfigurationChangeRequest request) {
    return applyOrDryRun(false, request);
  }

  @Override
  public ActorFuture<ConfigurationChangeResult> simulateOperations(
      final ConfigurationChangeRequest request) {
    return applyOrDryRun(true, request);
  }

  @Override
  public ActorFuture<CurrentClusterConfiguration> cancelChange(final long changeId) {
    return cancelChangeNewModel(changeId);
  }

  private ActorFuture<ConfigurationChangeResult> applyOrDryRun(
      final boolean dryRun, final ConfigurationChangeRequest request) {
    return applyOrDryRunNewModel(dryRun, request);
  }

  private void failFuture(final ActorFuture<?> future, final Throwable error) {
    LOG.warn("Failed to handle topology request", error);
    if (error instanceof ClusterConfigurationRequestFailedException) {
      future.completeExceptionally(error);
    } else {
      future.completeExceptionally(
          new ClusterConfigurationRequestFailedException.InternalError(error));
    }
  }

  private ActorFuture<CurrentClusterConfiguration> cancelChangeNewModel(final long changeId) {
    final ActorFuture<CurrentClusterConfiguration> future = executor.createFuture();
    executor.run(
        () ->
            clusterTopologyManager
                .updateMultiConfiguration(
                    currentConfiguration -> {
                      if (!validateCancelNewModel(changeId, currentConfiguration, future)) {
                        return currentConfiguration;
                      }
                      LOG.warn("Cancelling configuration change '{}'.", changeId);
                      return currentConfiguration.cancelPendingChanges(
                          changeId, completedChangeHistoryLimit);
                    })
                .onComplete(
                    (updatedConfiguration, error) -> {
                      if (error != null) {
                        failFuture(future, error);
                        return;
                      }
                      if (!future.isDone()) {
                        future.complete(updatedConfiguration);
                      }
                    },
                    executor));
    return future;
  }

  private boolean validateCancelNewModel(
      final long changeId,
      final CurrentClusterConfiguration currentConfiguration,
      final ActorFuture<CurrentClusterConfiguration> future) {
    if (currentConfiguration.isUninitialized()) {
      failFuture(
          future,
          new InvalidRequest(
              "Cannot cancel change " + changeId + " because the topology is not initialized"));
      return false;
    }
    final var state = currentConfiguration.phasedChangeState();
    if (!state.wasIssued(changeId)) {
      failFuture(
          future,
          new InvalidRequest(
              "Cannot cancel change " + changeId + " because it is not a known change"));
      return false;
    }
    // changeId < nextId: either still pending (cancel proceeds below) or already resolved
    // (completed/failed/cancelled, whether still in the retained history window or aged out of
    // it) — cancelling an already-resolved change is treated as an idempotent no-op success,
    // since a resolved id can never be re-distinguished from "no longer tracked" once history ages
    // it out, and both cases mean "there is nothing left to cancel".
    return true;
  }

  private ActorFuture<ConfigurationChangeResult> applyOrDryRunNewModel(
      final boolean dryRun, final ConfigurationChangeRequest request) {
    final ActorFuture<ConfigurationChangeResult> future = executor.createFuture();
    executor.run(
        () ->
            clusterTopologyManager
                .getMultiConfiguration()
                .onComplete(
                    (currentConfiguration, errorOnGettingConfig) -> {
                      if (errorOnGettingConfig != null) {
                        failFuture(future, errorOnGettingConfig);
                        return;
                      }
                      if (!request.isForced() && !isCoordinator(currentConfiguration)) {
                        failFuture(
                            future,
                            new ClusterConfigurationRequestFailedException.InternalError(
                                String.format(
                                    "Cannot process request to change the configuration. The broker '%s' is not the coordinator.",
                                    localMemberId)));
                        return;
                      }
                      // Request transformers are unchanged: they still read a legacy
                      // ClusterConfiguration (the default-group projection) to generate the
                      // phases to run. Everything past this point — validation, simulation, and
                      // application — uses the new multi-group model directly, so supporting
                      // non-default groups in the future only requires changing what phases()
                      // returns, not this coordinator.
                      //
                      // Requests that don't opt into targeting disabled physical tenants only see
                      // active partition groups here, so phases() can never generate a phase that
                      // targets a disabled tenant. Validation and application below still use the
                      // unfiltered configuration: the generated phases are already restricted to
                      // active groups, so validating/applying them against the full configuration
                      // is safe and lets a phase's post-condition still observe disabled groups.
                      final var configurationForPhases =
                          request.applyToDisabledTenants()
                              ? currentConfiguration
                              : currentConfiguration.withoutDisabledPartitionGroups();
                      final var generatedPhases = request.phases(configurationForPhases);
                      if (generatedPhases.isLeft()) {
                        failFuture(future, generatedPhases.getLeft());
                        return;
                      }
                      applyOrDryRunOnConfigurationNewModel(
                          dryRun, currentConfiguration, generatedPhases.get(), future);
                    },
                    executor));
    return future;
  }

  private void applyOrDryRunOnConfigurationNewModel(
      final boolean dryRun,
      final CurrentClusterConfiguration currentConfiguration,
      final List<Phase> phases,
      final ActorFuture<ConfigurationChangeResult> future) {
    if (phases.isEmpty()) {
      final var legacyView = currentConfiguration.toLegacyDefault();
      future.complete(
          new ConfigurationChangeResult(
              legacyView,
              legacyView,
              currentConfiguration,
              currentConfiguration,
              currentConfiguration
                  .phasedChangeState()
                  .lastChange()
                  .map(CompletedPhasedChange::id)
                  .orElse(0L),
              List.of(),
              List.of()));
      return;
    }

    // Captured before any mutation: the id the coordinator's single-threaded executor is about to
    // hand out for this request, whether or not it is ever actually applied (dry-run) or the
    // request races with a concurrent one (checked in checkConcurrentModification below).
    final long changeId = currentConfiguration.phasedChangeState().nextId();
    final ActorFuture<CurrentClusterConfiguration> validation =
        validateNewModelChangeRequest(currentConfiguration, changeId, phases);

    validation.onComplete(
        (simulatedFinalConfiguration, validationError) -> {
          if (validationError != null) {
            failFuture(future, validationError);
            return;
          }

          final var operations = flattenPhases(phases);
          if (dryRun) {
            future.complete(
                new ConfigurationChangeResult(
                    currentConfiguration.toLegacyDefault(),
                    simulatedFinalConfiguration.toLegacyDefault(),
                    currentConfiguration,
                    simulatedFinalConfiguration,
                    changeId,
                    operations,
                    phases));
            return;
          }

          // The id actually assigned may differ from `changeId` (captured from the snapshot used
          // to validate/simulate) if another, non-conflicting request was admitted in between —
          // checkConcurrentModification only rejects overlapping scopes, not unrelated admissions
          // that bump the counter. The applied id is therefore read fresh, inside the same
          // single-threaded transaction that calls initPlan, so it is exact.
          final MutableLong appliedChangeId = new MutableLong();
          clusterTopologyManager
              .updateMultiConfiguration(
                  config -> {
                    checkConcurrentModification(config, currentConfiguration, phases);
                    appliedChangeId.set(config.phasedChangeState().nextId());
                    return config.initPlan(phases);
                  })
              .onComplete(
                  (updated, error) -> {
                    if (error != null) {
                      failFuture(future, error);
                      return;
                    }
                    future.complete(
                        new ConfigurationChangeResult(
                            currentConfiguration.toLegacyDefault(),
                            simulatedFinalConfiguration.toLegacyDefault(),
                            currentConfiguration,
                            simulatedFinalConfiguration,
                            appliedChangeId.get(),
                            operations,
                            phases));
                  },
                  executor);
        });
  }

  private void checkConcurrentModification(
      final CurrentClusterConfiguration latestConfig,
      final CurrentClusterConfiguration configUsedForGeneratingOperations,
      final List<Phase> phases) {

    final var scope = PhasedChangePlan.scopeOf(phases);
    for (final var existing : latestConfig.phasedChangeState().pending().values()) {
      if (PhasedChangePlan.conflicts(scope, existing.scope())) {
        throw new ConcurrentModificationException(
            String.format(
                "Cannot apply configuration change. Another configuration change [%d] targeting an"
                    + " overlapping group is in progress.",
                existing.id()));
      }
    }

    // simple equality check on the whole configuration is not enough, because we allow concurrent
    // changes to different partition groups.

    // for each phase check if the corresponding state in latestConfig is the same as in
    // configUsedForGeneratingOperations
    for (final var phase : phases) {
      switch (phase) {
        case final GlobalPhase globalPhase -> {
          final var latestGlobalConfig = latestConfig.globalConfiguration();
          final var usedGlobalConfig = configUsedForGeneratingOperations.globalConfiguration();
          if (!Objects.equals(latestGlobalConfig, usedGlobalConfig)) {
            throw new ConcurrentModificationException(
                "Cannot apply configuration change. The global configuration has changed since the request was generated.");
          }
        }
        case final PartitionGroupPhase groupPhase ->
            checkGroupsUnchanged(
                groupPhase.groupGraphs().keySet(), latestConfig, configUsedForGeneratingOperations);
      }
    }
  }

  private void checkGroupsUnchanged(
      final Set<String> groupIds,
      final CurrentClusterConfiguration latestConfig,
      final CurrentClusterConfiguration configUsedForGeneratingOperations) {
    for (final var groupId : groupIds) {
      final var latestGroupConfig = latestConfig.partitionGroup(groupId);
      final var usedGroupConfig = configUsedForGeneratingOperations.partitionGroup(groupId);
      if (!Objects.equals(latestGroupConfig, usedGroupConfig)) {
        throw new ConcurrentModificationException(
            String.format(
                "Cannot apply configuration change. The partition group '%s' configuration has changed since the request was generated.",
                groupId));
      }
    }
  }

  /**
   * Validates a phased plan by simulating it against the new model: not initialized / a change
   * already in progress fail immediately; otherwise the plan is started via {@link
   * CurrentClusterConfiguration#initPlan(List)} and simulated phase by phase using the same
   * dispatch tables the manager uses to actually apply changes ({@link
   * GlobalConfigurationChangeAppliersImpl}, {@link PartitionGroupConfigurationChangeAppliersImpl}),
   * backed by no-op executors. The returned configuration is the fully-drained plan (no pending
   * changes, the plan moved into {@code lastChange}), used as the expected final configuration.
   */
  private ActorFuture<CurrentClusterConfiguration> validateNewModelChangeRequest(
      final CurrentClusterConfiguration currentConfiguration,
      final long newPlanId,
      final List<Phase> phases) {
    final ActorFuture<CurrentClusterConfiguration> validationFuture = executor.createFuture();

    if (currentConfiguration.globalConfiguration().members().isEmpty()) {
      failFuture(
          validationFuture,
          new OperationNotAllowed(
              "Cannot apply configuration change. The configuration is not initialized."));
      return validationFuture;
    }

    final var scope = PhasedChangePlan.scopeOf(phases);
    for (final var existing : currentConfiguration.phasedChangeState().pending().values()) {
      if (PhasedChangePlan.conflicts(scope, existing.scope())) {
        failFuture(
            validationFuture,
            new ConcurrentModificationException(
                String.format(
                    "Cannot apply configuration change. Another configuration change [%d]"
                        + " targeting an overlapping scope is in progress.",
                    existing.id())));
        return validationFuture;
      }
    }

    final var globalSimulator =
        new GlobalConfigurationChangeAppliersImpl(
            new NoopClusterMembershipChangeExecutor(), new NoopClusterChangeExecutor());
    final var groupSimulator =
        new PartitionGroupConfigurationChangeAppliersImpl(
            new NoopPartitionChangeExecutor(),
            new NoopPartitionScalingChangeExecutor(),
            new NoopModeChangeExecutor(),
            new NoopRestoreChangeExecutor());
    try {
      final var withPlan = currentConfiguration.initPlan(phases);
      simulateNewModelChange(
          withPlan, newPlanId, globalSimulator, groupSimulator, validationFuture);
    } catch (final Exception e) {
      failFuture(validationFuture, e);
    }
    return validationFuture;
  }

  private void simulateNewModelChange(
      final CurrentClusterConfiguration config,
      final long planId,
      final GlobalConfigurationChangeAppliers globalSimulator,
      final PartitionGroupConfigurationChangeAppliers groupSimulator,
      final ActorFuture<CurrentClusterConfiguration> simulationCompleted) {
    final var plan = config.phasedChangeState().pending().get(planId);
    if (plan == null) {
      // The plan has been fully drained and moved into history — simulation is done.
      simulationCompleted.complete(config);
      return;
    }
    switch (plan.currentPhase()) {
      case final GlobalPhase ignored ->
          simulateGlobalPhase(config, planId, globalSimulator, groupSimulator, simulationCompleted);
      case final PartitionGroupPhase groupPhase ->
          simulatePartitionGroupPhase(
              config,
              planId,
              new ArrayList<>(groupPhase.groupGraphs().keySet()),
              0,
              globalSimulator,
              groupSimulator,
              simulationCompleted);
    }
  }

  private void simulateGlobalPhase(
      final CurrentClusterConfiguration config,
      final long planId,
      final GlobalConfigurationChangeAppliers globalSimulator,
      final PartitionGroupConfigurationChangeAppliers groupSimulator,
      final ActorFuture<CurrentClusterConfiguration> simulationCompleted) {
    final var plan = config.globalConfiguration().pendingChanges().orElse(null);
    if (plan == null || !plan.hasPendingChanges()) {
      advancePhaseAndContinueSimulation(
          config, planId, globalSimulator, groupSimulator, simulationCompleted);
      return;
    }

    final var next =
        plan.operations().keySet().stream().filter(plan::isRunnable).findFirst().orElse(null);
    if (next == null) {
      // Every remaining operation is blocked, yet none has completed — only reachable if the graph
      // has a cycle, which construction rejects. Fail loudly rather than spin.
      failFuture(
          simulationCompleted,
          new InvalidRequest(
              new IllegalStateException(
                  "Cluster-wide change cannot make progress; outstanding operations are blocked on %s"
                      .formatted(plan.blockedBy()))));
      return;
    }

    final var operation = (GlobalChangeOperation) plan.operation(next);
    final var applier = globalSimulator.getApplier(operation);
    final var result = applier.init(config);
    if (result.isLeft()) {
      failFuture(simulationCompleted, new InvalidRequest(result.getLeft()));
      return;
    }
    final var configWithInit = config.updateGlobalConfiguration(result.get());
    applier
        .apply()
        .onComplete(
            (transformer, error) -> {
              if (error != null) {
                failFuture(simulationCompleted, new InvalidRequest(error));
                return;
              }
              final var advanced =
                  configWithInit.updateGlobalConfiguration(
                      g -> g.completeOperation(next, transformer).completeGraphChangeIfDrained());
              simulateGlobalPhase(
                  advanced, planId, globalSimulator, groupSimulator, simulationCompleted);
            });
  }

  private void simulatePartitionGroupPhase(
      final CurrentClusterConfiguration config,
      final long planId,
      final List<String> groupIds,
      final int index,
      final GlobalConfigurationChangeAppliers globalSimulator,
      final PartitionGroupConfigurationChangeAppliers groupSimulator,
      final ActorFuture<CurrentClusterConfiguration> simulationCompleted) {
    if (index >= groupIds.size()) {
      advancePhaseAndContinueSimulation(
          config, planId, globalSimulator, groupSimulator, simulationCompleted);
      return;
    }
    simulatePartitionGroupOperations(
        config,
        groupIds.get(index),
        groupSimulator,
        drained ->
            simulatePartitionGroupPhase(
                drained,
                planId,
                groupIds,
                index + 1,
                globalSimulator,
                groupSimulator,
                simulationCompleted),
        simulationCompleted);
  }

  private void simulatePartitionGroupOperations(
      final CurrentClusterConfiguration config,
      final String groupId,
      final PartitionGroupConfigurationChangeAppliers groupSimulator,
      final Consumer<CurrentClusterConfiguration> onGroupDrained,
      final ActorFuture<CurrentClusterConfiguration> simulationCompleted) {
    final var group = config.partitionGroup(groupId);
    Objects.requireNonNull(group);
    if (!group.hasPendingChanges()) {
      onGroupDrained.accept(config);
      return;
    }
    simulateGraphOperations(config, groupId, groupSimulator, onGroupDrained, simulationCompleted);
  }

  /**
   * Walks a dependency-graph change one runnable operation at a time.
   *
   * <p>The real cluster runs several of these at once on different brokers; the simulator takes
   * them in ascending operation-id order instead. Nothing validates that this is sound — the
   * disjoint-write-set check that would have guaranteed every legal execution order reaches the
   * same configuration was built, then removed (see {@link
   * io.camunda.zeebe.dynamic.config.state.OperationGraph}'s class javadoc). This one arbitrary
   * serialization is sound only to the extent the graph's author declared every edge that ordering
   * actually requires; if an edge is missing, the simulation can silently disagree with what the
   * real, concurrent execution does.
   *
   * <p>It deliberately drives the same {@code completeOperation} the manager uses rather than
   * modelling progress a second way: a divergence between what is simulated and what is applied
   * would be silent and very hard to find.
   */
  private void simulateGraphOperations(
      final CurrentClusterConfiguration config,
      final String groupId,
      final PartitionGroupConfigurationChangeAppliers groupSimulator,
      final Consumer<CurrentClusterConfiguration> onGroupDrained,
      final ActorFuture<CurrentClusterConfiguration> simulationCompleted) {
    final var group = Objects.requireNonNull(config.partitionGroup(groupId));
    final var plan = group.pendingChanges().orElse(null);
    if (plan == null || !plan.hasPendingChanges()) {
      onGroupDrained.accept(config);
      return;
    }

    final var next =
        plan.operations().keySet().stream().filter(plan::isRunnable).findFirst().orElse(null);
    if (next == null) {
      // Every remaining operation is blocked, yet none has completed — only reachable if the graph
      // has a cycle, which construction rejects. Fail loudly rather than spin.
      failFuture(
          simulationCompleted,
          new InvalidRequest(
              new IllegalStateException(
                  "Change for group '%s' cannot make progress; outstanding operations are blocked on %s"
                      .formatted(groupId, plan.blockedBy()))));
      return;
    }

    final var operation = (PartitionGroupOperation) plan.operation(next);
    final var applier = groupSimulator.getApplier(operation);
    final var result = applier.init(config.globalConfiguration(), group);
    if (result.isLeft()) {
      failFuture(simulationCompleted, new InvalidRequest(result.getLeft()));
      return;
    }
    final var configWithInit = config.updatePartitionGroupConfig(groupId, result.get());
    applier
        .apply()
        .onComplete(
            (transformer, error) -> {
              if (error != null) {
                failFuture(simulationCompleted, new InvalidRequest(error));
                return;
              }
              final var advanced =
                  configWithInit.updatePartitionGroupConfig(
                      groupId,
                      g -> g.completeOperation(next, transformer).completeGraphChangeIfDrained());
              simulateGraphOperations(
                  advanced, groupId, groupSimulator, onGroupDrained, simulationCompleted);
            });
  }

  private void advancePhaseAndContinueSimulation(
      final CurrentClusterConfiguration config,
      final long planId,
      final GlobalConfigurationChangeAppliers globalSimulator,
      final PartitionGroupConfigurationChangeAppliers groupSimulator,
      final ActorFuture<CurrentClusterConfiguration> simulationCompleted) {
    final var plan = config.phasedChangeState().pending().get(planId);
    final var next =
        plan.hasNextPhase()
            ? config.activateNextPhase(planId)
            : config.completePlan(planId, PhasedChangePlanStatus.COMPLETED);
    simulateNewModelChange(next, planId, globalSimulator, groupSimulator, simulationCompleted);
  }

  /**
   * Flattens a phase list back into the flat operation list the management API answers a request
   * with, preserving phase order. Within a {@link PartitionGroupPhase} the operations of each group
   * are concatenated; the order between groups is unspecified (they apply concurrently), but is
   * irrelevant while only the default group is used.
   */
  private static List<ClusterConfigurationChangeOperation> flattenPhases(final List<Phase> phases) {
    final List<ClusterConfigurationChangeOperation> operations = new ArrayList<>();
    for (final var phase : phases) {
      switch (phase) {
        case final GlobalPhase globalPhase -> operations.addAll(globalPhase.operations());
        case final PartitionGroupPhase groupPhase ->
            groupPhase.groupOperations().values().forEach(operations::addAll);
      }
    }
    return operations;
  }

  private boolean isCoordinator(final CurrentClusterConfiguration currentConfiguration) {
    return localMemberId.equals(
        currentConfiguration.globalConfiguration().members().keySet().stream()
            .min(MemberId::compareTo)
            .orElse(null));
  }
}

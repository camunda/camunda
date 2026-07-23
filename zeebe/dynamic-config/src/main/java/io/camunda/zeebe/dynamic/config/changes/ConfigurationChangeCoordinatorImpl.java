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
import io.camunda.zeebe.dynamic.config.state.ClusterChangePlan;
import io.camunda.zeebe.dynamic.config.state.ClusterConfiguration;
import io.camunda.zeebe.dynamic.config.state.ClusterConfigurationChangeOperation;
import io.camunda.zeebe.dynamic.config.state.CompletedChange;
import io.camunda.zeebe.dynamic.config.state.CompletedPhasedChange;
import io.camunda.zeebe.dynamic.config.state.CurrentClusterConfiguration;
import io.camunda.zeebe.dynamic.config.state.GlobalChangeOperation;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation;
import io.camunda.zeebe.dynamic.config.state.PhasedChangePlan;
import io.camunda.zeebe.dynamic.config.state.PhasedChangePlan.GlobalPhase;
import io.camunda.zeebe.dynamic.config.state.PhasedChangePlan.PartitionGroupParallelPhase;
import io.camunda.zeebe.dynamic.config.state.PhasedChangePlan.Phase;
import io.camunda.zeebe.dynamic.config.state.PhasedChangePlanStatus;
import io.camunda.zeebe.scheduler.ConcurrencyControl;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ConfigurationChangeCoordinatorImpl implements ConfigurationChangeCoordinator {
  private static final Logger LOG =
      LoggerFactory.getLogger(ConfigurationChangeCoordinatorImpl.class);
  private final ClusterConfigurationManager clusterTopologyManager;
  private final ConcurrencyControl executor;
  private final MemberId localMemberId;

  public ConfigurationChangeCoordinatorImpl(
      final ClusterConfigurationManager clusterTopologyManager,
      final MemberId localMemberId,
      final ConcurrencyControl executor) {
    this.clusterTopologyManager = clusterTopologyManager;
    this.executor = executor;
    this.localMemberId = localMemberId;
  }

  @Override
  public ActorFuture<ClusterConfiguration> getClusterConfiguration() {
    return clusterTopologyManager.getClusterConfiguration();
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
  public ActorFuture<ClusterConfiguration> cancelChange(final long changeId) {
    final ActorFuture<ClusterConfiguration> future = executor.createFuture();
    executor.run(
        () ->
            clusterTopologyManager.updateClusterConfiguration(
                clusterTopology -> {
                  if (!validateCancel(changeId, clusterTopology, future)) {
                    return clusterTopology;
                  }
                  final var completedOperation =
                      clusterTopology
                          .pendingChanges()
                          .map(ClusterChangePlan::completedOperations)
                          .orElse(List.of());
                  final var cancelledOperations =
                      clusterTopology
                          .pendingChanges()
                          .map(ClusterChangePlan::pendingOperations)
                          .orElse(List.of());
                  LOG.warn(
                      "Cancelling configuration change '{}'. Following operations have been already applied: {}. Following pending operations won't be applied: {}",
                      changeId,
                      completedOperation,
                      cancelledOperations);
                  final var cancelledTopology = clusterTopology.cancelPendingChanges();
                  future.complete(cancelledTopology);
                  return cancelledTopology;
                }));
    return future;
  }

  private ActorFuture<ConfigurationChangeResult> applyOrDryRun(
      final boolean dryRun, final ConfigurationChangeRequest request) {
    if (clusterTopologyManager.isUsingNewConfig()) {
      return applyOrDryRunNewModel(dryRun, request);
    }
    final ActorFuture<ConfigurationChangeResult> future = executor.createFuture();
    executor.run(
        () ->
            clusterTopologyManager
                .getClusterConfiguration()
                .onComplete(
                    (currentClusterTopology, errorOnGettingTopology) -> {
                      if (errorOnGettingTopology != null) {
                        failFuture(future, errorOnGettingTopology);
                        return;
                      }
                      if (!request.isForced() && !isCoordinator(currentClusterTopology)) {
                        // if request is forced, it can be processed by a broker which is not
                        // the default coordinator
                        failFuture(
                            future,
                            new ClusterConfigurationRequestFailedException.InternalError(
                                String.format(
                                    "Cannot process request to change the configuration. The broker '%s' is not the coordinator.",
                                    localMemberId)));
                        return;
                      }
                      final var generatedOperations = request.operations(currentClusterTopology);
                      if (generatedOperations.isLeft()) {
                        failFuture(future, generatedOperations.getLeft());
                        return;
                      }

                      applyOrDryRunOnTopology(
                          dryRun, currentClusterTopology, generatedOperations.get(), future);
                    },
                    executor));
    return future;
  }

  private void applyOrDryRunOnTopology(
      final boolean dryRun,
      final ClusterConfiguration currentClusterConfiguration,
      final List<ClusterConfigurationChangeOperation> operations,
      final ActorFuture<ConfigurationChangeResult> future) {
    if (operations.isEmpty()) {
      // No operations to apply
      future.complete(
          new ConfigurationChangeResult(
              currentClusterConfiguration,
              currentClusterConfiguration,
              currentClusterConfiguration.lastChange().map(CompletedChange::id).orElse(0L),
              operations));
      return;
    }

    final ActorFuture<ClusterConfiguration> validation =
        validateTopologyChangeRequest(currentClusterConfiguration, operations);

    validation.onComplete(
        (simulatedFinalTopology, validationError) -> {
          if (validationError != null) {
            failFuture(future, validationError);
            return;
          }

          // Validation was successful. If it's not a dry-run, apply the changes.
          final ActorFuture<ClusterConfiguration> applyFuture = executor.createFuture();
          if (dryRun) {
            LOG.debug(
                "Dry run for configuration change request validated "
                    + "successfully. The resulting cluster configuration would be: {}",
                simulatedFinalTopology);
            applyFuture.complete(currentClusterConfiguration.startConfigurationChange(operations));
          } else {
            applyTopologyChange(
                operations, currentClusterConfiguration, simulatedFinalTopology, applyFuture);
          }

          applyFuture.onComplete(
              (clusterTopologyWithPendingChanges, error) -> {
                if (error == null) {
                  final long changeId =
                      clusterTopologyWithPendingChanges
                          .pendingChanges()
                          .map(ClusterChangePlan::id)
                          .orElse(0L); // No changes, this should not happen because
                  // operations are not empty

                  future.complete(
                      new ConfigurationChangeResult(
                          currentClusterConfiguration,
                          simulatedFinalTopology,
                          changeId,
                          operations));
                } else {
                  failFuture(future, error);
                }
              });
        });
  }

  private ActorFuture<ClusterConfiguration> validateTopologyChangeRequest(
      final ClusterConfiguration currentClusterConfiguration,
      final List<ClusterConfigurationChangeOperation> operations) {

    final ActorFuture<ClusterConfiguration> validationFuture = executor.createFuture();

    if (currentClusterConfiguration.isUninitialized()) {
      failFuture(
          validationFuture,
          new OperationNotAllowed(
              "Cannot apply configuration change. The configuration is not initialized."));
    } else if (currentClusterConfiguration.hasPendingChanges()) {
      failFuture(
          validationFuture,
          new ConcurrentModificationException(
              String.format(
                  "Cannot apply configuration change. Another configuration change [%s] is in progress.",
                  currentClusterConfiguration)));
    } else {
      // simulate applying changes to validate the operations
      final var topologyChangeSimulator =
          new ConfigurationChangeAppliersImpl(
              new NoopPartitionChangeExecutor(),
              new NoopClusterMembershipChangeExecutor(),
              new NoopPartitionScalingChangeExecutor(),
              new NoopClusterChangeExecutor(),
              new NoopModeChangeExecutor());
      final var topologyWithPendingOperations =
          currentClusterConfiguration.startConfigurationChange(operations);

      // Simulate applying the operations. The resulting configuration will be the expected final
      // topology. If the sequence of operations is not valid, the simulation fails.
      simulateTopologyChange(
          topologyWithPendingOperations, topologyChangeSimulator, validationFuture);
    }
    return validationFuture;
  }

  private void applyTopologyChange(
      final List<ClusterConfigurationChangeOperation> operations,
      final ClusterConfiguration currentClusterConfiguration,
      final ClusterConfiguration simulatedFinalTopology,
      final ActorFuture<ClusterConfiguration> future) {
    executor.run(
        () ->
            clusterTopologyManager
                .updateClusterConfiguration(
                    clusterTopology -> {
                      if (!clusterTopology.equals(currentClusterConfiguration)) {
                        throw new ConcurrentModificationException(
                            "Topology changed while applying the change. Please retry.");
                      }
                      return clusterTopology.startConfigurationChange(operations);
                    })
                .onComplete(
                    (topologyWithPendingOperations, errorOnUpdatingTopology) -> {
                      if (errorOnUpdatingTopology != null) {
                        failFuture(future, errorOnUpdatingTopology);
                        return;
                      }
                      LOG.debug(
                          "Applying the topology change has started. The resulting topology will be {}",
                          simulatedFinalTopology);
                      future.complete(topologyWithPendingOperations);
                    }));
  }

  private void simulateTopologyChange(
      final ClusterConfiguration updatedTopology,
      final ConfigurationChangeAppliersImpl topologyChangeSimulator,
      final ActorFuture<ClusterConfiguration> simulationCompleted) {
    if (!updatedTopology.hasPendingChanges()) {
      simulationCompleted.complete(updatedTopology);
      return;
    }

    final var operation = updatedTopology.nextPendingOperation();
    final var applier = topologyChangeSimulator.getApplier(operation);
    final var result = applier.init(updatedTopology);
    if (result.isLeft()) {
      failFuture(simulationCompleted, new InvalidRequest(result.getLeft()));
      return;
    }

    final var initializedChanges = result.get().apply(updatedTopology);

    applier
        .apply()
        .onComplete(
            (topologyUpdater, error) -> {
              if (error != null) {
                failFuture(simulationCompleted, new InvalidRequest(error));
                return;
              }
              final var newTopology =
                  initializedChanges.advanceConfigurationChange(topologyUpdater);

              simulateTopologyChange(newTopology, topologyChangeSimulator, simulationCompleted);
            });
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

  private boolean validateCancel(
      final long changeId,
      final ClusterConfiguration currentClusterConfiguration,
      final ActorFuture<ClusterConfiguration> future) {
    if (currentClusterConfiguration.isUninitialized()) {
      failFuture(
          future,
          new InvalidRequest(
              "Cannot cancel change " + changeId + " because the topology is not initialized"));
      return false;
    }
    if (!currentClusterConfiguration.hasPendingChanges()) {
      failFuture(
          future,
          new InvalidRequest(
              "Cannot cancel change " + changeId + " because no change is in progress"));
      return false;
    }

    final var clusterChangePlan = currentClusterConfiguration.pendingChanges().orElseThrow();
    if (clusterChangePlan.id() != changeId) {
      failFuture(
          future,
          new InvalidRequest(
              "Cannot cancel change " + changeId + " because it is not the current change"));
      return false;
    }
    return true;
  }

  private boolean isCoordinator(final ClusterConfiguration clusterConfiguration) {
    // coordinator is usually the broker with the lowest member id
    // return false if there are currently no known members, which means it will be uninitialized.
    return localMemberId.equals(
        clusterConfiguration.members().keySet().stream().min(MemberId::compareTo).orElse(null));
  }

  // ---------------------------------------------------------------------------
  // New multi-partition-group model.
  // ---------------------------------------------------------------------------

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
                      final var generatedPhases = request.phases(currentConfiguration);
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
              currentConfiguration
                  .phasedChangeState()
                  .lastChange()
                  .map(CompletedPhasedChange::id)
                  .orElse(0L),
              List.of()));
      return;
    }

    final ActorFuture<CurrentClusterConfiguration> validation =
        validateNewModelChangeRequest(currentConfiguration, phases);

    validation.onComplete(
        (simulatedFinalConfiguration, validationError) -> {
          if (validationError != null) {
            failFuture(future, validationError);
            return;
          }

          final var operations = flattenPhases(phases);
          if (dryRun) {
            final long changeId =
                currentConfiguration
                    .phasedChangeState()
                    .lastChange()
                    .map(c -> c.id() + 1)
                    .orElse(1L);
            future.complete(
                new ConfigurationChangeResult(
                    currentConfiguration.toLegacyDefault(),
                    simulatedFinalConfiguration.toLegacyDefault(),
                    changeId,
                    operations));
            return;
          }

          clusterTopologyManager
              .updateMultiConfiguration(
                  config -> {
                    checkConcurrentModification(config, currentConfiguration, phases);
                    return config.initPlan(phases);
                  })
              .onComplete(
                  (updated, error) -> {
                    if (error != null) {
                      failFuture(future, error);
                      return;
                    }
                    final long changeId =
                        updated.phasedChangeState().pending().map(PhasedChangePlan::id).orElse(0L);
                    future.complete(
                        new ConfigurationChangeResult(
                            currentConfiguration.toLegacyDefault(),
                            simulatedFinalConfiguration.toLegacyDefault(),
                            changeId,
                            operations));
                  },
                  executor);
        });
  }

  private void checkConcurrentModification(
      final CurrentClusterConfiguration latestConfig,
      final CurrentClusterConfiguration configUsedForGeneratingOperations,
      final List<Phase> phases) {

    if (latestConfig.phasedChangeState().pending().isPresent()) {
      throw new ConcurrentModificationException(
          "Cannot apply configuration change. Another configuration change is in progress.");
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
        case final PartitionGroupParallelPhase parallelPhase -> {
          for (final var groupId : parallelPhase.groupOperations().keySet()) {
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
      final CurrentClusterConfiguration currentConfiguration, final List<Phase> phases) {
    final ActorFuture<CurrentClusterConfiguration> validationFuture = executor.createFuture();

    if (currentConfiguration.globalConfiguration().members().isEmpty()) {
      failFuture(
          validationFuture,
          new OperationNotAllowed(
              "Cannot apply configuration change. The configuration is not initialized."));
    } else if (currentConfiguration.phasedChangeState().pending().isPresent()) {
      failFuture(
          validationFuture,
          new ConcurrentModificationException(
              String.format(
                  "Cannot apply configuration change. Another configuration change [%d] is in progress.",
                  currentConfiguration
                      .phasedChangeState()
                      .pending()
                      .map(PhasedChangePlan::id)
                      .orElseThrow())));
    } else {
      final var globalSimulator =
          new GlobalConfigurationChangeAppliersImpl(
              new NoopClusterMembershipChangeExecutor(), new NoopClusterChangeExecutor());
      final var groupSimulator =
          new PartitionGroupConfigurationChangeAppliersImpl(
              new NoopPartitionChangeExecutor(),
              new NoopPartitionScalingChangeExecutor(),
              new NoopClusterChangeExecutor(),
              new NoopModeChangeExecutor());
      try {
        final var withPlan = currentConfiguration.initPlan(phases);
        simulateNewModelChange(withPlan, globalSimulator, groupSimulator, validationFuture);
      } catch (final Exception e) {
        failFuture(validationFuture, e);
      }
    }
    return validationFuture;
  }

  private void simulateNewModelChange(
      final CurrentClusterConfiguration config,
      final GlobalConfigurationChangeAppliers globalSimulator,
      final PartitionGroupConfigurationChangeAppliers groupSimulator,
      final ActorFuture<CurrentClusterConfiguration> simulationCompleted) {
    final var pending = config.phasedChangeState().pending();
    if (pending.isEmpty()) {
      simulationCompleted.complete(config);
      return;
    }
    switch (pending.get().currentPhase()) {
      case final GlobalPhase ignored ->
          simulateGlobalPhase(config, globalSimulator, groupSimulator, simulationCompleted);
      case final PartitionGroupParallelPhase parallelPhase ->
          simulatePartitionGroupPhase(
              config,
              new ArrayList<>(parallelPhase.groupOperations().keySet()),
              0,
              globalSimulator,
              groupSimulator,
              simulationCompleted);
    }
  }

  private void simulateGlobalPhase(
      final CurrentClusterConfiguration config,
      final GlobalConfigurationChangeAppliers globalSimulator,
      final PartitionGroupConfigurationChangeAppliers groupSimulator,
      final ActorFuture<CurrentClusterConfiguration> simulationCompleted) {
    if (!config.globalConfiguration().hasPendingChanges()) {
      advancePhaseAndContinueSimulation(
          config, globalSimulator, groupSimulator, simulationCompleted);
      return;
    }
    final var operation =
        (GlobalChangeOperation) config.globalConfiguration().nextPendingOperation();
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
                      g -> g.advanceConfigurationChange(transformer));
              simulateGlobalPhase(advanced, globalSimulator, groupSimulator, simulationCompleted);
            });
  }

  private void simulatePartitionGroupPhase(
      final CurrentClusterConfiguration config,
      final List<String> groupIds,
      final int index,
      final GlobalConfigurationChangeAppliers globalSimulator,
      final PartitionGroupConfigurationChangeAppliers groupSimulator,
      final ActorFuture<CurrentClusterConfiguration> simulationCompleted) {
    if (index >= groupIds.size()) {
      advancePhaseAndContinueSimulation(
          config, globalSimulator, groupSimulator, simulationCompleted);
      return;
    }
    simulatePartitionGroupOperations(
        config,
        groupIds.get(index),
        groupSimulator,
        drained ->
            simulatePartitionGroupPhase(
                drained, groupIds, index + 1, globalSimulator, groupSimulator, simulationCompleted),
        simulationCompleted);
  }

  private void simulatePartitionGroupOperations(
      final CurrentClusterConfiguration config,
      final String groupId,
      final PartitionGroupConfigurationChangeAppliers groupSimulator,
      final Consumer<CurrentClusterConfiguration> onGroupDrained,
      final ActorFuture<CurrentClusterConfiguration> simulationCompleted) {
    final var group = config.partitionGroup(groupId);
    if (group == null || !group.hasPendingChanges()) {
      onGroupDrained.accept(config);
      return;
    }
    final var operation = (PartitionGroupOperation) group.nextPendingOperation();
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
                      groupId, g -> g.advanceConfigurationChange(transformer));
              simulatePartitionGroupOperations(
                  advanced, groupId, groupSimulator, onGroupDrained, simulationCompleted);
            });
  }

  private void advancePhaseAndContinueSimulation(
      final CurrentClusterConfiguration config,
      final GlobalConfigurationChangeAppliers globalSimulator,
      final PartitionGroupConfigurationChangeAppliers groupSimulator,
      final ActorFuture<CurrentClusterConfiguration> simulationCompleted) {
    final var plan = config.phasedChangeState().pending().orElseThrow();
    final var next =
        plan.hasNextPhase()
            ? config.activateNextPhase()
            : config.completePlan(PhasedChangePlanStatus.COMPLETED);
    simulateNewModelChange(next, globalSimulator, groupSimulator, simulationCompleted);
  }

  /**
   * Flattens a phase list back into the flat operation list expected by {@link
   * ConfigurationChangeResult#operations()}, preserving phase order. Within a {@link
   * PartitionGroupParallelPhase} the operations of each group are concatenated; the order between
   * groups is unspecified (they apply concurrently), but is irrelevant while only the default group
   * is used.
   */
  private static List<ClusterConfigurationChangeOperation> flattenPhases(final List<Phase> phases) {
    final List<ClusterConfigurationChangeOperation> operations = new ArrayList<>();
    for (final var phase : phases) {
      switch (phase) {
        case final GlobalPhase globalPhase -> operations.addAll(globalPhase.operations());
        case final PartitionGroupParallelPhase parallelPhase ->
            parallelPhase.groupOperations().values().forEach(operations::addAll);
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

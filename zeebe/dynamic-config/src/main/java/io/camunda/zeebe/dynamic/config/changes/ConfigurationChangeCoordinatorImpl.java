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
import io.camunda.zeebe.dynamic.config.changes.PartitionScalingChangeExecutor.NoopPartitionScalingChangeExecutor;
import io.camunda.zeebe.dynamic.config.state.ClusterChangePlan;
import io.camunda.zeebe.dynamic.config.state.ClusterConfiguration;
import io.camunda.zeebe.dynamic.config.state.ClusterConfigurationChangeOperation;
import io.camunda.zeebe.dynamic.config.state.CompletedChange;
import io.camunda.zeebe.scheduler.ConcurrencyControl;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import java.util.List;
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
              new NoopPartitionScalingChangeExecutor());
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
}

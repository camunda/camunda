/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.topology.changes;

import io.camunda.zeebe.scheduler.ConcurrencyControl;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.topology.ClusterTopologyManager;
import io.camunda.zeebe.topology.api.TopologyRequestFailedException;
import io.camunda.zeebe.topology.api.TopologyRequestFailedException.ConcurrentModificationException;
import io.camunda.zeebe.topology.api.TopologyRequestFailedException.InvalidRequest;
import io.camunda.zeebe.topology.api.TopologyRequestFailedException.OperationNotAllowed;
import io.camunda.zeebe.topology.changes.TopologyChangeAppliers.OperationApplier;
import io.camunda.zeebe.topology.state.ClusterChangePlan;
import io.camunda.zeebe.topology.state.ClusterTopology;
import io.camunda.zeebe.topology.state.CompletedChange;
import io.camunda.zeebe.topology.state.TopologyChangeOperation;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TopologyChangeCoordinatorImpl implements TopologyChangeCoordinator {
  private static final Logger LOG = LoggerFactory.getLogger(TopologyChangeCoordinatorImpl.class);
  private final ClusterTopologyManager clusterTopologyManager;
  private final ConcurrencyControl executor;

  public TopologyChangeCoordinatorImpl(
      final ClusterTopologyManager clusterTopologyManager, final ConcurrencyControl executor) {
    this.clusterTopologyManager = clusterTopologyManager;
    this.executor = executor;
  }

  @Override
  public ActorFuture<ClusterTopology> getTopology() {
    return clusterTopologyManager.getClusterTopology();
  }

  @Override
  public ActorFuture<TopologyChangeResult> applyOperations(final TopologyChangeRequest request) {
    final ActorFuture<TopologyChangeResult> future = executor.createFuture();
    clusterTopologyManager
        .getClusterTopology()
        .onComplete(
            (currentClusterTopology, errorOnGettingTopology) -> {
              if (errorOnGettingTopology != null) {
                failFuture(future, errorOnGettingTopology);
                return;
              }

              final var operationsEither = request.operations(currentClusterTopology);
              if (operationsEither.isLeft()) {
                failFuture(future, operationsEither.getLeft());
                return;
              }
              final var operations = operationsEither.get();
              if (operations.isEmpty()) {
                // No operations to apply
                future.complete(
                    new TopologyChangeResult(
                        currentClusterTopology,
                        currentClusterTopology,
                        currentClusterTopology.lastChange().map(CompletedChange::id).orElse(0L),
                        operations));
                return;
              }

              final ActorFuture<ClusterTopology> validation =
                  validateTopologyChangeRequest(currentClusterTopology, operations);

              validation.onComplete(
                  (simulatedFinalTopology, validationError) -> {
                    if (validationError != null) {
                      failFuture(future, validationError);
                      return;
                    }

                    // if the validation was successful, apply the changes
                    final ActorFuture<ClusterTopology> applyFuture = executor.createFuture();
                    applyTopologyChange(
                        operations, currentClusterTopology, simulatedFinalTopology, applyFuture);

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
                                new TopologyChangeResult(
                                    currentClusterTopology,
                                    simulatedFinalTopology,
                                    changeId,
                                    operations));
                          } else {
                            failFuture(future, error);
                          }
                        });
                  });
            });
    return future;
  }

  private ActorFuture<ClusterTopology> validateTopologyChangeRequest(
      final ClusterTopology currentClusterTopology,
      final List<TopologyChangeOperation> operations) {

    final ActorFuture<ClusterTopology> validationFuture = executor.createFuture();

    if (currentClusterTopology.isUninitialized()) {
      failFuture(
          validationFuture,
          new OperationNotAllowed(
              "Cannot apply topology change. The topology is not initialized."));
    } else if (currentClusterTopology.hasPendingChanges()) {
      failFuture(
          validationFuture,
          new OperationNotAllowed(
              String.format(
                  "Cannot apply topology change. Another topology change [%s] is in progress.",
                  currentClusterTopology)));
    } else {
      // simulate applying changes to validate the operations
      final var topologyChangeSimulator =
          new TopologyChangeAppliersImpl(
              new NoopPartitionChangeExecutor(), new NoopTopologyMembershipChangeExecutor());
      final var topologyWithPendingOperations =
          currentClusterTopology.startTopologyChange(operations);

      // Simulate applying the operations. The resulting topology will be the expected final
      // topology. If the sequence of operations is not valid, the simulation fails.
      simulateTopologyChange(
          topologyWithPendingOperations, topologyChangeSimulator, validationFuture);
    }
    return validationFuture;
  }

  private void applyTopologyChange(
      final List<TopologyChangeOperation> operations,
      final ClusterTopology currentClusterTopology,
      final ClusterTopology simulatedFinalTopology,
      final ActorFuture<ClusterTopology> future) {
    clusterTopologyManager
        .updateClusterTopology(
            clusterTopology -> {
              if (!clusterTopology.equals(currentClusterTopology)) {
                throw new ConcurrentModificationException(
                    "Topology changed while applying the change. Please retry.");
              }
              return clusterTopology.startTopologyChange(operations);
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
            });
  }

  private void simulateTopologyChange(
      final ClusterTopology updatedTopology,
      final TopologyChangeAppliersImpl topologyChangeSimulator,
      final ActorFuture<ClusterTopology> simulationCompleted) {
    if (!updatedTopology.hasPendingChanges()) {
      simulationCompleted.complete(updatedTopology);
      return;
    }

    final var operation = updatedTopology.nextPendingOperation();
    final OperationApplier applier = topologyChangeSimulator.getApplier(operation);
    final var result = applier.init(updatedTopology);
    if (result.isLeft()) {
      failFuture(simulationCompleted, new InvalidRequest(result.getLeft()));
      return;
    }

    final var initializedChanges = updatedTopology.updateMember(operation.memberId(), result.get());

    applier
        .apply()
        .onComplete(
            (stateUpdater, error) -> {
              if (error != null) {
                failFuture(simulationCompleted, new InvalidRequest(error));
                return;
              }
              final var newTopology =
                  initializedChanges.advanceTopologyChange(operation.memberId(), stateUpdater);

              simulateTopologyChange(newTopology, topologyChangeSimulator, simulationCompleted);
            });
  }

  private void failFuture(final ActorFuture<?> future, final Throwable error) {
    if (error instanceof TopologyRequestFailedException) {
      future.completeExceptionally(error);
    } else {
      future.completeExceptionally(
          new TopologyRequestFailedException.InternalError(error.getMessage()));
    }
  }
}

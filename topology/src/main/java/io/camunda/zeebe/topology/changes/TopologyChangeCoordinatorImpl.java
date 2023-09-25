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
import io.camunda.zeebe.topology.changes.TopologyChangeAppliers.OperationApplier;
import io.camunda.zeebe.topology.state.ClusterTopology;
import io.camunda.zeebe.topology.state.TopologyChangeOperation;
import java.util.ConcurrentModificationException;
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
  public ActorFuture<ClusterTopology> applyOperations(
      final List<TopologyChangeOperation> operations) {
    final ActorFuture<ClusterTopology> future = executor.createFuture();

    clusterTopologyManager
        .getClusterTopology()
        .onComplete(
            (currentClusterTopology, errorOnGettingTopology) -> {
              if (errorOnGettingTopology != null) {
                future.completeExceptionally(errorOnGettingTopology);
                return;
              }

              final ActorFuture<ClusterTopology> validation =
                  validateTopologyChangeRequest(currentClusterTopology, operations);

              validation.onComplete(
                  (simulatedFinalTopology, validationError) -> {
                    if (validationError != null) {
                      future.completeExceptionally(validationError);
                      return;
                    }

                    // if the validation was successful, apply the changes
                    applyTopologyChange(
                        operations, currentClusterTopology, simulatedFinalTopology, future);
                  });
            });
    return future;
  }

  @Override
  public ActorFuture<Boolean> hasCompletedChanges(final long version) {
    final ActorFuture<Boolean> future = executor.createFuture();
    clusterTopologyManager
        .getClusterTopology()
        .onComplete(
            (currentClusterTopology, error) -> {
              if (error != null) {
                future.completeExceptionally(error);
                return;
              }

              if (currentClusterTopology.version() == version) {
                future.complete(!currentClusterTopology.hasPendingChanges());
              } else if (currentClusterTopology.version() == version + 1) {
                // We always increment the version, when the changes are completed.
                future.complete(true);
              } else if (currentClusterTopology.version() > version + 1) {
                future.completeExceptionally(
                    new UnknownStatus(
                        String.format(
                            "The topology has changed since the version %d. The current version is %d. The topology change would have been already completed.",
                            version, currentClusterTopology.version())));
              } else if (currentClusterTopology.version() < version) {
                future.completeExceptionally(
                    new IllegalArgumentException(
                        String.format(
                            "Expected version >= %d, but the current version is %d.",
                            version, currentClusterTopology.version())));
              }
            });

    return future;
  }

  private ActorFuture<ClusterTopology> validateTopologyChangeRequest(
      final ClusterTopology currentClusterTopology,
      final List<TopologyChangeOperation> operations) {

    final ActorFuture<ClusterTopology> validationFuture = executor.createFuture();

    if (currentClusterTopology.isUninitialized()) {
      validationFuture.completeExceptionally(
          new OperationNotAllowed(
              "Cannot apply topology change. The topology is not initialized."));
    } else if (currentClusterTopology.hasPendingChanges()) {
      validationFuture.completeExceptionally(
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
                future.completeExceptionally(errorOnUpdatingTopology);
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
    if (updatedTopology.changes().pendingOperations().isEmpty()) {
      simulationCompleted.complete(updatedTopology);
      return;
    }

    final var operation = updatedTopology.changes().pendingOperations().get(0);
    final OperationApplier applier = topologyChangeSimulator.getApplier(operation);
    final var result = applier.init(updatedTopology);
    if (result.isLeft()) {
      simulationCompleted.completeExceptionally(
          new InvalidTopologyChangeException(result.getLeft()));
      return;
    }

    final var initializedChanges = updatedTopology.updateMember(operation.memberId(), result.get());

    applier
        .apply()
        .onComplete(
            (stateUpdater, error) -> {
              if (error != null) {
                simulationCompleted.completeExceptionally(
                    new InvalidTopologyChangeException(error));
                return;
              }
              final var newTopology =
                  initializedChanges.advanceTopologyChange(operation.memberId(), stateUpdater);

              simulateTopologyChange(newTopology, topologyChangeSimulator, simulationCompleted);
            });
  }

  static class InvalidTopologyChangeException extends RuntimeException {
    public InvalidTopologyChangeException(final Throwable cause) {
      super(cause);
    }
  }

  static class OperationNotAllowed extends RuntimeException {
    public OperationNotAllowed(final String message) {
      super(message);
    }
  }

  static class UnknownStatus extends RuntimeException {
    public UnknownStatus(final String message) {
      super(message);
    }
  }
}

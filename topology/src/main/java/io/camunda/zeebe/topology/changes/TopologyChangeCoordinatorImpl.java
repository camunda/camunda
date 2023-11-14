/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.topology.changes;

import io.atomix.cluster.MemberId;
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
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TopologyChangeCoordinatorImpl implements TopologyChangeCoordinator {
  private static final Logger LOG = LoggerFactory.getLogger(TopologyChangeCoordinatorImpl.class);
  private final ClusterTopologyManager clusterTopologyManager;
  private final ConcurrencyControl executor;
  private final Function<MemberId, ActorFuture<ClusterTopology>> syncRequester;

  public TopologyChangeCoordinatorImpl(
      final ClusterTopologyManager clusterTopologyManager,
      final Function<MemberId, ActorFuture<ClusterTopology>> syncRequester,
      final ConcurrencyControl executor) {
    this.clusterTopologyManager = clusterTopologyManager;
    this.syncRequester = syncRequester;
    this.executor = executor;
  }

  @Override
  public ActorFuture<ClusterTopology> getTopology() {
    return clusterTopologyManager.getClusterTopology();
  }

  @Override
  public ActorFuture<TopologyChangeResult> applyOperations(final TopologyChangeRequest request) {
    return applyOrDryRun(false, request);
  }

  @Override
  public ActorFuture<TopologyChangeResult> simulateOperations(final TopologyChangeRequest request) {
    return applyOrDryRun(true, request);
  }

  private ActorFuture<TopologyChangeResult> applyOrDryRun(
      final boolean dryRun, final TopologyChangeRequest request) {
    final ActorFuture<TopologyChangeResult> future = executor.createFuture();
    executor.run(
        () ->
            clusterTopologyManager
                .getClusterTopology()
                .onComplete(
                    (currentClusterTopology, errorOnGettingTopology) -> {
                      if (errorOnGettingTopology != null) {
                        failFuture(future, errorOnGettingTopology);
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
      final ClusterTopology currentClusterTopology,
      final List<TopologyChangeOperation> operations,
      final ActorFuture<TopologyChangeResult> future) {
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

          // Validation was successful. If it's not a dry-run, apply the changes.
          final ActorFuture<ClusterTopology> applyFuture = executor.createFuture();
          if (dryRun) {
            applyFuture.complete(currentClusterTopology.startTopologyChange(operations));
          } else {
            applyTopologyChange(
                operations, currentClusterTopology, simulatedFinalTopology, applyFuture);
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
                      new TopologyChangeResult(
                          currentClusterTopology, simulatedFinalTopology, changeId, operations));
                } else {
                  failFuture(future, error);
                }
              });
        });
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
    executor.run(
        () ->
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
                    }));
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

  public ActorFuture<ClusterTopology> cancelChange(final long changeId) {
    final ActorFuture<ClusterTopology> future = executor.createFuture();
    executor.run(
        () ->
            clusterTopologyManager
                .getClusterTopology()
                .onComplete(
                    (currentClusterTopology, errorOnGettingTopology) -> {
                      if (errorOnGettingTopology != null) {
                        failFuture(future, errorOnGettingTopology);
                        return;
                      }
                      if (!canCancelOperation(changeId, currentClusterTopology, future)) {
                        return;
                      }

                      checkAndCancel(changeId, currentClusterTopology, future);
                    }));
    return future;
  }

  /**
   * Cancel the current operation. This is an inherently unsafe operation because there is no
   * coordination among the nodes. To make it slightly safer, we first check if the node that is
   * currently applying the operation is the same as that is in the local ClusterTopology. If it is
   * not, then we have to get the latest topology from the other nodes before cancelling the change.
   * This is still not safe if the changes are being applied while cancelling. We expect that cancel
   * is invoked only when the operation is actually stuck and cannot make progress by itself.
   *
   * @param changeId id of the change to be cancelled
   * @param currentClusterTopology current clusterTopology known to this node
   * @param future will be completed when the change is cancelled or on failure
   */
  private void checkAndCancel(
      final long changeId,
      final ClusterTopology currentClusterTopology,
      final ActorFuture<ClusterTopology> future) {
    final var memberApplyingCurrentChange =
        currentClusterTopology.nextPendingOperation().memberId();
    syncRequester
        .apply(memberApplyingCurrentChange)
        .onComplete(
            (remoteMembersTopology, errorOnQueryingTopology) -> {
              if (errorOnQueryingTopology != null) {
                // failed to retrieve topology from the remote member, but we will
                // cancel anyway
                cancelAndUpdateLocalTopology(changeId, currentClusterTopology, future);
                return;
              }

              // merge received topology and current topology
              final var mergedTopology = currentClusterTopology.merge(remoteMembersTopology);
              if (!canCancelOperation(changeId, mergedTopology, future)) {
                return;
              }

              if (memberApplyingCurrentChange.equals(
                  mergedTopology.nextPendingOperation().memberId())) {
                cancelAndUpdateLocalTopology(changeId, mergedTopology, future);
              } else {
                // the last change has been applied, so it is not safe to cancel now.
                checkAndCancel(changeId, mergedTopology, future);
              }
            });
  }

  private void cancelAndUpdateLocalTopology(
      final long changeId,
      final ClusterTopology latestKnownTopology,
      final ActorFuture<ClusterTopology> future) {
    clusterTopologyManager.updateClusterTopology(
        clusterTopology -> {
          // If the local topology was not up-to-date, it would have received latest topology from
          // other members via syncRequester. So merge it before cancelling otherwise, latest update
          // will be missed.
          final var mergedTopology = latestKnownTopology.merge(clusterTopology);
          if (mergedTopology.hasPendingChanges()
              && mergedTopology.pendingChanges().orElseThrow().id() == changeId) {
            final var cancelledTopology = mergedTopology.cancelPendingChanges();
            future.complete(cancelledTopology);
            return cancelledTopology;
          } else {
            future.completeExceptionally(
                new ConcurrentModificationException(
                    "Cannot cancel change "
                        + changeId
                        + " because topology changed while cancelling"));
            return mergedTopology;
          }
        });
  }

  private boolean canCancelOperation(
      final long changeId,
      final ClusterTopology currentClusterTopology,
      final ActorFuture<ClusterTopology> future) {
    if (currentClusterTopology.isUninitialized()) {
      failFuture(
          future,
          new InvalidRequest(
              "Cannot cancel change " + changeId + " because the topology is not initialized"));
      return false;
    }
    if (!currentClusterTopology.hasPendingChanges()) {
      failFuture(
          future,
          new InvalidRequest(
              "Cannot cancel change " + changeId + " because no change is in progress"));
      return false;
    }

    final var clusterChangePlan = currentClusterTopology.pendingChanges().orElseThrow();
    if (clusterChangePlan.id() != changeId) {
      failFuture(
          future,
          new InvalidRequest(
              "Cannot cancel change " + changeId + " because it is not the current change"));
      return false;
    }
    return true;
  }
}

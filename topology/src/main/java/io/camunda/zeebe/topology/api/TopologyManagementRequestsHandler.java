/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.topology.api;

import io.camunda.zeebe.scheduler.ConcurrencyControl;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.topology.api.TopologyManagementRequest.AddMembersRequest;
import io.camunda.zeebe.topology.api.TopologyManagementRequest.JoinPartitionRequest;
import io.camunda.zeebe.topology.api.TopologyManagementRequest.LeavePartitionRequest;
import io.camunda.zeebe.topology.api.TopologyManagementRequest.ReassignPartitionsRequest;
import io.camunda.zeebe.topology.api.TopologyManagementRequest.RemoveMembersRequest;
import io.camunda.zeebe.topology.api.TopologyManagementResponse.StatusCode;
import io.camunda.zeebe.topology.api.TopologyManagementResponse.TopologyChangeStatus;
import io.camunda.zeebe.topology.changes.TopologyChangeCoordinator;
import io.camunda.zeebe.topology.changes.TopologyChangeCoordinator.TopologyChangeRequest;
import io.camunda.zeebe.topology.state.TopologyChangeOperation.PartitionChangeOperation.PartitionJoinOperation;
import io.camunda.zeebe.topology.state.TopologyChangeOperation.PartitionChangeOperation.PartitionLeaveOperation;
import io.camunda.zeebe.util.Either;
import java.util.List;

/**
 * Handles the requests for the topology management. This is expected be running on the coordinator
 * node.
 */
final class TopologyManagementRequestsHandler implements TopologyManagementApi {

  private final TopologyChangeCoordinator coordinator;
  private final ConcurrencyControl executor;

  public TopologyManagementRequestsHandler(
      final TopologyChangeCoordinator coordinator, final ConcurrencyControl executor) {
    this.coordinator = coordinator;
    this.executor = executor;
  }

  @Override
  public ActorFuture<TopologyChangeStatus> addMembers(final AddMembersRequest addMembersRequest) {
    return handleRequest(new AddMembersTransformer(addMembersRequest.members()));
  }

  @Override
  public ActorFuture<TopologyChangeStatus> removeMembers(
      final RemoveMembersRequest removeMembersRequest) {
    return handleRequest(new RemoveMembersTransformer(removeMembersRequest.members()));
  }

  @Override
  public ActorFuture<TopologyChangeStatus> joinPartition(
      final JoinPartitionRequest joinPartitionRequest) {
    final TopologyChangeRequest requestTransformer =
        ignore ->
            Either.right(
                List.of(
                    new PartitionJoinOperation(
                        joinPartitionRequest.memberId(),
                        joinPartitionRequest.partitionId(),
                        joinPartitionRequest.priority())));
    return handleRequest(requestTransformer);
  }

  @Override
  public ActorFuture<TopologyChangeStatus> leavePartition(
      final LeavePartitionRequest leavePartitionRequest) {

    final TopologyChangeRequest requestTransformer =
        ignore ->
            Either.right(
                List.of(
                    new PartitionLeaveOperation(
                        leavePartitionRequest.memberId(), leavePartitionRequest.partitionId())));
    return handleRequest(requestTransformer);
  }

  @Override
  public ActorFuture<TopologyChangeStatus> reassignPartitions(
      final ReassignPartitionsRequest reassignPartitionsRequest) {
    final var transformer =
        new PartitionReassignRequestTransformer(reassignPartitionsRequest.members());
    return handleRequest(transformer);
  }

  private ActorFuture<TopologyChangeStatus> handleRequest(final TopologyChangeRequest transformer) {
    final ActorFuture<TopologyChangeStatus> responseFuture = executor.createFuture();
    coordinator
        .applyOperations(transformer)
        .onComplete(
            (result, error) -> {
              if (error == null) {
                // TODO: This must be return result directly
                final var changeStatus =
                    result.finalTopology().pendingChanges().isPresent()
                        ? new TopologyChangeStatus(
                            result.finalTopology().pendingChanges().get().id(),
                            StatusCode.IN_PROGRESS)
                        : new TopologyChangeStatus(
                            result.finalTopology().version(), StatusCode.COMPLETED);
                responseFuture.complete(changeStatus);
              } else {
                responseFuture.completeExceptionally(error);
              }
            });
    return responseFuture;
  }
}

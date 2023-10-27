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
import io.camunda.zeebe.topology.api.TopologyManagementRequest.ScaleRequest;
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
public final class TopologyManagementRequestsHandler implements TopologyManagementApi {

  private final TopologyChangeCoordinator coordinator;
  private final ConcurrencyControl executor;

  public TopologyManagementRequestsHandler(
      final TopologyChangeCoordinator coordinator, final ConcurrencyControl executor) {
    this.coordinator = coordinator;
    this.executor = executor;
  }

  @Override
  public ActorFuture<TopologyChangeResponse> addMembers(final AddMembersRequest addMembersRequest) {
    return handleRequest(new AddMembersTransformer(addMembersRequest.members()));
  }

  @Override
  public ActorFuture<TopologyChangeResponse> removeMembers(
      final RemoveMembersRequest removeMembersRequest) {
    return handleRequest(new RemoveMembersTransformer(removeMembersRequest.members()));
  }

  @Override
  public ActorFuture<TopologyChangeResponse> joinPartition(
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
  public ActorFuture<TopologyChangeResponse> leavePartition(
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
  public ActorFuture<TopologyChangeResponse> reassignPartitions(
      final ReassignPartitionsRequest reassignPartitionsRequest) {
    final var transformer =
        new PartitionReassignRequestTransformer(reassignPartitionsRequest.members());
    return handleRequest(transformer);
  }

  @Override
  public ActorFuture<TopologyChangeResponse> scaleMembers(final ScaleRequest scaleRequest) {
    return handleRequest(new ScaleRequestTransformer(scaleRequest.members()));
  }

  private ActorFuture<TopologyChangeResponse> handleRequest(
      final TopologyChangeRequest transformer) {
    final ActorFuture<TopologyChangeResponse> responseFuture = executor.createFuture();
    coordinator
        .applyOperations(transformer)
        .onComplete(
            (result, error) -> {
              if (error == null) {
                final var changeStatus =
                    new TopologyChangeResponse(
                        result.currentTopology().members(),
                        result.finalTopology().members(),
                        result.operations());
                responseFuture.complete(changeStatus);
              } else {
                responseFuture.completeExceptionally(error);
              }
            });
    return responseFuture;
  }
}

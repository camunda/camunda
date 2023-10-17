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
import io.camunda.zeebe.topology.api.TopologyManagementResponse.StatusCode;
import io.camunda.zeebe.topology.api.TopologyManagementResponse.TopologyChangeStatus;
import io.camunda.zeebe.topology.changes.TopologyChangeCoordinator;
import io.camunda.zeebe.topology.state.TopologyChangeOperation;
import io.camunda.zeebe.topology.state.TopologyChangeOperation.MemberJoinOperation;
import io.camunda.zeebe.topology.state.TopologyChangeOperation.PartitionChangeOperation.PartitionJoinOperation;
import io.camunda.zeebe.topology.state.TopologyChangeOperation.PartitionChangeOperation.PartitionLeaveOperation;
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
    return applyOperations(
        addMembersRequest.members().stream()
            .map(MemberJoinOperation::new)
            .map(TopologyChangeOperation.class::cast)
            .toList());
  }

  @Override
  public ActorFuture<TopologyChangeStatus> joinPartition(
      final JoinPartitionRequest joinPartitionRequest) {
    return applyOperations(
        List.of(
            new PartitionJoinOperation(
                joinPartitionRequest.memberId(),
                joinPartitionRequest.partitionId(),
                joinPartitionRequest.priority())));
  }

  @Override
  public ActorFuture<TopologyChangeStatus> leavePartition(
      final LeavePartitionRequest leavePartitionRequest) {
    return applyOperations(
        List.of(
            new PartitionLeaveOperation(
                leavePartitionRequest.memberId(), leavePartitionRequest.partitionId())));
  }

  private ActorFuture<TopologyChangeStatus> applyOperations(
      final List<TopologyChangeOperation> operations) {
    final ActorFuture<TopologyChangeStatus> responseFuture = executor.createFuture();
    coordinator
        .applyOperations(operations)
        .onComplete(
            (topology, error) -> {
              if (error == null) {
                final var status =
                    new TopologyChangeStatus(topology.version(), StatusCode.IN_PROGRESS);
                responseFuture.complete(status);
              } else {
                responseFuture.completeExceptionally(error);
              }
            });

    return responseFuture;
  }
}

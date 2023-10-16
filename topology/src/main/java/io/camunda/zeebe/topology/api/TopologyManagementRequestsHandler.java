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
import io.camunda.zeebe.topology.api.TopologyManagementResponse.StatusCode;
import io.camunda.zeebe.topology.api.TopologyManagementResponse.TopologyChangeStatus;
import io.camunda.zeebe.topology.changes.TopologyChangeCoordinator;
import io.camunda.zeebe.topology.state.TopologyChangeOperation;
import io.camunda.zeebe.topology.state.TopologyChangeOperation.MemberJoinOperation;

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
    final ActorFuture<TopologyChangeStatus> responseFuture = executor.createFuture();
    final var operations =
        addMembersRequest.members().stream()
            .map(MemberJoinOperation::new)
            .map(TopologyChangeOperation.class::cast)
            .toList();

    final var operationApplied = coordinator.applyOperations(operations);
    operationApplied.onComplete(
        (topology, error) -> {
          if (error == null) {
            final var status =
                new TopologyManagementResponse.TopologyChangeStatus(
                    topology.version(), StatusCode.IN_PROGRESS);
            responseFuture.complete(status);
          } else {
            responseFuture.completeExceptionally(error);
          }
        });

    return responseFuture;
  }
}

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.system.partitions.impl.steps;

import io.atomix.raft.RaftServer.Role;
import io.camunda.zeebe.broker.system.partitions.PartitionTransitionContext;
import io.camunda.zeebe.broker.system.partitions.PartitionTransitionStep;
import io.camunda.zeebe.protocol.ColumnFamilyScope;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import io.camunda.zeebe.snapshots.transfer.SnapshotTransferServiceImpl;
import java.util.Set;

public class SnapshotApiHandlerTransitionStep implements PartitionTransitionStep {

  // Register a snapshot transfer service when transitioning to leader
  @Override
  public ActorFuture<Void> prepareTransition(
      final PartitionTransitionContext context, final long term, final Role targetRole) {
    if (context.getCurrentRole().isLeader()) {
      context.getSnapshotApiRequestHandler().removeTransferService(context.getPartitionId());
    }
    return CompletableActorFuture.completed();
  }

  @Override
  public ActorFuture<Void> transitionTo(
      final PartitionTransitionContext context, final long term, final Role targetRole) {
    if (targetRole.isLeader()) {
      final var service =
          new SnapshotTransferServiceImpl(
              context.getPersistedSnapshotStore(),
              context.getPartitionId(),
              (from, to) ->
                  context.snapshotCopy().copySnapshot(from, to, Set.of(ColumnFamilyScope.GLOBAL)),
              context.getSnapshotApiRequestHandler());
      context.getSnapshotApiRequestHandler().addTransferService(context.getPartitionId(), service);
    }
    return CompletableActorFuture.completed();
  }

  @Override
  public String getName() {
    return "SnapshotApiHandlerTransitionStep";
  }
}

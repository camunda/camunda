/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker.system.partitions.impl.steps;

import io.atomix.raft.RaftServer.Role;
import io.camunda.zeebe.broker.system.partitions.PartitionTransitionContext;
import io.camunda.zeebe.broker.system.partitions.PartitionTransitionStep;
import io.camunda.zeebe.util.sched.future.ActorFuture;
import io.camunda.zeebe.util.sched.future.CompletableActorFuture;

public class StoragePartitionTransitionStep implements PartitionTransitionStep {

  @Override
  public ActorFuture<Void> transitionTo(
      final PartitionTransitionContext context, final long term, final Role role) {

    switch (role) {
      case LEADER:
        {
          context
              .getConstructableSnapshotStore()
              .addSnapshotListener(context.getSnapshotController());
          context.getSnapshotController().stopConsumeReplicatedSnapshots();
          break;
        }
      case FOLLOWER:
      case PASSIVE:
      case CANDIDATE:
      case PROMOTABLE:
        {
          context
              .getConstructableSnapshotStore()
              .removeSnapshotListener(context.getSnapshotController());
          context.getSnapshotController().consumeReplicatedSnapshots();
          break;
        }
      case INACTIVE:
      default:
        {
          context
              .getConstructableSnapshotStore()
              .removeSnapshotListener(context.getSnapshotController());
          context.getSnapshotController().stopConsumeReplicatedSnapshots();
          break;
        }
    }
    return CompletableActorFuture.completed(null);
  }

  @Override
  public String getName() {
    return "Update Snapshot Listeners";
  }
}

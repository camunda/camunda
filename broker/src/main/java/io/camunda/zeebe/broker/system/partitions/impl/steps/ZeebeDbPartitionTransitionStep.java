/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker.system.partitions.impl.steps;

import io.atomix.raft.RaftServer.Role;
import io.camunda.zeebe.broker.Loggers;
import io.camunda.zeebe.broker.system.partitions.PartitionTransitionContext;
import io.camunda.zeebe.broker.system.partitions.PartitionTransitionStep;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.util.sched.future.ActorFuture;
import io.camunda.zeebe.util.sched.future.CompletableActorFuture;

public class ZeebeDbPartitionTransitionStep implements PartitionTransitionStep {

  @Override
  public ActorFuture<Void> prepareTransition(
      final PartitionTransitionContext context, final long term, final Role targetRole) {

    final var currentRole = context.getCurrentRole();
    if (currentRole == Role.LEADER || targetRole == Role.INACTIVE) {
      try {
        context.getStateController().closeDb();
        context.setZeebeDb(null);
      } catch (final Exception e) {
        return CompletableActorFuture.completedExceptionally(e);
      }
    }

    return CompletableActorFuture.completed(null);
  }

  @Override
  public ActorFuture<Void> transitionTo(
      final PartitionTransitionContext context, final long term, final Role targetRole) {
    final var currentRole = context.getCurrentRole();

    if (targetRole == Role.INACTIVE) {
      return CompletableActorFuture.completed(null);
    }
    if (currentRole == Role.LEADER
        || currentRole == Role.INACTIVE
        || context.getZeebeDb() == null) {

      final ZeebeDb zeebeDb;
      try {
        context.getStateController().recover();
        zeebeDb = context.getStateController().openDb();
      } catch (final Exception e) {
        Loggers.SYSTEM_LOGGER.error("Failed to recover from snapshot", e);

        return CompletableActorFuture.completedExceptionally(
            new IllegalStateException(
                String.format(
                    "Unexpected error occurred while recovering snapshot controller during leader partition install for partition %d",
                    context.getPartitionId()),
                e));
      }

      context.setZeebeDb(zeebeDb);
    }
    return CompletableActorFuture.completed(null);
  }

  @Override
  public String getName() {
    return "ZeebeDb";
  }
}

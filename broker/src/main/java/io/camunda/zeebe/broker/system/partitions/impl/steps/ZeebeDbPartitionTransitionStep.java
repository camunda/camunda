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
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.util.sched.future.ActorFuture;
import io.camunda.zeebe.util.sched.future.CompletableActorFuture;

public final class ZeebeDbPartitionTransitionStep implements PartitionTransitionStep {

  private static final String RECOVERY_FAILED_ERROR_MSG =
      "Unexpected error occurred while recovering snapshot controller during leader partition install for partition %d";

  @Override
  public ActorFuture<Void> prepareTransition(
      final PartitionTransitionContext context, final long term, final Role targetRole) {

    final var currentRole = context.getCurrentRole();
    if (context.getZeebeDb() != null
        && (currentRole == Role.LEADER || targetRole == Role.INACTIVE)) {
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
    final CompletableActorFuture<Void> transitionFuture = new CompletableActorFuture<>();
    final var currentRole = context.getCurrentRole();

    if (targetRole == Role.INACTIVE) {
      return CompletableActorFuture.completed(null);
    }
    if (currentRole == Role.LEADER
        || currentRole == Role.INACTIVE
        || context.getZeebeDb() == null) {

      recoverDb(context, transitionFuture);
    } else {
      transitionFuture.complete(null);
    }
    return transitionFuture;
  }

  @Override
  public String getName() {
    return "ZeebeDb";
  }

  private void recoverDb(
      final PartitionTransitionContext context,
      final CompletableActorFuture<Void> transitionFuture) {
    final ActorFuture<ZeebeDb> recoverFuture;

    recoverFuture = context.getStateController().recover();

    recoverFuture.onComplete(
        (zeebeDb, error) -> {
          if (error != null) {
            transitionFuture.completeExceptionally(
                new IllegalStateException(
                    String.format(RECOVERY_FAILED_ERROR_MSG, context.getPartitionId()), error));
          } else {
            context.setZeebeDb(zeebeDb);
            transitionFuture.complete(null);
          }
        });
  }
}

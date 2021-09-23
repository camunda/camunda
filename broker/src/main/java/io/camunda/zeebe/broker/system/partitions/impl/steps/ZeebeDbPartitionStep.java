/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker.system.partitions.impl.steps;

import io.camunda.zeebe.broker.system.partitions.PartitionStartupAndTransitionContextImpl;
import io.camunda.zeebe.broker.system.partitions.PartitionStep;
import io.camunda.zeebe.broker.system.partitions.PartitionTransitionContext;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.util.sched.future.ActorFuture;
import io.camunda.zeebe.util.sched.future.CompletableActorFuture;

public class ZeebeDbPartitionStep implements PartitionStep {

  private static final String RECOVER_FAILED_ERROR_MSG =
      "Unexpected error occurred while recovering snapshot controller during leader partition install for partition %d";

  @Override
  public ActorFuture<Void> open(final PartitionStartupAndTransitionContextImpl context) {
    final CompletableActorFuture<Void> openFuture = new CompletableActorFuture<>();
    recoverDb(context, openFuture);
    return openFuture;
  }

  @Override
  public ActorFuture<Void> close(final PartitionStartupAndTransitionContextImpl context) {
    context.setZeebeDb(null);
    try {
      context.getStateController().closeDb();
    } catch (final Exception e) {
      return CompletableActorFuture.completedExceptionally(e);
    }

    return CompletableActorFuture.completed(null);
  }

  @Override
  public String getName() {
    return "ZeebeDb";
  }

  private void recoverDb(
      final PartitionTransitionContext context, final CompletableActorFuture<Void> openFuture) {
    final ActorFuture<ZeebeDb> recoverFuture;

    recoverFuture = context.getStateController().recover();

    recoverFuture.onComplete(
        (zeebeDb, error) -> {
          if (error != null) {
            openFuture.completeExceptionally(
                new IllegalStateException(
                    String.format(RECOVER_FAILED_ERROR_MSG, context.getPartitionId()), error));
          } else {
            context.setZeebeDb(zeebeDb);
            openFuture.complete(null);
          }
        });
  }
}

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker.system.partitions.impl.steps;

import io.camunda.zeebe.broker.Loggers;
import io.camunda.zeebe.broker.system.partitions.PartitionBootstrapContext;
import io.camunda.zeebe.broker.system.partitions.PartitionBootstrapStep;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.util.sched.future.ActorFuture;
import io.camunda.zeebe.util.sched.future.CompletableActorFuture;

public class ZeebeDbPartitionBootstrapStep implements PartitionBootstrapStep {

  @Override
  public ActorFuture<PartitionBootstrapContext> open(final PartitionBootstrapContext context) {
    context.getConstructableSnapshotStore().addSnapshotListener(context.getStateController());

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
    return CompletableActorFuture.completed(context);
  }

  @Override
  public ActorFuture<PartitionBootstrapContext> close(final PartitionBootstrapContext context) {
    // ZeebeDb is closed in the StateController's close()
    context.setZeebeDb(null);
    return CompletableActorFuture.completed(context);
  }

  @Override
  public String getName() {
    return "ZeebeDb";
  }
}

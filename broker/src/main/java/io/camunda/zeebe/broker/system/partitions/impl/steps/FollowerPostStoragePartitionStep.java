/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker.system.partitions.impl.steps;

import io.camunda.zeebe.broker.system.partitions.PartitionBoostrapAndTransitionContextImpl;
import io.camunda.zeebe.broker.system.partitions.PartitionStep;
import io.camunda.zeebe.util.sched.future.ActorFuture;
import io.camunda.zeebe.util.sched.future.CompletableActorFuture;

public class FollowerPostStoragePartitionStep implements PartitionStep {

  @Override
  public ActorFuture<Void> open(final PartitionBoostrapAndTransitionContextImpl context) {
    context.getSnapshotController().consumeReplicatedSnapshots();
    return CompletableActorFuture.completed(null);
  }

  @Override
  public ActorFuture<Void> close(final PartitionBoostrapAndTransitionContextImpl context) {
    return CompletableActorFuture.completed(null);
  }

  @Override
  public String getName() {
    return "ConsumeReplicatedSnapshots";
  }
}

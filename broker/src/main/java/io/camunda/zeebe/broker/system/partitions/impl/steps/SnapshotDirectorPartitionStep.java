/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.broker.system.partitions.impl.steps;

import io.zeebe.broker.system.partitions.PartitionContext;
import io.zeebe.broker.system.partitions.PartitionStep;
import io.zeebe.broker.system.partitions.impl.AsyncSnapshotDirector;
import io.zeebe.util.sched.future.ActorFuture;
import java.time.Duration;

public class SnapshotDirectorPartitionStep implements PartitionStep {

  @Override
  public ActorFuture<Void> open(final PartitionContext context) {
    final Duration snapshotPeriod = context.getBrokerCfg().getData().getSnapshotPeriod();
    final AsyncSnapshotDirector snapshotDirector =
        new AsyncSnapshotDirector(
            context.getNodeId(),
            context.getStreamProcessor(),
            context.getSnapshotController(),
            context.getLogStream(),
            snapshotPeriod);

    context.setSnapshotDirector(snapshotDirector);
    return context.getScheduler().submitActor(snapshotDirector);
  }

  @Override
  public ActorFuture<Void> close(final PartitionContext context) {
    final ActorFuture<Void> future = context.getSnapshotDirector().closeAsync();
    context.setSnapshotDirector(null);
    return future;
  }

  @Override
  public String getName() {
    return "AsyncSnapshotDirector";
  }
}

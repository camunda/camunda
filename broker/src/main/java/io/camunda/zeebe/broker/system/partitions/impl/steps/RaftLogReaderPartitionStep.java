/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.broker.system.partitions.impl.steps;

import io.atomix.raft.storage.log.RaftLogReader.Mode;
import io.zeebe.broker.Loggers;
import io.zeebe.broker.system.partitions.PartitionContext;
import io.zeebe.broker.system.partitions.PartitionStep;
import io.zeebe.util.sched.future.ActorFuture;
import io.zeebe.util.sched.future.CompletableActorFuture;

public class RaftLogReaderPartitionStep implements PartitionStep {

  @Override
  public ActorFuture<Void> open(final PartitionContext context) {
    final var reader = context.getRaftPartition().getServer().openReader(Mode.COMMITS);
    context.setRaftLogReader(reader);
    return CompletableActorFuture.completed(null);
  }

  @Override
  public ActorFuture<Void> close(final PartitionContext context) {
    try {
      context.getRaftLogReader().close();
    } catch (final Exception e) {
      Loggers.SYSTEM_LOGGER.error(
          "Unexpected error closing Raft log reader for partition {}", context.getPartitionId(), e);
      return CompletableActorFuture.completedExceptionally(e);
    } finally {
      context.setRaftLogReader(null);
    }

    return CompletableActorFuture.completed(null);
  }

  @Override
  public String getName() {
    return "RaftLogReader";
  }
}

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.broker.partitioning.steps;

import io.camunda.eventbridge.broker.logstreams.LogRetentionCompactor;
import io.camunda.eventbridge.broker.partitioning.PartitionContext;
import io.camunda.eventbridge.broker.partitioning.PartitionStartupStep;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import java.time.Duration;

/**
 * Starts the {@link LogRetentionCompactor} on the partition leader so older log segments are
 * compacted down to the configured record-retention window. Only the leader compacts; followers
 * receive the deletions through Raft. A non-positive {@code maxRecords} disables retention.
 */
public final class LogRetentionStep implements PartitionStartupStep {

  private final long maxRecords;
  private final Duration interval;

  private LogRetentionCompactor compactor;

  public LogRetentionStep(final long maxRecords, final long compactionIntervalMs) {
    this.maxRecords = maxRecords;
    interval = Duration.ofMillis(compactionIntervalMs);
  }

  @Override
  public String getName() {
    return "Log Retention";
  }

  @Override
  public ActorFuture<Void> activate(final PartitionContext context) {
    if (maxRecords <= 0) {
      return CompletableActorFuture.completed(null);
    }
    compactor =
        new LogRetentionCompactor(
            context.getPartitionId(),
            context.getRaftPartition(),
            context.getHighWatermark(),
            maxRecords,
            interval);
    return context.getActorScheduler().submitActor(compactor);
  }

  @Override
  public ActorFuture<Void> deactivate(final PartitionContext context) {
    if (compactor == null) {
      return CompletableActorFuture.completed(null);
    }
    final var closed = compactor.closeAsync();
    compactor = null;
    return closed;
  }
}

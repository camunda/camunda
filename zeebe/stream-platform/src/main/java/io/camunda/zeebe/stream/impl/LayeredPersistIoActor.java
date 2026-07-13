/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.stream.impl;

import io.camunda.cluster.PartitionId;
import io.camunda.zeebe.db.layered.LayeredStoreCoordinator.MergeRound;
import io.camunda.zeebe.db.layered.LayeredStoreCoordinator.PersistRound;
import io.camunda.zeebe.logstreams.impl.Loggers;
import io.camunda.zeebe.scheduler.Actor;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.stream.impl.LayeredStatePersistence.PersistIo;
import org.slf4j.Logger;

/**
 * The IO side of the stream processor's layered persist rounds and pipeline merges (experimental;
 * one per partition, submitted with {@link io.camunda.zeebe.scheduler.SchedulingHints#ioBound()} so
 * the blocking RocksDB batch commit runs on the scheduler's IO thread group, off the processing
 * actor): {@link #persist(PersistRound)} runs a round's drain-and-commit step here, {@link
 * #merge(MergeRound)} a merge round's index-only k-way walk, each returning a future the driver
 * marshals back onto the processing actor to complete the round.
 *
 * <p>Safe off the owner thread by the round contracts: {@link PersistRound#persist()} and {@link
 * MergeRound#merge()} touch only the immutable captured segments (plus, for persists, the round's
 * dedicated persist context) — never a store's mutable layers — and both are single-flight per
 * domain. Running persists and merges on the same single-threaded actor additionally serializes
 * them against each other, so a merge overlapping a round never executes concurrently with it.
 */
final class LayeredPersistIoActor extends Actor implements PersistIo {

  private static final Logger LOG = Loggers.LOGSTREAMS_LOGGER;

  LayeredPersistIoActor(final PartitionId partitionId) {
    super("LayeredPersistIo", partitionId);
  }

  /**
   * Runs the round's persist step on this actor. The returned future completes exceptionally when
   * the drain fails (the caller completes the round as failed and the segments stay buffered) or
   * when this actor closed before the round ran.
   */
  @Override
  public ActorFuture<Void> persist(final PersistRound round) {
    return actor.call(
        () -> {
          round.persist();
          return null;
        });
  }

  /**
   * Runs the merge round's k-way walk on this actor. The returned future completes exceptionally
   * when the merge fails (the caller completes it as failed and the captured runs stay unmerged) or
   * when this actor closed before the merge ran.
   */
  @Override
  public ActorFuture<Void> merge(final MergeRound round) {
    return actor.call(
        () -> {
          round.merge();
          return null;
        });
  }

  @Override
  protected void handleFailure(final Throwable failure) {
    // the persist/merge future is completed exceptionally with the same failure; the driver
    // completes the round as failed and the next trigger retries it — this actor keeps serving
    LOG.warn(
        "Layered persist or merge round failed on the IO actor '{}'; the round's segments stay"
            + " buffered and the next round retries them",
        getName(),
        failure);
  }
}

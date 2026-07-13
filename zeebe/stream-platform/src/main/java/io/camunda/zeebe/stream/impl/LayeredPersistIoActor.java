/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.stream.impl;

import io.camunda.cluster.PartitionId;
import io.camunda.zeebe.db.layered.LayeredStoreCoordinator.PersistRound;
import io.camunda.zeebe.logstreams.impl.Loggers;
import io.camunda.zeebe.scheduler.Actor;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import org.slf4j.Logger;

/**
 * The IO side of the stream processor's layered persist rounds (experimental; one per partition,
 * submitted with {@link io.camunda.zeebe.scheduler.SchedulingHints#ioBound()} so the blocking
 * RocksDB batch commit runs on the scheduler's IO thread group, off the processing actor): {@link
 * #persist(PersistRound)} runs the round's drain-and-commit step here and returns a future the
 * driver marshals back onto the processing actor to complete the round.
 *
 * <p>Safe off the owner thread by the persist-round contract: {@link PersistRound#persist()}
 * touches only the immutable captured segments and the round's dedicated persist context — never a
 * store's mutable layers — and rounds are single-flight per domain, so this actor never runs two
 * persists of one domain concurrently.
 */
final class LayeredPersistIoActor extends Actor {

  private static final Logger LOG = Loggers.LOGSTREAMS_LOGGER;

  LayeredPersistIoActor(final PartitionId partitionId) {
    super("LayeredPersistIo", partitionId);
  }

  /**
   * Runs the round's persist step on this actor. The returned future completes exceptionally when
   * the drain fails (the caller completes the round as failed and the segments stay buffered) or
   * when this actor closed before the round ran.
   */
  ActorFuture<Void> persist(final PersistRound round) {
    return actor.call(
        () -> {
          round.persist();
          return null;
        });
  }

  @Override
  protected void handleFailure(final Throwable failure) {
    // the persist future is completed exceptionally with the same failure; the driver completes
    // the round as failed and the next trigger retries it — this actor keeps serving rounds
    LOG.warn(
        "Layered persist round failed on the IO actor '{}'; the round's segments stay buffered"
            + " and the next round retries them",
        getName(),
        failure);
  }
}

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
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import io.camunda.zeebe.stream.impl.LayeredStatePersistence.PersistIo;
import java.time.Duration;
import org.slf4j.Logger;

/**
 * The IO side of the stream processor's layered persist rounds and pipeline merges (experimental;
 * one per partition, submitted with {@link io.camunda.zeebe.scheduler.SchedulingHints#ioBound()} so
 * the blocking RocksDB batch commits run on the scheduler's IO thread group, off the processing
 * actor): {@link #persist(PersistRound, DrainPacer)} runs a round's drain here as a <em>paced</em>
 * sequence of sub-batch slices, {@link #merge(MergeRound)} a merge round's index-only k-way walk,
 * each returning a future the driver marshals back onto the processing actor to complete the round.
 *
 * <p><b>Paced slicing:</b> each {@link PersistRound#persistSlice(long) slice} of at least the
 * configured minimum bytes commits its own batch as one actor job. Between slices the loop yields —
 * it re-submits itself to the <em>end</em> of this actor's queue when the pacer asks for no wait,
 * so a queued merge job interleaves between slices instead of waiting for the whole round, and it
 * re-schedules itself after the pacer's delay otherwise. Nothing ever blocks the actor thread. A
 * merge interleaving a round is safe: the merge's captured run is disjoint from the round's
 * segments, and both still execute on this single actor, never concurrently.
 *
 * <p>Safe off the owner thread by the round contracts: {@code persistSlice} and {@link
 * MergeRound#merge()} touch only the immutable captured structures (plus, for persists, the round's
 * dedicated persist context) — never a store's mutable layers — and both are single-flight per
 * domain.
 *
 * <p>If this actor closes mid-round (shutdown, failover), the in-flight persist future completes
 * exceptionally: the driver completes the round as failed while its completion callback still finds
 * the processing actor alive, the captured segments stay buffered, and — should the whole processor
 * die before that — the successor completes the stale round forward during recovery (partial slices
 * carry no anchor, so state never runs ahead of what a re-drain rewrites).
 */
final class LayeredPersistIoActor extends Actor implements PersistIo {

  private static final Logger LOG = Loggers.LOGSTREAMS_LOGGER;

  private final long minSliceBytes;
  private CompletableActorFuture<Void> inFlightPersist;

  LayeredPersistIoActor(final PartitionId partitionId, final long minSliceBytes) {
    super("LayeredPersistIo", partitionId);
    this.minSliceBytes = minSliceBytes;
  }

  /**
   * Runs the round's paced, sliced drain on this actor. The returned future completes once the
   * final (anchor-carrying) slice committed, and exceptionally when a slice fails, the actor closes
   * mid-round, or the actor closed before the round ran — in each failure case the caller completes
   * the round as failed and the segments stay buffered.
   */
  @Override
  public ActorFuture<Void> persist(final PersistRound round, final DrainPacer pacer) {
    final CompletableActorFuture<Void> done = new CompletableActorFuture<>();
    actor.call(
        () -> {
          inFlightPersist = done;
          drainSlice(round, pacer, done);
        });
    return done;
  }

  private void drainSlice(
      final PersistRound round, final DrainPacer pacer, final CompletableActorFuture<Void> done) {
    if (done.isDone()) {
      return; // completed exceptionally by onActorClosing; do not touch the round anymore
    }
    try {
      if (round.persistSlice(minSliceBytes)) {
        inFlightPersist = null;
        done.complete(null);
        return;
      }
      final long delayNanos = pacer.delayNanos(round.progress(), System.nanoTime());
      if (delayNanos <= 0) {
        // end-of-queue on purpose: a queued merge job gets to run between two slices
        actor.submit(() -> drainSlice(round, pacer, done));
      } else {
        actor.schedule(Duration.ofNanos(delayNanos), () -> drainSlice(round, pacer, done));
      }
    } catch (final Exception e) {
      inFlightPersist = null;
      done.completeExceptionally(e);
    }
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
  protected void onActorClosing() {
    if (inFlightPersist != null && !inFlightPersist.isDone()) {
      // a paced round's next slice would never run on a closed actor; fail the round now so the
      // driver completes it (segments stay buffered) while the processing actor is still alive
      inFlightPersist.completeExceptionally(
          new IllegalStateException(
              "The layered persist IO actor closed mid-round; the round's segments stay buffered"));
      inFlightPersist = null;
    }
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

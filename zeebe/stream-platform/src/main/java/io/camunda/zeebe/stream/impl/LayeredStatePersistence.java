/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.stream.impl;

import io.camunda.zeebe.db.layered.PersistTrigger;
import io.camunda.zeebe.db.layered.zdb.LayeredDomain;
import io.camunda.zeebe.db.layered.zdb.LayeredZeebeDb;
import io.camunda.zeebe.logstreams.impl.Loggers;
import java.time.Duration;
import java.util.Objects;
import java.util.function.LongSupplier;
import org.slf4j.Logger;

/**
 * Drives the persist and freeze cadences of a {@link LayeredZeebeDb}'s engine domain on the stream
 * processor's actor thread (experimental; only active when the layered-state flag is on). All
 * rounds run inline and synchronously on that thread:
 *
 * <ul>
 *   <li>{@link #onPeriodicTick()} — the regular persist cadence, invoked at the configured persist
 *       interval; drains whenever anything is buffered.
 *   <li>{@link #onFreezeTick()} — the freeze cadence, invoked at the configured freeze interval;
 *       freezes the active overlays into pipeline segments and republishes the read view whenever
 *       new batches were committed, bounding the staleness asynchronous view readers observe to
 *       roughly the freeze interval.
 *   <li>{@link #onBatchCommitted()} — invoked after every committed processing batch; forces a
 *       round as soon as the buffered bytes exceed their budget.
 *   <li>{@link #tryPersistForSnapshot()} — invoked before a snapshot checkpoint so the durable
 *       store holds a cut at least as new as the position the snapshot will claim.
 *   <li>{@link #tryFreezeForScheduledTask()} — invoked before an asynchronous checker executes, so
 *       the view it acquires observes every batch committed before its execution.
 * </ul>
 *
 * <p>Neither rounds nor freezes run while a batch is in flight: a mid-batch cut would have to
 * include uncommitted staging writes. Periodic ticks simply skip and retry on their next occasion;
 * the pre-snapshot and pre-scheduled-task variants report the conflict so the caller can retry once
 * the batch completed.
 */
final class LayeredStatePersistence {

  private static final Logger LOG = Loggers.LOGSTREAMS_LOGGER;

  private final LayeredDomain domain;
  private final LongSupplier lastProcessedPosition;
  private final Duration persistInterval;
  private final Duration freezeInterval;

  /**
   * @param db the layered database the stream processor writes through
   * @param lastProcessedPosition supplies the watermark of a round: the highest log position whose
   *     effects the buffered state contains (the last successfully committed position)
   */
  LayeredStatePersistence(final LayeredZeebeDb<?> db, final LongSupplier lastProcessedPosition) {
    this.lastProcessedPosition =
        Objects.requireNonNull(lastProcessedPosition, "lastProcessedPosition");
    domain = db.defaultDomain();
    persistInterval = db.config().persistInterval();
    freezeInterval = db.config().freezeInterval();
    // build the coordinator eagerly: every layered column family must exist by now (they are all
    // created during recovery and record-processor init), and failing fast here beats failing on
    // the first persist round
    domain.coordinator();
  }

  Duration persistInterval() {
    return persistInterval;
  }

  Duration freezeInterval() {
    return freezeInterval;
  }

  /** The regular persist cadence; never throws — a failed round stays buffered and is retried. */
  void onPeriodicTick() {
    if (domain.batchInFlight() || !domain.hasBufferedWrites()) {
      return;
    }
    tryPersist(PersistTrigger.INTERVAL);
  }

  /**
   * The regular freeze cadence: freezes the active overlays into pipeline segments and republishes
   * the read view whenever batches were committed since the last freeze. Cheap — pointer swaps and
   * flattens, no durable IO. Skips while a batch is in flight (staging must be empty on a freeze)
   * and when there is nothing new to freeze; the next tick catches up.
   */
  void onFreezeTick() {
    if (domain.batchInFlight() || !domain.hasActiveWrites()) {
      return;
    }
    domain.freezeNow(lastProcessedPosition.getAsLong());
  }

  /**
   * Freezes the active overlays right before an asynchronous checker executes, so the read view the
   * checker acquires observes every batch committed before its execution — the freshness that keeps
   * event-driven checkers from losing wake-ups (a scan that misses a committed-but-unfrozen timer
   * could otherwise derive no next wake-up and never run again).
   *
   * @return false if a batch is in flight — its staging writes must never surface, so the caller
   *     retries once the batch completed (matching today's semantics, where a checker's scan sees
   *     committed batches only)
   */
  boolean tryFreezeForScheduledTask() {
    if (domain.batchInFlight()) {
      return false;
    }
    if (domain.hasActiveWrites()) {
      domain.freezeNow(lastProcessedPosition.getAsLong());
    }
    return true;
  }

  /**
   * Makes every committed batch visible to a scheduled task about to execute on the stream
   * processor's actor. Event-driven checkers (timers, job backoff) re-derive their next wake-up
   * from persisted-state scans; a scan that misses a committed-but-buffered entry would lose that
   * wake-up permanently, so the buffer is drained before the task runs.
   *
   * @return false if a batch is in flight — its staging writes must never surface, so the caller
   *     re-submits the task to run after the batch completed (matching today's semantics, where a
   *     scan sees committed batches only)
   */
  boolean prepareForScheduledTask() {
    if (domain.batchInFlight()) {
      return false;
    }
    if (domain.hasBufferedWrites()) {
      // a failure keeps the segments buffered; the task still runs — a stale scan is preferable
      // to blocking all scheduled work while persistence is failing
      tryPersist(PersistTrigger.SCHEDULED_TASK);
    }
    return true;
  }

  /** Forces a round right after a batch commit once the buffered bytes exceed their budget. */
  void onBatchCommitted() {
    if (!domain.overCapacity() || domain.batchInFlight()) {
      return;
    }
    tryPersist(PersistTrigger.OVER_CAPACITY);
  }

  /**
   * Runs a pre-snapshot round so the durable store contains everything committed so far.
   *
   * @return false if a batch is in flight and the caller must retry, true once the buffered state
   *     is drained (or there was nothing to drain)
   * @throws io.camunda.zeebe.db.ZeebeDbException if the round fails; the snapshot must not proceed
   *     on a stale durable cut
   */
  boolean tryPersistForSnapshot() {
    if (domain.batchInFlight()) {
      return false;
    }
    if (domain.hasBufferedWrites()) {
      domain.persistNow(lastProcessedPosition.getAsLong(), PersistTrigger.PRE_SNAPSHOT);
    }
    return true;
  }

  private void tryPersist(final PersistTrigger trigger) {
    try {
      domain.persistNow(lastProcessedPosition.getAsLong(), trigger);
    } catch (final Exception e) {
      // recoverable by design: the segments stay in their pipelines and the next round retries
      // them, while the log remains the durable source of everything buffered
      LOG.warn("Failed to persist buffered layered state (trigger: {}); will retry", trigger, e);
    }
  }
}

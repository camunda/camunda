/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.broker.publish;

import io.camunda.eventbridge.broker.flowcontrol.FlowControl;
import io.camunda.eventbridge.broker.logstreams.EventStreamListener;
import io.camunda.eventbridge.broker.watermark.HighWatermark;
import io.camunda.zeebe.logstreams.storage.LogStorage;
import io.camunda.zeebe.scheduler.Actor;
import io.camunda.zeebe.scheduler.ScheduledTimer;
import java.time.Duration;
import java.time.InstantSource;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.function.Predicate;

/**
 * Per-partition actor that drains the inbound MPSC queue and batches writes to LogStorage.
 *
 * <p><b>Batching & Latency:</b>
 * Uses an adaptive state machine. If the pipeline is idle, inbound data is flushed immediately
 * for minimum latency. If appends are currently in-flight, a linger timer accumulates subsequent
 * data into larger batches to maximize throughput and preserve Raft IOPS.
 *
 * <p><b>Memory & GC Management:</b>
 * Bounded by {@code maxInFlightAppends}. Uses a pre-allocated object pool of {@link PendingAppend}
 * contexts to ensure that array manipulation, closures, and futures generate zero heap garbage
 * during the hot path.
 */
public final class EventStreamPublisher extends Actor {

  private final int partitionId;
  private final LogStorage logStorage;
  private final EventStreamListener listener;
  private final FlowControl flowControl;
  private final InstantSource clock;
  private final Duration lingerInterval;
  private final InboundQueue inbound;
  private final HighWatermark highWatermark;

  private final int maxBatchesPerDrain;
  private final int maxBytesTotalBatches = 4 * 1024 * 1024;
  private final int maxBytesPerDrain = maxBytesTotalBatches - (4 * 1024); // Reserve 4KB for framing
  private final int maxInFlightAppends;
  // --- Drain State ---
  // Shared accumulation target. Cleared at the beginning of each tryDrain() cycle.
  private final List<InflightBatchEntry> drainedEntries;
  private int currentDrainBytes;
  private final Predicate<InflightBatchEntry> drainVisitor = this::visitDrainedEntry;
  // --- Flush State & Context Pools ---
  // Contains unused contexts ready for the next flush.
  private final Queue<PendingAppend> appendPool;
  // Tracks contexts currently handed off to LogStorage. Used to salvage permits on shutdown.
  private final List<PendingAppend> activeAppends;

  private int inFlightAppends;
  private long position; // Monotonically increasing logical position
  private ScheduledTimer lingerTimer;

  public EventStreamPublisher(
      final int partitionId,
      final LogStorage logStorage,
      final EventStreamListener listener,
      final FlowControl flowControl,
      final long initialPosition,
      final InstantSource clock,
      final int maxBatchesPerDrain,
      final Duration lingerInterval,
      final int maxInFlightAppends,
      final InboundQueue inbound,
      final HighWatermark highWatermark) {
    if (lingerInterval.toMillis() <= 0) {
      throw new IllegalArgumentException("lingerInterval must be strictly > 0 to protect Raft IOPS");
    }

    this.partitionId = partitionId;
    this.logStorage = logStorage;
    this.listener = listener;
    this.flowControl = flowControl;
    position = initialPosition;
    this.clock = clock;
    this.maxBatchesPerDrain = maxBatchesPerDrain;
    this.lingerInterval = lingerInterval;
    this.maxInFlightAppends = maxInFlightAppends;
    this.inbound = inbound;
    this.highWatermark = highWatermark;

    drainedEntries = new ArrayList<>(maxBatchesPerDrain);

    // Initialize pools to the exact required capacity
    activeAppends = new ArrayList<>(maxInFlightAppends);
    appendPool = new ArrayDeque<>(maxInFlightAppends);
    for (int i = 0; i < maxInFlightAppends; i++) {
      appendPool.offer(new PendingAppend(this, maxBatchesPerDrain));
    }
  }

  @Override
  public String getName() {
    return "EventStreamAppender-" + partitionId;
  }

  @Override
  protected void onActorClosing() {
    cancelLingerTimer();
    drainedEntries.clear();
    currentDrainBytes = 0;

    int unreleasedPermits = 0;
    final var error = new IllegalStateException(
        "Event stream appender for partition " + partitionId + " closing");

    // Phase 1: Drain and fail anything remaining in the inbound queue
    inbound.drainAndCheckRemaining(entry -> {
      listener.onFailed(entry.requestId(), error);
      return true; // continue draining, ignoring max limits
    });

    // Phase 2: Fail anything drained but not yet submitted to LogStorage
    for (int i = 0; i < drainedEntries.size(); i++) {
      final var entry = drainedEntries.get(i);
      listener.onFailed(entry.requestId(), error);
      unreleasedPermits += entry.entryCount();
    }

    // Phase 3: Salvage flow control permits trapped in asynchronous Raft futures
    for (int i = 0; i < activeAppends.size(); i++) {
      final var append = activeAppends.get(i);
      unreleasedPermits += append.entryCount();

      for (int j = 0; j < append.entriesSize(); j++) {
        listener.onFailed(append.getEntry(j).requestId(), error);
      }
    }

    if (unreleasedPermits > 0) {
      flowControl.release(unreleasedPermits);
    }

    activeAppends.clear();
  }

  /**
   * Signals the actor to process data. Coalesced by {@link InboundQueue#scheduleDrain}.
   */
  public void submitDrain() {
    actor.submit(this::onDataAvailable);
  }

  /**
   * Internal callback mechanism invoked by {@link PendingAppend#run()} to schedule
   * completion logic without allocating a lambda wrapper.
   */
  void submitAppendCompletion(final PendingAppend append) {
    actor.submit(append);
  }

  private void onDataAvailable() {
    if (inFlightAppends == 0) {
      // Fast path: clear any stale timer and flush immediately
      cancelLingerTimer();
      tryDrain();
    } else {
      // Pipeline under load: wait for timer to batch elements
      scheduleLingerIfNeeded();
    }
  }

  private void onLingerExpired() {
    lingerTimer = null;
    tryDrain();
  }

  /** Evaluates entries during the drain cycle against throughput boundaries. */
  private boolean visitDrainedEntry(final InflightBatchEntry entry) {
    drainedEntries.add(entry);
    currentDrainBytes += entry.batchLength();

    // Stop draining if we hit max limits, allowing the queue to yield and flush.
    return drainedEntries.size() < maxBatchesPerDrain && currentDrainBytes < maxBytesPerDrain;
  }

  private void tryDrain() {
    if (inFlightAppends >= maxInFlightAppends) {
      scheduleLingerIfNeeded();
      return;
    }

    drainedEntries.clear();
    currentDrainBytes = 0;

    final boolean hasRemaining = inbound.drainAndCheckRemaining(drainVisitor);

    if (!drainedEntries.isEmpty()) {
      flush();

      // If data remains and pipeline isn't full, yield to the scheduler and continue immediately
      if (hasRemaining && inFlightAppends < maxInFlightAppends) {
        actor.submit(this::tryDrain);
      }
    } else if (hasRemaining) {
      // Mid-write producer race detected in the MPSC queue. Re-schedule to pick it up.
      actor.submit(this::tryDrain);
    }
  }

  private void flush() {
    final var append = appendPool.poll();
    if (append == null) {
      throw new IllegalStateException("Bug: No PendingAppend available.");
    }

    append.reset(position);

    // Clean encapsulation! Publisher doesn't manage the internal math anymore.
    for (int i = 0; i < drainedEntries.size(); i++) {
      append.addEntry(drainedEntries.get(i));
    }

    final long firstBatchPosition = position;
    position += append.entryCount(); // Optimistically advance position

    try {
      logStorage.append(
          firstBatchPosition,
          append.lastPosition(),
          append.getWriter(clock.millis()),
          append
      );

      inFlightAppends++;
      activeAppends.add(append);

    } catch (final Exception e) {
      position = firstBatchPosition; // Rollback

      for (int i = 0; i < append.entriesSize(); i++) {
        listener.onFailed(append.getEntry(i).requestId(), e);
      }
      flowControl.release(append.entryCount());
      appendPool.offer(append);
    }

    drainedEntries.clear();
  }

  /**
   * Processes terminal states from LogStorage (Commit or Failure).
   */
  void onAppendCompleted(final PendingAppend append) {
    inFlightAppends--;
    activeAppends.remove(append);

    if (append.commitError() != null) {
      for (int i = 0; i < append.entriesSize(); i++) {
        listener.onFailed(append.getEntry(i).requestId(), append.commitError());
      }
    } else {
      highWatermark.onCommitted(append.lastPosition(), append.batchLength());

      long currentPos = append.firstPosition();
      for (int i = 0; i < append.entriesSize(); i++) {
        final var entry = append.getEntry(i);
        final long first = currentPos;
        final long last = currentPos + entry.entryCount() - 1;

        listener.onCommitted(entry.requestId(), first, last);
        currentPos += entry.entryCount();
      }
    }

    flowControl.release(append.entryCount());
    appendPool.offer(append);

    if (inbound.hasData()) {
      actor.submit(this::tryDrain);
    }
  }

  private void scheduleLingerIfNeeded() {
    if (lingerTimer == null) {
      lingerTimer = actor.schedule(lingerInterval, this::onLingerExpired);
    }
  }

  private void cancelLingerTimer() {
    if (lingerTimer != null) {
      lingerTimer.cancel();
      lingerTimer = null;
    }
  }
}

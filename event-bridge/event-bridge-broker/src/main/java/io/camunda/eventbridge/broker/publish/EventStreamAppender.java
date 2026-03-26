/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.broker.publish;

import io.camunda.eventbridge.broker.logstreams.EventStreamListener;
import io.camunda.zeebe.logstreams.storage.LogStorage;
import io.camunda.zeebe.scheduler.Actor;
import io.camunda.zeebe.scheduler.ScheduledTimer;
import java.time.Duration;
import java.time.InstantSource;
import java.util.ArrayList;
import java.util.List;

/**
 * Per-partition actor that drains the inbound ring buffer and appends batches to LogStorage.
 *
 * <p>Idle-efficient: the appender only wakes up when data arrives. On first data arrival after
 * idle, it schedules a linger timer. When the timer fires or max batches are reached, it flushes.
 * If no new data arrives after a flush, the appender goes back to sleep — no periodic polling.
 *
 * <p>State machine:
 *
 * <pre>
 *                    data arrives
 * IDLE ──────────────────────────────► LINGERING
 *  ▲                                      │
 *  │                                      │ timer fires or max batches
 *  │                                      ▼
 *  │                                   DRAINING
 *  │                                      │
 *  │           no data                    │ has data
 *  └──────────────────────────────────────┘
 * </pre>
 */
public final class EventStreamAppender extends Actor {

  private final int partitionId;
  private final LogStorage logStorage;
  private final EventStreamListener listener;
  private final InstantSource clock;
  private final int maxBatchesPerDrain;
  private final Duration lingerInterval;

  private final InboundQueue inbound;
  private final List<InflightBatchEntry> drainedEntries = new ArrayList<>();

  private int inFlightAppends;
  private final int maxInFlightAppends;

  private long position;
  private ScheduledTimer lingerTimer;

  public EventStreamAppender(
      final int partitionId,
      final LogStorage logStorage,
      final EventStreamListener listener,
      final long initialPosition,
      final InstantSource clock,
      final int maxBatchesPerDrain,
      final Duration lingerInterval,
      final int maxInFlightAppends,
      final InboundQueue inbound) {
    this.partitionId = partitionId;
    this.logStorage = logStorage;
    this.listener = listener;
    position = initialPosition;
    this.clock = clock;
    this.maxBatchesPerDrain = maxBatchesPerDrain;
    this.lingerInterval = lingerInterval;
    this.maxInFlightAppends = maxInFlightAppends;
    this.inbound = inbound;
  }

  @Override
  public String getName() {
    return "EventStreamAppender-" + partitionId;
  }

  @Override
  protected void onActorClosing() {
    cancelLingerTimer();

    drainedEntries.clear();
    inbound.drain(drainedEntries, maxBatchesPerDrain);

    final var error =
        new IllegalStateException(
            "Event stream appender for partition " + partitionId + " closing");

    for (final var entry : drainedEntries) {
      listener.onFailed(entry.requestId(), error);
    }
  }

  public void submitDrain() {
    actor.submit(this::onDataAvailable);
  }

  private void onDataAvailable() {
    if (lingerTimer == null) {
      lingerTimer = actor.schedule(lingerInterval, this::onLingerExpired);
    }
  }

  private void onLingerExpired() {
    if (lingerTimer == null) {
      return;
    }
    lingerTimer = null;
    tryDrain();
  }

  private void tryDrain() {
    if (inFlightAppends >= maxInFlightAppends) {
      if (lingerTimer == null) {
        lingerTimer = actor.schedule(lingerInterval, this::onLingerExpired);
      }
      return;
    }

    drainedEntries.clear();
    inbound.drain(drainedEntries, maxBatchesPerDrain);

    if (!drainedEntries.isEmpty()) {
      flush();
    } else if (inbound.hasData()) {
      if (lingerTimer == null) {
        lingerTimer = actor.schedule(lingerInterval, this::onLingerExpired);
      }
    }
  }

  private void flush() {
    final var firstBatchPosition = position;
    final var timestamp = clock.millis();

    final var resolved = new ArrayList<PendingAppend.ResolvedEntry>(drainedEntries.size());
    int totalDataLength = 0;

    for (final var entry : drainedEntries) {
      final var entryFirstPosition = position;
      final var entryLastPosition = position + entry.entryCount() - 1;

      resolved.add(
          new PendingAppend.ResolvedEntry(
              entry.requestId(), entryFirstPosition, entryLastPosition));

      position += entry.entryCount();
      totalDataLength += entry.batchLength();
    }

    final var lastEntryPosition = position - 1;
    final var entriesSnapshot = List.copyOf(drainedEntries);
    final var resolvedSnapshot = List.copyOf(resolved);

    final var pendingAppend = new PendingAppend(resolvedSnapshot);

    final var writer =
        new BatchBufferWriter(entriesSnapshot, totalDataLength, firstBatchPosition, timestamp);

    try {
      logStorage.append(firstBatchPosition, lastEntryPosition, writer, pendingAppend);

      inFlightAppends++;

      pendingAppend
          .getCommitFuture()
          .onComplete(
              (ok, error) ->
                  actor.submit(
                      () -> {
                        if (error != null) {
                          onAppendFailed(pendingAppend, error);
                        } else {
                          onAppendCommitted(pendingAppend);
                        }
                      }));

    } catch (final Exception e) {
      position = firstBatchPosition;
      onAppendFailed(pendingAppend, e);
    }
  }

  private void onAppendCommitted(final PendingAppend append) {
    for (final var entry : append.getEntries()) {
      listener.onCommitted(entry.requestId(), entry.firstPosition(), entry.lastPosition());
    }
  }

  private void onAppendFailed(final PendingAppend append, final Throwable error) {
    for (final var entry : append.getEntries()) {
      listener.onFailed(entry.requestId(), error);
    }
  }

  private void cancelLingerTimer() {
    if (lingerTimer != null) {
      lingerTimer.cancel();
      lingerTimer = null;
    }
  }
}

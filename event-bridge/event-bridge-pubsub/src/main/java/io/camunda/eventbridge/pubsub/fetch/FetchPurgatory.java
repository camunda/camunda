/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.pubsub.fetch;

import io.camunda.eventbridge.pubsub.threading.ActorWorkSignaler;
import io.camunda.eventbridge.pubsub.watermark.HighWatermark;
import io.camunda.eventbridge.pubsub.watermark.PartitionWatermark;
import io.camunda.zeebe.scheduler.Actor;
import java.time.Duration;
import java.time.InstantSource;
import java.util.ArrayList;
import java.util.TreeMap;
import java.util.function.Consumer;

/**
 * Actor-based Purgatory for delayed long-polling fetches on a single partition.
 *
 * <p><b>Architecture & Performance Design:</b> Implements a lock-free Dual Wait-Queue to achieve
 * O(1) evaluation latency. Requests are indexed strictly by the condition blocking them (offset vs.
 * accumulated bytes).
 */
public final class FetchPurgatory extends Actor {

  private final int partitionId;
  private final HighWatermark highWatermark;
  private final InstantSource clock;
  private final ActorWorkSignaler evaluateWatermarkSignaler;
  private final TreeMap<Long, ArrayList<DelayedFetch>> offsetWaiters = new TreeMap<>();
  private final TreeMap<Long, ArrayList<DelayedFetch>> byteWaiters = new TreeMap<>();
  private Consumer<FetchTask> timeoutHandler;
  private final Consumer<DelayedFetch> onFetchTimeout = this::handleTimeOut;
  private Consumer<FetchTask> fetchDispatcher;

  public FetchPurgatory(
      final int partitionId, final HighWatermark highWatermark, final InstantSource clock) {
    this.partitionId = partitionId;
    this.highWatermark = highWatermark;
    this.clock = clock;
    evaluateWatermarkSignaler = new ActorWorkSignaler(this::doEvaluateWatermark);
  }

  @Override
  public String getName() {
    return "FetchPurgatory-" + partitionId;
  }

  @Override
  protected void onActorClosing() {
    offsetWaiters.values().forEach(b -> b.forEach(DelayedFetch::cancelTimer));
    byteWaiters.values().forEach(b -> b.forEach(DelayedFetch::cancelTimer));
    offsetWaiters.clear();
    byteWaiters.clear();
  }

  public void park(
      final FetchTask task, final long requiredDelta, final PartitionWatermark parkedWatermark) {
    actor.submit(
        () -> {
          final var now = clock.millis();
          if (now >= task.deadlineMs() || task.isCancelled()) {
            timeoutHandler.accept(task);
            return;
          }

          final long parkedBytes = parkedWatermark.committedBytes();
          final long targetBytes = parkedBytes + requiredDelta;
          final long targetOffset = task.offset();

          // TOCTOU Safety: Validate against current watermark in case a commit arrived while queued
          final var currentWatermark = highWatermark.get();
          if (currentWatermark.commitPosition() >= targetOffset
              && currentWatermark.committedBytes() >= targetBytes) {
            fetchDispatcher.accept(task);
            return;
          }

          final var delayed = new DelayedFetch(task, parkedBytes, requiredDelta, onFetchTimeout);
          final var timeout = Duration.ofMillis(task.deadlineMs() - now);
          final var timeoutTask = actor.schedule(timeout, delayed);
          delayed.setTimeoutTask(timeoutTask);

          if (targetOffset > currentWatermark.commitPosition()) {
            offsetWaiters.computeIfAbsent(targetOffset, k -> new ArrayList<>()).add(delayed);
          } else {
            byteWaiters.computeIfAbsent(targetBytes, k -> new ArrayList<>()).add(delayed);
          }
        });
  }

  public void onWatermarkAdvanced() {
    evaluateWatermarkSignaler.signal(actor);
  }

  private void doEvaluateWatermark() {
    final var watermark = highWatermark.get();
    drainOffsetWaiters(watermark.commitPosition(), watermark.committedBytes());
    drainByteWaiters(watermark.committedBytes());
  }

  private void drainOffsetWaiters(final long currentPos, final long currentBytes) {
    while (!offsetWaiters.isEmpty() && offsetWaiters.firstKey() <= currentPos) {
      final var bucket = offsetWaiters.pollFirstEntry().getValue();

      for (int i = 0; i < bucket.size(); i++) {
        final DelayedFetch delayed = bucket.get(i);

        if (delayed.isCancelled()) {
          delayed.cancelTimer();
        } else if (currentBytes >= delayed.targetBytes()) {
          delayed.cancelTimer();
          fetchDispatcher.accept(delayed.task());
        } else {
          // Offset reached, but data volume lacking. Move to byte queue.
          byteWaiters.computeIfAbsent(delayed.targetBytes(), k -> new ArrayList<>()).add(delayed);
        }
      }
    }
  }

  private void drainByteWaiters(final long currentBytes) {
    while (!byteWaiters.isEmpty() && byteWaiters.firstKey() <= currentBytes) {
      final var bucket = byteWaiters.pollFirstEntry().getValue();

      for (int i = 0; i < bucket.size(); i++) {
        final DelayedFetch delayed = bucket.get(i);
        delayed.cancelTimer();

        if (!delayed.isCancelled()) {
          fetchDispatcher.accept(delayed.task());
        }
      }
    }
  }

  private void handleTimeOut(final DelayedFetch delayed) {
    removeDelayed(offsetWaiters, delayed.targetOffset(), delayed);
    removeDelayed(byteWaiters, delayed.targetBytes(), delayed);
    timeoutHandler.accept(delayed.task());
  }

  private void removeDelayed(
      final TreeMap<Long, ArrayList<DelayedFetch>> map,
      final long key,
      final DelayedFetch delayed) {
    final var bucket = map.get(key);
    if (bucket != null && bucket.remove(delayed) && bucket.isEmpty()) {
      map.remove(key);
    }
  }

  public void setFetchDispatcher(final Consumer<FetchTask> fetchDispatcher) {
    actor.submit(
        () -> {
          this.fetchDispatcher = fetchDispatcher;
        });
  }

  public void setTimeoutHandler(final Consumer<FetchTask> timeoutHandler) {
    actor.submit(
        () -> {
          this.timeoutHandler = timeoutHandler;
        });
  }
}

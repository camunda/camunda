/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.eventplane.service;

import io.camunda.zeebe.eventplane.notification.UploadNotice;
import io.camunda.zeebe.eventplane.segment.LogId;
import java.time.Duration;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.jspecify.annotations.Nullable;

/**
 * An open notification stream's share of the notices: those of the logs it subscribed to, queued
 * for the thread that writes the stream. Delivery is best effort, so a subscriber that doesn't keep
 * up loses the notices that don't fit; it finds those segments by listing.
 */
final class Subscription {

  static final int CAPACITY = 1024;

  /** Wakes the reader of a closed subscription. Never delivered. */
  private static final UploadNotice WAKE_UP =
      new UploadNotice("-", "-", 1, new UUID(0, 0), "-", 0, 0, 0, 0, 0);

  private final Set<LogId> logs;
  private final BlockingQueue<UploadNotice> queue = new LinkedBlockingQueue<>(CAPACITY);
  private volatile boolean closed;

  Subscription(final Set<LogId> logs) {
    this.logs = Set.copyOf(logs);
  }

  /** Whether the notices of the log are for this subscription. */
  boolean subscribedTo(final LogId log) {
    return logs.contains(log);
  }

  /** Queues the notice, unless the queue is full. Returns whether it was queued. */
  boolean offer(final UploadNotice notice) {
    return !closed && queue.offer(notice);
  }

  /**
   * The next notice, waiting for at most the timeout.
   *
   * @return the notice, or {@code null} if there was none in time, or the subscription was closed
   */
  @Nullable UploadNotice poll(final Duration timeout) throws InterruptedException {
    final var notice = queue.poll(timeout.toNanos(), TimeUnit.NANOSECONDS);
    return notice == WAKE_UP ? null : notice;
  }

  boolean isClosed() {
    return closed;
  }

  /** Ends the subscription, and wakes the thread that waits for its next notice. */
  void close() {
    closed = true;
    // If the queue is full, the reader is not waiting, and sees that the subscription is closed.
    queue.offer(WAKE_UP);
  }
}

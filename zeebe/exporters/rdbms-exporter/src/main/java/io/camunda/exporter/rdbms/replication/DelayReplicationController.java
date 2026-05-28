/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.rdbms.replication;

import io.camunda.zeebe.exporter.api.context.Controller;
import io.camunda.zeebe.exporter.api.context.ScheduledTask;
import java.time.Duration;
import java.time.InstantSource;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Delays acknowledgment of flushed exporter positions by a fixed duration. On each flush, the
 * position is queued with its release time ({@code now + delay}). A periodic check drains expired
 * entries and acknowledges the highest expired position to the broker controller.
 *
 * <p>On close, pending entries are discarded without acknowledgment. This is intentional: it is
 * safer to re-export from the last committed position on restart than to acknowledge too early and
 * risk data loss after a failover.
 */
public class DelayReplicationController implements ReplicationController {

  public static final int DEFAULT_QUEUE_CAPACITY = 8192;

  private static final Logger LOG = LoggerFactory.getLogger(DelayReplicationController.class);

  private final Controller controller;
  private final Duration delay;
  private final InstantSource clock;
  private final int partitionId;
  private final BlockingQueue<DelayedEntry> pendingEntries;

  private volatile ScheduledTask checkTask;

  public DelayReplicationController(
      final Controller controller,
      final Duration delay,
      final InstantSource clock,
      final int partitionId) {
    this.controller = controller;
    this.delay = delay;
    this.clock = clock;
    this.partitionId = partitionId;
    pendingEntries = new ArrayBlockingQueue<>(DEFAULT_QUEUE_CAPACITY);
    checkTask = controller.scheduleCancellableTask(delay, this::checkDue);
  }

  @Override
  public void onFlush(final long exporterPosition) {
    final long releaseTimeMs = clock.millis() + delay.toMillis();
    if (!pendingEntries.offer(new DelayedEntry(exporterPosition, releaseTimeMs))) {
      LOG.warn(
          "[RDBMS Exporter P{}] Delay replication queue is full, dropping entry (position={})",
          partitionId,
          exporterPosition);
    }
  }

  void checkDue() {
    final long now = clock.millis();
    long highestExpired = Long.MIN_VALUE;

    DelayedEntry entry;
    while ((entry = pendingEntries.peek()) != null) {
      if (entry.releaseTimeMs() > now) {
        break;
      }
      highestExpired = entry.position();
      pendingEntries.poll();
    }

    if (highestExpired != Long.MIN_VALUE) {
      LOG.debug(
          "[RDBMS Exporter P{}] Acknowledging delayed position {}", partitionId, highestExpired);
      controller.updateLastExportedRecordPosition(highestExpired);
    }

    // if null, controller was closed during check
    if (checkTask != null) {
      checkTask = controller.scheduleCancellableTask(delay, this::checkDue);
    }
  }

  @Override
  public void close() {
    final ScheduledTask task = checkTask;
    checkTask = null;
    if (task != null) {
      task.cancel();
    }
    // Pending entries are intentionally discarded: the exporter will re-export from the last
    // acknowledged position on restart, which is safer than acknowledging too early.
  }

  record DelayedEntry(long position, long releaseTimeMs) {}
}

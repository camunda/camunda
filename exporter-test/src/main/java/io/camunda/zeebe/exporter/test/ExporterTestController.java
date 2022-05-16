/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.exporter.test;

import io.camunda.zeebe.exporter.api.context.Controller;
import io.camunda.zeebe.exporter.api.context.ScheduledTask;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import net.jcip.annotations.ThreadSafe;

/**
 * A thread safe implementation of {@link Controller}. Tasks are scheduled and executed
 * synchronously. To trigger execution of scheduled tasks, a manual call to {@link
 * #runScheduledTasks(Duration)} is required. Time is always relative to the last call to this
 * method.
 *
 * <p>NOTE: if a task is scheduled with a {@link Duration#ZERO}, it is <em>not</em> ran immediately,
 * but instead will run the next time {@link #runScheduledTasks(Duration)} is called.
 *
 * <p>NOTE: tasks are not removed from the in-memory lists when they are executed. This is so you
 * can still assert/verify their properties even after they were executed. You can clear the task
 * lists if it grows to large via {@link #resetScheduledTasks()}
 */
@ThreadSafe
public final class ExporterTestController implements Controller {
  private static final long UNKNOWN_POSITION = -1;

  private final AtomicLong position = new AtomicLong(UNKNOWN_POSITION);
  private final List<ExporterTestScheduledTask> scheduledTasks = new CopyOnWriteArrayList<>();
  private volatile long lastRanAtMs = 0;

  @Override
  public void updateLastExportedRecordPosition(final long position) {
    this.position.getAndAccumulate(position, Math::max);
  }

  @Override
  public synchronized ScheduledTask scheduleCancellableTask(
      final Duration delay, final Runnable task) {
    final var scheduledTask =
        new ExporterTestScheduledTask(
            Objects.requireNonNull(delay, "must specify a task delay"),
            Objects.requireNonNull(task, "must specify a task"));

    scheduledTasks.add(scheduledTask);
    return scheduledTask;
  }

  /**
   * Clears the list of scheduled tasks and resets the time of the scheduler to 0. NOTE: this call
   * does not cancel scheduled tasks.
   */
  public synchronized void resetScheduledTasks() {
    lastRanAtMs = 0;
    scheduledTasks.clear();
  }

  /**
   * Returns the last updated position, as set via {@link #updateLastExportedRecordPosition(long)}.
   */
  public long getPosition() {
    return position.get();
  }

  /**
   * Returns all scheduled tasks since the last call to {@link #resetScheduledTasks()}, including
   * tasks that were already canceled or executed.
   */
  public List<ExporterTestScheduledTask> getScheduledTasks() {
    return scheduledTasks;
  }

  /** Returns the last time the scheduler ran. Primarily for debugging purposes. */
  public Instant getLastRanAt() {
    return Instant.ofEpochMilli(lastRanAtMs);
  }

  /**
   * Will run all tasks scheduled since the last time this was executed + the given duration.
   *
   * @param elapsed upper bound of tasks delay
   */
  public synchronized void runScheduledTasks(final Duration elapsed) {
    Objects.requireNonNull(elapsed, "must specify a tick duration");
    final Duration upperBound = elapsed.plusMillis(lastRanAtMs);

    scheduledTasks.stream()
        .filter(t -> t.getDelay().compareTo(upperBound) <= 0)
        .filter(t -> !t.isCanceled())
        .sorted(Comparator.comparing(ExporterTestScheduledTask::getDelay))
        .forEach(ExporterTestScheduledTask::run);

    lastRanAtMs = upperBound.toMillis();
  }
}

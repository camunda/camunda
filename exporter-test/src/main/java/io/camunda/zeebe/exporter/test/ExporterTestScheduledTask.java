/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.exporter.test;

import io.camunda.zeebe.exporter.api.context.ScheduledTask;
import java.time.Duration;
import java.util.Objects;
import net.jcip.annotations.ThreadSafe;

/**
 * A controllable, thread-safe implementation of {@link ScheduledTask}. Thread-safety is important
 * as exporters may cancel the task from a different thread, and it's not that difficult to
 * guarantee.
 *
 * <p>This implementation is meant to be used with {@link ExporterTestController}.
 */
@ThreadSafe
public final class ExporterTestScheduledTask implements ScheduledTask, Runnable {
  private final Duration delay;
  private final Runnable task;

  private volatile boolean isExecuted;
  private volatile boolean isCanceled;

  public ExporterTestScheduledTask(final Duration delay, final Runnable task) {
    this.delay = Objects.requireNonNull(delay, "must specify a task delay");
    this.task = Objects.requireNonNull(task, "must specify a task");
  }

  public Duration getDelay() {
    return delay;
  }

  public Runnable getTask() {
    return task;
  }

  public boolean isCanceled() {
    return isCanceled;
  }

  public boolean wasExecuted() {
    return isExecuted;
  }

  @Override
  public synchronized void run() {
    if (isCanceled || isExecuted) {
      return;
    }

    task.run();
    isExecuted = true;
  }

  @Override
  public synchronized void cancel() {
    if (isCanceled || isExecuted) {
      return;
    }

    isCanceled = true;
  }
}

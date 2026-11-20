/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.backup.common;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * A manually driven clock whose sleeps advance time instead of blocking, and that runs scheduled
 * tasks once time has advanced past them.
 */
final class FakeClock {

  private final List<ScheduledTask> scheduled = new ArrayList<>();
  private long nanos;
  private long sleptNanos;

  synchronized long nanos() {
    return nanos;
  }

  void sleep(final long sleepNanos) {
    synchronized (this) {
      sleptNanos += sleepNanos;
    }
    advance(Duration.ofNanos(sleepNanos));
  }

  void advance(final Duration duration) {
    final List<Runnable> due = new ArrayList<>();
    synchronized (this) {
      nanos += duration.toNanos();
      scheduled.removeIf(task -> task.dueNanos() <= nanos && due.add(task.task()));
    }
    due.forEach(Runnable::run);
  }

  synchronized void schedule(final long delayNanos, final Runnable task) {
    scheduled.add(new ScheduledTask(nanos + delayNanos, task));
  }

  synchronized Duration slept() {
    return Duration.ofNanos(sleptNanos);
  }

  private record ScheduledTask(long dueNanos, Runnable task) {}
}

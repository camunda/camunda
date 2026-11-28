/*
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.camunda.client.impl.backpressure;

import java.util.ArrayList;
import java.util.List;
import java.util.function.LongSupplier;

/**
 * Time that only moves when a test says so: serves as the clock and as the scheduler, running the
 * delays that fall due while time advances.
 */
final class ManualTime implements LongSupplier, BackpressureScheduler {

  private long now;
  private final List<Task> tasks = new ArrayList<>();

  ManualTime(final long start) {
    now = start;
  }

  @Override
  public long getAsLong() {
    return now;
  }

  @Override
  public Cancellable schedule(final Runnable task, final long delayMs) {
    final Task scheduled = new Task(now + Math.max(0, delayMs), task);
    tasks.add(scheduled);
    return () -> scheduled.cancelled = true;
  }

  /** Moves time forward, running every task that falls due on the way, in order. */
  void advance(final long millis) {
    final long target = now + millis;
    while (true) {
      Task next = null;
      for (final Task task : tasks) {
        if (!task.cancelled && task.dueAt <= target && (next == null || task.dueAt < next.dueAt)) {
          next = task;
        }
      }
      if (next == null) {
        break;
      }
      tasks.remove(next);
      now = Math.max(now, next.dueAt);
      next.action.run();
    }
    now = target;
  }

  /** The delays that are still waiting to fall due. */
  List<Long> pendingDelays() {
    final List<Long> delays = new ArrayList<>();
    for (final Task task : tasks) {
      if (!task.cancelled) {
        delays.add(task.dueAt - now);
      }
    }
    return delays;
  }

  private static final class Task {
    private final long dueAt;
    private final Runnable action;
    private boolean cancelled;

    private Task(final long dueAt, final Runnable action) {
      this.dueAt = dueAt;
      this.action = action;
    }
  }
}
